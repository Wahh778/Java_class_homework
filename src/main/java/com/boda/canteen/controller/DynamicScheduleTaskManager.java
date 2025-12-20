package com.boda.canteen.controller;

import com.boda.canteen.common.MyTimeUtils;
import com.boda.canteen.entity.*;
import com.boda.canteen.security.service.*;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 动态定时任务管理器（支持Cron热更新）
 */
@Slf4j
@Component
@EnableScheduling // 开启定时任务核心功能
public class DynamicScheduleTaskManager {

    // 保存任务句柄：key=任务名称，value=任务未来执行句柄（用于取消旧任务）
    private final Map<String, ScheduledFuture<?>> taskMap = new ConcurrentHashMap<>();

    @Autowired
    private TaskScheduler taskScheduler; // Spring默认的任务调度器

    @Autowired
    private TimeConfigService timeConfigService;

    // 业务服务依赖（与原MyScheduleTask一致）
    @Autowired
    private SaleService saleService;
    @Autowired
    private BlanketOrderService blanketOrderService;
    @Autowired
    private ShopCartService shopCartService;
    @Autowired
    private MenuService menuService;
    @Autowired
    private HistoryService historyService;

    // ========== 任务名称常量 ==========
    private static final String TASK_CLEAR_SHOP_CART = "clearShopCart"; // 清空购物车任务
    private static final String TASK_ADD_HISTORY_MENU = "addHistoryMenu"; // 统计菜单任务
    private static final String TASK_ADD_MONTH_SALE = "addMonthSale";     // 月度销售任务（固定Cron）

    // ========== 初始化：应用启动时注册所有任务 ==========
    @PostConstruct
    public void initAllTasks() {
        // 1. 注册固定Cron任务（每月1号）
        registerFixedCronTask(TASK_ADD_MONTH_SALE, "0 0 0 1 * ?", this::addMonthSale);
        // 2. 注册动态Cron任务（从数据库读取）
        refreshDynamicTasks();
        log.info("所有定时任务初始化完成");
    }

    // ========== 核心方法：刷新动态任务（热更新入口） ==========
    public void refreshDynamicTasks() {
        // 1. 刷新「清空购物车」任务
        refreshSingleDynamicTask(TASK_CLEAR_SHOP_CART, timeConfigService.getOrderDeadlineCron(), this::clearShopCart);
        // 2. 刷新「统计菜单」任务
        refreshSingleDynamicTask(TASK_ADD_HISTORY_MENU, timeConfigService.getMealStartTimeCron(), this::addHistoryMenu);
        log.info("动态定时任务已刷新，最新Cron：清空购物车={}, 统计菜单={}",
                timeConfigService.getOrderDeadlineCron(),
                timeConfigService.getMealStartTimeCron());
    }

    // ========== 工具方法：刷新单个动态任务 ==========
    private void refreshSingleDynamicTask(String taskName, String newCron, Runnable taskRunnable) {
        if (StrUtil.isBlank(newCron)) {
            log.error("任务{}的Cron表达式为空，跳过刷新", taskName);
            return;
        }

        // 步骤1：取消旧任务（如果存在）
        ScheduledFuture<?> oldFuture = taskMap.remove(taskName);
        if (oldFuture != null) {
            oldFuture.cancel(false); // false：不中断正在执行的任务
            log.info("旧任务{}已取消", taskName);
        }

        // 步骤2：注册新任务
        try {
            CronTrigger trigger = new CronTrigger(newCron);
            ScheduledFuture<?> newFuture = taskScheduler.schedule(taskRunnable, trigger);
            taskMap.put(taskName, newFuture);
            log.info("新任务{}注册成功，Cron={}", taskName, newCron);
        } catch (Exception e) {
            log.error("任务{}注册失败，Cron={}", taskName, newCron, e);
        }
    }

    // ========== 工具方法：注册固定Cron任务 ==========
    private void registerFixedCronTask(String taskName, String cron, Runnable taskRunnable) {
        // 先取消旧任务（防止重复注册）
        ScheduledFuture<?> oldFuture = taskMap.remove(taskName);
        if (oldFuture != null) {
            oldFuture.cancel(false);
        }
        // 注册新任务
        CronTrigger trigger = new CronTrigger(cron);
        ScheduledFuture<?> future = taskScheduler.schedule(taskRunnable, trigger);
        taskMap.put(taskName, future);
        log.info("固定Cron任务{}注册成功，Cron={}", taskName, cron);
    }

    // ========== 业务逻辑方法（与原MyScheduleTask完全一致） ==========
    /** 月度销售统计（固定Cron：每月1号0点） */
    public void addMonthSale() {
        Sale sale = new Sale();
        Date currDate = DateUtil.lastMonth();
        String month = DateUtil.formatDate(currDate).substring(0, 7);
        sale.setMonth(month);

        Long totalPrice = 0L;
        LambdaQueryWrapper<BlanketOrder> queryWrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotEmpty(month)) {
            month += "-01";
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

    /** 清空购物车（动态Cron：从数据库读取） */
    public void clearShopCart() {
        LambdaQueryWrapper<ShopCart> queryWrapper = new LambdaQueryWrapper<>();
        shopCartService.remove(queryWrapper);
        log.info("当日订餐截止，自动清除所有用户购物车");
    }

    /** 统计当日菜单（动态Cron：从数据库读取） */
    public void addHistoryMenu() {
        History history = new History();
        Date todayBegin = MyTimeUtils.getDayOfBeginTime();
        Date todayEnd = MyTimeUtils.getDayOfEndTime();
        String timeRange = DateUtil.formatDate(todayBegin) + "~" + DateUtil.formatDate(todayEnd);
        history.setTimeRange(timeRange);

        StringBuilder sb = new StringBuilder();
        LambdaQueryWrapper<Menu> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.between(Menu::getCreateTime, todayBegin, todayEnd);
        List<Menu> menuList = menuService.list(queryWrapper);
        for (Menu m : menuList) {
            sb.append(m.getMenuId()).append(",");
        }
        if (sb.length() > 0) {
            history.setMenuIds(sb.substring(0, sb.length() - 1));
        } else {
            history.setMenuIds("");
        }

        historyService.save(history);
        log.info("自动收集当日历史菜单：{}", timeRange);
    }
    // 新增：每分钟轮询一次配置，检测变化后刷新
    @Scheduled(fixedRate = 60 * 1000) // 60秒执行一次
    public void autoRefreshTask() {
        // 1. 获取当前数据库的Cron
        String newClearCron = timeConfigService.getOrderDeadlineCron();
        String newHistoryCron = timeConfigService.getMealStartTimeCron();

        // 2. 获取当前任务的Cron（需扩展taskMap，保存Cron与任务的映射）
        // （简化版：直接调用refreshDynamicTasks，即使无变化也刷新，性能影响可忽略）
        refreshDynamicTasks();
    }
}