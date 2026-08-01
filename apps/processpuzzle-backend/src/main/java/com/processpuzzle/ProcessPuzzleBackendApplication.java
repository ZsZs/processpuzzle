package com.processpuzzle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.modulith.Modulith;

/**
 * The Modulith root. Sits directly in {@code com.processpuzzle} rather than a sub-package on purpose:
 * Spring Modulith derives the application modules from the direct sub-packages of the annotated
 * class's package, so this placement is what makes {@code app}, {@code rule}, {@code basestate},
 * {@code workflow}, {@code store}, {@code core} and {@code shared} modules rather than plain jars.
 *
 * <p>{@code @Modulith} is meta-annotated with {@code @SpringBootApplication}, and the component-scan
 * base package now defaults to {@code com.processpuzzle}, so an explicit {@code scanBasePackages} is
 * no longer needed. The JPA attributes stay spelled out because the libraries' entities and
 * repositories are spread across sibling packages and saying so reads better than relying on the
 * default.
 */
@Modulith(systemName = "ProcessPuzzle Backend")
@EnableJpaRepositories(basePackages = "com.processpuzzle")
@EntityScan(basePackages = "com.processpuzzle")
public class ProcessPuzzleBackendApplication {

    static void main(String[] args) {
        SpringApplication.run(ProcessPuzzleBackendApplication.class, args);
    }

}
