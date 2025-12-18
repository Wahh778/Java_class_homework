package com.boda.canteen.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.poi.excel.ExcelReader;
import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.boda.canteen.common.R;
import com.boda.canteen.entity.MyUser;
import com.boda.canteen.exception.CustomException;
import com.boda.canteen.security.service.MyUserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

@Slf4j
@RestController
@RequestMapping("/user")
public class MyUserController {

    @Autowired
    private MyUserService myUserService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * 获取当前用户信息接口
     */
    @GetMapping("/getInfo")
    public R<MyUser> getInfo(HttpServletRequest request) {
        MyUser user = (MyUser) request.getSession().getAttribute("currUser");
        return R.success(user);
    }

    /**
     * 用户分页接口
     */
    @PreAuthorize("hasRole('manager')")
    @GetMapping("/page")
    public R<Page<MyUser>> getAllUsers(int page, int limit,
                                       @RequestParam(required = false) String name) {

        Page<MyUser> pageInfo = new Page<>(page, limit);

        LambdaQueryWrapper<MyUser> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(StrUtil.isNotEmpty(name), MyUser::getName, name)
                .orderByAsc(MyUser::getUserId);

        myUserService.page(pageInfo, queryWrapper);

        return R.success(pageInfo);
    }

    /**
     * 添加用户接口
     */
    @PreAuthorize("hasRole('manager')")
    @PostMapping("/add")
    public R<String> addUser(@RequestBody MyUser myUser) {
        if (StrUtil.isEmpty(myUser.getUsername())) {
            throw new CustomException("用户名为空");
        }
        if (StrUtil.isEmpty(myUser.getName())) {
            throw new CustomException("昵称为空");
        }
        if (StrUtil.isEmpty(myUser.getTelephone())) {
            throw new CustomException("电话为空");
        }
        if (StrUtil.isEmpty(myUser.getDepartment())) {
            throw new CustomException("部门为空");
        }
        if (StrUtil.isEmpty(myUser.getRole())) {
            throw new CustomException("角色为空");
        }
        // 角色唯一性校验：只限制 manager 和 chef
        String role = myUser.getRole();
        if ("manager".equals(role) || "chef".equals(role)) {
            LambdaQueryWrapper<MyUser> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(MyUser::getRole, role);
            long count = myUserService.count(queryWrapper);
            if (count > 0) {
                String roleName = "manager".equals(role) ? "食堂经理" : "厨房大厨";
                return R.fail(roleName + "已存在，不允许新增");
            }
        }
        // 密码加密
        String rawPassword = "123456";
        String encodedPassword = passwordEncoder.encode(rawPassword);
        myUser.setPassword(encodedPassword);
        boolean res = myUserService.save(myUser);
        if (res) {
            return R.success("添加成功");
        } else {
            return R.fail("添加失败");
        }
    }

