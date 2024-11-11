package com.ordernest.sso;

import com.ordernest.sso.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class SsoServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SsoServiceApplication.class, args);
    }
}
