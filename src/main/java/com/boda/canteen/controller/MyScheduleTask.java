package com.boda.canteen.controller;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.boda.canteen.common.MyTimeUtils;
import com.boda.canteen.entity.*;
import com.boda.canteen.security.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

/**
 * 配置定时任务
 */
@Slf4j
@Component
public class MyScheduleTask {
    @Autowired
    private SaleService saleService;
    @Autowired
    private TimeConfigService timeConfigService; // 新增注入
    @Autowired
    private BlanketOrderService blanketOrderService;

    @Autowired
    private ShopCartService shopCartService;

    @Autowired
    private MenuService menuService;

    @Autowired
    private HistoryService historyService;

    /**
     * 每月的1号会自动生成月度销售信息
     */
    @Scheduled(cron = "0 0 0 1 * ?")    // 执行时间为每月的1号的00:00:00
    public void addMonthSale(){
        // 构建销售对象
        Sale sale = new Sale();
        // 获取对应年月
        Date currDate = DateUtil.lastMonth();
        String month = DateUtil.formatDate(currDate).substring(0,7);
        sale.setMonth(month);

        // 查询这个月总金额
        Long totalPrice = 0L;
        LambdaQueryWrapper<BlanketOrder> queryWrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotEmpty(month)){
            month = month + "-01";
            Date begin = MyTimeUtils.getMonthOfBeginTime(month);
            Date end = MyTimeUtils.getMonthOfEndTime(month);
            queryWrapper.between(BlanketOrder::getCreateTime, begin, end);
        }
        List<BlanketOrder> list = blanketOrderService.list(queryWrapper);
        for (BlanketOrder bo : list) {
            totalPrice += bo.getTotalPrice();
        }
        sale.setTotalPrice(totalPrice);

        boolean res = saleService.save(sale);
        log.info("上个月的销售订单自动生成{}", res ? "成功" : "失败");
    }

//    /**
//     * 每周的周一的0点整自动清除所有用户的购物车信息
//     */
//    @Scheduled(cron = "0 0 0 ? * 2")
//    public void clearShopCart(){
//        LambdaQueryWrapper<ShopCart> queryWrapper = new LambdaQueryWrapper<>();
//        queryWrapper.or();
//        shopCartService.remove(queryWrapper);
//        log.info("本日已结束，自动清除所有用户购物车");
//    }
    /**
     * 清除所有用户的购物车信息（动态cron，移除@Scheduled注解）
     */
    public void clearShopCart(){
        // 优化：移除无用的or()条件，直接删除所有购物车
        boolean res = shopCartService.remove(new LambdaQueryWrapper<>());
        log.info("自动清除所有用户购物车：{}", res ? "成功" : "失败");
    }

    /**
     * 每周周天23:00:00分自动统计该周的所有菜品并归纳为这周历史菜单
     */
//    @Scheduled(cron = "0 0 23 ? * 1")
//    public void addHistoryMenu() {
//        History history = new History();
//        // 获取当前时间的一周的开始与结尾
//        Date weekOfBeginTime = MyTimeUtils.getWeekOfBeginTime();
//        Date weekOfEndTime = MyTimeUtils.getWeekOfEndTime();
//        String weekOfBeginTimeStr = DateUtil.formatDate(weekOfBeginTime);
//        String weekOfEndTimeStr = DateUtil.formatDate(weekOfEndTime);
//        // 设置时间范围
//        history.setTimeRange(weekOfBeginTimeStr + "~" + weekOfEndTimeStr);
//        // 设置menuIds 通过字符串拼接这一周的所有菜单编号
//        StringBuilder sb = new StringBuilder();
//        LambdaQueryWrapper<Menu> queryWrapper = new LambdaQueryWrapper<>();
//        queryWrapper.between(Menu::getCreateTime, weekOfBeginTime, weekOfEndTime);
//        List<Menu> menuList = menuService.list(queryWrapper);
//        for (Menu m : menuList) {
//            sb.append(m.getMenuId()).append(",");
//        }
//        history.setMenuIds(sb.substring(0, sb.length() - 1));
//        // 加入数据库
//        boolean res = historyService.save(history);
//        log.info("自动收集历史菜单：{}", res ? "成功" : "失败");
//    }
    /**
     * 自动统计当日菜单的菜品并归纳为历史菜单（时间范围：本日菜单的业务时间范围）
     */
    public void addHistoryMenu() {
        // 1. 获取数据库时间配置
        TimeConfig timeConfig = timeConfigService.getCurrentConfig();
        String mealStartTime = timeConfig.getMealStartTime(); // 配餐开始时间（HH:mm:ss）
        String orderDeadline = timeConfig.getOrderDeadline(); // 订餐截止时间（HH:mm:ss）

        // 2. 计算「本日菜单」的业务时间范围（和pageToday接口逻辑一致）
        Date startTime, endTime;
        LocalDate today = MyTimeUtils.getToday();
        if (MyTimeUtils.isAfterTodayOrderDeadline(orderDeadline)) {
            // 当前时间已超过本日orderDeadline → 统计的是「已结束的本日菜单范围」：本日mealStartTime ~ 明日orderDeadline
            startTime = MyTimeUtils.getDateWithTime(today, mealStartTime); // 本日mealStartTime
            endTime = MyTimeUtils.getDateWithTime(today.plusDays(1), orderDeadline); // 明日orderDeadline
        } else {
            // 当前时间未超过本日orderDeadline → 统计的是「已结束的昨日菜单范围」：昨日mealStartTime ~ 本日orderDeadline
            startTime = MyTimeUtils.getDateWithTime(today.minusDays(1), mealStartTime); // 昨日mealStartTime
            endTime = MyTimeUtils.getDateWithTime(today, orderDeadline); // 本日orderDeadline
        }

        // 3. 构建历史菜单的时间范围描述（带时分秒，更精准）
        String timeRange = DateUtil.formatDateTime(startTime) + "~" + DateUtil.formatDateTime(endTime);
        History history = new History();
        history.setTimeRange(timeRange);

        // 4. 查询该时间范围内的所有菜单
        StringBuilder sb = new StringBuilder();
        LambdaQueryWrapper<Menu> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.between(Menu::getCreateTime, startTime, endTime);
        List<Menu> menuList = menuService.list(queryWrapper);

        // 5. 保存历史菜单（拼接菜单ID）
        for (Menu m : menuList) {
            sb.append(m.getMenuId()).append(",");
        }
        if (sb.length() > 0) {
            history.setMenuIds(sb.substring(0, sb.length() - 1)); // 移除最后一个逗号
            boolean res = historyService.save(history);
            log.info("自动收集历史菜单[{}]：{}，共{}个菜品", timeRange, res ? "成功" : "失败", menuList.size());
        } else {
            log.info("历史菜单[{}]无菜品数据，无需收集", timeRange);
        }
    }
}