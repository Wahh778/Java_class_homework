package com.boda.canteen.config;

import com.boda.canteen.common.JacksonObjectMapper;
import com.boda.canteen.interception.StaffOrderInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;

import java.util.List;

@Configuration
public class WebMvcConfig extends WebMvcConfigurationSupport {

    // 注入 Spring 管理的拦截器实例
    @Autowired
    private StaffOrderInterceptor staffOrderInterceptor; // 新增

    /**
     * 配置视图控制
     */
    @Override
    protected void addViewControllers(ViewControllerRegistry registry) {
        // 登录页面跳转
        registry.addViewController("/login").setViewName("login");
        // 后台首页框架页面跳转
        registry.addViewController("/back/toIndex").setViewName("index");
        // 错误页面跳转
        registry.addViewController("/to403").setViewName("error/403");
        registry.addViewController("/to404").setViewName("error/404");
        registry.addViewController("/to500").setViewName("error/500");
    }

    /**
     * 设置静态资源映射（保持原有逻辑）
     */
    @Override
    protected void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/");
        registry.addResourceHandler("/picture/**")
                .addResourceLocations("file:./picture/");
    }

    /**
     * 扩展消息转换器（保持原有逻辑）
     */
    @Override
    protected void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        MappingJackson2HttpMessageConverter messageConverter = new MappingJackson2HttpMessageConverter();
        messageConverter.setObjectMapper(new JacksonObjectMapper());
        converters.add(0, messageConverter);
    }

    /**
     * 注册拦截器（新增/完善此方法）
     */
    @Override
    protected void addInterceptors(InterceptorRegistry registry) {
        // 使用注入的实例，而非 new StaffOrderInterceptor()
        registry.addInterceptor(staffOrderInterceptor)
                .addPathPatterns("/order/submit");
    }
}