package com.boda.canteen.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 定时任务配置刷新接口（用于Cron热更新）
 */
@Slf4j
@RestController
@RequestMapping("/schedule")
public class ScheduleController {

    @Autowired
    private DynamicScheduleTaskManager scheduleTaskManager;

    /**
     * 刷新动态定时任务（修改TimeConfig后调用）
     */
    @PostMapping("/refresh")
    public String refreshDynamicTasks() {
        try {
            scheduleTaskManager.refreshDynamicTasks();
            return "定时任务刷新成功";
        } catch (Exception e) {
            log.error("定时任务刷新失败", e);
            return "定时任务刷新失败：" + e.getMessage();
        }
    }
}