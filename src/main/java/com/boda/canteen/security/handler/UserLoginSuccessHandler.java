package com.boda.canteen.security.handler;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.boda.canteen.common.R;
import com.boda.canteen.common.ResponseUtil;
import com.boda.canteen.entity.MyUser;
import com.boda.canteen.security.service.MyUserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User; // 导入Spring Security的User类
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 登录成功处理类（修复类型转换+日志）
 */
@Component
public class UserLoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(UserLoginSuccessHandler.class);

    @Autowired
    private MyUserService myUserService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        // 关键修复：转换为Spring Security的User类（而非Tomcat的User）
        User securityUser = (User) authentication.getPrincipal();
        String username = securityUser.getUsername();

        // 查询数据库用户信息
        LambdaQueryWrapper<MyUser> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(StrUtil.isNotEmpty(username), MyUser::getUsername, username);
        MyUser myUser = myUserService.getOne(queryWrapper);

        if (myUser == null) {
            log.error("登录成功后未查询到用户信息，用户名：{}", username);
            ResponseUtil.out(response, R.fail("用户信息异常"));
            return;
        }

        // 存储当前用户到Session
        request.getSession().setAttribute("currUser", myUser);
        log.info("用户{}（角色：{}）登录成功", username, myUser.getRole());

        // 按角色返回响应
        if ("staff".equals(myUser.getRole())) {
            ResponseUtil.out(response, R.success("员工登录成功"));
        } else {
            ResponseUtil.out(response, R.success("登录成功"));
        }
    }
}