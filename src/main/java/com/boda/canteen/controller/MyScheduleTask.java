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

    /**
     * 清除所有用户的购物车信息（动态cron，移除@Scheduled注解）
     */
    public void clearShopCart(){
        // 优化：移除无用的or()条件，直接删除所有购物车
        boolean res = shopCartService.remove(new LambdaQueryWrapper<>());
        log.info("自动清除所有用户购物车：{}", res ? "成功" : "失败");
    }

    /**
     * 自动统计当日菜单的菜品并归纳为历史菜单
     * 核心逻辑：timeRange格式为「昨日配餐开始时间~本日订餐截止时间」（如2025-12-17 10:31:00~2025-12-18 10:00:00）
     * 新增：时间范围唯一性校验，避免重复保存相同时间段的历史记录
     */
    public void addHistoryMenu() {
        // 1. 获取数据库中的时间配置（配餐开始时间、订餐截止时间）
        TimeConfig timeConfig = timeConfigService.getCurrentConfig();
        String mealStartTime = timeConfig.getMealStartTime(); // 例：10:31:00
        String orderDeadline = timeConfig.getOrderDeadline(); // 例：10:00:00

        // 2. 计算「本日菜单」的业务时间范围（核心：昨日配餐开始 ~ 本日订餐截止）
        LocalDate today = MyTimeUtils.getToday();
        LocalDate yesterday = today.minusDays(1); // 昨日日期

        // 昨日配餐开始时间（如2025-12-17 10:31:00）
        Date startTime = MyTimeUtils.getDateWithTime(yesterday, mealStartTime);
        // 本日订餐截止时间（如2025-12-18 10:00:00）
        Date endTime = MyTimeUtils.getDateWithTime(today, orderDeadline);

        // 3. 构建timeRange字段（格式：yyyy-MM-dd HH:mm:ss~yyyy-MM-dd HH:mm:ss）
        String startTimeStr = DateUtil.formatDateTime(startTime);
        String endTimeStr = DateUtil.formatDateTime(endTime);
        String timeRange = startTimeStr + "~" + endTimeStr;

        // ========== 新增：时间范围唯一性校验 ==========
        LambdaQueryWrapper<History> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(History::getTimeRange, timeRange);
        // 检查该时间范围是否已存在历史记录
        long count = historyService.count(wrapper);
        if (count > 0) {
            log.info("时间范围{}已存在历史记录，跳过保存", timeRange);
            return; // 直接返回，不执行后续保存逻辑
        }

        // 4. 构建历史菜单对象
        History history = new History();
        history.setTimeRange(timeRange); // 保留完整时间范围格式

        // 5. 查询该时间范围内的所有菜单
        StringBuilder sb = new StringBuilder();
        LambdaQueryWrapper<Menu> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.between(Menu::getCreateTime, startTime, endTime);
        List<Menu> menuList = menuService.list(queryWrapper);

        // 6. 拼接菜单ID并保存
        for (Menu m : menuList) {
            sb.append(m.getMenuId()).append(",");
        }
        if (sb.length() > 0) {
            history.setMenuIds(sb.substring(0, sb.length() - 1)); // 移除最后一个逗号
            boolean res = historyService.save(history);
            log.info("自动收集历史菜单[{}]：{}，共{}个菜品",
                    timeRange, res ? "成功" : "失败", menuList.size());
        } else {
            log.info("历史菜单[{}]无菜品数据，无需收集", timeRange);
        }
    }
}