package com.inventory.api;

import com.inventory.api.repository.common.CommonRepositoryImpl;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Boots the inventory service.
 * <p>
 * The {@code repositoryBaseClass} below is not decoration: it is what makes every
 * repository in this service inherit {@link CommonRepositoryImpl}, and therefore
 * the shared search, soft-delete and activate/deactivate behaviour. Remove it and
 * those methods disappear from every repository at once.
 */
@SpringBootApplication
@EnableJpaRepositories(
        repositoryBaseClass = CommonRepositoryImpl.class
)
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

}
