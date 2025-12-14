package com.boda.canteen.security.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.boda.canteen.entity.TimeConfig;

public interface TimeConfigService extends IService<TimeConfig> {
    // 获取当前时间配置（系统中通常只有一条配置记录）
    TimeConfig getCurrentConfig();

    // 更新时间配置
    boolean updateConfig(TimeConfig timeConfig);
}