package com.boda.canteen.interception;

import cn.hutool.core.date.DateUtil;
import com.boda.canteen.common.MyTimeUtils;
import com.boda.canteen.common.R;
import com.boda.canteen.common.ResponseUtil;
import com.boda.canteen.entity.TimeConfig;
import com.boda.canteen.security.service.TimeConfigService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * 用户点餐拦截器
 * 可点单时间：
 *  1. 上一日mealStartTime → 本日orderDeadline（包含边界）
 *  2. 本日mealStartTime → 明日orderDeadline（包含边界）
 * 拦截时间：本日orderDeadline → 本日mealStartTime（包含边界，完全禁止点单）
 */
@Slf4j
@Component
public class StaffOrderInterceptor implements HandlerInterceptor {

    @Autowired
    private TimeConfigService timeConfigService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 获取数据库时间配置
        TimeConfig config = timeConfigService.getCurrentConfig();
        // 兜底默认值
        String mealStartTimeStr = config.getMealStartTime() == null ? "11:30:00" : config.getMealStartTime();
        String orderDeadlineStr = config.getOrderDeadline() == null ? "09:00:00" : config.getOrderDeadline();

        // 2. 解析时间（使用MyTimeUtils的公有格式化器，兼容一位/两位小时）
        LocalTime mealStartTime = LocalTime.parse(mealStartTimeStr, MyTimeUtils.LENIENT_TIME_FORMATTER);
        LocalTime orderDeadline = LocalTime.parse(orderDeadlineStr, MyTimeUtils.LENIENT_TIME_FORMATTER);

        // 3. 构建核心时间节点（转换为Date便于比较）
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate tomorrow = today.plusDays(1);

        // 可点单时段1：上一日mealStartTime → 本日orderDeadline（包含边界）
        Date period1Start = MyTimeUtils.getDateWithTime(yesterday, mealStartTimeStr);
        Date period1End = MyTimeUtils.getDateWithTime(today, orderDeadlineStr);

        // 可点单时段2：本日mealStartTime → 明日orderDeadline（包含边界）
        Date period2Start = MyTimeUtils.getDateWithTime(today, mealStartTimeStr);
        Date period2End = MyTimeUtils.getDateWithTime(tomorrow, orderDeadlineStr);

        // 当前时间
        Date currentTime = new Date();

        // 4. 判断是否在可点单时间范围内（包含等于边界的情况）
        boolean isInPeriod1 = !currentTime.before(period1Start) && !currentTime.after(period1End);
        boolean isInPeriod2 = !currentTime.before(period2Start) && !currentTime.after(period2End);
        boolean canOrder = isInPeriod1 || isInPeriod2;

        // 5. 拦截逻辑：不可点单则返回提示
        if (!canOrder) {
            // 拼接可点单时间提示
            String orderableTime = String.format(
                    "可点单时间：%s ~ %s 或 %s ~ %s",
                    DateUtil.format(period1Start, "HH:mm:ss"),
                    DateUtil.format(period1End, "HH:mm:ss"),
                    DateUtil.format(period2Start, "HH:mm:ss"),
                    DateUtil.format(period2End, "HH:mm:ss")
            );
            String forbiddenMsg = String.format("当前处于配送时间段，禁止点单！%s", orderableTime);
            log.warn("点餐拦截：当前时间{}，配送时段{}~{}",
                    DateUtil.formatDateTime(currentTime),
                    DateUtil.format(period1End, "HH:mm:ss"),
                    DateUtil.format(period2Start, "HH:mm:ss"));
            ResponseUtil.out(response, R.fail(forbiddenMsg));
            return false;
        }

        // 允许点单
        log.info("点餐允许：当前时间{}，处于可点单时段", DateUtil.formatDateTime(currentTime));
        return true;
    }
}