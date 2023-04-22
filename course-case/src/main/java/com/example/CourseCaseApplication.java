package com.example;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;


@MapperScan({"com.example.transaction.mapper"})
@EnableTransactionManagement
@SpringBootApplication
public class CourseCaseApplication {

    public static void main(String[] args) {
        SpringApplication.run(CourseCaseApplication.class, args);
    }

}
