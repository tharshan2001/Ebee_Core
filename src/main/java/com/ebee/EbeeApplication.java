package com.ebee;

import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableRetry
@EnableSchedulerLock(defaultLockAtMostFor = "10m")
public class EbeeApplication {

    public static void main(String[] args) {
        SpringApplication.run(EbeeApplication.class, args);
    }

}