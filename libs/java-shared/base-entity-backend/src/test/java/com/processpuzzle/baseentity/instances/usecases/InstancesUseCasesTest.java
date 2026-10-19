package com.processpuzzle.baseentity.instances.usecases;

import com.processpuzzle.baseentity.common.ConflictException;
import com.processpuzzle.baseentity.common.NotFoundException;
import com.processpuzzle.baseentity.common.ValidationException;
import com.processpuzzle.baseentity.instances.domain.EntityObject;
import com.processpuzzle.baseentity.instances.domain.EntityObjectRepository;
import com.processpuzzle.baseentity.instances.domain.EntityObjectScope;
import com.processpuzzle.baseentity.instances.domain.event.EntityObjectCreatedEvent;
import com.processpuzzle.baseentity.instances.domain.event.EntityObjectDeletedEvent;
import com.processpuzzle.baseentity.instances.domain.event.EntityObjectUpdatedEvent;
import com.processpuzzle.baseentity.instances.usecases.inbound.CreateEntityInstanceUseCase;
import com.processpuzzle.baseentity.instances.usecases.inbound.DeleteEntityInstanceUseCase;
import com.processpuzzle.baseentity.instances.usecases.inbound.FindEntityInstanceByIdUseCase;
import com.processpuzzle.baseentity.instances.usecases.inbound.SearchEntityInstancesUseCase;
import com.processpuzzle.baseentity.instances.usecases.inbound.UpdateEntityInstanceUseCase;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityAttributeView;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityAttributeView.ValueKindView;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionLookupPort;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionView;
import com.processpuzzle.baseentity.instances.usecases.outbound.PayloadValidatorPort;
import com.processpuzzle.baseentity.instances.usecases.outbound.RsqlToInstanceSpecificationPort;
import com.processpuzzle.baseentity.port.ManagedAttributesPort;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.processpuzzle.shared.event.PlatformEvent;
import com.processpuzzle.shared.event.PlatformEventAction;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeastOnce;
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
        EntityObjectScope scope = new EntityObjectScope(repository);
        createUseCase = new CreateEntityInstanceUseCase(repository, scope, definitionLookupPort, payloadValidatorPort, eventPublisher);
        updateUseCase = new UpdateEntityInstanceUseCase(repository, scope, definitionLookupPort, payloadValidatorPort, eventPublisher, ManagedAttributesPort.NONE);
        deleteUseCase = new DeleteEntityInstanceUseCase(repository, scope, eventPublisher);
        findByIdUseCase = new FindEntityInstanceByIdUseCase(scope);
        searchUseCase = new SearchEntityInstancesUseCase(repository, scope, rsqlAdapter);
    }

    @Test
    void createEntityInstance_success() {
        EntityDefinitionView defView = new EntityDefinitionView("partner", false, List.of());
        when(definitionLookupPort.findByCode(ORG, "partner")).thenReturn(Optional.of(defView));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> {
            EntityObject argument = invocation.getArgument(0);
            argument.setId(UUID.randomUUID());
            return argument;
        });

        Map<String, Object> payload = Map.of("name", "ACME Corp");
        EntityObject result = createUseCase.create(ORG, "partner", payload);

        assertThat(result.getOrgKey()).isEqualTo(ORG);
        assertThat(result.getEntityDefinitionCode()).isEqualTo("partner");
        assertThat(result.getPayload()).isEqualTo(payload);
        verify(payloadValidatorPort).validate(ORG, defView, payload);
        verify(repository).saveAndFlush(any(EntityObject.class));
    }

    @Test
    void createEntityInstance_definitionNotFound_throwsNotFound() {
        when(definitionLookupPort.findByCode(ORG, "unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> createUseCase.create(ORG, "unknown", Map.of()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createEntityInstance_embeddedDefinition_throwsConflict() {
        EntityDefinitionView embeddedDef = new EntityDefinitionView("address", true, List.of());
        when(definitionLookupPort.findByCode(ORG, "address")).thenReturn(Optional.of(embeddedDef));

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

        when(repository.findByIdAndOrgKey(id, ORG)).thenReturn(Optional.of(existing));
        when(definitionLookupPort.findByCode(ORG, "partner")).thenReturn(Optional.of(defView));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> newPayload = Map.of("name", "New");
        EntityObject result = updateUseCase.update(ORG, "partner", id, 1L, newPayload);

        assertThat(result.getPayload()).isEqualTo(newPayload);
        verify(payloadValidatorPort).validate(ORG, defView, newPayload);
        verify(repository).saveAndFlush(existing);
    }

    @Test
    void updateEntityInstance_keepsTheStoredValueOfAManagedAttribute() {
        UUID id = UUID.randomUUID();
        EntityObject existing = EntityObject.builder()
                .id(id)
                .entityDefinitionCode("order")
                .version(1L)
                .payload(Map.of("name", "Old", "status", "DRAFT"))
                .build();
        EntityDefinitionView defView = new EntityDefinitionView("order", false, List.of());
        UpdateEntityInstanceUseCase guarded = new UpdateEntityInstanceUseCase(repository, new EntityObjectScope(repository),
                definitionLookupPort, payloadValidatorPort, eventPublisher, (orgKey, code) -> Set.of("status", "priority"));

        when(repository.findByIdAndOrgKey(id, ORG)).thenReturn(Optional.of(existing));
        when(definitionLookupPort.findByCode(ORG, "order")).thenReturn(Optional.of(defView));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        EntityObject result = guarded.update(ORG, "order", id, 1L, Map.of("name", "New", "status", "SHIPPED", "priority", "HIGH"));

        Map<String, Object> expected = Map.of("name", "New", "status", "DRAFT");
        assertThat(result.getPayload()).isEqualTo(expected);
        verify(payloadValidatorPort).validate(ORG, defView, expected);
    }

    private static final EntityDefinitionView NOTE_DEF = new EntityDefinitionView("note", false, List.of(
            new EntityAttributeView("comments", ValueKindView.REFERENCE, true, false, null, false, true)));
    private static final Map<String, Object> FIRST = Map.of("author", "Ada", "createdAt", "2026-10-01T09:30:00Z",
            "content", Map.of("type", "doc", "content", List.of(Map.of("type", "heading", "attrs", Map.of("level", 1)))));
    private static final Map<String, Object> SECOND = Map.of("author", "Grace", "createdAt", "2026-10-02T10:00:00Z", "content", Map.of("type", "doc"));
    private static final Map<String, Object> THIRD = Map.of("author", "Alan", "createdAt", "2026-10-03T11:00:00Z", "content", Map.of("type", "doc"));

    private EntityObject storedNote(UUID id, List<Map<String, Object>> comments) {
        EntityObject existing = EntityObject.builder()
                .id(id)
                .entityDefinitionCode("note")
                .version(1L)
                .payload(Map.of("title", "Old", "comments", comments))
                .build();
        when(repository.findByIdAndOrgKey(id, ORG)).thenReturn(Optional.of(existing));
        when(definitionLookupPort.findByCode(ORG, "note")).thenReturn(Optional.of(NOTE_DEF));
        return existing;
    }

    @Test
    void updateEntityInstance_appendingAComment_isAccepted() {
        UUID id = UUID.randomUUID();
        storedNote(id, List.of(FIRST, SECOND));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> payload = Map.of("title", "New", "comments", List.of(FIRST, SECOND, THIRD));
        EntityObject result = updateUseCase.update(ORG, "note", id, 1L, payload);

        assertThat(result.getPayload()).isEqualTo(payload);
    }

    /** The stored side and the submitted side parse JSON numbers independently; a 1 is a 1 either way. */
    @Test
    void updateEntityInstance_resubmittingTheHistoryWithOtherNumberTypes_isAccepted() {
        UUID id = UUID.randomUUID();
        storedNote(id, List.of(FIRST));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Map<String, Object> resubmitted = Map.of("author", "Ada", "createdAt", "2026-10-01T09:30:00Z",
                "content", Map.of("type", "doc", "content", List.of(Map.of("type", "heading", "attrs", Map.of("level", 1L)))));

        updateUseCase.update(ORG, "note", id, 1L, Map.of("title", "New", "comments", List.of(resubmitted)));

        verify(repository).saveAndFlush(any());
    }

    @Test
    void updateEntityInstance_omittingTheComments_keepsTheStoredHistory() {
        UUID id = UUID.randomUUID();
        storedNote(id, List.of(FIRST, SECOND));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        EntityObject result = updateUseCase.update(ORG, "note", id, 1L, Map.of("title", "New"));

        Map<String, Object> expected = Map.of("title", "New", "comments", List.of(FIRST, SECOND));
        assertThat(result.getPayload()).isEqualTo(expected);
        verify(payloadValidatorPort).validate(ORG, NOTE_DEF, expected);
    }

    @Test
    void updateEntityInstance_editingRemovingReorderingOrTruncatingAComment_isRejected() {
        UUID id = UUID.randomUUID();
        storedNote(id, List.of(FIRST, SECOND));
        Map<String, Object> edited = Map.of("author", "Grace", "createdAt", "2026-10-02T10:00:00Z", "content", Map.of("type", "doc", "edited", true));

        for (Object comments : List.of(List.of(FIRST, edited), List.of(SECOND, THIRD), List.of(SECOND, FIRST), List.of(FIRST), List.of(), "gone")) {
            assertThatThrownBy(() -> updateUseCase.update(ORG, "note", id, 1L, Map.of("title", "New", "comments", comments)))
                    .as("comments %s", comments)
                    .isInstanceOf(ValidationException.class)
                    .satisfies(ex -> assertThat(((ValidationException) ex).getViolations())
                            .containsExactly(new ValidationException.Violation("comments", "comments are append-only")));
        }
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void updateEntityInstance_withoutAStoredHistory_acceptsAnyComments() {
        UUID id = UUID.randomUUID();
        storedNote(id, List.of());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        EntityObject result = updateUseCase.update(ORG, "note", id, 1L, Map.of("title", "New", "comments", List.of(THIRD)));

        assertThat(result.getPayload()).containsEntry("comments", List.of(THIRD));
    }

    @Test
    void updateEntityInstance_versionMismatch_throwsConflict() {
        UUID id = UUID.randomUUID();
        EntityObject existing = EntityObject.builder()
                .id(id)
                .entityDefinitionCode("partner")
                .version(2L)
                .build();

        when(repository.findByIdAndOrgKey(id, ORG)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> updateUseCase.update(ORG, "partner", id, 1L, Map.of()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void deleteEntityInstance_success() {
        UUID id = UUID.randomUUID();
        EntityObject existing = EntityObject.builder().id(id).entityDefinitionCode("partner").build();
        when(repository.findByIdAndOrgKey(id, ORG)).thenReturn(Optional.of(existing));

        deleteUseCase.delete(ORG, "partner", id, false);

        verify(repository).delete(existing);
    }

    @Test
    void findEntityInstanceById_notFound_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndOrgKey(id, ORG)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> findByIdUseCase.findById(ORG, "partner", id))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void findEntityInstanceById_success() {
        UUID id = UUID.randomUUID();
        EntityObject entity = EntityObject.builder().id(id).entityDefinitionCode("partner").build();
        when(repository.findByIdAndOrgKey(id, ORG)).thenReturn(Optional.of(entity));

        EntityObject result = findByIdUseCase.findById(ORG, "partner", id);

        assertThat(result).isSameAs(entity);
    }

    @Test
    void anotherOrganizationsObjectIsNotFoundByIdForUpdateOrDelete() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndOrgKey(id, "other-org")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> findByIdUseCase.findById("other-org", "partner", id)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> updateUseCase.update("other-org", "partner", id, 1L, Map.of())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> deleteUseCase.delete("other-org", "partner", id, true)).isInstanceOf(NotFoundException.class);
        verify(repository, never()).delete(any(EntityObject.class));
    }

    @Test
    void updateEntityInstance_notFound_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndOrgKey(id, ORG)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> updateUseCase.update(ORG, "partner", id, 1L, Map.of()))
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
        when(repository.findByIdAndOrgKey(id, ORG)).thenReturn(Optional.of(existing));
        when(definitionLookupPort.findByCode(ORG, "partner")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> updateUseCase.update(ORG, "partner", id, 1L, Map.of()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteEntityInstance_notFound_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndOrgKey(id, ORG)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deleteUseCase.delete(ORG, "partner", id, false))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteEntityInstance_hasReferencesAndCascadeFalse_throwsConflict() {
        UUID id = UUID.randomUUID();
        EntityObject existing = EntityObject.builder().id(id).entityDefinitionCode("partner").build();
        when(repository.findByIdAndOrgKey(id, ORG)).thenReturn(Optional.of(existing));
        when(repository.existsAnyReferenceTo(id.toString())).thenReturn(true);

        assertThatThrownBy(() -> deleteUseCase.delete(ORG, "partner", id, false))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("is still referenced by other entities — pass cascade=true to delete anyway");
    }

    @Test
    void deleteEntityInstance_hasReferencesAndCascadeTrue_deletes() {
        UUID id = UUID.randomUUID();
        EntityObject existing = EntityObject.builder().id(id).entityDefinitionCode("partner").build();
        when(repository.findByIdAndOrgKey(id, ORG)).thenReturn(Optional.of(existing));

        deleteUseCase.delete(ORG, "partner", id, true);

        verify(repository).delete(existing);
    }

    @Test
    void createEntityInstance_publishesCreatedEvent() {
        UUID id = UUID.randomUUID();
        EntityDefinitionView defView = new EntityDefinitionView("partner", false, List.of());
        when(definitionLookupPort.findByCode(ORG, "partner")).thenReturn(Optional.of(defView));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> {
            EntityObject argument = invocation.getArgument(0);
            argument.setId(id);
            argument.setVersion(0L);
            return argument;
        });

        createUseCase.create(ORG, "partner", Map.of("name", "ACME Corp"));

        EntityObjectCreatedEvent event = published(EntityObjectCreatedEvent.class);
        assertThat(event.orgKey()).isEqualTo(ORG);
        assertThat(event.entityDefinitionCode()).isEqualTo("partner");
        assertThat(event.objectId()).isEqualTo(id);
        assertThat(event.version()).isZero();
        assertThat(event.payload()).containsEntry("name", "ACME Corp");
        assertThat(event.occurredAt()).isNotNull();

        PlatformEvent fact = published(PlatformEvent.class);
        assertThat(fact.orgKey()).isEqualTo(ORG);
        assertThat(fact.subjectType()).isEqualTo("partner");
        assertThat(fact.subjectId()).isEqualTo(id.toString());
        assertThat(fact.action()).isEqualTo(PlatformEventAction.CREATED);
        assertThat(fact.state()).isNull();
        assertThat(fact.payload()).containsEntry("name", "ACME Corp");
        assertThat(fact.occurredAt()).isEqualTo(event.occurredAt());
    }

    @Test
    void createEntityInstance_definitionNotFound_publishesNothing() {
        when(definitionLookupPort.findByCode(ORG, "unknown")).thenReturn(Optional.empty());

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
        when(repository.findByIdAndOrgKey(id, ORG)).thenReturn(Optional.of(existing));
        when(definitionLookupPort.findByCode(ORG, "partner")).thenReturn(Optional.of(defView));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        updateUseCase.update(ORG, "partner", id, 1L, Map.of("name", "New"));

        EntityObjectUpdatedEvent event = published(EntityObjectUpdatedEvent.class);
        assertThat(event.orgKey()).isEqualTo(ORG);
        assertThat(event.entityDefinitionCode()).isEqualTo("partner");
        assertThat(event.objectId()).isEqualTo(id);
        assertThat(event.payload()).containsEntry("name", "New");
        PlatformEvent fact = published(PlatformEvent.class);
        assertThat(fact.action()).isEqualTo(PlatformEventAction.UPDATED);
        assertThat(fact.payload()).containsEntry("name", "New");
    }

    @Test
    void updateEntityInstance_versionMismatch_publishesNothing() {
        UUID id = UUID.randomUUID();
        EntityObject existing = EntityObject.builder().id(id).entityDefinitionCode("partner").version(2L).build();
        when(repository.findByIdAndOrgKey(id, ORG)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> updateUseCase.update(ORG, "partner", id, 1L, Map.of()))
                .isInstanceOf(ConflictException.class);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void deleteEntityInstance_publishesDeletedEvent() {
        UUID id = UUID.randomUUID();
        EntityObject existing = EntityObject.builder().id(id).entityDefinitionCode("partner").build();
        when(repository.findByIdAndOrgKey(id, ORG)).thenReturn(Optional.of(existing));

        deleteUseCase.delete(ORG, "partner", id, false);

        EntityObjectDeletedEvent event = published(EntityObjectDeletedEvent.class);
        assertThat(event.orgKey()).isEqualTo(ORG);
        assertThat(event.entityDefinitionCode()).isEqualTo("partner");
        assertThat(event.objectId()).isEqualTo(id);
        assertThat(event.occurredAt()).isNotNull();
        PlatformEvent fact = published(PlatformEvent.class);
        assertThat(fact.action()).isEqualTo(PlatformEventAction.DELETED);
        assertThat(fact.subjectId()).isEqualTo(id.toString());
        assertThat(fact.payload()).isEmpty();
    }

    private <T> T published(Class<T> type) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
        return captor.getAllValues().stream().filter(type::isInstance).map(type::cast).findFirst()
                .orElseThrow(() -> new AssertionError("No " + type.getSimpleName() + " published"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchEntityInstances_success() {
        Specification<EntityObject> spec = (root, query, cb) -> null;
        when(rsqlAdapter.toSpecification(ORG, "name==ACME", "partner")).thenReturn(spec);

        Pageable pageable = PageRequest.of(0, 10);
        Page<EntityObject> page = new PageImpl<>(List.of(EntityObject.builder().entityDefinitionCode("partner").build()));
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        Page<EntityObject> result = searchUseCase.search(ORG, "partner", "name==ACME", pageable);

        assertThat(result.getContent()).hasSize(1);
    }

    /** An id of another type's object is not found, whichever verb carries it. */
    @Test
    void anObjectOfAnotherTypeIsNotFoundByIdForFindUpdateOrDelete() {
        UUID id = UUID.randomUUID();
        EntityObject order = EntityObject.builder().id(id).orgKey(ORG).entityDefinitionCode("order").version(1L).build();
        when(repository.findByIdAndOrgKey(id, ORG)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> findByIdUseCase.findById(ORG, "partner", id)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> updateUseCase.update(ORG, "partner", id, 1L, Map.of())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> deleteUseCase.delete(ORG, "partner", id, true)).isInstanceOf(NotFoundException.class);
        verify(repository, never()).saveAndFlush(any());
        verify(repository, never()).delete(any(EntityObject.class));
    }
}
