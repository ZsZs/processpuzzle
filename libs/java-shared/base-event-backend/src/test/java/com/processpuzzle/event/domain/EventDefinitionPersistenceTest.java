package com.processpuzzle.event.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.shared.event.PlatformEventAction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** The mapping binds, and the derived finders the use cases and the catalog listener rely on are scoped. */
@DataJpaTest(showSql = false)
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
        repository.saveAndFlush(EventDefinitionTest.system("OrderConfirmedEvent", PlatformEventAction.STATE_CHANGED, "CONFIRMED"));
        EventDefinition elsewhere = EventDefinitionTest.system("OrderCreatedEvent", PlatformEventAction.CREATED, null);
        elsewhere.setOrgKey("org-2");
        repository.saveAndFlush(elsewhere);

        EventDefinition reloaded = repository.findByOrgKeyAndIdAndChannel("org-1", "OrderConfirmedEvent", Channel.DRAFT).orElseThrow();
        assertThat(reloaded.getState()).isEqualTo("CONFIRMED");
        assertThat(reloaded.getVersion()).isZero();
        assertThat(reloaded.getCreatedAt()).isNotNull();

        assertThat(repository.findByOrgKeyAndChannelAndKindAndSubjectTypeAndAction("org-1", Channel.DRAFT, EventKind.SYSTEM, "order", PlatformEventAction.CREATED))
                .extracting(EventDefinition::getId).containsExactly("OrderCreatedEvent");
        assertThat(repository.findByOrgKeyAndChannelOrderByIdAsc("org-1", Channel.DRAFT))
                .extracting(EventDefinition::getId).containsExactly("OrderConfirmedEvent", "OrderCreatedEvent");
        assertThat(repository.existsByOrgKeyAndIdAndChannel("org-2", "OrderConfirmedEvent", Channel.DRAFT)).isFalse();
    }
}
