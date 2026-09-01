package com.inventory.api;

import com.inventory.api.repository.common.CommonRepositoryImpl;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories(
        repositoryBaseClass = CommonRepositoryImpl.class
)
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

}
