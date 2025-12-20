package com.boda.canteen.controller;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import com.boda.canteen.common.MyTimeUtils;
import com.boda.canteen.common.R;
import com.boda.canteen.entity.*;
import com.boda.canteen.exception.CustomException;
import com.boda.canteen.security.service.BlanketOrderService;
import com.boda.canteen.security.service.OrderFormService;
import com.boda.canteen.security.service.ShopCartService;
import com.boda.canteen.security.service.TimeConfigService;
import io.jsonwebtoken.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/order")
public class OrderController {

    @Autowired
    private ShopCartService shopCartService;

    @Autowired
    private OrderFormService orderFormService;

    @Autowired
    private BlanketOrderService blanketOrderService;

    // 新增：注入时间配置服务
    @Autowired
    private TimeConfigService timeConfigService;


    /**
     * 订单提交接口
     * 核心规则：
     * 1. 配送时间段（本日orderDeadline ~ mealStartTime）：完全禁止点单
     * 2. 非配送时间段：本日菜单时间范围内（上一日mealStartTime ~ 本日orderDeadline）仅可提交一次订单，不可重复提交/覆盖
     */
    @PostMapping("/submit")
    @Transactional(rollbackFor = Exception.class) // 事务保障，防止数据不一致
    public R<String> submit(@RequestBody OrderForm orderForm, HttpServletRequest request){
        // ===== 1. 基础校验 =====
        MyUser currUser = (MyUser) request.getSession().getAttribute("currUser");
        if (currUser == null || currUser.getUserId() == null) {
            throw new CustomException("用户未登录，请先登录");
        }
        Long userId = currUser.getUserId();

        // ===== 2. 双重校验：是否处于配送时段（防止拦截器失效） =====
        TimeConfig timeConfig = timeConfigService.getCurrentConfig();
        String orderDeadlineStr = timeConfig.getOrderDeadline() == null ? "09:00:00" : timeConfig.getOrderDeadline();
        String mealStartTimeStr = timeConfig.getMealStartTime() == null ? "11:30:00" : timeConfig.getMealStartTime();

        LocalDate today = LocalDate.now();
        Date now = new Date();
        Date todayOrderDeadline = MyTimeUtils.getDateWithTime(today, orderDeadlineStr);
        Date todayMealStartTime = MyTimeUtils.getDateWithTime(today, mealStartTimeStr);

        // 判断是否在配送时段（orderDeadline <= 当前时间 <= mealStartTime）
        boolean isInDeliveryPeriod = !now.before(todayOrderDeadline) && !now.after(todayMealStartTime);
        if (isInDeliveryPeriod) {
            throw new CustomException("当前处于配送时间段，禁止提交订单！");
        }

        // ===== 3. 计算本日菜单时间范围（与菜单接口逻辑完全对齐） =====
        Date todayMenuStartTime, todayMenuEndTime;
        LocalDate baseDate;
        if (MyTimeUtils.isAfterTodayOrderDeadline(orderDeadlineStr)) {
            // 当前时间已超过本日orderDeadline → 本日菜单：本日mealStartTime ~ 明日orderDeadline
            baseDate = MyTimeUtils.getToday();
            todayMenuStartTime = MyTimeUtils.getDateWithTime(baseDate, mealStartTimeStr);
            todayMenuEndTime = MyTimeUtils.getDateWithTime(baseDate.plusDays(1), orderDeadlineStr);
        } else {
            // 当前时间未超过本日orderDeadline → 本日菜单：昨日mealStartTime ~ 本日orderDeadline
            baseDate = MyTimeUtils.getYesterday();
            todayMenuStartTime = MyTimeUtils.getDateWithTime(baseDate, mealStartTimeStr);
            todayMenuEndTime = MyTimeUtils.getDateWithTime(baseDate.plusDays(1), orderDeadlineStr);
        }
        log.info("用户{}本日菜单时间范围：{} ~ {}", userId,
                DateUtil.formatDateTime(todayMenuStartTime),
                DateUtil.formatDateTime(todayMenuEndTime));

        // ===== 4. 校验：本日菜单时间范围内是否已有订单（核心规则） =====
        LambdaQueryWrapper<OrderForm> existOrderQuery = new LambdaQueryWrapper<>();
        existOrderQuery.eq(OrderForm::getUserId, userId)
                .between(OrderForm::getOrderTime, todayMenuStartTime, todayMenuEndTime);
        long existOrderCount = orderFormService.count(existOrderQuery);

        if (existOrderCount > 0) {
            throw new CustomException("本日菜单时间范围内仅可提交一次订单，您已提交过，无法重复提交！");
        }

        // ===== 5. 购物车校验 =====
        LambdaQueryWrapper<ShopCart> cartQueryWrapper = new LambdaQueryWrapper<>();
        cartQueryWrapper.eq(ShopCart::getUserId, userId);
        List<ShopCart> shopCartList = shopCartService.list(cartQueryWrapper);
        if (shopCartList == null || shopCartList.isEmpty()) {
            throw new CustomException("购物车为空，无法提交订单");
        }

        // ===== 6. 生成新订单 =====
        // 生成唯一订单ID
        long newOrderId = RandomUtil.randomLong(100000L, 999999999999L);
        // 补齐订单信息
        orderForm.setOrderId(newOrderId);
        orderForm.setUserId(userId);
        orderForm.setName(currUser.getUsername());
        orderForm.setOrderTime(now);
        orderForm.setTelephone(currUser.getTelephone());
        orderForm.setWorkInformation(currUser.getWorkInformation());

        // 保存主订单
        boolean saveMainOrder = orderFormService.save(orderForm);
        if (!saveMainOrder) {
            log.error("用户{}主订单保存失败，订单号：{}", userId, newOrderId);
            throw new CustomException("订单提交失败：主订单保存失败");
        }

        // ===== 7. 保存订单明细 =====
        try {
            for (ShopCart cart : shopCartList) {
                BlanketOrder blanketOrder = new BlanketOrder();
                blanketOrder.setName(cart.getName());
                blanketOrder.setUnit(cart.getUnit());
                blanketOrder.setWeight(cart.getWeight());
                blanketOrder.setPrice(cart.getPrice());
                blanketOrder.setTotalPrice(cart.getTotalPrice());
                blanketOrder.setOrderId(newOrderId);
                blanketOrder.setCreateTime(now);

                boolean saveDetail = blanketOrderService.save(blanketOrder);
                if (!saveDetail) {
                    throw new CustomException("菜品【" + cart.getName() + "】明细保存失败");
                }
            }
        } catch (Exception e) {
            // 明细保存失败，回滚主订单
            orderFormService.removeById(newOrderId);
            log.error("用户{}订单明细保存失败，已回滚主订单，订单号：{}", userId, newOrderId, e);
            throw new CustomException("订单提交失败：" + e.getMessage());
        }

        // ===== 8. 清空购物车 =====
        LambdaQueryWrapper<ShopCart> clearCartQuery = new LambdaQueryWrapper<>();
        clearCartQuery.eq(ShopCart::getUserId, userId);
        boolean clearCart = shopCartService.remove(clearCartQuery);

        // ===== 9. 返回结果 =====
        if (clearCart) {
            log.info("用户{}订单提交成功，订单号：{}", userId, newOrderId);
            return R.success("订单提交成功");
        } else {
            log.warn("用户{}订单提交成功，但购物车清空失败，订单号：{}", userId, newOrderId);
            return R.fail("订单提交成功，购物车清空失败");
        }
    }


