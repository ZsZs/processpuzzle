package com.processpuzzle.calculation.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.calculation.adapter.outbound.CamundaFeelExpressionEngine;
import com.processpuzzle.calculation.domain.CalculationDefinition;
import com.processpuzzle.calculation.domain.CalculationDefinitionRepository;
import com.processpuzzle.calculation.port.EntityCatalogPort;
import com.processpuzzle.calculation.usecase.CalculationValidator;
import com.processpuzzle.calculation.usecase.CreateCalculationDefinition;
import com.processpuzzle.calculation.usecase.ReplaceCalculationDefinition;
import com.processpuzzle.calculation.usecase.assist.Shop;
import com.processpuzzle.calculation.usecase.assist.SourceChecker;
import com.processpuzzle.calculation.usecase.exception.CalculationAlreadyExistsException;
import com.processpuzzle.core.definition.AuthoringIssue;
import com.processpuzzle.core.definition.Channel;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

class CalculationAuthoringParticipantTest {

    private final CalculationDefinitionRepository repository = mock(CalculationDefinitionRepository.class);
    private final CreateCalculationDefinition create = mock(CreateCalculationDefinition.class);
    private final ReplaceCalculationDefinition replace = mock(ReplaceCalculationDefinition.class);
    private final CalculationAuthoringParticipant participant = new CalculationAuthoringParticipant(repository, create, replace,
        new SourceChecker(new CalculationValidator(new CamundaFeelExpressionEngine())), provider(EntityCatalogPort.class, Shop.CATALOG));

    private static String json(String source) {
        try {
            return new ObjectMapper().writeValueAsString(Map.of("source", source));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static CalculationDefinition stored(String source) {
        CalculationDefinition definition = CalculationDefinition.builder().orgKey("acme").id("order-total").channel(Channel.DRAFT).build();
        definition.applySource(source, new CalculationValidator(new CamundaFeelExpressionEngine()).validate(source).model());
        return definition;
    }

    @Test
    void theKindIsCalculationAndTheSchemaIsTheContractsInput() {
        assertThat(participant.kind()).isEqualTo("calculation");
        assertThat(participant.schema()).contains("source", "version");
    }

    @Test
    void theDraftsAreListedByIdAndAStoredOneIsReadAsItsSource() {
        when(repository.findByOrgKeyAndChannelOrderByIdAsc("acme", Channel.DRAFT)).thenReturn(List.of(stored(Shop.ORDER_TOTAL)));
        when(repository.findByOrgKeyAndIdAndChannel("acme", "order-total", Channel.DRAFT)).thenReturn(Optional.of(stored(Shop.ORDER_TOTAL)));

        assertThat(participant.list("acme")).containsExactly("order-total");
        assertThat(participant.get("acme", "order-total")).get().asString().contains("\"source\"", "calculation: order-total");
        assertThat(participant.get("acme", "missing")).isEmpty();
    }

    @Test
    void aValidSourceIsCreatedWhenThereIsNoDraft() {
        assertThat(participant.saveDraft("acme", "order-total", json(Shop.ORDER_TOTAL))).isEmpty();

        verify(create).create("acme", Shop.ORDER_TOTAL);
        verify(replace, never()).replace(anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void aValidSourceReplacesTheDraftThatExists() {
        when(repository.existsByOrgKeyAndIdAndChannel("acme", "order-total", Channel.DRAFT)).thenReturn(true);

        assertThat(participant.saveDraft("acme", "order-total", json(Shop.ORDER_TOTAL))).isEmpty();

        verify(replace).replace("acme", "order-total", Shop.ORDER_TOTAL, null);
        verify(create, never()).create(anyString(), anyString());
    }

    @Test
    void anEntityReferenceErrorIsRefusedAndNothingIsWritten() {
        String bad = Shop.ORDER_TOTAL.replace("mapping: { total: total }", "mapping: { totl: total }");

        List<AuthoringIssue> issues = participant.saveDraft("acme", "order-total", json(bad));

        assertThat(issues).singleElement().satisfies(issue -> assertThat(issue.path()).isEqualTo("output.mapping.totl"));
        verify(create, never()).create(anyString(), anyString());
    }

    @Test
    void aSourceTheValidatorRefusesComesBackAsIssues() {
        String broken = Shop.ORDER_TOTAL.replace("sum: lineTotal", "sum: lineTotal, avg: lineTotal");
        assertThat(participant.saveDraft("acme", "order-total", json(broken))).isNotEmpty();
        verify(create, never()).create(anyString(), anyString());
    }

    @Test
    void theIdOfTheSourceHasToBeTheKey() {
        assertThatThrownBy(() -> participant.saveDraft("acme", "other", json(Shop.ORDER_TOTAL)))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("other");
    }

    @Test
    void aUseCaseRefusalComesBackAsAnIssue() {
        when(create.create(anyString(), anyString())).thenThrow(new CalculationAlreadyExistsException("acme", "order-total"));

        assertThat(participant.saveDraft("acme", "order-total", json(Shop.ORDER_TOTAL))).singleElement()
            .satisfies(issue -> assertThat(issue.severity()).isEqualTo(AuthoringIssue.Severity.ERROR));
    }

    @Test
    void notJsonIsAnIssue() {
        assertThat(participant.saveDraft("acme", "order-total", "{nope")).singleElement()
            .satisfies(issue -> assertThat(issue.text()).contains("not valid JSON"));
    }

    @Test
    void validateChecksTheStoredDraftAgainstTheEntities() {
        when(repository.findByOrgKeyAndIdAndChannel("acme", "order-total", Channel.DRAFT))
            .thenReturn(Optional.of(stored(Shop.ORDER_TOTAL.replace("path: lineItems", "path: lines"))));

        assertThat(participant.validate("acme", "order-total")).anySatisfy(issue -> assertThat(issue.path()).isEqualTo("inputs.lines.path"));
        assertThat(participant.validate("acme", "missing")).isEmpty();
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T bean) {
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        factory.addBean(type.getSimpleName(), bean);
        return factory.getBeanProvider(type);
    }
}
