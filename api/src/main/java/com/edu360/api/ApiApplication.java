package com.edu360.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The starting point of the whole backend. Running this file's main method
 * starts the Spring Boot server (by default on port 8080).
 *
 * EnableScheduling: needed for TenantRoutingDataSource's periodic idle-pool eviction
 * (ISOLATED-tenant database pools that haven't been used in a while get closed and removed).
 */
@SpringBootApplication
@EnableScheduling
public class ApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(ApiApplication.class, args);
	}

}
