package com.boda.canteen.common;
import cn.hutool.core.date.DateUtil;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 业务中时间处理工具类
 */
public class MyTimeUtils {
    // 时间格式器（HH:mm:ss）
    public static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    // 兼容一位/两位小时的时间正则（H:mm:ss 或 HH:mm:ss）
    public static final Pattern TIME_PATTERN = Pattern.compile("^(\\d{1,2}):(\\d{2}):(\\d{2})$");
    // 标准时间格式器（HH:mm:ss）
    public static final DateTimeFormatter STANDARD_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    // 宽松时间格式器（兼容一位小时）
    public static final DateTimeFormatter LENIENT_TIME_FORMATTER = DateTimeFormatter.ofPattern("H:mm:ss");

    /**
     *  获取当前时间的一周的开始
     */
    public static Date getWeekOfBeginTime() {
        // 获取当前时间是这周第几天
        int thisDayOfWeek = DateUtil.thisDayOfWeek();
        Date weekOfBeginTime;
        if (thisDayOfWeek == 1) {
            weekOfBeginTime = DateUtil.beginOfDay(DateUtil.offsetDay(DateUtil.date(),(DateUtil.thisDayOfWeek()-7)));
        }else{
            weekOfBeginTime = DateUtil.beginOfDay(DateUtil.offsetDay(DateUtil.date(),(2 - DateUtil.thisDayOfWeek())));
        }
        return weekOfBeginTime;
    }

    /**
     *  获取当前时间的一周的结尾
     */
    public static Date getWeekOfEndTime() {
        // 获取当前时间是这周第几天
        int thisDayOfWeek = DateUtil.thisDayOfWeek();
        Date weekOfEndTime;
        if (thisDayOfWeek == 1) {   // 是否是周日
            weekOfEndTime = DateUtil.endOfDay(DateUtil.date());
        }else{
            weekOfEndTime = DateUtil.endOfDay(DateUtil.offsetDay(DateUtil.date(),(8 - DateUtil.thisDayOfWeek())));
        }
        return weekOfEndTime;
    }

    /**
     *  获取当前时间的下一周的开始
     */
    public static Date getNextWeekOfBeginTime() {
        // 获取当前时间的这一周的开始
        Date weekOfBeginTime = getWeekOfBeginTime();
        return DateUtil.offsetWeek(weekOfBeginTime, 1);
    }

    /**
     *  获取当前时间的下一周的结尾
     */
    public static Date getNextWeekOfEndTime() {
        // 获取当前时间的这一周的结尾
        Date weekOfEndTime = getWeekOfEndTime();
        return DateUtil.offsetWeek(weekOfEndTime, 1);
    }

    /**
     * 通过yyyy-MM-01获取当前所在月份的开始时间
     */
    public static Date getMonthOfBeginTime(String date) {
        Date month = DateUtil.parse(date);
        return DateUtil.beginOfDay(month);
    }

    /**
     * 通过yyyy-MM-01获取当前所在月份的终点时间
     */
    public static Date getMonthOfEndTime(String date) {
        Date month = DateUtil.parse(date);
        return DateUtil.endOfDay(DateUtil.offsetDay(DateUtil.offsetMonth(month, 1), -1));
    }
    // 修正后的日期转换方法（将Instant转换为Date）
// 新增：获取当天开始时间（00:00:00）
    public static Date getDayOfBeginTime() {
        return Date.from(LocalDateTime.now().toLocalDate().atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    }

    // 新增：获取当天结束时间（23:59:59）
    public static Date getDayOfEndTime() {
        return Date.from(LocalDateTime.now().toLocalDate().atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant());
    }

    // 新增：获取明天开始时间（明天00:00:00）
    public static Date getNextDayOfBeginTime() {
        return Date.from(LocalDateTime.now().toLocalDate().plusDays(1).atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    }

    // 新增：获取明天结束时间（明天23:59:59）
    public static Date getNextDayOfEndTime() {
        return Date.from(LocalDateTime.now().toLocalDate().plusDays(1).atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant());
    }
    // ========== 修复后的核心方法 ==========
    /**
     * 拼接「指定日期 + 时间字符串（兼容H:mm:ss/HH:mm:ss）」为Date
     * @param date 基准日期（LocalDate）
     * @param timeStr 时间字符串（如9:00:00 / 09:00:00）
     * @return 拼接后的Date
     */
    public static Date getDateWithTime(LocalDate date, String timeStr) {
        if (date == null) {
            throw new IllegalArgumentException("基准日期不能为空");
        }
        if (timeStr == null || timeStr.trim().isEmpty()) {
            throw new IllegalArgumentException("时间字符串不能为空");
        }

        // 1. 清洗时间字符串（去除空格）
        String cleanTime = timeStr.trim();

        // 2. 校验时间格式是否合法
        Matcher matcher = TIME_PATTERN.matcher(cleanTime);
        if (!matcher.matches()) {
            throw new RuntimeException("时间格式错误（需H:mm:ss或HH:mm:ss）：" + timeStr);
        }

        // 3. 解析时间（兼容一位/两位小时）
        LocalTime localTime;
        try {
            // 先尝试宽松解析（H:mm:ss），兼容一位小时
            localTime = LocalTime.parse(cleanTime, LENIENT_TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            // 兜底：尝试标准解析（HH:mm:ss）
            try {
                localTime = LocalTime.parse(cleanTime, STANDARD_TIME_FORMATTER);
            } catch (DateTimeParseException ex) {
                throw new RuntimeException("时间格式解析失败（需H:mm:ss或HH:mm:ss）：" + timeStr, ex);
            }
        }

        // 4. 拼接日期+时间并转换为Date
        LocalDateTime localDateTime = LocalDateTime.of(date, localTime);
        return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
    }
    /**
     * 判断当前时间是否超过「本日 orderDeadline」
     * @param orderDeadlineStr 订餐截止时间（HH:mm:ss）
     * @return true=已超过，false=未超过
     */
    public static boolean isAfterTodayOrderDeadline(String orderDeadlineStr) {
        // 解析截止时间为LocalTime
        LocalTime orderDeadline = LocalTime.parse(orderDeadlineStr, LENIENT_TIME_FORMATTER);
        // 当前时间
        LocalDateTime now = LocalDateTime.now();
        // 本日截止时间（今日日期 + 截止时间）
        LocalDateTime todayOrderDeadline = LocalDateTime.of(now.toLocalDate(), orderDeadline);
        // 比较当前时间是否超过本日截止时间
        return now.isAfter(todayOrderDeadline);
    }

    /**
     * 获取上一日的日期（LocalDate）
     */
    public static LocalDate getYesterday() {
        return LocalDate.now().minusDays(1);
    }

    /**
     * 获取今日日期（LocalDate）
     */
    public static LocalDate getToday() {
        return LocalDate.now();
    }

    /**
     * 获取明日日期（LocalDate）
     */
    public static LocalDate getTomorrow() {
        return LocalDate.now().plusDays(1);
    }

    /**
     * 获取次日日期（指定日期的下一天）
     */
    public static LocalDate getNextDay(LocalDate date) {
        return date.plusDays(1);
    }
}