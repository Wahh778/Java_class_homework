package com.boda.canteen.controller;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.boda.canteen.common.MyTimeUtils;
import com.boda.canteen.common.R;
import com.boda.canteen.entity.Menu;
import com.boda.canteen.entity.Recipe;
import com.boda.canteen.exception.CustomException;
import com.boda.canteen.security.service.MenuService;
import com.boda.canteen.security.service.RecipeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Date;

@Slf4j
@RestController
@RequestMapping("/menu")
public class MenuController {

    @Autowired
    private MenuService menuService;

    @Autowired
    private RecipeService recipeService;

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
     * 今日菜单分页接口
     */
    @GetMapping("/pageToday")
    public R<Page<Menu>> getTodayMenu(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String name) {
        Page<Menu> pageInfo = new Page<>(page, limit);
        LambdaQueryWrapper<Menu> queryWrapper = new LambdaQueryWrapper<>();

        // 获取当天时间范围
        Date todayBegin = MyTimeUtils.getDayOfBeginTime();
        Date todayEnd = MyTimeUtils.getDayOfEndTime();

        queryWrapper.eq(StringUtils.isNotEmpty(name), Menu::getName, name)
                .between(Menu::getCreateTime, todayBegin, todayEnd)
                .orderByDesc(Menu::getCreateTime);

        menuService.page(pageInfo, queryWrapper);
        return R.success(pageInfo);
    }

    /**
     * 明日菜单分页接口
     */
    @GetMapping("/pageTomorrow")
    public R<Page<Menu>> getTomorrowMenu(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String name) {
        Page<Menu> pageInfo = new Page<>(page, limit);
        LambdaQueryWrapper<Menu> queryWrapper = new LambdaQueryWrapper<>();

        // 获取明日时间范围
        Date tomorrowBegin = MyTimeUtils.getNextDayOfBeginTime();
        Date tomorrowEnd = MyTimeUtils.getNextDayOfEndTime();

        queryWrapper.eq(StringUtils.isNotEmpty(name), Menu::getName, name)
                .between(Menu::getCreateTime, tomorrowBegin, tomorrowEnd)
                .orderByDesc(Menu::getCreateTime);

        menuService.page(pageInfo, queryWrapper);
        return R.success(pageInfo);
    }

    /**
     * 添加菜单接口（默认添加到明日菜单）
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
        // 明日时间范围
        Date tomorrowBegin = MyTimeUtils.getNextDayOfBeginTime();
        Date tomorrowEnd = MyTimeUtils.getNextDayOfEndTime();

        // 检查明日是否已存在该菜品
        LambdaQueryWrapper<Menu> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Menu::getName, name)
                .between(Menu::getCreateTime, tomorrowBegin, tomorrowEnd);
        Menu isExist = menuService.getOne(queryWrapper);

        if (isExist != null) {
            return R.fail("当前菜品已添加到明日菜单");
        }

        Menu menu = new Menu();
        menu.setName(recipe.getName());
        menu.setCategory(recipe.getCategory());
        menu.setPicture(recipe.getPicture());
        menu.setUnit(recipe.getUnit());
        menu.setPrice(recipe.getPrice());
        menu.setCreateTime(tomorrowBegin); // 默认为明日0点

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
     * 复用到明日菜单接口
     */
    @PreAuthorize("hasRole('manager')")
    @PostMapping("/multiplex/{menuIds}")
    public R<String> multiplex(@PathVariable String menuIds) {
        if (StrUtil.isEmpty(menuIds)) {
            return R.fail("请选择要复用的菜单");
        }

        StringBuilder sb = new StringBuilder();
        boolean res = true;
        String[] ids = menuIds.split(",");
        Date tomorrowBegin = MyTimeUtils.getNextDayOfBeginTime();
        Date tomorrowEnd = MyTimeUtils.getNextDayOfEndTime();

        for (String id : ids) {
            try {
                Long menuId = Long.valueOf(id);
                Menu menu = menuService.getById(menuId);

                if (menu != null) {
                    String name = menu.getName();
                    // 检查明日是否已存在该菜品
                    LambdaQueryWrapper<Menu> queryWrapper = new LambdaQueryWrapper<>();
                    queryWrapper.eq(Menu::getName, name)
                            .between(Menu::getCreateTime, tomorrowBegin, tomorrowEnd);
                    Menu one = menuService.getOne(queryWrapper);

                    if (one == null) {
                        menu.setCreateTime(tomorrowBegin);
                        menu.setMenuId(null); // 清空ID实现新增
                        res = res && menuService.save(menu);
                    } else {
                        res = false;
                        sb.append(menu.getName()).append("、");
                    }
                }
            } catch (NumberFormatException e) {
                res = false;
                log.error("菜单ID格式错误: {}", id, e);
            }
        }

        if (res) {
            return R.success("批量复用成功");
        } else {
            String msg = sb.length() > 0 ? "[" + sb.substring(0, sb.length() - 1) + "]已存在，无法复用，其余复用成功" : "部分菜单复用失败";
            return R.fail("批量复用失败，" + msg);
        }
    }
}