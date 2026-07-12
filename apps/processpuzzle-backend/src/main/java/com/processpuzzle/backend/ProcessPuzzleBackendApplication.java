package com.processpuzzle.backend;

import com.processpuzzle.store.adapters.outbound.MinioConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.processpuzzle")
@EnableJpaRepositories(basePackages = "com.processpuzzle")
@EntityScan(basePackages = "com.processpuzzle")
@Import({MinioConfig.class})
public class ProcessPuzzleBackendApplication {

    static void main(String[] args) {
        SpringApplication.run(ProcessPuzzleBackendApplication.class, args);
    }

}
