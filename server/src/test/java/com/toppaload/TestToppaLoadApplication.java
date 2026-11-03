package com.toppaload;

import org.springframework.boot.SpringApplication;

public class TestToppaLoadApplication {

    public static void main(String[] args) {
        SpringApplication.from(ToppaLoadApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}

