package com.boda.canteen.controller;

import cn.hutool.core.date.DateUtil;
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

    /**
     * 销售分页接口
     */
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
     * 销售订单详情接口
     */
    @PreAuthorize("hasAnyRole('treasurer','manager')")
    @GetMapping("/details")
    public R<Page<BlanketOrder>> details(int page, int limit, HttpServletRequest request){
        String month = (String) request.getSession().getAttribute("month");
        Page<BlanketOrder> pageInfo = new Page<>(page, limit);
        System.out.println(11111);
        System.out.println(11111);

        QueryWrapper<BlanketOrder> queryWrapper = new QueryWrapper<>();
        if (StrUtil.isNotEmpty(month)){
            month = month + "-01";
            Date begin = MyTimeUtils.getMonthOfBeginTime(month);
            Date end = MyTimeUtils.getMonthOfEndTime(month);
            queryWrapper.between("createTime", begin, end);
        }
        // 通过sum(totalPrice)不存在直接单价×数量的问题
        queryWrapper.select("name, unit, sum(weight) as weight, sum(totalPrice) as totalPrice");
        queryWrapper.groupBy("name, unit, price");
        blanketOrderService.page(pageInfo, queryWrapper);

        return R.success(pageInfo);
    }

    //员工月度订单
    @PreAuthorize("hasAnyRole('treasurer','manager')")
    @GetMapping("/monthlyOrderDetails")
    public R<Page<OrderForm>> monthlyOrderDetails(
            @RequestParam int page,
            @RequestParam int limit,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String month) {

        QueryWrapper<OrderForm> orderFormWrapper = new QueryWrapper<>();

        // userId
        orderFormWrapper.eq("user_id", userId);

        // 月份范围
        String monthStart = month + "-01";
        Date begin = MyTimeUtils.getMonthOfBeginTime(monthStart);
        Date end = MyTimeUtils.getMonthOfEndTime(monthStart);
        orderFormWrapper.between("order_time", begin, end);

        // 只查 orderId
        orderFormWrapper.select("order_id");

        List<OrderForm> orderForms = orderFormService.list(orderFormWrapper);

        // 没有订单，直接返回空分页
        if (orderForms.isEmpty()) {
            return R.success(new Page<>(page, limit));
        }

        List<Long> orderIdList = orderForms.stream()
                .map(OrderForm::getOrderId)
                .collect(Collectors.toList());

        /* ================= ② 用 orderId 查 blanket_order ================= */

        Page<BlanketOrder> pageInfo = new Page<>(page, limit);
        QueryWrapper<BlanketOrder> blanketWrapper = new QueryWrapper<>();

        blanketWrapper.in("order_id", orderIdList);

        blanketWrapper
                .select(
                        "name",
                        "unit",
                        "sum(weight) as weight",
                        "sum(total_price) as totalPrice"
                )
                .groupBy("name", "unit");

        blanketOrderService.page(pageInfo, blanketWrapper);

        return R.success(pageInfo);
    }


    /**
     * 批量打印月度销售统计
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
                    wrapper.select("name, unit, sum(weight) as weight, sum(totalPrice) as totalPrice");
                    wrapper.groupBy("name, unit, price");
                    List<BlanketOrder> blanketOrderList = blanketOrderService.list(wrapper);
                    ArrayList<Map<String, Object>> list2 = new ArrayList<>();
                    for (BlanketOrder bo : blanketOrderList) {
                        Map<String, Object> map1 = new LinkedHashMap<>();
                        map1.put("菜品名称", bo.getName());
                        map1.put("计量单位", bo.getUnit());
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