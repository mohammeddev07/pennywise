package com.axel.pennywise.config;

import com.axel.pennywise.util.RequestIdFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RequestIdConfig {
    @Bean
    public RequestIdFilter requestIdFilter() {
        return new RequestIdFilter();
    }
}
