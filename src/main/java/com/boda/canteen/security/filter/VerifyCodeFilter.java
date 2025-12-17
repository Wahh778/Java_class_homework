package com.boda.canteen.security.filter;

import com.boda.canteen.exception.VerifyCodeException;
import com.boda.canteen.security.handler.UserLoginFailureHandler;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 验证码校验过滤器
 */
@Slf4j
@Component
public class VerifyCodeFilter extends OncePerRequestFilter {

    @Resource
    private UserLoginFailureHandler userLoginFailureHandler;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // 仅拦截登录接口（精准匹配，避免拦截其他接口）
        String requestURI = request.getRequestURI();
        String method = request.getMethod();
        if ("/login/userLogin".equals(requestURI) && "POST".equalsIgnoreCase(method)) {
            try {
                validate(request); // 校验验证码
            } catch (VerifyCodeException e) {
                // 校验失败，交给失败处理器处理
                userLoginFailureHandler.onAuthenticationFailure(request, response, e);
                return; // 终止过滤器链，不再向下执行
            }
        }
        // 非登录接口/校验通过，放行请求
        filterChain.doFilter(request, response);
    }

    /**
     * 验证码校验核心逻辑（增加全量空值校验+trim处理）
     */
    private void validate(HttpServletRequest request) {
        // 1. 获取前端提交的验证码（可能为null）
        String captcha = request.getParameter("captcha");
        // 2. 获取session中存储的验证码（可能为null）
        String code = (String) request.getSession().getAttribute("code");

        log.info("前端提交的验证码：{}", captcha);
        log.info("Session中存储的验证码：{}", code);

        // 3. 空值校验：前端未传验证码
        if (captcha == null || captcha.trim().isEmpty()) {
            throw new VerifyCodeException("验证码不能为空");
        }

        // 4. 空值校验：Session中无验证码（过期/未生成）
        if (code == null || code.trim().isEmpty()) {
            throw new VerifyCodeException("验证码已过期，请重新获取");
        }

        // 5. 忽略大小写+trim后比较（避免空格导致的校验失败）
        if (!code.trim().equalsIgnoreCase(captcha.trim())) {
            throw new VerifyCodeException("验证码不正确");
        }

        // 6. 验证通过后移除Session中的验证码（防止重复使用）
        request.getSession().removeAttribute("code");
    }
}