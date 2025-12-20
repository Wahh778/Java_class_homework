package com.boda.canteen.security.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.boda.canteen.entity.TimeConfig;
import com.boda.canteen.mapper.TimeConfigMapper;
import com.boda.canteen.security.service.TimeConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.Date;

@Slf4j
@Service("timeConfigService") // 显式指定Bean名称（可选，默认是类名首字母小写） // 关键：确保此注解存在，Spring才会将其注册为Bean
public class TimeConfigServiceImpl extends ServiceImpl<TimeConfigMapper, TimeConfig> implements TimeConfigService {

    @Override
    public String getOrderDeadlineCron() {
        TimeConfig config = getCurrentConfig();
        if (config != null && config.getOrderDeadline() != null) {
            return parseTimeToCron(config.getOrderDeadline());
        }
        return "0 0 9 * * ?"; // 默认每天9:00
    }

    @Override
    public String getMealStartTimeCron() {
        TimeConfig config = getCurrentConfig();
        if (config != null && config.getMealStartTime() != null) {
            return parseTimeToCron(config.getMealStartTime());
        }
        return "0 30 11 * * ?"; // 默认每天11:30
    }

    @Override
    public TimeConfig getCurrentConfig() {
        LambdaQueryWrapper<TimeConfig> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByDesc(TimeConfig::getUpdateTime).last("LIMIT 1");
        TimeConfig config = baseMapper.selectOne(queryWrapper);
        return config != null ? config : initDefaultConfig();
    }

    @Override
    public boolean updateConfig(TimeConfig timeConfig) {
        if (timeConfig == null || timeConfig.getId() == null) {
            return false;
        }
        timeConfig.setUpdateTime(new Date());
        return baseMapper.updateById(timeConfig) > 0;
    }

    @Override
    public TimeConfig initDefaultConfig() {
        TimeConfig defaultConfig = new TimeConfig();
        defaultConfig.setOrderDeadline("09:00:00");
        defaultConfig.setMealStartTime("11:30:00");
        defaultConfig.setUpdateTime(new Date());
        if (baseMapper.insert(defaultConfig) > 0) {
            return defaultConfig;
        }
        return null;
    }

    // 新增：时间转Cron表达式的工具方法
    private String parseTimeToCron(String time) {
        String[] timeParts = time.split(":");
        if (timeParts.length == 3) {
            try {
                int hours = Integer.parseInt(timeParts[0]);
                int minutes = Integer.parseInt(timeParts[1]);
                int seconds = Integer.parseInt(timeParts[2]);
                return String.format("%d %d %d * * ?", seconds, minutes, hours);
            } catch (NumberFormatException e) {
                log.error("时间格式解析错误: {}", time, e);
            }
        }
        return null;
    }
}