package com.boda.canteen.controller;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.util.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.boda.canteen.common.MyTimeUtils;
import com.boda.canteen.common.R;
import com.boda.canteen.entity.Menu;
import com.boda.canteen.entity.Recipe;
import com.boda.canteen.entity.TimeConfig;
import com.boda.canteen.exception.CustomException;
import com.boda.canteen.security.service.MenuService;
import com.boda.canteen.security.service.RecipeService;
import com.boda.canteen.security.service.TimeConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDate;
import java.util.Date;

@Slf4j
@RestController
@RequestMapping("/menu")
public class MenuController {

    @Autowired
    private MenuService menuService;

    @Autowired
    private RecipeService recipeService;

    @Autowired
    private TimeConfigService timeConfigService; // 注入时间配置服务

    /**
     * 查询某个菜单接口
     */
    @GetMapping("{menuId}")
    public R<Menu> getMenuInfo(@PathVariable Long menuId) {
        if (menuId == null || menuId <= 0) {
            return R.fail("菜单ID不合法");
        }
        Menu menu = menuService.getById(menuId);
        return menu != null ? R.success(menu) : R.fail("获取菜品信息失败");
    }

    /**
     * 今日菜单分页接口（动态范围：根据当前时间是否超过本日orderDeadline调整）
     */
    @GetMapping("/pageToday")
    public R<Page<Menu>> getTodayMenu(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String name) {
        // 1. 获取数据库时间配置
        TimeConfig timeConfig = timeConfigService.getCurrentConfig();
        String mealStartTime = timeConfig.getMealStartTime(); // 配餐开始时间（HH:mm:ss）
        String orderDeadline = timeConfig.getOrderDeadline(); // 订餐截止时间（HH:mm:ss）

        // 2. 动态计算今日菜单时间范围
        Date startTime, endTime;
        LocalDate baseDate;
        if (MyTimeUtils.isAfterTodayOrderDeadline(orderDeadline)) {
            // 当前时间已超过本日orderDeadline → 今日菜单：本日mealStartTime ~ 明日orderDeadline
            baseDate = MyTimeUtils.getToday();
            startTime = MyTimeUtils.getDateWithTime(baseDate, mealStartTime); // 本日mealStartTime
            endTime = MyTimeUtils.getDateWithTime(baseDate.plusDays(1), orderDeadline); // 明日orderDeadline
        } else {
            // 当前时间未超过本日orderDeadline → 今日菜单：昨日mealStartTime ~ 本日orderDeadline
            baseDate = MyTimeUtils.getYesterday();
            startTime = MyTimeUtils.getDateWithTime(baseDate, mealStartTime); // 昨日mealStartTime
            endTime = MyTimeUtils.getDateWithTime(baseDate.plusDays(1), orderDeadline); // 本日orderDeadline
        }

        log.info("今日菜单时间范围：{} ~ {}",
                DateUtil.formatDateTime(startTime),
                DateUtil.formatDateTime(endTime));

        // 3. 分页查询
        Page<Menu> pageInfo = new Page<>(page, limit);
        LambdaQueryWrapper<Menu> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(StringUtils.hasText(name), Menu::getName, name)
                .between(Menu::getCreateTime, startTime, endTime)
                .orderByDesc(Menu::getCreateTime);

        menuService.page(pageInfo, queryWrapper);
        return R.success(pageInfo);
    }

    /**
     * 明日菜单分页接口（动态范围：根据当前时间是否超过本日orderDeadline调整）
     */
    @GetMapping("/pageTomorrow")
    public R<Page<Menu>> getTomorrowMenu(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String name) {
        // 1. 获取数据库时间配置
        TimeConfig timeConfig = timeConfigService.getCurrentConfig();
        String mealStartTime = timeConfig.getMealStartTime(); // 配餐开始时间（HH:mm:ss）
        String orderDeadline = timeConfig.getOrderDeadline(); // 订餐截止时间（HH:mm:ss）

        // 2. 动态计算明日菜单时间范围
        Date startTime, endTime;
        LocalDate baseDate;
        if (MyTimeUtils.isAfterTodayOrderDeadline(orderDeadline)) {
            // 当前时间已超过本日orderDeadline → 明日菜单：明日mealStartTime ~ 后日orderDeadline
            baseDate = MyTimeUtils.getTomorrow();
            startTime = MyTimeUtils.getDateWithTime(baseDate, mealStartTime); // 明日mealStartTime
            endTime = MyTimeUtils.getDateWithTime(baseDate.plusDays(1), orderDeadline); // 后日orderDeadline
        } else {
            // 当前时间未超过本日orderDeadline → 明日菜单：本日mealStartTime ~ 明日orderDeadline
            baseDate = MyTimeUtils.getToday();
            startTime = MyTimeUtils.getDateWithTime(baseDate, mealStartTime); // 本日mealStartTime
            endTime = MyTimeUtils.getDateWithTime(baseDate.plusDays(1), orderDeadline); // 明日orderDeadline
        }

        log.info("明日菜单查询时间范围：{} ~ {}",
                DateUtil.formatDateTime(startTime),
                DateUtil.formatDateTime(endTime));

        // 3. 分页查询
        Page<Menu> pageInfo = new Page<>(page, limit);
        LambdaQueryWrapper<Menu> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(StringUtils.hasText(name), Menu::getName, name)
                .between(Menu::getCreateTime, startTime, endTime)
                .orderByDesc(Menu::getCreateTime);

        menuService.page(pageInfo, queryWrapper);
        return R.success(pageInfo);
    }

