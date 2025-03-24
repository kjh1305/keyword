package com.example.demo;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@MapperScan(basePackages = {"com.example.demo.api.keyword.apicount", "com.example.demo.api.keyword.work", "com.example.demo.api.keyword.backup",
"com.example.demo.api.keyword.category", "com.example.demo.api.keyword.rank"})
@EnableJpaRepositories
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

}