    /**
     * 订单分页接口
     */
    @GetMapping("/page")
    public R<Page<OrderForm>> page(int page, int limit,
                                   @RequestParam(required = false) String name,
                                   @RequestParam(required = false) String orderTime){
        Page<OrderForm> pageInfo = new Page<>(page, limit);

        LambdaQueryWrapper<OrderForm> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(StrUtil.isNotEmpty(name), OrderForm::getName, name)
                .orderByDesc(OrderForm::getOrderTime);

        // 处理当前这一天的订单
        if (orderTime != null && StrUtil.isNotEmpty(orderTime)) {
            Date begin = MyTimeUtils.getMonthOfBeginTime(orderTime);
            Date end = MyTimeUtils.getMonthOfEndTime(orderTime);
            // 加入时间查找
            queryWrapper.between(OrderForm::getOrderTime, begin, end);
        }

        orderFormService.page(pageInfo, queryWrapper);
        return R.success(pageInfo);
    }

    /**
     * 订单详情接口
     */
    @GetMapping("/boPage")
    public R<Page<BlanketOrder>> boPage(int page, int limit, HttpServletRequest request){
        Page<BlanketOrder> pageInfo = new Page<>(page, limit);

        Long orderId = (Long) request.getSession().getAttribute("detailsId");

        LambdaQueryWrapper<BlanketOrder> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(orderId != null, BlanketOrder::getOrderId, orderId);
        blanketOrderService.page(pageInfo, queryWrapper);

        return R.success(pageInfo);
    }

