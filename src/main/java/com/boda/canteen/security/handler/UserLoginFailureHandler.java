package com.boda.canteen.security.handler;

import com.boda.canteen.common.R;
import com.boda.canteen.common.ResponseUtil;
import com.boda.canteen.exception.VerifyCodeException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 登录失败处理类
 * Spring Security 登录失败后的自定义处理器，实现了 AuthenticationFailureHandler 接口，
 * 核心作用是替代 Spring Security 默认的 “登录失败跳转页面” 逻辑，改为按失败原因返回标准化的 JSON
 * 错误响应（区分验证码错误、用户名不存在、密码错误等场景），让前端能精准提示用户失败原因。
 */
@Component  //标记为 Spring 组件，让容器扫描并注入配置类的使用）。
public class UserLoginFailureHandler implements AuthenticationFailureHandler {
    //必须重写 onAuthenticationFailure 方法 —— 该方法会在用户登录校验失败时（如验证码错、用户名错、密码错）自动触发。
    @Override
    //HTTP 请求 / 响应对象，用于向前端返回 JSON 响应；
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {
        // 这些对于操作的处理类可以根据不同异常进行不同处理
        if (exception instanceof VerifyCodeException){
            ResponseUtil.out(response, R.fail(500, "验证码错误", null));
        }
        if (exception instanceof UsernameNotFoundException){
            ResponseUtil.out(response, R.fail(500, "用户名不存在", null));
        }
        if (exception instanceof BadCredentialsException){
            ResponseUtil.out(response,R.fail(500, "用户名密码不正确", null));
        }
        ResponseUtil.out(response,R.fail(500, "出现未知错误，登录失败", null));
    }
}