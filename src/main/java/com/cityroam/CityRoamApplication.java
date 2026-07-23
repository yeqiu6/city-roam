package com.cityroam;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import com.cityroam.config.AiProperties;

@EnableAspectJAutoProxy(exposeProxy = true)
@MapperScan("com.cityroam.mapper")
@SpringBootApplication
@EnableConfigurationProperties(AiProperties.class)
public class CityRoamApplication {

    public static void main(String[] args) {
        SpringApplication.run(CityRoamApplication.class, args);
    }

}
