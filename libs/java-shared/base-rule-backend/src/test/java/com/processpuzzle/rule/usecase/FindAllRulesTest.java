package com.processpuzzle.rule.usecase;

import com.processpuzzle.rule.domain.RuleDefinition;
import com.processpuzzle.rule.domain.RuleDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FindAllRulesTest {

    private RuleDefinitionRepository repository;
    private FindAllRules findAllRules;

    @BeforeEach
    void setUp() {
        repository = mock(RuleDefinitionRepository.class);
        findAllRules = new FindAllRules(repository);
    }

    @Test
    void allParamsNull_callsRepositoryWithNullSpecAndUnsorted() {
        when(repository.findAll((Specification<RuleDefinition>) any(), any(Sort.class))).thenReturn(List.of());

        findAllRules.execute(null, null, null);

        ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
        verify(repository).findAll((Specification<RuleDefinition>) isNull(), sortCaptor.capture());
        assertThat(sortCaptor.getValue().isSorted()).isFalse();
    }

    @Test
    void contextOnly_passesSpecAndUnsorted() {
        when(repository.findAll((Specification<RuleDefinition>) any(), any(Sort.class))).thenReturn(List.of());

        findAllRules.execute("Order", null, null);

        ArgumentCaptor<Specification<RuleDefinition>> specCaptor = specCaptor();
        ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
        verify(repository).findAll(specCaptor.capture(), sortCaptor.capture());
        assertThat(specCaptor.getValue()).isNotNull();
        assertThat(sortCaptor.getValue().isSorted()).isFalse();
    }

    @Test
    void order_isParsedAndPassed() {
        when(repository.findAll((Specification<RuleDefinition>) any(), any(Sort.class))).thenReturn(List.of());

        findAllRules.execute(null, null, "name,desc");

        ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
        verify(repository).findAll((Specification<RuleDefinition>) any(), sortCaptor.capture());
        assertThat(sortCaptor.getValue()).containsExactly(Sort.Order.desc("name"));
    }

    @Test
    void whereAndContext_areCombinedIntoSingleSpec() {
        when(repository.findAll((Specification<RuleDefinition>) any(), any(Sort.class))).thenReturn(List.of());

        findAllRules.execute("Order", "enabled==true", null);

        ArgumentCaptor<Specification<RuleDefinition>> specCaptor = specCaptor();
        verify(repository).findAll(specCaptor.capture(), any(Sort.class));
        assertThat(specCaptor.getValue()).isNotNull();
    }

    @Test
    void invalidWhere_bubblesAsIllegalArgument() {
        try {
            findAllRules.execute(null, "context==", null);
        } catch (IllegalArgumentException expected) {
            assertThat(expected).hasMessageStartingWith("Invalid RSQL");
            verifyNoInteractions(repository);
            return;
        }
        throw new AssertionError("expected IllegalArgumentException");
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Specification<RuleDefinition>> specCaptor() {
        return ArgumentCaptor.forClass(Specification.class);
    }
}
