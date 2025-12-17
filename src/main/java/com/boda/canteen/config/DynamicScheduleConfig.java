package com.boda.canteen.config;

import com.boda.canteen.controller.MyScheduleTask;
import com.boda.canteen.security.service.TimeConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;

import java.time.Instant;
import java.util.Date;

/**
 * 动态定时任务配置（从数据库读取cron规则）
 */
@Slf4j
@Configuration
@EnableScheduling
public class DynamicScheduleConfig implements SchedulingConfigurer {

    @Autowired
    private MyScheduleTask myScheduleTask;

    // 注入接口（TimeConfigService）而非实现类
    @Autowired
    private TimeConfigService timeConfigService;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        // 1. 动态注册：清除购物车任务（从数据库读取orderDeadline生成cron）
        taskRegistrar.addTriggerTask(
                // 任务执行逻辑（复用原clearShopCart方法）
                myScheduleTask::clearShopCart,
                // 动态获取cron并计算下次执行时间
                triggerContext -> {
                    String cron = timeConfigService.getClearCartCron();
                    log.info("清除购物车任务当前Cron规则：{}", cron);
                    // 先获取Date类型的执行时间，再转换为Instant（兼容新旧版本）
                    Date nextExecDate = new CronTrigger(cron).nextExecutionTime(triggerContext);
                    return nextExecDate.toInstant();
                }
        );

        // 2. 保留月度销售任务（固定cron，无需动态化）
        taskRegistrar.addCronTask(
                myScheduleTask::addMonthSale,
                "0 0 0 1 * ?"
        );

        // 3. 动态注册：历史菜单统计任务（从数据库读取orderDeadline生成cron）
        taskRegistrar.addTriggerTask(
                // 任务执行逻辑（原addHistoryMenu方法）
                myScheduleTask::addHistoryMenu,
                // 动态获取cron并计算下次执行时间
                triggerContext -> {
                    String cron = timeConfigService.getHistoryMenuCron();
                    log.info("历史菜单统计任务当前Cron规则：{}", cron);
                    // 先获取Date类型的执行时间，再转换为Instant（兼容新旧版本）
                    Date nextExecDate = new CronTrigger(cron).nextExecutionTime(triggerContext);
                    return nextExecDate.toInstant();
                }
        );
    }
}