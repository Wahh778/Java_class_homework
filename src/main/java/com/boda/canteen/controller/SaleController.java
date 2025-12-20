package com.boda.canteen.controller;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.boda.canteen.common.MyTimeUtils;
import com.boda.canteen.common.R;
import com.boda.canteen.entity.BlanketOrder;
import com.boda.canteen.entity.MyUser;
import com.boda.canteen.entity.OrderForm;
import com.boda.canteen.entity.Sale;
import com.boda.canteen.security.service.BlanketOrderService;
import com.boda.canteen.security.service.MyUserService;
import com.boda.canteen.security.service.OrderFormService;
import com.boda.canteen.security.service.SaleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/sale")
public class SaleController {

    @Autowired
    private MyUserService myUserService;

    @Autowired
    private OrderFormService orderFormService;

    @Autowired
    private SaleService saleService;

    @Autowired
    private BlanketOrderService blanketOrderService;

    // ========== 新增：手动统计截止当前时间的月度销售 ==========
    @PreAuthorize("hasAnyRole('treasurer','manager')")
    @PostMapping("/manualAddMonthSale")
    public R<String> manualAddMonthSale(@RequestParam String month) {
        try {
            // 校验月份格式（YYYY-MM）
            if (StrUtil.isEmpty(month) || !month.matches("\\d{4}-\\d{2}")) {
                return R.fail("月份格式错误，需为YYYY-MM");
            }

            Sale sale = new Sale();
            sale.setMonth(month);

            // 构建当月时间范围
            String monthStart = month + "-01";
            Date begin = MyTimeUtils.getMonthOfBeginTime(monthStart);
            Date end = new Date(); // 截止当前时间

            // 统计当月订单总金额
            LambdaQueryWrapper<BlanketOrder> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.between(BlanketOrder::getCreateTime, begin, end);
            List<BlanketOrder> list = blanketOrderService.list(queryWrapper);

            Long totalPrice = 0L;
            for (BlanketOrder bo : list) {
                totalPrice += bo.getTotalPrice();
            }
            sale.setTotalPrice(totalPrice);

            // 先删除当月已存在的统计记录，再新增
            LambdaQueryWrapper<Sale> saleWrapper = new LambdaQueryWrapper<>();
            saleWrapper.eq(Sale::getMonth, month);
            saleService.remove(saleWrapper);

            boolean res = saleService.save(sale);
            if (res) {
                log.info("手动统计{}月度销售成功，总金额：{}", month, totalPrice);
                return R.success("手动统计" + month + "月度销售成功，总金额：" + totalPrice);
            } else {
                return R.fail("手动统计月度销售失败");
            }
        } catch (Exception e) {
            log.error("手动统计月度销售异常", e);
            return R.fail("手动统计失败：" + e.getMessage());
        }
    }

    // ========== 原有接口修复 ==========
    @PreAuthorize("hasAnyRole('treasurer','manager')")
    @GetMapping("/page")
    public R<Page<Sale>> page(int page, int limit,
                              @RequestParam(required = false) String month){

        Page<Sale> pageInfo = new Page<>(page, limit);

        LambdaQueryWrapper<Sale> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(StrUtil.isNotEmpty(month), Sale::getMonth, month);

        saleService.page(pageInfo, queryWrapper);

        return R.success(pageInfo);
    }

    @PreAuthorize("hasAnyRole('treasurer','manager')")
    @GetMapping("/monthlyOrder")
    public R<Page<MyUser>> monthlyOrder(int page, int limit,
                                        @RequestParam(required = false) String month){

        Page<MyUser> pageInfo = new Page<>(page, limit);

        LambdaQueryWrapper<MyUser> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(StrUtil.isNotEmpty(month), MyUser::getName, month)
                .orderByAsc(MyUser::getUserId);

        myUserService.page(pageInfo, queryWrapper);

        return R.success(pageInfo);
    }

    /**
     * 修复点1：details方法 - 解决GROUP BY和SELECT字段不匹配问题
     */
    @PreAuthorize("hasAnyRole('treasurer','manager')")
    @GetMapping("/details")
    public R<Page<BlanketOrder>> details(int page, int limit, HttpServletRequest request){
        String month = (String) request.getSession().getAttribute("month");
        Page<BlanketOrder> pageInfo = new Page<>(page, limit);

        QueryWrapper<BlanketOrder> queryWrapper = new QueryWrapper<>();
        if (StrUtil.isNotEmpty(month)){
            month = month + "-01";
            Date begin = MyTimeUtils.getMonthOfBeginTime(month);
            Date end = MyTimeUtils.getMonthOfEndTime(month);
            queryWrapper.between("createTime", begin, end);
        }
        // 修复：1. SELECT补充price（用MAX聚合） 2. GROUP BY包含所有非聚合字段
        queryWrapper.select(
                "name",
                "unit",
                "MAX(price) as price",  // 单价用MAX聚合，确保同一菜品单价一致
                "sum(weight) as weight",
                "sum(totalPrice) as totalPrice"
        );
        queryWrapper.groupBy("name, unit, price"); // GROUP BY包含price
        blanketOrderService.page(pageInfo, queryWrapper);

        return R.success(pageInfo);
    }

