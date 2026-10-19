package com.processpuzzle.calculation.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.processpuzzle.calculation.adapter.outbound.CamundaFeelExpressionEngine;
import com.processpuzzle.calculation.domain.CalculationDefinition;
import com.processpuzzle.calculation.domain.CalculationDefinitionRepository;
import com.processpuzzle.calculation.domain.definition.CalculationParser;
import com.processpuzzle.calculation.engine.Calculations;
import com.processpuzzle.calculation.usecase.CalculationValidator;
import com.processpuzzle.calculation.usecase.CalculationsReleased;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.definition.PendingChange;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class CalculationReleaseParticipantTest {

    private CalculationDefinitionRepository repository;
    private ApplicationEventPublisher publisher;
    private CalculationReleaseParticipant participant;

    @BeforeEach
    void setUp() {
        repository = mock(CalculationDefinitionRepository.class);
        publisher = mock(ApplicationEventPublisher.class);
        participant = new CalculationReleaseParticipant(repository, new CalculationValidator(new CamundaFeelExpressionEngine()),
                publisher);
    }

    private static CalculationDefinition definition(String id, String source, Channel channel) {
        CalculationDefinition definition = CalculationDefinition.builder().orgKey("org").id(id).channel(channel).build();
        definition.applySource(source, CalculationParser.parse(source).model());
        return definition;
    }

    @Test
    void releasesNewAndChangedDraftsAndRemovesDeletedOnes() {
        CalculationDefinition draft = definition("order-total", Calculations.source("order-total"), Channel.DRAFT);
        CalculationDefinition gone = definition("old", "calculation: old\ndecisions: { x: { expr: \"1\" } }\n", Channel.RELEASED);
        when(repository.findByOrgKeyAndChannelOrderByIdAsc("org", Channel.DRAFT)).thenReturn(List.of(draft));
        when(repository.findByOrgKeyAndChannelOrderByIdAsc("org", Channel.RELEASED)).thenReturn(List.of(gone));

        assertThat(participant.kind()).isEqualTo(DefinitionKinds.CALCULATION);
        assertThat(participant.order()).isEqualTo(DefinitionKinds.CALCULATION_ORDER);
        assertThat(participant.drafts("org")).containsExactly("order-total");
        assertThat(participant.pending("org")).extracting(PendingChange::key).containsExactly("old", "order-total");
        assertThat(participant.references("org")).extracting(reference -> reference.toKey()).containsExactly("order", "order-line");
        assertThat(participant.check("org", null)).isEmpty();

        participant.release("org");

        ArgumentCaptor<CalculationDefinition> saved = ArgumentCaptor.forClass(CalculationDefinition.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getChannel()).isEqualTo(Channel.RELEASED);
        assertThat(saved.getValue().getSource()).isEqualTo(draft.getSource());
        verify(repository).deleteAll(List.of(gone));
        verify(publisher).publishEvent(new CalculationsReleased("org", Set.of("order-total")));
    }

    @Test
    void updatesAReleasedRowInPlaceAndReportsAnInvalidDraft() {
        CalculationDefinition draft = definition("order-total", Calculations.source("order-total"), Channel.DRAFT);
        draft.setSource("calculation: order-total\n");
        CalculationDefinition released = definition("order-total", Calculations.source("order-total"), Channel.RELEASED);
        when(repository.findByOrgKeyAndChannelOrderByIdAsc("org", Channel.DRAFT)).thenReturn(List.of(draft));
        when(repository.findByOrgKeyAndChannelOrderByIdAsc("org", Channel.RELEASED)).thenReturn(List.of(released));

        assertThat(participant.check("org", null)).singleElement()
                .satisfies(problem -> assertThat(problem.message()).contains("at least one decision"));

        participant.release("org", List.of("order-total", "unknown"));
        verify(repository).save(released);
        assertThat(released.getSource()).isEqualTo("calculation: order-total\n");
        verify(repository, never()).deleteAll(any());
        verify(publisher).publishEvent(new CalculationsReleased("org", Set.of("order-total")));
    }

    @Test
    void announcesNothingWhenNothingWasReleased() {
        participant.release("org", List.of("unknown"));
        verifyNoInteractions(publisher);
    }
}
