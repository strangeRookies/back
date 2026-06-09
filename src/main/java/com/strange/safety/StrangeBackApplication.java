package com.strange.safety;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StrangeBackApplication {

    public static void main(String[] args) {
        SpringApplication.run(StrangeBackApplication.class, args);
    }
}
