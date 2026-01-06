package com.boda.canteen.config;

import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ListenerConfig {

    @Bean
    public ServletListenerRegistrationBean<OnlineUserListener> onlineUserListener() {
        ServletListenerRegistrationBean<OnlineUserListener> registrationBean = new ServletListenerRegistrationBean<>();
        registrationBean.setListener(new OnlineUserListener());
        return registrationBean;
    }
}
