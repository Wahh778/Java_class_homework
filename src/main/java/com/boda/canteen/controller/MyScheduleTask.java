//package com.boda.canteen.controller;
//
//import cn.hutool.core.date.DateUtil;
//import cn.hutool.core.util.StrUtil;
//import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
//import com.boda.canteen.common.MyTimeUtils;
//import com.boda.canteen.entity.*;
//import com.boda.canteen.security.service.*;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Component;
//
//import java.time.LocalDate;
//import java.util.Date;
//import java.util.List;
//
///**
// * 配置定时任务
// */
//@Slf4j
//@Component
//public class MyScheduleTask {
//    @Autowired
//    private SaleService saleService;
//    @Autowired
//    private TimeConfigService timeConfigService; // 新增注入
//    @Autowired
//    private BlanketOrderService blanketOrderService;
//
//    @Autowired
//    private ShopCartService shopCartService;
//
//    @Autowired
//    private MenuService menuService;
//
//    @Autowired
//    private HistoryService historyService;
//
//    /**
//     * 每月的1号会自动生成月度销售信息
//     */
//    @Scheduled(cron = "0 0 0 1 * ?")    // 执行时间为每月的1号的00:00:00
//    public void addMonthSale(){
//        // 构建销售对象
//        Sale sale = new Sale();
//        // 获取对应年月
//        Date currDate = DateUtil.lastMonth();
//        String month = DateUtil.formatDate(currDate).substring(0,7);
//        sale.setMonth(month);
//
//        // 查询这个月总金额
//        Long totalPrice = 0L;
//        LambdaQueryWrapper<BlanketOrder> queryWrapper = new LambdaQueryWrapper<>();
//        if (StrUtil.isNotEmpty(month)){
//            month = month + "-01";
//            Date begin = MyTimeUtils.getMonthOfBeginTime(month);
//            Date end = MyTimeUtils.getMonthOfEndTime(month);
//            queryWrapper.between(BlanketOrder::getCreateTime, begin, end);
//        }
//        List<BlanketOrder> list = blanketOrderService.list(queryWrapper);
//        for (BlanketOrder bo : list) {
//            totalPrice += bo.getTotalPrice();
//        }
//        sale.setTotalPrice(totalPrice);
//
//        boolean res = saleService.save(sale);
//        log.info("上个月的销售订单自动生成{}", res ? "成功" : "失败");
//    }
//
//
//    /**
//     * 清除所有用户的购物车信息（动态cron，移除@Scheduled注解）
//     */
//    public void clearShopCart() {
//        try {
//            log.info("===== 清除购物车定时任务开始执行 =====");
//            // 打印当前时间，确认执行时机
//            log.info("当前执行时间：{}", DateUtil.formatDateTime(new Date()));
//
//            // 执行清除逻辑（保留原逻辑，增加日志）
//            LambdaQueryWrapper<ShopCart> wrapper = new LambdaQueryWrapper<>();
//            boolean res = shopCartService.remove(wrapper);
//
//            log.info("购物车清除结果：{}，清除数量：{}", res, res ? "全部" : "0");
//            log.info("===== 清除购物车定时任务执行完成 =====");
//        } catch (Exception e) {
//            log.error("清除购物车定时任务执行失败", e);
//        }
//    }
//
//    /**
//     * 自动统计当日菜单的菜品并归纳为历史菜单
//     * 核心逻辑：timeRange格式为「昨日配餐开始时间~本日订餐截止时间」（如2025-12-17 10:31:00~2025-12-18 10:00:00）
//     * 新增：时间范围唯一性校验，避免重复保存相同时间段的历史记录
//     */
//    // 修正addHistoryMenu()的时间范围计算逻辑
////    public void addHistoryMenu() {
////        try {
////            log.info("===== 历史菜单定时任务开始执行 =====");
////            TimeConfig timeConfig = timeConfigService.getCurrentConfig();
////            String mealStartTime = timeConfig.getMealStartTime(); // 17:33:00
////            String orderDeadline = timeConfig.getOrderDeadline(); // 17:30:00
////
////            // 修正逻辑：本日00:00 ~ 本日配餐开始时间（覆盖所有当日菜单）
////            LocalDate today = LocalDate.now();
////            Date startTime = DateUtil.beginOfDay(DateUtil.date(today)); // 本日00:00:00
////            Date endTime = MyTimeUtils.getDateWithTime(today, mealStartTime); // 本日17:33:00
////            log.info("修正后的筛选范围：startTime={}, endTime={}",
////                    DateUtil.formatDateTime(startTime), DateUtil.formatDateTime(endTime));
////
////            // 构建timeRange
////            String startTimeStr = DateUtil.formatDateTime(startTime);
////            String endTimeStr = DateUtil.formatDateTime(endTime);
////            String timeRange = startTimeStr + "~" + endTimeStr;
////
////            // 唯一性校验
////            LambdaQueryWrapper<History> wrapper = new LambdaQueryWrapper<>();
////            wrapper.eq(History::getTimeRange, timeRange);
////            long count = historyService.count(wrapper);
////            if (count > 0) {
////                log.info("时间范围{}已存在，跳过保存", timeRange);
////                return;
////            }
////
////            // 筛选菜单（此时能包含17:33创建的菜单）
////            LambdaQueryWrapper<Menu> queryWrapper = new LambdaQueryWrapper<>();
////            queryWrapper.between(Menu::getCreateTime, startTime, endTime);
////            List<Menu> menuList = menuService.list(queryWrapper);
////            log.info("筛选出菜单数量：{}", menuList.size());
////
////
////            History history = new History();
////            history.setTimeRange(timeRange);
////            StringBuilder sb = new StringBuilder();
////            for (Menu m : menuList) {
////                sb.append(m.getMenuId()).append(",");
////            }
////            if (sb.length() > 0) {
////                history.setMenuIds(sb.substring(0, sb.length() - 1));
////                boolean res = historyService.save(history);
////                log.info("保存结果：{}，menuIds={}", res, history.getMenuIds());
////            } else {
////                log.info("无菜单数据");
////            }
////            log.info("===== 历史菜单定时任务执行完成 =====");
////        } catch (Exception e) {
////            log.error("定时任务执行失败", e);
////        }
////    }
//    /**
//     * 自动统计当日菜单的菜品并归纳为历史菜单
//     * 核心逻辑：timeRange格式为「昨日配餐开始时间~本日订餐截止时间」（如2025-12-17 10:31:00~2025-12-18 10:00:00）
//     * 新增：时间范围唯一性校验，避免重复保存相同时间段的历史记录
//     */
//    public void addHistoryMenu() {
//        try {
//            log.info("===== 历史菜单定时任务开始执行（执行时间：orderDeadline前3分钟） =====");
//            TimeConfig timeConfig = timeConfigService.getCurrentConfig();
//            String mealStartTime = timeConfig.getMealStartTime(); // 17:33:00
//            String orderDeadline = timeConfig.getOrderDeadline(); // 17:30:00
//            // 打印执行时间依据
//            log.info("配置的orderDeadline：{}，任务实际执行时间：{}前3分钟",
//                    orderDeadline, orderDeadline);
//
//            // 修正逻辑：本日00:00 ~ 本日配餐开始时间（覆盖所有当日菜单）
//            LocalDate today = LocalDate.now();
//            Date startTime = DateUtil.beginOfDay(DateUtil.date(today)); // 本日00:00:00
//            Date endTime = MyTimeUtils.getDateWithTime(today, mealStartTime); // 本日17:33:00
//            log.info("修正后的筛选范围：startTime={}, endTime={}",
//                    DateUtil.formatDateTime(startTime), DateUtil.formatDateTime(endTime));
//
//            // 构建timeRange
//            String startTimeStr = DateUtil.formatDateTime(startTime);
//            String endTimeStr = DateUtil.formatDateTime(endTime);
//            String timeRange = startTimeStr + "~" + endTimeStr;
//
//            // 唯一性校验
//            LambdaQueryWrapper<History> wrapper = new LambdaQueryWrapper<>();
//            wrapper.eq(History::getTimeRange, timeRange);
//            long count = historyService.count(wrapper);
//            if (count > 0) {
//                log.info("时间范围{}已存在，跳过保存", timeRange);
//                return;
//            }
//
//            // 筛选菜单（此时能包含17:33创建的菜单）
//            LambdaQueryWrapper<Menu> queryWrapper = new LambdaQueryWrapper<>();
//            queryWrapper.between(Menu::getCreateTime, startTime, endTime);
//            List<Menu> menuList = menuService.list(queryWrapper);
//            log.info("筛选出菜单数量：{}", menuList.size());
//
//            // 保存逻辑...
//            History history = new History();
//            history.setTimeRange(timeRange);
//            StringBuilder sb = new StringBuilder();
//            for (Menu m : menuList) {
//                sb.append(m.getMenuId()).append(",");
//            }
//            if (sb.length() > 0) {
//                history.setMenuIds(sb.substring(0, sb.length() - 1));
//                boolean res = historyService.save(history);
//                log.info("保存结果：{}，menuIds={}", res, history.getMenuIds());
//            } else {
//                log.info("无菜单数据");
//            }
//            log.info("===== 历史菜单定时任务执行完成 =====");
//        } catch (Exception e) {
//            log.error("定时任务执行失败", e);
//        }
//    }
//}