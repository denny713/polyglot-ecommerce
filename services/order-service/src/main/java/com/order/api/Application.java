package com.order.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import com.order.api.repository.common.CommonRepositoryImpl;

/** Boots the order service. */
@SpringBootApplication
@EnableJpaRepositories(repositoryBaseClass = CommonRepositoryImpl.class)
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

}
