package com.boda.canteen.security;

import com.boda.canteen.security.filter.VerifyCodeFilter;
import com.boda.canteen.security.handler.*;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity; // 替代旧注解
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.frameoptions.XFrameOptionsHeaderWriter; // 新增
@Configuration  //配置类
@EnableWebSecurity  //启动spring security核心功能
@EnableMethodSecurity(prePostEnabled = true) // 开启方法级权限控制，允许使用@PreAuthorize/@PostAuthorize 注解
public class SecurityConfig {

    @Resource
    private UserLoginSuccessHandler userLoginSuccessHandler;//登录成功后的自定义逻辑（如记录日志、返回自定义响应）
    @Resource
    private UserLoginFailureHandler userLoginFailureHandler;//登录失败后的自定义逻辑（如返回错误原因）
    @Resource
    private UserLogoutSuccessHandler userLogoutSuccessHandler;//登出成功后的自定义逻辑（如清理 Session）
    @Resource
    private UserAuthAccessDeniedHandler userAuthAccessDeniedHandler;//用户权限不足时的异常处理
    @Resource
    private UserAuthenticationEntryPointHandler userAuthenticationEntryPointHandler;//用户未登录访问受保护接口时的异常处理
    @Resource
    private VerifyCodeFilter verifyCodeFilter;  //登录前的验证码校验过滤器

    //密码加密器 PasswordEncoder
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    //安全过滤链 SecurityFilterChain
    //通过 HttpSecurity 构建请求过滤规则
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. 使用 authorizeHttpRequests() 配置 URL 访问权限
                .authorizeHttpRequests(auth -> auth
                        // 配置无需登录的接口/资源
                        .requestMatchers(
                                "/admin/**",
                                "/component/**",
                                "/config/**",
                                "/frontStatic/**",// 静态资源/公共接口
                                "/back/**",
                                "/picture/**",
                                "/login",
                                "/unAuth",
                                "/login/userLogin",// 登录相关接口
                                "/common/getCode",
                                "/sale/add" // 验证码/销售添加接口
                        ).permitAll()// 以上接口允许匿名访问（无需登录）
                        // 其他所有请求需要认证（登录后才能访问）
                        .anyRequest().authenticated()
                )
                // 2. 未登录访问受保护接口时的处理
                .httpBasic(basic -> basic
                        .authenticationEntryPoint(userAuthenticationEntryPointHandler)
                )
                // 3. 配置表单登录，通过 formLogin() 自定义登录逻辑：
                .formLogin(form -> form
                        .loginPage("/login")// 自定义登录页面
                        .loginProcessingUrl("/login/userLogin")// 登录请求处理接口
                        .usernameParameter("username")// 用户名参数名（与前端表单对应）
                        .passwordParameter("password")// 密码参数名
                        .successHandler(userLoginSuccessHandler)// 登录成功处理器
                        .failureHandler(userLoginFailureHandler)// 登录失败处理器
                )
                // 4. 配置登出，通过 logout() 定义登出行为：
                .logout(logout -> logout
                        .logoutUrl("/login/logout")// 登出请求的接口地址
                        .logoutSuccessHandler(userLogoutSuccessHandler)// 登出成功处理器
                )
                // 5. 配置权限异常处理
                .exceptionHandling(ex -> ex// 权限不足时的处理器
                        .accessDeniedHandler(userAuthAccessDeniedHandler) // 权限不足时跳转的页面（兜底，优先级低于 Handler）
                        .accessDeniedPage("/to403")
                )
                // 6. 跨域配置
                .cors(cors -> cors.configurationSource(request -> {
                    // 保持默认跨域配置，如需自定义可在此扩展
                    return new org.springframework.web.cors.CorsConfiguration().applyPermitDefaultValues();
                }))
                // 7. 关闭 CSRF 保护（适合前后端分离/内部系统，生产环境需评估）
                .csrf(csrf -> csrf.disable())
                // 8. 配置 X-Frame-Options：允许同源页面嵌入（如 iframe）
                .headers(headers -> headers
                        .addHeaderWriter(new XFrameOptionsHeaderWriter(
                                XFrameOptionsHeaderWriter.XFrameOptionsMode.SAMEORIGIN
                        ))
                );

        // 关键：在用户名密码校验过滤器之前，添加验证码校验过滤器
        http.addFilterBefore(verifyCodeFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}