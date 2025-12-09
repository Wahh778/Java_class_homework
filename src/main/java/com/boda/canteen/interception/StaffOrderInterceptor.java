package com.boda.canteen.interception;

import cn.hutool.core.date.DateUtil;
import com.boda.canteen.common.R;
import com.boda.canteen.common.ResponseUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component; // 新增注解
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 用户点餐拦截器
 */
@Component // 声明为Spring组件，由容器管理
public class StaffOrderInterceptor implements HandlerInterceptor {
    /**
     * 用户点餐前加入拦截器，判断当前时间是否在九点之后
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 获取当前时刻
        String time = DateUtil.format(DateUtil.date(), "HH:mm:ss");
        // 计算当前时间大小（转换为秒数）
        int hour = Integer.parseInt(time.substring(0,2));
        int min = Integer.parseInt(time.substring(3,5));
        int sec = Integer.parseInt(time.substring(6,8));
        int currentSeconds = hour * 3600 + min * 60 + sec;

        // 9点对应的秒数（9*3600=32400）
        final int NINE_O_CLOCK = 32400;

        // 当前时间大于9点整，拦截请求
        if (currentSeconds > NINE_O_CLOCK) {
            ResponseUtil.out(response, R.fail("当前时间不允许点餐"));
            return false;
        } else {  // 当前时间在9点前，放行请求
            return true;
        }
    }
}