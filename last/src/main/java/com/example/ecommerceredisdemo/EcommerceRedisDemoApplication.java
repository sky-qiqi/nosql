package com.example.ecommerceredisdemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // 启用定时任务
public class EcommerceRedisDemoApplication {

    public static void main(String[] args) {
        // 核心修改在这里：使用 EcommerceRedisDemoApplication.class
        SpringApplication.run(EcommerceRedisDemoApplication.class, args);
    }
}