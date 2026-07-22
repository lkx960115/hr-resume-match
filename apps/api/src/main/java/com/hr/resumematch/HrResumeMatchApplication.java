package com.hr.resumematch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class HrResumeMatchApplication {
    public static void main(String[] args) {
        SpringApplication.run(HrResumeMatchApplication.class, args);
    }
}
