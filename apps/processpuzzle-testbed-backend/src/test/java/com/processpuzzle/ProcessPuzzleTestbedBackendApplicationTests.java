package com.processpuzzle;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProcessPuzzleTestbedBackendApplicationTests {

    private final ApplicationContext applicationContext;

    @Autowired
    ProcessPuzzleTestbedBackendApplicationTests(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Test
    void contextLoads() {
        assertThat(applicationContext).isNotNull();
    }

}
