package com.scheduletl;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SchedulEtlApplication {

    public static void main(String[] args) {
        SpringApplication.run(SchedulEtlApplication.class, args);
    }
}
