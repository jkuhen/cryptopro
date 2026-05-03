package com.kuhen.cryptopro;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CryptoproApplication {

    public static void main(String[] args) {
        SpringApplication.run(CryptoproApplication.class, args);
    }
}

