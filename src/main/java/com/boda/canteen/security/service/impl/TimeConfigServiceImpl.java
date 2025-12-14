package com.boda.canteen.security.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.boda.canteen.entity.TimeConfig;
import com.boda.canteen.mapper.TimeConfigMapper;
import com.boda.canteen.security.service.TimeConfigService;
import org.springframework.stereotype.Service;

@Service
public class TimeConfigServiceImpl extends ServiceImpl<TimeConfigMapper, TimeConfig> implements TimeConfigService {

    @Override
    public TimeConfig getCurrentConfig() {
        // 假设系统中只有一条配置记录，取第一条
        return baseMapper.selectOne(new LambdaQueryWrapper<>());
    }

    @Override
    public boolean updateConfig(TimeConfig timeConfig) {
        // 更新时会自动触发数据库的update_time字段更新（因表中设置了ON UPDATE CURRENT_TIMESTAMP）
        return baseMapper.updateById(timeConfig) > 0;
    }
}