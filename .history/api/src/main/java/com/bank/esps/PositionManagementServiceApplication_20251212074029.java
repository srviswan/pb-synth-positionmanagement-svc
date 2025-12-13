package com.bank.esps;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@EnableKafka
public class PositionManagementServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PositionManagementServiceApplication.class, args);
    }
}
