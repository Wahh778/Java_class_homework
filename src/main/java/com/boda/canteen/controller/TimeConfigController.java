package com.boda.canteen.controller;

import com.boda.canteen.common.R;
import com.boda.canteen.entity.TimeConfig;
import com.boda.canteen.security.service.TimeConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/timeConfig")
public class TimeConfigController {

    @Autowired
    private TimeConfigService timeConfigService;

    /**
     * 获取当前时间配置（所有角色均可查看）
     */
    @GetMapping("/current")
    public R<TimeConfig> getCurrentConfig() {
        log.info("===== 接收获取当前时间配置请求 =====");
        try {
            log.info("开始调用timeConfigService.getCurrentConfig()获取配置...");
            TimeConfig config = timeConfigService.getCurrentConfig();
            log.info("配置获取成功，返回结果：orderDeadline={}, mealStartTime={}",
                    config.getOrderDeadline(), config.getMealStartTime());
            return R.success(config);
        } catch (Exception e) {
            log.error("获取当前时间配置失败", e);
            return R.fail("获取时间配置失败：" + e.getMessage());
        }
    }

    /**
     * 更新时间配置（仅经理角色可操作）
     */
    @PreAuthorize("hasRole('manager')")
    @PutMapping("/update")
    public R<String> updateConfig(@RequestBody TimeConfig timeConfig) {
        log.info("===== 接收更新时间配置请求 =====");
        try {
            log.info("更新请求参数：id={}, orderDeadline={}, mealStartTime={}",
                    timeConfig.getId(), timeConfig.getOrderDeadline(), timeConfig.getMealStartTime());

            // 参数校验
            if (timeConfig.getOrderDeadline() == null || timeConfig.getMealStartTime() == null) {
                log.warn("更新请求参数不完整，缺少必要字段");
                return R.fail("请提供完整的时间配置信息");
            }

            log.info("开始调用timeConfigService.updateConfig()更新配置...");
            boolean success = timeConfigService.updateConfig(timeConfig);

            if (success) {
                log.info("时间配置更新成功");
                return R.success("时间配置更新成功");
            } else {
                log.warn("时间配置更新失败");
                return R.fail("时间配置更新失败");
            }
        } catch (Exception e) {
            log.error("更新时间配置时发生异常", e);
            return R.fail("更新时间配置失败：" + e.getMessage());
        }
    }
}