    /**
     * 下载导入模板
     * 彻底移除 userId、password 列，仅保留业务列；角色、部门列添加下拉选择框
     */
    @PreAuthorize("hasRole('manager')")
    @GetMapping("/template")
    public void downloadTemplate(HttpServletResponse response) {
        try {
            // 设置响应头
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment;filename=" +
                    URLEncoder.encode("员工导入模板.xlsx", "UTF-8"));

            // 创建Excel并设置表头（仅保留业务列，强制剔除userId/password）
            ExcelWriter writer = ExcelUtil.getWriter();
            // 关键：设置仅输出添加了别名的列（彻底排除userId、password等未映射字段）
            writer.setOnlyAlias(true);

            // 仅映射需要的业务列（顺序对应Excel列顺序）
            writer.addHeaderAlias("username", "账号");    // A列
            writer.addHeaderAlias("name", "昵称");        // B列
            writer.addHeaderAlias("sex", "性别");         // C列
            writer.addHeaderAlias("telephone", "电话");   // D列
            writer.addHeaderAlias("department", "部门");  // E列
            writer.addHeaderAlias("role", "角色");        // F列
            writer.addHeaderAlias("workInformation", "工作信息"); // G列

            // 写入示例数据（仅包含业务列，无userId/password）
            MyUser example1 = new MyUser();
            example1.setUsername("caterer01");
            example1.setName("配餐员01");
            example1.setSex("女");
            example1.setTelephone("13800138001");
            example1.setDepartment("生产部");
            example1.setRole("caterer");
            example1.setWorkInformation("6楼303室");

            MyUser example2 = new MyUser();
            example2.setUsername("treasurer02");
            example2.setName("财务02");
            example2.setSex("女");
            example2.setTelephone("13800138002");
            example2.setDepartment("财务部");
            example2.setRole("treasurer");
            example2.setWorkInformation("6楼301室");

            MyUser example3 = new MyUser();
            example3.setUsername("staff01");
            example3.setName("员工01");
            example3.setSex("男");
            example3.setTelephone("13800138003");
            example3.setDepartment("技术部");
            example3.setRole("staff");
            example3.setWorkInformation("6楼305室");

            writer.write(List.of(example1, example2, example3), true);

            // ========== 设置下拉选择框（仅针对部门、角色列） ==========
            Sheet sheet = writer.getSheet();
            DataValidationHelper helper = sheet.getDataValidationHelper();

            // 1. 部门列下拉配置（E列，第5列，索引从0开始）
            String[] departments = {"财务部", "技术部", "营销部", "生产部"};
            // 下拉范围：从第2行（数据行）到第1000行，第4列（E列，索引0开始）
            CellRangeAddressList deptRange = new CellRangeAddressList(1, 1000, 4, 4);
            DataValidationConstraint deptConstraint = helper.createExplicitListConstraint(departments);
            DataValidation deptValidation = helper.createValidation(deptConstraint, deptRange);
            deptValidation.setShowErrorBox(true);
            deptValidation.createErrorBox("错误", "请选择有效的部门！");
            sheet.addValidationData(deptValidation);

            // 2. 角色列下拉配置（F列，第6列，索引从0开始）
            String[] roles = {"caterer(配餐员)", "treasurer(财务管理)", "staff(企业员工)"};
            // 下拉范围：从第2行（数据行）到第1000行，第5列（F列，索引0开始）
            CellRangeAddressList roleRange = new CellRangeAddressList(1, 1000, 5, 5);
            DataValidationConstraint roleConstraint = helper.createExplicitListConstraint(roles);
            DataValidation roleValidation = helper.createValidation(roleConstraint, roleRange);
            roleValidation.setShowErrorBox(true);
            roleValidation.createErrorBox("错误", "请选择有效的角色！");
            sheet.addValidationData(roleValidation);

            // 输出到响应流
            writer.flush(response.getOutputStream());
            writer.close();
        } catch (Exception e) {
            log.error("下载模板失败", e);
            throw new CustomException("模板下载失败：" + e.getMessage());
        }
    }

    /**
     * 批量导入员工
     * 支持角色：caterer（配餐员）、treasurer（财务管理）、staff（企业员工）
     */
    @PreAuthorize("hasRole('manager')")
    @PostMapping("/batchImport")
    public R<String> batchImport(@RequestParam("file") MultipartFile file) {
        // 1. 文件校验
        if (file.isEmpty()) {
            return R.fail("文件不能为空");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || (!originalFilename.endsWith(".xlsx") && !originalFilename.endsWith(".xls"))) {
            return R.fail("仅支持.xlsx/.xls格式的Excel文件");
        }

        try (InputStream inputStream = file.getInputStream()) {
            // 2. 读取Excel文件
            ExcelReader reader = ExcelUtil.getReader(inputStream);
            // 表头映射（仅业务列，无userId）
            reader.addHeaderAlias("账号", "username");
            reader.addHeaderAlias("昵称", "name");
            reader.addHeaderAlias("性别", "sex");
            reader.addHeaderAlias("电话", "telephone");
            reader.addHeaderAlias("部门", "department");
            reader.addHeaderAlias("角色", "role");
            reader.addHeaderAlias("工作信息", "workInformation");

            List<Map<String, Object>> dataList = reader.readAll();
            if (dataList.isEmpty()) {
                return R.fail("Excel文件中无有效数据");
            }

            // 3. 处理导入数据
            int successCount = 0;
            int failCount = 0;
            StringJoiner errorMsg = new StringJoiner("；");
            // 允许导入的角色列表
            List<String> allowRoles = List.of("caterer", "treasurer", "staff");

            for (int i = 0; i < dataList.size(); i++) {
                Map<String, Object> data = dataList.get(i);
                int rowNum = i + 2; // Excel行号（表头行是1）
                try {
                    MyUser user = new MyUser();
                    // 读取基础字段（无userId）
                    user.setUsername(getStringValue(data, "username"));
                    user.setName(getStringValue(data, "name"));
                    user.setSex(getStringValue(data, "sex"));
                    user.setTelephone(getStringValue(data, "telephone"));
                    user.setDepartment(getStringValue(data, "department"));
                    user.setWorkInformation(getStringValue(data, "workInformation"));

                    // 读取角色字段并处理（兼容下拉值的格式：caterer(配餐员)）
                    String role = getStringValue(data, "role");
                    if (StrUtil.isEmpty(role)) {
                        throw new CustomException("角色为空");
                    }
                    // 提取括号前的英文值：如 "caterer(配餐员)" -> "caterer"
                    String roleValue = role.contains("(") ? role.split("\\(")[0].trim() : role.trim();
                    if (!allowRoles.contains(roleValue)) {
                        throw new CustomException("角色不合法（仅支持：caterer/treasurer/staff）");
                    }
                    user.setRole(roleValue);

                    // 4. 数据校验
                    validateUser(user);

                    // 5. 用户名唯一性校验
                    if (myUserService.count(new LambdaQueryWrapper<MyUser>()
                            .eq(MyUser::getUsername, user.getUsername())) > 0) {
                        throw new CustomException("用户名已存在");
                    }

                    // 6. 密码加密
                    user.setPassword(passwordEncoder.encode("123456"));

                    // 7. 保存用户（userId自增，无需手动设置）
                    boolean saveResult = myUserService.save(user);
                    if (saveResult) {
                        successCount++;
                    } else {
                        failCount++;
                        errorMsg.add("第" + rowNum + "行：保存失败");
                    }
                } catch (Exception e) {
                    failCount++;
                    errorMsg.add("第" + rowNum + "行：" + e.getMessage());
                    log.error("处理第{}行数据失败", rowNum, e);
                }
            }

            // 8. 返回导入结果
            String resultMsg = String.format("导入完成，成功%d条，失败%d条", successCount, failCount);
            if (failCount > 0) {
                resultMsg += "。错误信息：" + errorMsg.toString();
            }
            return R.success(resultMsg);

        } catch (Exception e) {
            log.error("批量导入用户失败", e);
            return R.fail("导入失败：" + e.getMessage());
        }
    }

    /**
     * 转换角色值（兼容中文输入）
     */
    private String convertRoleToValue(String role) {
        if (StrUtil.isEmpty(role)) {
            return "";
        }
        role = role.trim();
        return switch (role) {
            case "配餐员", "caterer" -> "caterer";
            case "财务管理", "treasurer" -> "treasurer";
            case "企业员工", "staff" -> "staff";
            default -> role;
        };
    }

    /**
     * 验证用户必填字段
     */
    private void validateUser(MyUser user) {
        if (StrUtil.isEmpty(user.getUsername())) {
            throw new CustomException("用户名为空");
        }
        if (StrUtil.isEmpty(user.getName())) {
            throw new CustomException("昵称为空");
        }
        if (StrUtil.isEmpty(user.getTelephone())) {
            throw new CustomException("电话为空");
        }
        if (StrUtil.isEmpty(user.getDepartment())) {
            throw new CustomException("部门为空");
        }
        // 部门合法性校验
        List<String> validDepartments = List.of("财务部", "技术部", "营销部", "生产部");
        if (!validDepartments.contains(user.getDepartment())) {
            throw new CustomException("部门不合法（仅支持：" + String.join("、", validDepartments) + "）");
        }
        // 性别合法性校验
        List<String> validSex = List.of("男", "女");
        if (!validSex.contains(user.getSex())) {
            throw new CustomException("性别不合法（仅支持：" + String.join("、", validSex) + "）");
        }
    }

    /**
     * 工具方法：从Map中获取字符串值
     */
    private String getStringValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value != null ? value.toString().trim() : "";
    }

    /**
     * 修改用户接口
     */
    @PreAuthorize("hasRole('manager')")
    @PutMapping("/update")
    public R<String> updateUser(@RequestBody MyUser myUser, HttpServletRequest request) {
        if (StrUtil.isEmpty(myUser.getUsername())) {
            throw new CustomException("用户名为空");
        }
        if (StrUtil.isEmpty(myUser.getName())) {
            throw new CustomException("昵称为空");
        }
        if (StrUtil.isEmpty(myUser.getTelephone())) {
            throw new CustomException("电话为空");
        }
        if (StrUtil.isEmpty(myUser.getDepartment())) {
            throw new CustomException("部门为空");
        }
        if (StrUtil.isEmpty(myUser.getRole())) {
            throw new CustomException("角色为空");
        }
        MyUser user = (MyUser) request.getSession().getAttribute("editUser");
        myUser.setUserId(user.getUserId());
        boolean res = myUserService.updateById(myUser);
        if (res) {
            return R.success("修改成功");
        } else {
            return R.fail("修改失败");
        }
    }

    /**
     * 删除用户接口
     */
    @PreAuthorize("hasRole('manager')")
    @DeleteMapping("/delete/{userId}") // 修正为 DELETE 方式
    public R<String> deleteUser(@PathVariable Long userId) {
        boolean res = false;
        if (userId != null) {
            res = myUserService.removeById(userId);
        }
        if (res) {
            return R.success("删除成功");
        } else {
            return R.fail("删除失败");
        }
    }

    /**
     * 批量删除用户接口
     */
    @PreAuthorize("hasRole('manager')")
    @DeleteMapping("/deleteBatch/{userIds}") // 修正为 DELETE 方式
    public R<String> deleteBatchUser(@PathVariable String userIds) {
        boolean res = true;
        String[] ids = userIds.split(",");
        for (String id : ids) {
            // 增加空值/格式校验，避免转换异常
            if (StrUtil.isEmpty(id) || !StrUtil.isNumeric(id)) {
                res = false;
                continue;
            }
            Long userId = Long.valueOf(id);
            boolean flag = myUserService.removeById(userId);
            res = res && flag;
        }
        if (res) {
            return R.success("批量删除成功");
        } else {
            return R.fail("批量删除失败，部分数据可能未删除");
        }
    }

    /**
     * 修改用户密码接口（同步加密处理）
     */
    @PostMapping("/updatePwd")
    public R<String> updatePsw(@RequestParam String password, HttpServletRequest request) {
        MyUser user = (MyUser) request.getSession().getAttribute("currUser");
        if (user == null) {
            return R.fail("用户未登录");
        }
        String username = user.getUsername();

        // 对用户输入的新密码进行加密
        String encodedPassword = passwordEncoder.encode(password);

        LambdaUpdateWrapper<MyUser> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(MyUser::getUsername, username)
                .set(MyUser::getPassword, encodedPassword);
        boolean res = myUserService.update(null, updateWrapper);
        if (res) {
            user.setPassword(encodedPassword);
            request.getSession().setAttribute("currUser", user);
            return R.success("更新成功");
        } else {
            return R.fail("更新失败");
        }
    }
    /**
     * 校验旧密码是否正确（新增接口）
     */
    @PostMapping("/checkOldPwd")
    public R<Boolean> checkOldPassword(@RequestParam String oldPassword, HttpServletRequest request) {
        MyUser user = (MyUser) request.getSession().getAttribute("currUser");
        // 从数据库查询最新的加密密码（避免session中密码过期）
        MyUser dbUser = myUserService.getById(user.getUserId());
        // 使用PasswordEncoder比对明文与密文
        boolean isMatch = passwordEncoder.matches(oldPassword, dbUser.getPassword());
        return R.success(isMatch);
    }
}