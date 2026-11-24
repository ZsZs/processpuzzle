package com.processpuzzle.baseentity.instances.usecases.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.baseentity.instances.domain.EntityObject;
import com.processpuzzle.baseentity.instances.domain.EntityObjectScope;
import com.processpuzzle.baseentity.instances.domain.RecordPlanner.RecordInput;
import com.processpuzzle.baseentity.instances.usecases.inbound.LoadRecordsUseCase.Action;
import com.processpuzzle.baseentity.instances.usecases.inbound.LoadRecordsUseCase.Report;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionLookupPort;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionView;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class LoadRecordsUseCaseTest {

    private static final String ORG = "org";

    @Mock
    private EntityObjectScope scope;
    @Mock
    private EntityDefinitionLookupPort definitions;
    @Mock
    private CreateEntityInstanceUseCase create;
    @Mock
    private PlatformTransactionManager transactionManager;

    private LoadRecordsUseCase useCase;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        useCase = new LoadRecordsUseCase(scope, definitions, create, transactionManager);
        lenient().when(definitions.findByCode(ORG, "boat-class")).thenReturn(java.util.Optional.of(boatClass()));
        lenient().when(definitions.findByCode(ORG, "boat")).thenReturn(java.util.Optional.of(definition("boat", List.of(List.of("sailNumber")))));
    }

    @Test
    void creates_the_records_and_resolves_a_reference_to_the_one_created_before_it() {
        UUID classId = UUID.randomUUID();
        when(scope.findAll(ORG, "boat-class")).thenReturn(List.of());
        when(scope.findAll(ORG, "boat")).thenReturn(List.of());
        when(create.create(eq(ORG), eq("boat-class"), any())).thenReturn(stored(classId, "boat-class", Map.of("name", "470")));
        when(create.create(eq(ORG), eq("boat"), any())).thenReturn(stored(UUID.randomUUID(), "boat", Map.of("sailNumber", "G 1")));

        Report report = useCase.load(ORG, List.of(
                new RecordInput("boat", Map.of("sailNumber", "G 1", "boatClass", byName("470"))),
                new RecordInput("boat-class", Map.of("name", "470"))), false);

        assertThat(report.count(Action.CREATED)).isEqualTo(2);
        verify(create).create(eq(ORG), eq("boat"), eq(Map.of("sailNumber", "G 1", "boatClass", classId.toString())));
    }

    @Test
    void a_record_whose_natural_key_is_stored_already_is_left_alone() {
        when(scope.findAll(ORG, "boat-class")).thenReturn(List.of(stored(UUID.randomUUID(), "boat-class", Map.of("name", "470"))));

        Report report = useCase.load(ORG, List.of(new RecordInput("boat-class", Map.of("name", "470"))), false);

        assertThat(report.count(Action.ALREADY_PRESENT)).isEqualTo(1);
        verify(create, never()).create(anyString(), anyString(), any());
    }

    @Test
    void a_record_of_an_unknown_definition_is_refused_and_the_others_still_load() {
        when(scope.findAll(ORG, "boat-class")).thenReturn(List.of());
        when(create.create(eq(ORG), eq("boat-class"), any())).thenReturn(stored(UUID.randomUUID(), "boat-class", Map.of("name", "Star")));

        Report report = useCase.load(ORG, List.of(
                new RecordInput("unknown", Map.of("name", "x")),
                new RecordInput("boat-class", Map.of("name", "Star"))), false);

        assertThat(report.items().get(0).action()).isEqualTo(Action.REJECTED);
        assertThat(report.items().get(0).detail()).contains("no entity definition");
        assertThat(report.items().get(1).action()).isEqualTo(Action.CREATED);
    }

    @Test
    void a_record_that_refers_to_nothing_stored_is_refused_with_its_dependents() {
        when(scope.findAll(ORG, "boat-class")).thenReturn(List.of());

        Report report = useCase.load(ORG, List.of(
                new RecordInput("boat", Map.of("sailNumber", "G 2", "boatClass", byName("Nonexistent")))), false);

        assertThat(report.count(Action.REJECTED)).isEqualTo(1);
        assertThat(report.items().get(0).detail()).contains("does not exist");
        verify(create, never()).create(anyString(), anyString(), any());
    }

    @Test
    void a_dry_run_reports_what_it_would_do_and_rolls_the_load_back() {
        when(scope.findAll(ORG, "boat-class")).thenReturn(List.of());
        when(create.create(eq(ORG), eq("boat-class"), any())).thenReturn(stored(UUID.randomUUID(), "boat-class", Map.of("name", "Finn")));

        Report report = useCase.load(ORG, List.of(new RecordInput("boat-class", Map.of("name", "Finn"))), true);

        assertThat(report.dryRun()).isTrue();
        assertThat(report.count(Action.CREATED)).isEqualTo(1);
        verify(transactionManager).commit(any());
    }

    private static Map<String, Object> byName(String name) {
        return Map.of(com.processpuzzle.baseentity.instances.domain.RecordPlanner.REF,
                Map.of("entity", "boat-class", "key", Map.of("name", name)));
    }

    private static EntityDefinitionView boatClass() {
        return definition("boat-class", List.of(List.of("name")));
    }

    private static EntityDefinitionView definition(String code, List<List<String>> identifiers) {
        return new EntityDefinitionView(code, false, List.of(), null, code, identifiers);
    }

    private static EntityObject stored(UUID id, String code, Map<String, Object> payload) {
        return EntityObject.builder().id(id).entityDefinitionCode(code).payload(payload).build();
    }

}
