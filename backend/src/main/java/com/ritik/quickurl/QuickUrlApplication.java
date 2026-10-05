package com.ritik.quickurl;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class QuickUrlApplication {

	public static void main(String[] args) {
		SpringApplication.run(QuickUrlApplication.class, args);
	}

}
