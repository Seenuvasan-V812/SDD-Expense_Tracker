package com.dailyexpense.savingsgoal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SavingsGoalServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(SavingsGoalServiceApplication.class, args);
    }
}
