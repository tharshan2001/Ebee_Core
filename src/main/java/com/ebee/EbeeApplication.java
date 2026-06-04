package com.ebee;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EbeeApplication {

    public static void main(String[] args) {
        SpringApplication.run(EbeeApplication.class, args);
    }

}