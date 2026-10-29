package com.rajharita;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RajharitaApplication {

    public static void main(String[] args) {
        SpringApplication.run(RajharitaApplication.class, args);
        System.out.println("🌿 Rajharita Naturals started successfully!");
    }
}