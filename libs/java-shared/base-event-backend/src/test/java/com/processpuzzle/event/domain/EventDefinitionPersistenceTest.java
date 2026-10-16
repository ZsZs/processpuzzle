package com.processpuzzle.event.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.shared.event.PlatformEventAction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;

/** The mapping binds, and the derived finders the use cases and the catalog listener rely on are scoped. */
@DataJpaTest(showSql = false)
// PostgreSQL mode, as the other JPA tests here: the parameters column is jsonb.
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:event-persistence;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@EntityScan("com.processpuzzle.event.domain")
@EnableJpaRepositories("com.processpuzzle.event.domain")
class EventDefinitionPersistenceTest {

    @Configuration
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestConfig {
    }

    @Autowired
    private EventDefinitionRepository repository;

    @Test
    void roundTripsAndFindsCandidatesPerOrganization() {
        repository.saveAndFlush(EventDefinitionTest.system("OrderCreatedEvent", PlatformEventAction.CREATED, null));
        EventDefinition confirmed = EventDefinitionTest.system("OrderConfirmedEvent", PlatformEventAction.STATE_CHANGED, "CONFIRMED");
        confirmed.setParameters(java.util.List.of(EventParameter.builder().name("orderNumber")
                .type(com.processpuzzle.shared.event.EventParameterType.STRING).required(true).build()));
        repository.saveAndFlush(confirmed);
        EventDefinition elsewhere = EventDefinitionTest.system("OrderCreatedEvent", PlatformEventAction.CREATED, null);
        elsewhere.setOrgKey("org-2");
        repository.saveAndFlush(elsewhere);

        EventDefinition reloaded = repository.findByOrgKeyAndIdAndChannel("org-1", "OrderConfirmedEvent", Channel.DRAFT).orElseThrow();
        assertThat(reloaded.getState()).isEqualTo("CONFIRMED");
        assertThat(reloaded.getVersion()).isZero();
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getParameters()).singleElement().satisfies(parameter -> {
            assertThat(parameter.getName()).isEqualTo("orderNumber");
            assertThat(parameter.isRequired()).isTrue();
        });

        assertThat(repository.findByOrgKeyAndChannelAndKindAndSubjectTypeAndAction("org-1", Channel.DRAFT, EventKind.SYSTEM, "order", PlatformEventAction.CREATED))
                .extracting(EventDefinition::getId).containsExactly("OrderCreatedEvent");
        assertThat(repository.findByOrgKeyAndChannelOrderByIdAsc("org-1", Channel.DRAFT))
                .extracting(EventDefinition::getId).containsExactly("OrderConfirmedEvent", "OrderCreatedEvent");
        assertThat(repository.existsByOrgKeyAndIdAndChannel("org-2", "OrderConfirmedEvent", Channel.DRAFT)).isFalse();
    }
}
