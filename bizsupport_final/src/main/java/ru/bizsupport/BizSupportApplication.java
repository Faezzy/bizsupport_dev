package ru.bizsupport;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BizSupportApplication {
    public static void main(String[] args) {
        SpringApplication.run(BizSupportApplication.class, args);
    }
}