    /**
     * 取消订单接口（删除订单+明细，允许重新提交）
     * 核心规则：仅允许在非配送时段取消「本日菜单时间范围」内的订单
     */
    @PostMapping("/cancel/{orderId}")
    @Transactional(rollbackFor = Exception.class)
    public R<String> cancelOrder(@PathVariable Long orderId, HttpServletRequest request) {
        // 1. 基础校验：用户登录状态
        MyUser currUser = (MyUser) request.getSession().getAttribute("currUser");
        if (currUser == null || currUser.getUserId() == null) {
            throw new CustomException("用户未登录，请先登录");
        }
        Long userId = currUser.getUserId();

        // 2. 校验订单是否存在且归属当前用户
        OrderForm orderForm = orderFormService.getById(orderId);
        if (orderForm == null) {
            throw new CustomException("订单不存在，无法取消");
        }
        if (!userId.equals(orderForm.getUserId())) {
            throw new CustomException("无权取消他人订单");
        }

        // 3. 校验：是否处于配送时段（配送时段禁止取消）
        TimeConfig timeConfig = timeConfigService.getCurrentConfig();
        String orderDeadlineStr = timeConfig.getOrderDeadline() == null ? "09:00:00" : timeConfig.getOrderDeadline();
        String mealStartTimeStr = timeConfig.getMealStartTime() == null ? "11:30:00" : timeConfig.getMealStartTime();

        LocalDate today = LocalDate.now();
        Date now = new Date();
        Date todayOrderDeadline = MyTimeUtils.getDateWithTime(today, orderDeadlineStr);
        Date todayMealStartTime = MyTimeUtils.getDateWithTime(today, mealStartTimeStr);

        boolean isInDeliveryPeriod = !now.before(todayOrderDeadline) && !now.after(todayMealStartTime);
        if (isInDeliveryPeriod) {
            throw new CustomException("当前处于配送时间段，禁止取消订单！");
        }

        // 4. 校验：订单是否在「本日菜单时间范围」内（仅允许取消本日菜单内的订单）
        Date todayMenuStartTime, todayMenuEndTime;
        LocalDate baseDate;
        if (MyTimeUtils.isAfterTodayOrderDeadline(orderDeadlineStr)) {
            baseDate = MyTimeUtils.getToday();
            todayMenuStartTime = MyTimeUtils.getDateWithTime(baseDate, mealStartTimeStr);
            todayMenuEndTime = MyTimeUtils.getDateWithTime(baseDate.plusDays(1), orderDeadlineStr);
        } else {
            baseDate = MyTimeUtils.getYesterday();
            todayMenuStartTime = MyTimeUtils.getDateWithTime(baseDate, mealStartTimeStr);
            todayMenuEndTime = MyTimeUtils.getDateWithTime(baseDate.plusDays(1), orderDeadlineStr);
        }

        if (orderForm.getOrderTime().before(todayMenuStartTime) || orderForm.getOrderTime().after(todayMenuEndTime)) {
            throw new CustomException("仅可取消本日菜单时间范围内的订单，历史订单无法取消");
        }

        // 5. 删除订单明细
        LambdaQueryWrapper<BlanketOrder> blanketQuery = new LambdaQueryWrapper<>();
        blanketQuery.eq(BlanketOrder::getOrderId, orderId);
        boolean removeDetail = blanketOrderService.remove(blanketQuery);
        if (!removeDetail) {
            log.warn("用户{}取消订单{}时，明细删除失败", userId, orderId);
        }

        // 6. 删除主订单
        boolean removeMain = orderFormService.removeById(orderId);
        if (!removeMain) {
            throw new CustomException("订单取消失败：主订单删除失败");
        }

        log.info("用户{}成功取消订单，订单号：{}", userId, orderId);
        return R.success("订单取消成功，您可重新提交新订单");
    }


    /**
     * 厨师专栏订单分页接口
     */
    @GetMapping("/pageChef")
    public R<Page<BlanketOrder>> pageByChef(int page, int limit){
        Page<BlanketOrder> pageInfo = new Page<>(page, limit);

        QueryWrapper<BlanketOrder> queryWrapper = new QueryWrapper<>();
        // 获取当前这一天的开始与结尾
        Date begin = DateUtil.beginOfDay(DateUtil.date());
        Date end = DateUtil.endOfDay(DateUtil.date());
        queryWrapper.between("createTime", begin, end)
                .select("name, unit, sum(weight) as weight, sum(totalPrice) as totalPrice")
                .groupBy("name, unit, price");
        blanketOrderService.page(pageInfo, queryWrapper);
        return R.success(pageInfo);
    }

