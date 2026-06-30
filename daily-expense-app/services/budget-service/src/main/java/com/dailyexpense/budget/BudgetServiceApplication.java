package com.dailyexpense.budget;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BudgetServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(BudgetServiceApplication.class, args);
    }
}
