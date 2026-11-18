package com.processpuzzle.chat.definition.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.processpuzzle.core.definition.Channel;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;

/** The mapping binds, the lists survive as JSON, and the derived finders are scoped by organization and channel. */
@DataJpaTest(showSql = false)
// PostgreSQL mode, as the other JPA tests here: the role and state lists are jsonb.
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:chat-definition-persistence;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@EntityScan("com.processpuzzle.chat.definition.domain")
@EnableJpaRepositories("com.processpuzzle.chat.definition.domain")
class ChatDefinitionPersistenceTest {

    @Configuration
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestConfig {
    }

    @Autowired
    private ChatDefinitionRepository repository;

    @Test
    void roundTripsEveryFieldIncludingTheListsAndGetsVersionAndTimestamps() {
        ChatDefinition definition = ChatDefinitionTest.definition("race-protests");
        definition.setDescription("Protests of a race");
        definition.setMultiplicity(Multiplicity.MANY);
        definition.setLifecycle(ChatLifecycle.STATE_BOUND);
        definition.setWritableStates(new ArrayList<>(List.of("Running", "Protest")));
        definition.setReadRoles(new ArrayList<>(List.of("sailor", "judge")));
        definition.setWriteRoles(new ArrayList<>(List.of("sailor")));
        definition.setCreateRoles(new ArrayList<>(List.of("judge")));
        definition.setIncludeSubjectOwner(true);
        definition.setRetentionDays(90);
        repository.saveAndFlush(definition);

        ChatDefinition reloaded = repository.findByOrgKeyAndDefinitionKeyAndChannel("org-1", "race-protests", Channel.DRAFT).orElseThrow();

        assertThat(reloaded.getDescription()).isEqualTo("Protests of a race");
        assertThat(reloaded.getSubjectType()).isEqualTo(SubjectType.ENTITY);
        assertThat(reloaded.getSubjectDefinition()).isEqualTo("order");
        assertThat(reloaded.getMultiplicity()).isEqualTo(Multiplicity.MANY);
        assertThat(reloaded.getLifecycle()).isEqualTo(ChatLifecycle.STATE_BOUND);
        assertThat(reloaded.getWritableStates()).containsExactly("Running", "Protest");
        assertThat(reloaded.getReadRoles()).containsExactly("sailor", "judge");
        assertThat(reloaded.getWriteRoles()).containsExactly("sailor");
        assertThat(reloaded.getCreateRoles()).containsExactly("judge");
        assertThat(reloaded.isIncludeSubjectOwner()).isTrue();
        assertThat(reloaded.getRetentionDays()).isEqualTo(90);
        assertThat(reloaded.getVersion()).isZero();
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void aDraftAndItsReleasedCopyAreTwoRowsOfOneKey() {
        ChatDefinition draft = repository.saveAndFlush(ChatDefinitionTest.definition("order-discussion"));
        repository.saveAndFlush(ChatDefinition.releasedCopyOf(draft));

        assertThat(repository.findByOrgKeyAndDefinitionKeyAndChannel("org-1", "order-discussion", Channel.DRAFT)).isPresent();
        assertThat(repository.findByOrgKeyAndDefinitionKeyAndChannel("org-1", "order-discussion", Channel.RELEASED)).isPresent();
        assertThat(repository.findByOrgKeyAndChannelOrderByDefinitionKeyAsc("org-1", Channel.RELEASED)).hasSize(1);
    }

    @Test
    void findersAreScopedByOrganizationAndOrderedByKey() {
        repository.saveAndFlush(ChatDefinitionTest.definition("b-chat"));
        repository.saveAndFlush(ChatDefinitionTest.definition("a-chat"));
        ChatDefinition elsewhere = ChatDefinitionTest.definition("a-chat");
        elsewhere.setOrgKey("org-2");
        repository.saveAndFlush(elsewhere);

        assertThat(repository.findByOrgKeyAndChannelOrderByDefinitionKeyAsc("org-1", Channel.DRAFT))
                .extracting(ChatDefinition::getDefinitionKey).containsExactly("a-chat", "b-chat");
        assertThat(repository.findByOrgKeyAndDefinitionKeyAndChannel("org-2", "b-chat", Channel.DRAFT)).isEmpty();
    }

    @Test
    void erasesOneTenantsDefinitionsInOneStatement() {
        repository.saveAndFlush(ChatDefinitionTest.definition("a-chat"));
        ChatDefinition elsewhere = ChatDefinitionTest.definition("a-chat");
        elsewhere.setOrgKey("org-2");
        repository.saveAndFlush(elsewhere);

        assertThat(repository.eraseByOrgKey("org-1")).isEqualTo(1);

        assertThat(repository.findByOrgKeyAndChannelOrderByDefinitionKeyAsc("org-1", Channel.DRAFT)).isEmpty();
        assertThat(repository.findByOrgKeyAndChannelOrderByDefinitionKeyAsc("org-2", Channel.DRAFT)).hasSize(1);
    }
}
