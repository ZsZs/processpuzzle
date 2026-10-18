package com.processpuzzle.baseentity.instances.adapters.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import com.processpuzzle.baseentity.common.JpaAuditingConfig;
import com.processpuzzle.baseentity.common.SecurityAuditorAware;
import com.processpuzzle.baseentity.instances.domain.EntityObject;
import com.processpuzzle.baseentity.instances.domain.EntityObjectRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
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

/** The version-keeping payload write binds its JSON parameter, lands, and refuses a moved object. */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:entity-keeping-version;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@EntityScan("com.processpuzzle.baseentity.instances.domain")
@EnableJpaRepositories("com.processpuzzle.baseentity.instances.domain")
@Import(JpaAuditingConfig.class)
class EntityObjectKeepingVersionPersistenceTest {

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

    @Test
    void replacesThePayloadAndKeepsTheVersion() {
        EntityObject order = repository.saveAndFlush(EntityObject.builder().orgKey("acme").entityDefinitionCode("order")
                .payload(new LinkedHashMap<>(Map.of("orderNumber", "ORD-1", "lineItems", List.of(Map.of("quantity", 2))))).build());
        long version = order.getVersion();

        Map<String, Object> payload = new LinkedHashMap<>(order.getPayload());
        payload.put("total", new BigDecimal("89.88"));
        assertThat(repository.replacePayloadKeepingVersion(order.getId(), "acme", version, payload)).isEqualTo(1);
        assertThat(repository.replacePayloadKeepingVersion(order.getId(), "acme", version + 1, payload)).isZero();
        assertThat(repository.replacePayloadKeepingVersion(order.getId(), "other", version, payload)).isZero();

        EntityObject reloaded = repository.findById(order.getId()).orElseThrow();
        assertThat(reloaded.getVersion()).isEqualTo(version);
        assertThat(reloaded.getPayload()).containsEntry("orderNumber", "ORD-1");
        assertThat(new BigDecimal(reloaded.getPayload().get("total").toString())).isEqualByComparingTo("89.88");
        assertThat(reloaded.getPayload().get("lineItems")).isInstanceOf(List.class);
    }
}
