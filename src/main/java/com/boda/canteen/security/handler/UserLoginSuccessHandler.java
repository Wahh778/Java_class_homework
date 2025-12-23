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
 * UserLoginSuccessHandler 是 Spring Security 登录成功后的自定义处理器，实现了 AuthenticationSuccessHandler 接口，
 * 核心作用是替代 Spring Security 默认的 “登录成功后跳转页面” 逻辑，
 * 改为自定义的登录成功行为（如查询用户完整信息、存储 Session、记录日志、按角色返回差异化响应）。
 */
@Component
public class UserLoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(UserLoginSuccessHandler.class);

    @Autowired
    private MyUserService myUserService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        // 从Authentication对象中获取已认证的用户主体（Spring Security的User类）
        //Authentication authentication：Spring Security 认证成功后封装的认证信息对象，包含已通过校验的用户信息、权限等。
        //authentication.getPrincipal()：返回 “用户主体”，登录成功后此处是 Spring Security 内置的 User 类实例（由 MyUserDetailsService 构建），存储了用户名和权限。
        User securityUser = (User) authentication.getPrincipal();
        String username = securityUser.getUsername();

        // 根据用户名查询数据库中的完整用户信息
        //目的：Spring Security 的 User 类仅存储了用户名、密码、权限，而业务中需要用户的完整信息（如角色、ID、昵称等），因此需再次查询数据库的 MyUser 实体（自定义业务实体）。
        //StrUtil.isNotEmpty(username)：防御性编程，防止用户名空值导致无效 SQL
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