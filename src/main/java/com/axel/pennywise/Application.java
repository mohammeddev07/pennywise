package com.axel.pennywise;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class Application {

	public static void main(String[] args) {
		log.info("Starting PennyWise Application");
		SpringApplication.run(Application.class, args);
		log.info("PennyWise Application started successfully");
	}

}
