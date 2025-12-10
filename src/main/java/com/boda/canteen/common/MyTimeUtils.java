package com.boda.canteen.common;
import cn.hutool.core.date.DateUtil;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * 业务中时间处理工具类
 */
public class MyTimeUtils {

    /**
     *  获取当前时间的一周的开始
     */
    public static Date getWeekOfBeginTime() {
        // 获取当前时间是这周第几天
        int thisDayOfWeek = DateUtil.thisDayOfWeek();
        Date weekOfBeginTime;
        if (thisDayOfWeek == 1) {   // 是否是周日
            // 偏移到七天前即本周周一并获取这一天的开始位置
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
}