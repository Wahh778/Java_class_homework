package com.boda.canteen.interception;

import cn.hutool.core.date.DateUtil;
import com.boda.canteen.common.R;
import com.boda.canteen.common.ResponseUtil;
import com.boda.canteen.entity.TimeConfig;
import com.boda.canteen.security.service.TimeConfigService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component; // 新增注解
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 用户点餐拦截器
 */
@Component
public class StaffOrderInterceptor implements HandlerInterceptor {

    @Autowired
    private TimeConfigService timeConfigService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 获取当前时间配置
        TimeConfig config = timeConfigService.getCurrentConfig();
        String deadline = config.getOrderDeadline(); // 从数据库获取截止时间

        // 解析截止时间为秒数
        String[] deadlineParts = deadline.split(":");
        int deadlineHour = Integer.parseInt(deadlineParts[0]);
        int deadlineMin = Integer.parseInt(deadlineParts[1]);
        int deadlineSec = Integer.parseInt(deadlineParts[2]);
        int deadlineSeconds = deadlineHour * 3600 + deadlineMin * 60 + deadlineSec;

        // 计算当前时间的秒数
        String currentTime = DateUtil.format(DateUtil.date(), "HH:mm:ss");
        String[] currentParts = currentTime.split(":");
        int currentHour = Integer.parseInt(currentParts[0]);
        int currentMin = Integer.parseInt(currentParts[1]);
        int currentSec = Integer.parseInt(currentParts[2]);
        int currentSeconds = currentHour * 3600 + currentMin * 60 + currentSec;

        // 比较当前时间与截止时间
        if (currentSeconds > deadlineSeconds) {
            ResponseUtil.out(response, R.fail("当前时间不允许点餐（截止时间：" + deadline + "）"));
            return false;
        }
        return true;
    }
}