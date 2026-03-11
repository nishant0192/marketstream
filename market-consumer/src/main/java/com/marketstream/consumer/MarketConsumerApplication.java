package com.marketstream.consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EntityScan("com.marketstream.common.entity")
public class MarketConsumerApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarketConsumerApplication.class, args);
    }
}
