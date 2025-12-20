package com.boda.canteen.security.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.boda.canteen.entity.TimeConfig;

public interface TimeConfigService extends IService<TimeConfig> {
    // 获取当前时间配置（系统中通常只有一条配置记录）
//    TimeConfig getCurrentConfig();
//
//    // 更新时间配置
//    boolean updateConfig(TimeConfig timeConfig);
//
//    // 新增：获取清除购物车的动态cron表达式
//    String getClearCartCron();
//
//    // 新增：获取历史菜单统计的动态cron表达式（基于orderDeadline）
//    String getHistoryMenuCron();
        // 移除默认方法，由实现类实现
        String getOrderDeadlineCron();
        String getMealStartTimeCron();
        TimeConfig getCurrentConfig();
        boolean updateConfig(TimeConfig timeConfig);
        TimeConfig initDefaultConfig();
}