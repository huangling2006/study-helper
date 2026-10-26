package com.hl.study;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.hl.study.**.mapper") // 加在这里
public class ProjectAApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProjectAApplication.class, args);
    }
}