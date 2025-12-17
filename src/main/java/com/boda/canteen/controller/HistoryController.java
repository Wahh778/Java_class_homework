package com.boda.canteen.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.boda.canteen.common.R;
import com.boda.canteen.entity.History;
import com.boda.canteen.entity.Menu;
import com.boda.canteen.security.service.HistoryService;
import com.boda.canteen.security.service.MenuService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;

@Slf4j
@RestController
@RequestMapping("/history")
public class HistoryController {

    @Autowired
    private HistoryService historyService;

    @Autowired
    private MenuService menuService;

    /**
     *  历史菜单分页接口（优化：支持按天模糊查询）
     */
    @PreAuthorize("hasRole('manager')")
    @GetMapping("/page")
    public R<Page<History>> getAllMenu(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String timeRange) {

        Page<History> pageInfo = new Page<>(page, limit);
        LambdaQueryWrapper<History> queryWrapper = new LambdaQueryWrapper<>();

        // 优化：模糊匹配时间范围（支持按日期筛选，如传入2025-12-18匹配当天记录）
        if (StringUtils.isNotEmpty(timeRange)) {
            queryWrapper.like(History::getTimeRange, timeRange);
        }
        queryWrapper.orderByDesc(History::getHisId); // 按创建时间倒序，最新的记录在前面

        historyService.page(pageInfo, queryWrapper);
        return R.success(pageInfo);
    }

    /**
     * 详情接口（无需修改，兼容按天存储的menuIds）
     */
    @PreAuthorize("hasRole('manager')")
    @GetMapping("/details")
    public R<Page<Menu>> details(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            HttpServletRequest request) {

        String menuIds = (String) request.getSession().getAttribute("hisMenuIds");
        if (StringUtils.isEmpty(menuIds)) {
            return R.fail("未选择历史菜单记录");
        }

        Page<Menu> pageInfo = new Page<>(page, limit);
        String[] menuArr = menuIds.split(",");
        LambdaQueryWrapper<Menu> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(Menu::getMenuId, Arrays.stream(menuArr).toArray());

        menuService.page(pageInfo, queryWrapper);
        return R.success(pageInfo);
    }
}