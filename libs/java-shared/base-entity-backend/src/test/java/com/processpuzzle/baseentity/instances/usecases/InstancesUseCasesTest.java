package com.processpuzzle.baseentity.instances.usecases;

import com.processpuzzle.baseentity.common.ConflictException;
import com.processpuzzle.baseentity.common.NotFoundException;
import com.processpuzzle.baseentity.instances.domain.EntityObject;
import com.processpuzzle.baseentity.instances.domain.EntityObjectRepository;
import com.processpuzzle.baseentity.instances.domain.event.EntityObjectCreatedEvent;
import com.processpuzzle.baseentity.instances.domain.event.EntityObjectDeletedEvent;
import com.processpuzzle.baseentity.instances.domain.event.EntityObjectUpdatedEvent;
import com.processpuzzle.baseentity.instances.usecases.inbound.CreateEntityInstanceUseCase;
import com.processpuzzle.baseentity.instances.usecases.inbound.DeleteEntityInstanceUseCase;
import com.processpuzzle.baseentity.instances.usecases.inbound.FindEntityInstanceByIdUseCase;
import com.processpuzzle.baseentity.instances.usecases.inbound.SearchEntityInstancesUseCase;
import com.processpuzzle.baseentity.instances.usecases.inbound.UpdateEntityInstanceUseCase;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionLookupPort;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionView;
import com.processpuzzle.baseentity.instances.usecases.outbound.PayloadValidatorPort;
import com.processpuzzle.baseentity.instances.usecases.outbound.RsqlToInstanceSpecificationPort;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstancesUseCasesTest {

    /** The tenant the request was addressed to. Never persisted; only carried into the events. */
    private static final String ORG = "processpuzzle-testbed";

    @Mock
    private EntityObjectRepository repository;

    @Mock
    private EntityDefinitionLookupPort definitionLookupPort;

    @Mock
    private PayloadValidatorPort payloadValidatorPort;

    @Mock
    private RsqlToInstanceSpecificationPort rsqlAdapter;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private CreateEntityInstanceUseCase createUseCase;
    private UpdateEntityInstanceUseCase updateUseCase;
    private DeleteEntityInstanceUseCase deleteUseCase;
    private FindEntityInstanceByIdUseCase findByIdUseCase;
    private SearchEntityInstancesUseCase searchUseCase;

    @BeforeEach
    void setUp() {
        createUseCase = new CreateEntityInstanceUseCase(repository, definitionLookupPort, payloadValidatorPort, eventPublisher);
        updateUseCase = new UpdateEntityInstanceUseCase(repository, definitionLookupPort, payloadValidatorPort, eventPublisher);
        deleteUseCase = new DeleteEntityInstanceUseCase(repository, eventPublisher);
        findByIdUseCase = new FindEntityInstanceByIdUseCase(repository);
        searchUseCase = new SearchEntityInstancesUseCase(repository, rsqlAdapter);
    }

    @Test
    void createEntityInstance_success() {
        EntityDefinitionView defView = new EntityDefinitionView("partner", false, List.of());
        when(definitionLookupPort.findByCode("partner")).thenReturn(Optional.of(defView));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> payload = Map.of("name", "ACME Corp");
        EntityObject result = createUseCase.create(ORG, "partner", payload);

        assertThat(result.getEntityDefinitionCode()).isEqualTo("partner");
        assertThat(result.getPayload()).isEqualTo(payload);
        verify(payloadValidatorPort).validate(defView, payload);
        verify(repository).saveAndFlush(any(EntityObject.class));
    }

    @Test
    void createEntityInstance_definitionNotFound_throwsNotFound() {
        when(definitionLookupPort.findByCode("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> createUseCase.create(ORG, "unknown", Map.of()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createEntityInstance_embeddedDefinition_throwsConflict() {
        EntityDefinitionView embeddedDef = new EntityDefinitionView("address", true, List.of());
        when(definitionLookupPort.findByCode("address")).thenReturn(Optional.of(embeddedDef));

        assertThatThrownBy(() -> createUseCase.create(ORG, "address", Map.of()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateEntityInstance_success() {
        UUID id = UUID.randomUUID();
        EntityObject existing = EntityObject.builder()
                .id(id)
                .entityDefinitionCode("partner")
                .version(1L)
                .payload(Map.of("name", "Old"))
                .build();
        EntityDefinitionView defView = new EntityDefinitionView("partner", false, List.of());

        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(definitionLookupPort.findByCode("partner")).thenReturn(Optional.of(defView));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> newPayload = Map.of("name", "New");
        EntityObject result = updateUseCase.update(ORG, id, 1L, newPayload);

        assertThat(result.getPayload()).isEqualTo(newPayload);
        verify(payloadValidatorPort).validate(defView, newPayload);
        verify(repository).saveAndFlush(existing);
    }

    @Test
    void updateEntityInstance_versionMismatch_throwsConflict() {
        UUID id = UUID.randomUUID();
        EntityObject existing = EntityObject.builder()
                .id(id)
                .entityDefinitionCode("partner")
                .version(2L)
                .build();

        when(repository.findById(id)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> updateUseCase.update(ORG, id, 1L, Map.of()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void deleteEntityInstance_success() {
        UUID id = UUID.randomUUID();
        EntityObject existing = EntityObject.builder().id(id).build();
        when(repository.findById(id)).thenReturn(Optional.of(existing));

        deleteUseCase.delete(ORG, id, false);

        verify(repository).delete(existing);
    }

    @Test
    void findEntityInstanceById_notFound_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> findByIdUseCase.findById(id))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void findEntityInstanceById_success() {
        UUID id = UUID.randomUUID();
        EntityObject entity = EntityObject.builder().id(id).entityDefinitionCode("partner").build();
        when(repository.findById(id)).thenReturn(Optional.of(entity));

        EntityObject result = findByIdUseCase.findById(id);

        assertThat(result).isSameAs(entity);
    }

    @Test
    void updateEntityInstance_notFound_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> updateUseCase.update(ORG, id, 1L, Map.of()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateEntityInstance_definitionNotFound_throwsNotFound() {
        UUID id = UUID.randomUUID();
        EntityObject existing = EntityObject.builder()
                .id(id)
                .entityDefinitionCode("partner")
                .version(1L)
                .build();
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(definitionLookupPort.findByCode("partner")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> updateUseCase.update(ORG, id, 1L, Map.of()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteEntityInstance_notFound_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deleteUseCase.delete(ORG, id, false))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteEntityInstance_hasReferencesAndCascadeFalse_throwsConflict() {
        UUID id = UUID.randomUUID();
        EntityObject existing = EntityObject.builder().id(id).build();
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.existsAnyReferenceTo(id.toString())).thenReturn(true);

        assertThatThrownBy(() -> deleteUseCase.delete(ORG, id, false))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("is still referenced by other entities — pass cascade=true to delete anyway");
    }

    @Test
    void deleteEntityInstance_hasReferencesAndCascadeTrue_deletes() {
        UUID id = UUID.randomUUID();
        EntityObject existing = EntityObject.builder().id(id).build();
        when(repository.findById(id)).thenReturn(Optional.of(existing));

        deleteUseCase.delete(ORG, id, true);

        verify(repository).delete(existing);
    }

    @Test
    void createEntityInstance_publishesCreatedEvent() {
        UUID id = UUID.randomUUID();
        EntityDefinitionView defView = new EntityDefinitionView("partner", false, List.of());
        when(definitionLookupPort.findByCode("partner")).thenReturn(Optional.of(defView));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> {
            EntityObject argument = invocation.getArgument(0);
            argument.setId(id);
            argument.setVersion(0L);
            return argument;
        });

        createUseCase.create(ORG, "partner", Map.of("name", "ACME Corp"));

        ArgumentCaptor<EntityObjectCreatedEvent> captor = ArgumentCaptor.forClass(EntityObjectCreatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        EntityObjectCreatedEvent event = captor.getValue();
        assertThat(event.orgKey()).isEqualTo(ORG);
        assertThat(event.entityDefinitionCode()).isEqualTo("partner");
        assertThat(event.objectId()).isEqualTo(id);
        assertThat(event.version()).isZero();
        assertThat(event.payload()).containsEntry("name", "ACME Corp");
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    void createEntityInstance_definitionNotFound_publishesNothing() {
        when(definitionLookupPort.findByCode("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> createUseCase.create(ORG, "unknown", Map.of()))
                .isInstanceOf(NotFoundException.class);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void updateEntityInstance_publishesUpdatedEvent() {
        UUID id = UUID.randomUUID();
        EntityObject existing = EntityObject.builder()
                .id(id)
                .entityDefinitionCode("partner")
                .version(1L)
                .payload(Map.of("name", "Old"))
                .build();
        EntityDefinitionView defView = new EntityDefinitionView("partner", false, List.of());
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(definitionLookupPort.findByCode("partner")).thenReturn(Optional.of(defView));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        updateUseCase.update(ORG, id, 1L, Map.of("name", "New"));

        ArgumentCaptor<EntityObjectUpdatedEvent> captor = ArgumentCaptor.forClass(EntityObjectUpdatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        EntityObjectUpdatedEvent event = captor.getValue();
        assertThat(event.orgKey()).isEqualTo(ORG);
        assertThat(event.entityDefinitionCode()).isEqualTo("partner");
        assertThat(event.objectId()).isEqualTo(id);
        assertThat(event.payload()).containsEntry("name", "New");
    }

    @Test
    void updateEntityInstance_versionMismatch_publishesNothing() {
        UUID id = UUID.randomUUID();
        EntityObject existing = EntityObject.builder().id(id).entityDefinitionCode("partner").version(2L).build();
        when(repository.findById(id)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> updateUseCase.update(ORG, id, 1L, Map.of()))
                .isInstanceOf(ConflictException.class);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void deleteEntityInstance_publishesDeletedEvent() {
        UUID id = UUID.randomUUID();
        EntityObject existing = EntityObject.builder().id(id).entityDefinitionCode("partner").build();
        when(repository.findById(id)).thenReturn(Optional.of(existing));

        deleteUseCase.delete(ORG, id, false);

        ArgumentCaptor<EntityObjectDeletedEvent> captor = ArgumentCaptor.forClass(EntityObjectDeletedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        EntityObjectDeletedEvent event = captor.getValue();
        assertThat(event.orgKey()).isEqualTo(ORG);
        assertThat(event.entityDefinitionCode()).isEqualTo("partner");
        assertThat(event.objectId()).isEqualTo(id);
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchEntityInstances_success() {
        Specification<EntityObject> spec = (root, query, cb) -> null;
        when(rsqlAdapter.toSpecification("name==ACME", "partner")).thenReturn(spec);

        Pageable pageable = PageRequest.of(0, 10);
        Page<EntityObject> page = new PageImpl<>(List.of(EntityObject.builder().entityDefinitionCode("partner").build()));
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        Page<EntityObject> result = searchUseCase.search("partner", "name==ACME", pageable);

        assertThat(result.getContent()).hasSize(1);
    }
}
