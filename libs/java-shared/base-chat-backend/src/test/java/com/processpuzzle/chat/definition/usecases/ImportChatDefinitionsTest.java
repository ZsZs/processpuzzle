package com.processpuzzle.chat.definition.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.chat.definition.domain.ChatDefinition;
import com.processpuzzle.chat.definition.domain.ChatDefinitionRepository;
import com.processpuzzle.chat.definition.domain.ChatLifecycle;
import com.processpuzzle.chat.definition.domain.Multiplicity;
import com.processpuzzle.chat.definition.usecases.inbound.ImportChatDefinitions;
import com.processpuzzle.chat.definition.usecases.inbound.ImportOutcome;
import com.processpuzzle.core.definition.Channel;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ImportChatDefinitionsTest {

    private static final String TWO = """
            chat-definitions:
              - key: order-discussion
                name: Order discussion
                subjectType: ENTITY
                subjectDefinition: order
              - key: race-protests
                name: Protests
                subjectType: ENTITY
                subjectDefinition: race
                multiplicity: MANY
                lifecycle: STATE_BOUND
                writableStates: [Running, Protest]
                readRoles: [sailor]
                createRoles: [judge]
                includeSubjectOwner: true
                retentionDays: 90
            """;

    private ChatDefinitionRepository repository;
    private ImportChatDefinitions importer;

    @BeforeEach
    void setUp() {
        repository = mock(ChatDefinitionRepository.class);
        when(repository.findByOrgKeyAndDefinitionKeyAndChannel(any(), any(), any())).thenReturn(Optional.empty());
        importer = new ImportChatDefinitions(repository);
    }

    private ImportOutcome run(String yaml, boolean release) throws IOException {
        return importer.execute("org-1", new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), release);
    }

    @Test
    void createsTheDraftsWithDefaultsAndTheListedFields() throws IOException {
        ImportOutcome outcome = run(TWO, false);

        assertThat(outcome.created()).isEqualTo(2);
        assertThat(outcome.updated()).isZero();
        assertThat(outcome.written()).containsExactly("order-discussion", "race-protests");
        ArgumentCaptor<ChatDefinition> saved = ArgumentCaptor.forClass(ChatDefinition.class);
        verify(repository, times(2)).save(saved.capture());
        ChatDefinition order = saved.getAllValues().get(0);
        assertThat(order.getChannel()).isEqualTo(Channel.DRAFT);
        assertThat(order.getMultiplicity()).isEqualTo(Multiplicity.SINGLE);
        assertThat(order.getLifecycle()).isEqualTo(ChatLifecycle.ON_DEMAND);
        ChatDefinition protests = saved.getAllValues().get(1);
        assertThat(protests.getLifecycle()).isEqualTo(ChatLifecycle.STATE_BOUND);
        assertThat(protests.getWritableStates()).containsExactly("Running", "Protest");
        assertThat(protests.getReadRoles()).containsExactly("sailor");
        assertThat(protests.getCreateRoles()).containsExactly("judge");
        assertThat(protests.isIncludeSubjectOwner()).isTrue();
        assertThat(protests.getRetentionDays()).isEqualTo(90);
    }

    @Test
    void alsoWritesTheReleasedRowsWhenAskedTo() throws IOException {
        run(TWO, true);

        ArgumentCaptor<ChatDefinition> saved = ArgumentCaptor.forClass(ChatDefinition.class);
        verify(repository, times(4)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(ChatDefinition::getChannel)
                .containsExactly(Channel.DRAFT, Channel.RELEASED, Channel.DRAFT, Channel.RELEASED);
    }

    @Test
    void replacesAnExistingRowInsteadOfRejectingIt() throws IOException {
        ChatDefinition existing = ChatDefinition.builder().orgKey("org-1").definitionKey("order-discussion").name("Old").build();
        when(repository.findByOrgKeyAndDefinitionKeyAndChannel("org-1", "order-discussion", Channel.DRAFT)).thenReturn(Optional.of(existing));

        ImportOutcome outcome = run(TWO, false);

        assertThat(outcome.created()).isEqualTo(1);
        assertThat(outcome.updated()).isEqualTo(1);
        assertThat(existing.getName()).isEqualTo("Order discussion");
        assertThat(existing.getSubjectDefinition()).isEqualTo("order");
    }

    @Test
    void isAllOrNothingAndReportsEveryProblem() throws IOException {
        ImportOutcome outcome = run("""
                chat-definitions:
                  - key: ok-one
                    name: Fine
                    subjectType: ENTITY
                    subjectDefinition: order
                  - key: Bad Key
                    name: Broken
                    subjectType: ENTITY
                    subjectDefinition: order
                  - key: ok-one
                    name: Twice
                    subjectType: ENTITY
                    subjectDefinition: order
                """, true);

        assertThat(outcome.errors()).hasSize(2)
                .anyMatch(error -> error.contains("'Bad Key'"))
                .anyMatch(error -> error.contains("Duplicate key"));
        assertThat(outcome.written()).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test
    void anEmptyFileOrOneWithoutTheListImportsNothing() throws IOException {
        assertThat(run("", true).written()).isEmpty();
        assertThat(run("other: 1\n", true).written()).isEmpty();
        assertThat(run("chat-definitions: []\n", true)).isEqualTo(new ImportOutcome(0, 0, List.of(), List.of()));
        verify(repository, never()).save(any());
    }
}
