package com.stegovault;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// This is the entry point of the entire Spring Boot application
// @SpringBootApplication combines three annotations:
// @Configuration + @EnableAutoConfiguration + @ComponentScan
@SpringBootApplication
public class StegoVaultApplication {

    public static void main(String[] args) {
        SpringApplication.run(StegoVaultApplication.class, args);
        System.out.println("StegoVault is running at http://localhost:8080");
    }
}