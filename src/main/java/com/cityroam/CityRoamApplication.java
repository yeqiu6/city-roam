package com.cityroam;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@EnableAspectJAutoProxy(exposeProxy = true)
@MapperScan("com.cityroam.mapper")
@SpringBootApplication
public class CityRoamApplication {

    public static void main(String[] args) {
        SpringApplication.run(CityRoamApplication.class, args);
    }

}
