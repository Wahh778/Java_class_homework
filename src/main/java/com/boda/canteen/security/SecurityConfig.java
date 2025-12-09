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
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true) // 替代 @EnableGlobalMethodSecurity
public class SecurityConfig {

    @Resource
    private UserLoginSuccessHandler userLoginSuccessHandler;
    @Resource
    private UserLoginFailureHandler userLoginFailureHandler;
    @Resource
    private UserLogoutSuccessHandler userLogoutSuccessHandler;
    @Resource
    private UserAuthAccessDeniedHandler userAuthAccessDeniedHandler;
    @Resource
    private UserAuthenticationEntryPointHandler userAuthenticationEntryPointHandler;
    @Resource
    private VerifyCodeFilter verifyCodeFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. 替代 authorizeRequests()：使用 authorizeHttpRequests()
                .authorizeHttpRequests(auth -> auth
                        // 配置无需登录的接口/资源
                        .requestMatchers(
                                "/admin/**",
                                "/component/**",
                                "/config/**",
                                "/frontStatic/**",
                                "/back/**",
                                "/picture/**",
                                "/login",
                                "/unAuth",
                                "/login/userLogin",
                                "/common/getCode",
                                "/sale/add"
                        ).permitAll()
                        // 其他请求需要认证
                        .anyRequest().authenticated()
                )
                // 2. 配置未登录处理
                .httpBasic(basic -> basic
                        .authenticationEntryPoint(userAuthenticationEntryPointHandler)
                )
                // 3. 配置表单登录
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login/userLogin")
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .successHandler(userLoginSuccessHandler)
                        .failureHandler(userLoginFailureHandler)
                )
                // 4. 配置登出
                .logout(logout -> logout
                        .logoutUrl("/login/logout")
                        .logoutSuccessHandler(userLogoutSuccessHandler)
                )
                // 5. 配置权限异常处理
                .exceptionHandling(ex -> ex
                        .accessDeniedHandler(userAuthAccessDeniedHandler)
                        .accessDeniedPage("/to403")
                )
                // 6. 跨域配置
                .cors(cors -> cors.configurationSource(request -> {
                    // 保持默认跨域配置，如需自定义可在此扩展
                    return new org.springframework.web.cors.CorsConfiguration().applyPermitDefaultValues();
                }))
                // 7. 关闭CSRF
                .csrf(csrf -> csrf.disable())
                // 8. 替代 frameOptions()：配置X-Frame-Options允许同源
                .headers(headers -> headers
                        .addHeaderWriter(new XFrameOptionsHeaderWriter(
                                XFrameOptionsHeaderWriter.XFrameOptionsMode.SAMEORIGIN
                        ))
                );

        // 添加验证码过滤器
        http.addFilterBefore(verifyCodeFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}