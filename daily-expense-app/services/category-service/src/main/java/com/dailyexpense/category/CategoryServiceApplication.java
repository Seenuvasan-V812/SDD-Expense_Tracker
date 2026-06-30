package com.dailyexpense.category;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CategoryServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CategoryServiceApplication.class, args);
    }
}
