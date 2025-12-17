package com.boda.canteen.security.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.boda.canteen.entity.TimeConfig;
import com.boda.canteen.mapper.TimeConfigMapper;
import com.boda.canteen.security.service.TimeConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Pattern;

@Slf4j
@Service
public class TimeConfigServiceImpl extends ServiceImpl<TimeConfigMapper, TimeConfig> implements TimeConfigService {

    // 兼容一位/两位小时的时间正则（HH:mm:ss 或 H:mm:ss）
    private static final Pattern TIME_PATTERN = Pattern.compile("^([01]?\\d|2[0-3]):[0-5]\\d:[0-5]\\d$");
    // 时间格式化器（用于补全一位小时为两位）
    private static final SimpleDateFormat TIME_FORMATTER = new SimpleDateFormat("HH:mm:ss");
    // 默认清除购物车cron（原固定规则：每周一0点）
    private static final String DEFAULT_CLEAR_CART_CRON = "0 0 0 ? * 2";
    // 默认历史菜单统计cron（原固定规则：每天23点）
    private static final String DEFAULT_HISTORY_MENU_CRON = "0 0 23 * * ?";

    @Override
    public TimeConfig getCurrentConfig() {
        // 假设系统中只有一条配置记录，取第一条；无配置则返回默认值
        TimeConfig config = baseMapper.selectOne(new LambdaQueryWrapper<>());
        if (config == null) {
            config = new TimeConfig();
            config.setOrderDeadline("00:00:00"); // 默认订餐截止时间（每周一0点）
            config.setMealStartTime("23:00:00"); // 默认配餐开始时间
        }
        return config;
    }

    @Override
    public boolean updateConfig(TimeConfig timeConfig) {
        // 更新时自动更新updateTime（若数据库未设置自动更新，手动设置）
        timeConfig.setUpdateTime(new Date());
        return baseMapper.updateById(timeConfig) > 0;
    }

    /**
     * 从数据库orderDeadline生成清除购物车的cron表达式
     * 规则：每周一 + 数据库配置的HH:mm:ss（兼容一位/两位小时）
     * cron格式：秒 分 时 ? 月 周几（2代表周一）
     */
    @Override
    public String getClearCartCron() {
        TimeConfig config = getCurrentConfig();
        String orderDeadline = config.getOrderDeadline();

        // 1. 校验时间格式（兼容一位/两位小时）
        if (!TIME_PATTERN.matcher(orderDeadline).matches()) {
            log.warn("数据库orderDeadline格式错误（{}），使用默认cron：{}", orderDeadline, DEFAULT_CLEAR_CART_CRON);
            return DEFAULT_CLEAR_CART_CRON;
        }

        // 2. 补全一位小时为两位（如 9:00:00 → 09:00:00）
        String standardTime;
        try {
            // 解析为Date再格式化，自动补全位数
            Date time = new SimpleDateFormat("H:mm:ss").parse(orderDeadline);
            standardTime = TIME_FORMATTER.format(time);
        } catch (ParseException e) {
            log.error("时间格式解析失败：{}", orderDeadline, e);
            return DEFAULT_CLEAR_CART_CRON;
        }

        // 3. 拆分标准格式的时分秒：HH:mm:ss → 时、分、秒
        String[] timeParts = standardTime.split(":");
        String second = timeParts[2];   // 秒
        String minute = timeParts[1];   // 分
        String hour = timeParts[0];     // 时（已补全为两位）

        // 4. 生成cron表达式（每周一执行）
        String cron = String.format("%s %s %s ? * 2", second, minute, hour);
        log.info("从数据库生成清除购物车cron：{}（原始orderDeadline：{}，标准格式：{}）", cron, orderDeadline, standardTime);
        return cron;
    }

    /**
     * 从数据库orderDeadline生成历史菜单统计的cron表达式
     * 规则：每天 + 数据库配置的HH:mm:ss（兼容一位/两位小时）
     * cron格式：秒 分 时 * * ?（每天执行）
     */
    @Override
    public String getHistoryMenuCron() {
        TimeConfig config = getCurrentConfig();
        String orderDeadline = config.getOrderDeadline();

        // 1. 校验时间格式（兼容一位/两位小时）
        if (!TIME_PATTERN.matcher(orderDeadline).matches()) {
            log.warn("数据库orderDeadline格式错误（{}），使用默认cron：{}", orderDeadline, DEFAULT_HISTORY_MENU_CRON);
            return DEFAULT_HISTORY_MENU_CRON;
        }

        // 2. 补全一位小时为两位（如 9:00:00 → 09:00:00）
        String standardTime;
        try {
            // 解析为Date再格式化，自动补全位数
            Date time = new SimpleDateFormat("H:mm:ss").parse(orderDeadline);
            standardTime = TIME_FORMATTER.format(time);
        } catch (ParseException e) {
            log.error("时间格式解析失败：{}", orderDeadline, e);
            return DEFAULT_HISTORY_MENU_CRON;
        }

        // 3. 拆分标准格式的时分秒：HH:mm:ss → 时、分、秒
        String[] timeParts = standardTime.split(":");
        String second = timeParts[2];   // 秒
        String minute = timeParts[1];   // 分
        String hour = timeParts[0];     // 时（已补全为两位）

        // 4. 生成cron表达式（每天执行）
        String cron = String.format("%s %s %s * * ?", second, minute, hour);
        log.info("从数据库生成历史菜单统计cron：{}（原始orderDeadline：{}，标准格式：{}）", cron, orderDeadline, standardTime);
        return cron;
    }
}