    /**
     * 厨师批量打印功能
     */
    @GetMapping("/printChef/{names}")
    public void printByBatchWithChef(@PathVariable String names, HttpServletResponse response){
        QueryWrapper<BlanketOrder> queryWrapper = new QueryWrapper<>();
        // 获取当前这一天的开始与结尾
        Date begin = DateUtil.beginOfDay(DateUtil.date());
        Date end = DateUtil.endOfDay(DateUtil.date());
        queryWrapper.between("createTime", begin, end)
                .in("name", Arrays.stream(names.split(",")).toArray())
                .select("name, unit, sum(weight) as weight, sum(totalPrice) as totalPrice")
                .groupBy("name, unit, price");
        List<BlanketOrder> list = blanketOrderService.list(queryWrapper);

        ArrayList<Map<String, Object>> data = new ArrayList<>();
        for (BlanketOrder bo : list) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("菜品名称", bo.getName());
            map.put("计量单位", bo.getUnit());
            map.put("数量", bo.getWeight());
            map.put("总计", bo.getTotalPrice());
            data.add(map);
        }
        ServletOutputStream out = null;
        try (ExcelWriter writer = ExcelUtil.getWriter()) {
            writer.merge(3, "今日备餐汇总");
            writer.write(data, true);
            response.setContentType("application/vnd.ms-excel;charset=utf-8");
            response.setHeader("Content-Disposition", "attachment;filename=orderByChef.xls");
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


    private CellStyle createBorderStyle(ExcelWriter writer) {
        Workbook workbook = writer.getWorkbook();
        CellStyle style = workbook.createCellStyle();

        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);

        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);

