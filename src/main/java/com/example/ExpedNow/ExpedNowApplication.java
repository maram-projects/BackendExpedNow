package com.example.ExpedNow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@EnableConfigurationProperties


@SpringBootApplication(scanBasePackages = {"com.example.ExpedNow"})
public class 	ExpedNowApplication {

	public static void main(String[] args) {
		SpringApplication.run(ExpedNowApplication.class, args);
	}

}