    /**
     * 添加菜单接口（默认添加到明日菜单，时间范围匹配查询逻辑）
     */
    @PreAuthorize("hasRole('manager')")
    @PostMapping("/add/{recipeId}")
    public R<String> add(@PathVariable Long recipeId) {
        if (recipeId == null || recipeId <= 0) {
            return R.fail("食谱ID不合法");
        }

        Recipe recipe = recipeService.getById(recipeId);
        if (recipe == null) {
            return R.fail("食谱不存在");
        }

        String name = recipe.getName();

        // 1. 获取数据库时间配置
        TimeConfig timeConfig = timeConfigService.getCurrentConfig();
        String mealStartTime = timeConfig.getMealStartTime(); // 配餐开始时间
        String orderDeadline = timeConfig.getOrderDeadline(); // 订餐截止时间

        // 2. 计算明日菜单时间范围（和查询接口逻辑一致）
        Date tomorrowMenuBegin, tomorrowMenuEnd;
        LocalDate baseDate;
        if (MyTimeUtils.isAfterTodayOrderDeadline(orderDeadline)) {
            // 当前时间已超本日截止 → 明日菜单：明日mealStartTime ~ 后日orderDeadline
            baseDate = MyTimeUtils.getTomorrow();
            tomorrowMenuBegin = MyTimeUtils.getDateWithTime(baseDate, mealStartTime);
            tomorrowMenuEnd = MyTimeUtils.getDateWithTime(baseDate.plusDays(1), orderDeadline);
        } else {
            // 当前时间未超本日截止 → 明日菜单：本日mealStartTime ~ 明日orderDeadline
            baseDate = MyTimeUtils.getToday();
            tomorrowMenuBegin = MyTimeUtils.getDateWithTime(baseDate, mealStartTime);
            tomorrowMenuEnd = MyTimeUtils.getDateWithTime(baseDate.plusDays(1), orderDeadline);
        }

        log.info("明日菜单时间范围：{} ~ {}",
                DateUtil.formatDateTime(tomorrowMenuBegin),
                DateUtil.formatDateTime(tomorrowMenuEnd));

        // 3. 检查该时间范围内是否已存在该菜品
        LambdaQueryWrapper<Menu> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Menu::getName, name)
                .between(Menu::getCreateTime, tomorrowMenuBegin, tomorrowMenuEnd);
        Menu isExist = menuService.getOne(queryWrapper);

        if (isExist != null) {
            return R.fail("当前菜品已添加到明日菜单");
        }

        // 4. 构建菜单对象（createTime设为明日菜单起始时间）
        Menu menu = new Menu();
        menu.setName(recipe.getName());
        menu.setCategory(recipe.getCategory());
        menu.setPicture(recipe.getPicture());
        menu.setUnit(recipe.getUnit());
        menu.setPrice(recipe.getPrice());
        menu.setCreateTime(tomorrowMenuBegin); // 匹配明日菜单起始时间

