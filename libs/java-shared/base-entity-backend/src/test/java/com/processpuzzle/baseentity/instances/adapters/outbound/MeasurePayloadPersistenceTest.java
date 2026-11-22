package com.processpuzzle.baseentity.instances.adapters.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import com.processpuzzle.baseentity.common.JpaAuditingConfig;
import com.processpuzzle.baseentity.common.SecurityAuditorAware;
import com.processpuzzle.baseentity.instances.domain.EntityObject;
import com.processpuzzle.baseentity.instances.domain.EntityObjectRepository;
import com.processpuzzle.baseentity.instances.domain.MeasureValues;
import com.processpuzzle.baseentity.instances.usecases.outbound.MeasureView;
import jakarta.persistence.EntityManager;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;

/**
 * A measure is stored with the numbers a query compares. They are doubles because the payload is JSON and a stored
 * decimal reads back as a Double: a number of any other type would never equal its own stored copy, and every save of
 * an unchanged object would rewrite it and bump its version.
 */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:measure-payload;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@EntityScan("com.processpuzzle.baseentity.instances.domain")
@EnableJpaRepositories("com.processpuzzle.baseentity.instances.domain")
@Import(JpaAuditingConfig.class)
class MeasurePayloadPersistenceTest {

    @Configuration
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestConfig {

        @Bean
        SecurityAuditorAware securityAuditorAware() {
            return new SecurityAuditorAware();
        }
    }

    @Autowired
    private EntityObjectRepository repository;

    @Autowired
    private EntityManager entityManager;

    private static Map<String, Object> measures() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("weight", MeasureValues.normalized(new MeasureView(MeasureView.Kind.QUANTITY, "MASS", null, null, null, null),
                Map.of("amount", "250.5", "unit", "g")));
        payload.put("whole", MeasureValues.normalized(new MeasureView(MeasureView.Kind.QUANTITY, "MASS", null, null, null, null),
                Map.of("amount", "5", "unit", "kg")));
        payload.put("price", MeasureValues.normalized(new MeasureView(MeasureView.Kind.MONEY, null, null, null, null, null),
                Map.of("amount", "19.90", "currency", "EUR")));
        payload.put("size", MeasureValues.normalized(new MeasureView(MeasureView.Kind.DIMENSIONS, null, null, null, null, 3),
                Map.of("width", "120", "height", "80", "depth", "50", "unit", "cm")));
        return payload;
    }

    @Test
    void aStoredMeasureReadsBackEqual_soAnUnchangedSaveDoesNotBumpTheVersion() {
        Map<String, Object> payload = measures();
        EntityObject saved = repository.saveAndFlush(EntityObject.builder().orgKey("acme").entityDefinitionCode("item").payload(payload).build());
        entityManager.clear();

        EntityObject reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getPayload()).isEqualTo(payload);
        long version = reloaded.getVersion();

        reloaded.setPayload(measures());
        EntityObject unchanged = repository.saveAndFlush(reloaded);
        assertThat(unchanged.getVersion()).as("an identical payload is not a change").isEqualTo(version);

        Map<String, Object> changed = measures();
        changed.put("weight", MeasureValues.normalized(new MeasureView(MeasureView.Kind.QUANTITY, "MASS", null, null, null, null),
                Map.of("amount", "251", "unit", "g")));
        reloaded.setPayload(changed);
        assertThat(repository.saveAndFlush(reloaded).getVersion()).isGreaterThan(version);
    }
}
