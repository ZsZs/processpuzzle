package com.processpuzzle.starter.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@DataJpaTest(showSql = false)
@EntityScan("com.processpuzzle.starter.domain")
@EnableJpaRepositories("com.processpuzzle.starter.domain")
class DataSpaceRepositoryTest {

    @Configuration
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestConfig {
    }

    @Autowired
    private DataSpaceRepository dataSpaces;
    @Autowired
    private InstalledStarterRepository installed;

    @BeforeEach
    void setUp() {
        dataSpaces.save(new DataSpace("alpha-common", "alpha"));
        install("club", "alpha");
        install("club", "beta");
        install("other", "beta");
    }

    private void install(String orgKey, String starterId) {
        InstalledStarter starter = new InstalledStarter(orgKey, starterId);
        starter.recordInstall("1.0.0", Instant.now(), "test");
        installed.save(starter);
    }

    @Test
    void anOrganizationIsMemberOfTheCommonsOfTheStartersItInstalled() {
        assertThat(dataSpaces.findCommonKeysOf("club")).containsExactly("alpha-common");
    }

    @Test
    void anOrganizationWhoseStartersHaveNoCommonHasNone() {
        assertThat(dataSpaces.findCommonKeysOf("other")).isEmpty();
    }
}