    /**
     * 修复点2：monthlyOrderDetails方法（核心）- 彻底解决only_full_group_by错误
     */
    @PreAuthorize("hasAnyRole('treasurer','manager')")
    @GetMapping("/monthlyOrderDetails")
    public R<Page<BlanketOrder>> monthlyOrderDetails(
            @RequestParam int page,
            @RequestParam int limit,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String month) {
        try {
            // 1. 新增：userId非空校验
            if (StrUtil.isEmpty(userId)) {
                return R.fail("用户ID不能为空");
            }

            QueryWrapper<OrderForm> orderFormWrapper = new QueryWrapper<>();
            orderFormWrapper.eq("userId", userId);

            // 2. 时间范围：截止当前时间
            if (StrUtil.isNotEmpty(month)) {
                String monthStart = month + "-01";
                Date begin = MyTimeUtils.getMonthOfBeginTime(monthStart);
                Date end = new Date();
                orderFormWrapper.between("orderTime", begin, end);
            }

            orderFormWrapper.select("orderId");
            List<OrderForm> orderForms = orderFormService.list(orderFormWrapper);

            if (orderForms.isEmpty()) {
                log.info("用户{}在{}月暂无订单记录", userId, month);
                return R.success(new Page<>(page, limit));
            }

            List<Long> orderIdList = orderForms.stream()
                    .map(OrderForm::getOrderId)
                    .collect(Collectors.toList());

            Page<BlanketOrder> pageInfo = new Page<>(page, limit);
            QueryWrapper<BlanketOrder> blanketWrapper = new QueryWrapper<>();
            blanketWrapper.in("orderId", orderIdList);

            // 核心修复：
            // - price用MAX()聚合（避免非聚合字段错误）
            // - GROUP BY包含所有非聚合字段（name, unit, price）
            blanketWrapper.select(
                    "name",
                    "unit",
                    "MAX(price) as price",  // 关键：单价聚合
                    "sum(weight) as weight",
                    "sum(totalPrice) as totalPrice"
            ).groupBy("name, unit, price"); // GROUP BY补充price

            blanketOrderService.page(pageInfo, blanketWrapper);

            return R.success(pageInfo);
        } catch (Exception e) {
            log.error("月度订单详情查询异常", e);
            return R.error("查询失败：" + e.getMessage());
        }
    }

    /**
     * 修复点3：print方法 - 同步修复GROUP BY逻辑
     */
    @GetMapping("/print/{saleIds}")
    public void print(@PathVariable String saleIds, HttpServletResponse response) {
        LambdaQueryWrapper<Sale> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(Sale::getSaleId, Arrays.stream(saleIds.split(",")).toArray());
        List<Sale> list = saleService.list(queryWrapper);
        ServletOutputStream out = null;
        if (list.size() >= 1) {
            int cnt = 0;
            try (ExcelWriter writer = ExcelUtil.getWriterWithSheet(list.get(cnt).getMonth())) {
                for (Sale sale : list) {
                    if (cnt != 0) writer.setSheet(sale.getMonth());
                    writer.merge(3, "月度销售统计总报表");
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("统计编号", String.valueOf(sale.getSaleId()));
                    map.put("", "");
                    map.put("统计年月", sale.getMonth());
                    map.put("总计金额", sale.getTotalPrice());
                    ArrayList<Map<String, Object>> list1 = new ArrayList<>();
                    list1.add(map);
                    writer.write(list1, true);
                    String month = sale.getMonth();
                    QueryWrapper<BlanketOrder> wrapper = new QueryWrapper<>();
                    if (StrUtil.isNotEmpty(month)){
                        month = month + "-01";
                        Date begin = MyTimeUtils.getMonthOfBeginTime(month);
                        Date end = MyTimeUtils.getMonthOfEndTime(month);
                        wrapper.between("createTime", begin, end);
                    }
                    // 修复print方法的GROUP BY
                    wrapper.select(
                            "name",
                            "unit",
                            "MAX(price) as price",
                            "sum(weight) as weight",
                            "sum(totalPrice) as totalPrice"
                    );
                    wrapper.groupBy("name, unit, price");
                    List<BlanketOrder> blanketOrderList = blanketOrderService.list(wrapper);
                    ArrayList<Map<String, Object>> list2 = new ArrayList<>();
                    for (BlanketOrder bo : blanketOrderList) {
                        Map<String, Object> map1 = new LinkedHashMap<>();
                        map1.put("菜品名称", bo.getName());
                        map1.put("计量单位", bo.getUnit());
                        map1.put("单价", bo.getPrice()); // 新增：导出单价
                        map1.put("数量", bo.getWeight());
                        map1.put("总计", bo.getTotalPrice());
                        list2.add(map1);
                    }
                    writer.write(list2, true);
                    cnt++;
                }
                response.setContentType("application/vnd.ms-excel;charset=utf-8");
                response.setHeader("Content-Disposition", "attachment;filename=saleAll.xls");
                out = response.getOutputStream();
                writer.flush(out, true);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (out != null) {
                    IoUtil.close(out);
                }
            }
        }
    }
}