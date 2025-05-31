package io.g8.customai.customer_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Random;

@Configuration
public class CsConfig {
    /*
        Bean扔到Impl里会循环依赖
    */
    @Bean
    public Random getRandom(){
        return new Random();
    }
}