        return style;
    }

    /**
     * 配送员批量打印功能
     */
    @GetMapping("/exportExcel/{orderIds}")
    public void printByBatchWithCaterer(@PathVariable String orderIds,
                                        HttpServletResponse response) {

        List<OrderForm> orders = orderFormService.list(
                new LambdaQueryWrapper<OrderForm>()
                        .in(OrderForm::getOrderId, orderIds.split(","))
        );
        if (orders.isEmpty()) return;

        try (ExcelWriter writer = ExcelUtil.getWriter()) {

            CellStyle borderStyle = createBorderStyle(writer);
            int rowIndex = 0;

            for (OrderForm o : orders) {

                // 顶部信息（合并0-5列，保持整体宽度）
                writer.merge(rowIndex, rowIndex, 0, 5,
                        "员工：" + o.getName() + "        联系电话：" + o.getTelephone(), false);
                // 给顶部合并单元格设置边框（可选，保持样式统一）
                writer.getCell(0, rowIndex).setCellStyle(borderStyle);
                rowIndex++;

                writer.merge(rowIndex, rowIndex, 0, 5,
                        "工位信息：" + o.getWorkInformation(), false);
                writer.getCell(0, rowIndex).setCellStyle(borderStyle);
                rowIndex++;

                //  菜品表头（合并列实现占格）
                // 菜名：合并0、1、2列（占3格）
                writer.merge(rowIndex, rowIndex, 0, 2, "菜名", false);
                // 单位：单独3列（过渡列）
                writer.writeCellValue(3, rowIndex, "单位");
                // 分量：合并4、5列（占2格）
                writer.merge(rowIndex, rowIndex, 4, 5, "分量", false);

                // 给表头所有列设置边框样式
                // 菜名合并列（0-2）：设置0列样式即可（合并单元格样式以首列为准）
                writer.getCell(0, rowIndex).setCellStyle(borderStyle);
                // 单位列（3）
                writer.getCell(3, rowIndex).setCellStyle(borderStyle);
                // 分量合并列（4-5）：设置4列样式即可
                writer.getCell(4, rowIndex).setCellStyle(borderStyle);
                rowIndex++;

                // 菜品数据（和表头合并列对应）
                List<BlanketOrder> items = blanketOrderService.list(
                        new LambdaQueryWrapper<BlanketOrder>()
                                .eq(BlanketOrder::getOrderId, o.getOrderId())
                );

                for (BlanketOrder bo : items) {
                    // 菜名：合并0-2列写入（占3格）
                    writer.merge(rowIndex, rowIndex, 0, 2, bo.getName(), false);
                    // 单位：3列单独写入
                    writer.writeCellValue(3, rowIndex, bo.getUnit());
                    // 分量：合并4-5列写入（占2格）
                    writer.merge(rowIndex, rowIndex, 4, 5, bo.getWeight(), false);

                    // 给数据行设置边框样式
                    writer.getCell(0, rowIndex).setCellStyle(borderStyle); // 菜名合并列
                    writer.getCell(3, rowIndex).setCellStyle(borderStyle); // 单位列
                    writer.getCell(4, rowIndex).setCellStyle(borderStyle); // 分量合并列
                    rowIndex++;
                }

                // 补空行（保持票据高度，和表头列结构一致）
                // 补一行空行，按合并列结构填充空值+边框
                writer.merge(rowIndex, rowIndex, 0, 2, "", false); // 菜名列空值
                writer.writeCellValue(3, rowIndex, ""); // 单位列空值
                writer.merge(rowIndex, rowIndex, 4, 5, "", false); // 分量列空值
                // 设置空行边框
                writer.getCell(0, rowIndex).setCellStyle(borderStyle);
                writer.getCell(3, rowIndex).setCellStyle(borderStyle);
                writer.getCell(4, rowIndex).setCellStyle(borderStyle);
                rowIndex++;

                // 底部信息
                writer.merge(rowIndex, rowIndex, 0, 5,
                        "送餐员：caterer        打印时间：" +
                                DateUtil.format(new Date(), "yyyy-MM-dd HH:mm:ss"),
                        false);
                writer.getCell(0, rowIndex).setCellStyle(borderStyle);
                rowIndex += 2;

                // 订单分隔
                rowIndex++; // 空一行，防止订单粘连
            }

            // 响应配置
            response.setContentType("application/vnd.ms-excel;charset=utf-8");
            response.setHeader("Content-Disposition",
                    "attachment;filename=送餐单批量.xls");

            writer.flush(response.getOutputStream(), true);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @GetMapping("/print/{orderIds}")
    public void print(@PathVariable String orderIds, HttpServletResponse response) {
        LambdaQueryWrapper<OrderForm> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(OrderForm::getOrderId, Arrays.stream(orderIds.split(",")).toArray());
        List<OrderForm> list = orderFormService.list(queryWrapper);
        ServletOutputStream out = null;
        if (list.size() >= 1) {
            int cnt = 0;
            try (ExcelWriter writer = ExcelUtil.getWriterWithSheet(String.valueOf(list.get(cnt).getOrderId()))) {
                for (OrderForm o : list) {
                    if (cnt != 0) writer.setSheet(String.valueOf(o.getOrderId()));
                    writer.merge(5, "订单信息");
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("订单编号", String.valueOf(o.getOrderId()));
                    map.put("用户名", o.getName());
                    map.put("电话", o.getTelephone());
                    map.put("下单时间", DateUtil.format(o.getOrderTime(), "HH:mm:ss"));
                    map.put("", "");
                    map.put("订单金额", o.getOrderPrice());
                    ArrayList<Map<String, Object>> list1 = new ArrayList<>();
                    list1.add(map);
                    writer.write(list1, true);
                    LambdaQueryWrapper<BlanketOrder> lambdaQueryWrapper = new LambdaQueryWrapper<>();
                    lambdaQueryWrapper.eq(BlanketOrder::getOrderId, o.getOrderId());
                    List<BlanketOrder> blanketOrderList = blanketOrderService.list(lambdaQueryWrapper);
                    ArrayList<Map<String, Object>> list2 = new ArrayList<>();
                    for (BlanketOrder bo : blanketOrderList) {
                        Map<String, Object> map1 = new LinkedHashMap<>();
                        map1.put("菜品名称", bo.getName());
                        map1.put("计量单位", bo.getUnit());
                        map1.put("数量", bo.getWeight());
                        map1.put("单价", bo.getPrice());
                        map1.put("总计", bo.getTotalPrice());
                        map1.put("下单时间", DateUtil.format(bo.getCreateTime(), "HH:mm:ss"));
                        list2.add(map1);
                    }
                    writer.write(list2, true);
                    cnt++;
                }
                response.setContentType("application/vnd.ms-excel;charset=utf-8");
                response.setHeader("Content-Disposition", "attachment;filename=orderAll.xls");
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