        boolean res = menuService.save(menu);
        return res ? R.success("添加明日菜单成功") : R.fail("添加明日菜单失败");
    }

    /**
     * 删除菜单接口
     */
    @PreAuthorize("hasRole('manager')")
    @DeleteMapping("/delete/{menuId}")
    public R<String> delete(@PathVariable Long menuId) {
        if (menuId == null || menuId <= 0) {
            return R.fail("菜单ID不合法");
        }
        boolean res = menuService.removeById(menuId);
        return res ? R.success("删除成功") : R.fail("删除失败");
    }

    /**
     * 修改菜单接口
     */
    @PreAuthorize("hasRole('manager')")
    @PutMapping("/update")
    public R<String> update(@RequestBody Menu menu, HttpServletRequest request) {
        if (menu == null) {
            return R.fail("菜单信息不能为空");
        }

        Menu editMenu = (Menu) request.getSession().getAttribute("editMenu");
        if (editMenu == null) {
            return R.fail("未找到编辑的菜单信息");
        }

        if (StrUtil.isEmpty(menu.getName())) {
            throw new CustomException("名字为空!");
        }
        if (StrUtil.isEmpty(menu.getCategory())) {
            throw new CustomException("分类为空!");
        }
        if (StrUtil.isEmpty(menu.getUnit())) {
            throw new CustomException("计量单位为空!");
        }
        if (menu.getPrice() == null || menu.getPrice() < 0) {
            throw new CustomException("价格不合法!");
        }

        if (StrUtil.isEmpty(menu.getPicture())) {
            menu.setPicture(editMenu.getPicture());
        }
        menu.setMenuId(editMenu.getMenuId());

        boolean res = menuService.updateById(menu);
        return res ? R.success("修改成功！") : R.fail("修改失败！");
    }

    /**
     * 批量删除菜单接口
     */
    @PreAuthorize("hasRole('manager')")
    @DeleteMapping("/deleteBatch/{menuIds}")
    public R<String> deleteBatch(@PathVariable String menuIds) {
        if (StrUtil.isEmpty(menuIds)) {
            return R.fail("请选择要删除的菜单");
        }

        String[] ids = menuIds.split(",");
        boolean res = menuService.removeByIds(java.util.Arrays.asList(ids));
        return res ? R.success("批量删除成功") : R.fail("批量删除失败");
    }

    /**
     * 复用到明日菜单接口（与添加菜单接口时间逻辑完全对齐）
     */
    @PreAuthorize("hasRole('manager')")
    @PostMapping("/multiplex/{menuIds}")
    public R<String> multiplex(@PathVariable String menuIds) {
        if (StrUtil.isEmpty(menuIds)) {
            return R.fail("请选择要复用的菜单");
        }

        // 1. 获取数据库时间配置（与添加接口一致）
        TimeConfig timeConfig = timeConfigService.getCurrentConfig();
        String mealStartTime = timeConfig.getMealStartTime(); // 配餐开始时间
        String orderDeadline = timeConfig.getOrderDeadline(); // 订餐截止时间

        // 2. 计算明日菜单时间范围（完全复用添加接口的逻辑）
        Date tomorrowMenuBegin, tomorrowMenuEnd;
        LocalDate baseDate;
        if (MyTimeUtils.isAfterTodayOrderDeadline(orderDeadline)) {
            // 当前时间已超本日截止 → 明日菜单：明日mealStartTime ~ 后日orderDeadline
            baseDate = MyTimeUtils.getTomorrow();
            tomorrowMenuBegin = MyTimeUtils.getDateWithTime(baseDate, mealStartTime);
            tomorrowMenuEnd = MyTimeUtils.getDateWithTime(baseDate.plusDays(1), orderDeadline);
        } else {
            // 当前时间未超本日截止 → 明日菜单：本日mealStartTime ~ 明日orderDeadline
            baseDate = MyTimeUtils.getToday();
            tomorrowMenuBegin = MyTimeUtils.getDateWithTime(baseDate, mealStartTime);
            tomorrowMenuEnd = MyTimeUtils.getDateWithTime(baseDate.plusDays(1), orderDeadline);
        }

        log.info("复用明日菜单时间范围：{} ~ {}",
                DateUtil.formatDateTime(tomorrowMenuBegin),
                DateUtil.formatDateTime(tomorrowMenuEnd));

        StringBuilder sb = new StringBuilder();
        boolean res = true;
        String[] ids = menuIds.split(",");

        for (String id : ids) {
            try {
                Long menuId = Long.valueOf(id);
                Menu sourceMenu = menuService.getById(menuId);

                if (sourceMenu == null) {
                    res = false;
                    log.warn("菜单ID {} 不存在，跳过复用", id);
                    continue;
                }

                String menuName = sourceMenu.getName();
                // 3. 检查明日菜单时间范围内是否已存在该菜品（与添加接口一致）
                LambdaQueryWrapper<Menu> queryWrapper = new LambdaQueryWrapper<>();
                queryWrapper.eq(Menu::getName, menuName)
                        .between(Menu::getCreateTime, tomorrowMenuBegin, tomorrowMenuEnd);
                Menu existMenu = menuService.getOne(queryWrapper);

                if (existMenu != null) {
                    res = false;
                    sb.append(menuName).append("、");
                    continue;
                }

                // 4. 构建新菜单对象（createTime与添加接口一致，设为明日菜单起始时间）
                Menu newMenu = new Menu();
                newMenu.setName(sourceMenu.getName());
                newMenu.setCategory(sourceMenu.getCategory());
                newMenu.setPicture(sourceMenu.getPicture());
                newMenu.setUnit(sourceMenu.getUnit());
                newMenu.setPrice(sourceMenu.getPrice());
                newMenu.setCreateTime(tomorrowMenuBegin); // 核心：与添加接口时间对齐

                // 5. 执行新增（自增主键，无需手动设置menuId）
                boolean saveSuccess = menuService.save(newMenu);
                if (!saveSuccess) {
                    res = false;
                    log.error("菜单 {}（ID:{}）复用失败", menuName, id);
                }
            } catch (NumberFormatException e) {
                res = false;
                log.error("菜单ID格式错误: {}，请传入数字ID", id, e);
            }
        }

        // 6. 统一返回结果（细化提示信息）
        if (res) {
            return R.success("所有选中菜单批量复用至明日菜单成功");
        } else {
            String errorMsg = "";
            if (sb.length() > 0) {
                // 移除最后一个顿号
                String existMenus = sb.substring(0, sb.length() - 1);
                errorMsg = String.format("[%s]已存在于明日菜单中，其余菜单复用成功（部分可能因ID错误/不存在失败）", existMenus);
            } else {
                errorMsg = "菜单复用失败，原因：部分菜单ID格式错误/菜单不存在/新增操作失败";
            }
            return R.fail("批量复用至明日菜单失败，" + errorMsg);
        }
    }
}