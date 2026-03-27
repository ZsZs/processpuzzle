package com.processpuzzle.backend;

import com.processpuzzle.objectstore.adapters.outbound.MinioConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication(scanBasePackages = "com.processpuzzle")
@Import({MinioConfig.class})
public class ProcessPuzzleBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProcessPuzzleBackendApplication.class, args);
    }

}
