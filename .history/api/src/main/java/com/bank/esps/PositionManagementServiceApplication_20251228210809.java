package com.bank.esps;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication(scanBasePackages = "com.bank.esps")
@EnableKafka
@EnableRetry
@EnableTransactionManagement
public class PositionManagementServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PositionManagementServiceApplication.class, args);
    }
}
