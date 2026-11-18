package com.processpuzzle.baseentity.instances.usecases;

import com.processpuzzle.baseentity.common.ConflictException;
import com.processpuzzle.baseentity.common.NotFoundException;
import com.processpuzzle.baseentity.instances.domain.EntityObject;
import com.processpuzzle.baseentity.instances.domain.EntityObjectRepository;
import com.processpuzzle.baseentity.instances.domain.EntityObjectScope;
import com.processpuzzle.baseentity.instances.usecases.inbound.CollectionAddress;
import com.processpuzzle.baseentity.instances.usecases.inbound.EmbeddedCollectionUseCase;
import com.processpuzzle.baseentity.instances.usecases.inbound.UpdateEntityInstanceUseCase;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityAttributeView;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityAttributeView.ValueKindView;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionLookupPort;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionView;
import com.processpuzzle.baseentity.instances.usecases.outbound.PayloadValidatorPort;
import com.processpuzzle.baseentity.port.ManagedAttributesPort;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmbeddedCollectionUseCaseTest {

    private static final String ORG = "processpuzzle-testbed";

    @Mock
    private EntityObjectRepository repository;
    @Mock
    private EntityDefinitionLookupPort definitionLookupPort;
    @Mock
    private PayloadValidatorPort payloadValidatorPort;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private EmbeddedCollectionUseCase useCase;
    private final UUID id = UUID.randomUUID();
    private EntityObject race;

    @BeforeEach
    void setUp() {
        EntityObjectScope scope = new EntityObjectScope(repository);
        UpdateEntityInstanceUseCase update = new UpdateEntityInstanceUseCase(repository, scope, definitionLookupPort, payloadValidatorPort,
                eventPublisher, ManagedAttributesPort.NONE);
        useCase = new EmbeddedCollectionUseCase(scope, definitionLookupPort, update);

        List<Map<String, Object>> rounds = new ArrayList<>();
        List<Map<String, Object>> results = new ArrayList<>();
        results.add(new java.util.LinkedHashMap<>(Map.of("id", "x1", "score", 5, "itemVersion", 1)));
        Map<String, Object> round = new java.util.LinkedHashMap<>(Map.of("id", "r1", "number", 1, "label", "first"));
        round.put("results", results);
        rounds.add(round);
        race = EntityObject.builder().id(id).entityDefinitionCode("race").version(5L)
                .payload(new java.util.LinkedHashMap<>(Map.of("name", "Spring Cup", "rounds", rounds))).build();
        // Lenient: the other-organization test never gets as far as the definition.
        org.mockito.Mockito.lenient().when(repository.findByIdAndOrgKey(id, ORG)).thenReturn(Optional.of(race));
        org.mockito.Mockito.lenient().when(definitionLookupPort.findByCode(ORG, "race")).thenReturn(Optional.of(new EntityDefinitionView("race", false, List.of(
                new EntityAttributeView("name", ValueKindView.TEXT, false, false, null, true),
                new EntityAttributeView("rounds", ValueKindView.TEXT, true, true, "Round", false),
                new EntityAttributeView("field", ValueKindView.TEXT, false, true, "Field", false)))));
        org.mockito.Mockito.lenient().when(definitionLookupPort.findByCode(ORG, "Round")).thenReturn(Optional.of(new EntityDefinitionView("Round", true, List.of(
                new EntityAttributeView("number", ValueKindView.NUMBER, false, false, null, false),
                new EntityAttributeView("results", ValueKindView.TEXT, true, true, "Result", false)))));
        org.mockito.Mockito.lenient().when(definitionLookupPort.findByCode(ORG, "Result")).thenReturn(Optional.of(new EntityDefinitionView("Result", true, List.of(
                new EntityAttributeView("score", ValueKindView.NUMBER, false, false, null, false)))));
        // What the database does: the saved object has the next version.
        org.mockito.Mockito.lenient().when(repository.saveAndFlush(any())).thenAnswer(invocation -> {
            EntityObject saved = invocation.getArgument(0);
            saved.setVersion(saved.getVersion() + 1);
            return saved;
        });
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> storedRounds() {
        return (List<Map<String, Object>>) race.getPayload().get("rounds");
    }

    @Test
    void list_returnsTheItemsAndTheObjectVersion() {
        var items = useCase.list(ORG, "race", id, "rounds");

        assertThat(items.items()).extracting(item -> item.get("id")).containsExactly("r1");
        assertThat(items.version()).isEqualTo(5L);
    }

    @Test
    void append_assignsAnIdAndSavesOnlyTheChangedCollection() {
        var result = useCase.append(ORG, "race", id, "rounds", Map.of("number", 2));

        assertThat(result.item().get("id")).asString().isNotBlank();
        assertThat(result.item()).containsEntry("number", 2);
        assertThat(result.version()).isEqualTo(6L);
        assertThat(result.itemVersion()).isEqualTo(1L);
        assertThat(storedRounds()).hasSize(2);
        assertThat(race.getPayload()).containsEntry("name", "Spring Cup");
        verify(payloadValidatorPort).validate(any(), any(), any());
    }

    @Test
    void append_keepsAnIdTheCallerBrings_andRefusesADuplicate() {
        assertThat(useCase.append(ORG, "race", id, "rounds", Map.of("id", "r2")).item()).containsEntry("id", "r2");

        assertThatThrownBy(() -> useCase.append(ORG, "race", id, "rounds", Map.of("id", "r1"))).isInstanceOf(ConflictException.class);
    }

    @Test
    void append_isRetriedWhenAnotherWriteWinsTheRace() {
        // The first read is a stale copy (version 4) while the update, which reads again, finds version 5: a lost race.
        // The retry reads the stored object every time.
        EntityObject stale = EntityObject.builder().id(id).entityDefinitionCode("race").version(race.getVersion() - 1).payload(race.getPayload()).build();
        when(repository.findByIdAndOrgKey(id, ORG)).thenReturn(Optional.of(stale)).thenReturn(Optional.of(race));

        var result = useCase.append(ORG, "race", id, "rounds", Map.of("number", 2));

        assertThat(result.item()).containsEntry("number", 2);
    }

    @Test
    void patch_mergesMembersAndRemovesNulls_keepingTheId() {
        Map<String, Object> patch = new java.util.HashMap<>();
        patch.put("label", null);
        patch.put("number", 7);

        var result = useCase.patch(ORG, "race", id, "rounds", "r1", null, patch);

        assertThat(result.item()).containsEntry("id", "r1").containsEntry("number", 7).doesNotContainKey("label");
        assertThat(storedRounds().get(0)).doesNotContainKey("label");
    }

    @Test
    void patch_refusesToChangeTheId() {
        assertThatThrownBy(() -> useCase.patch(ORG, "race", id, "rounds", "r1", null, Map.of("id", "other")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void patch_withAStaleItemVersion_isAConflict_andIsNotRetried() {
        assertThatThrownBy(() -> useCase.patch(ORG, "race", id, "rounds", "r1", 4L, Map.of("number", 9)))
                .isInstanceOf(EmbeddedCollectionUseCase.ItemVersionConflictException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void patch_withTheCurrentItemVersion_succeeds_andCountsTheChange() {
        var result = useCase.patch(ORG, "race", id, "rounds", "r1", 1L, Map.of("number", 9));

        assertThat(result.itemVersion()).isEqualTo(2L);
        assertThat(result.item()).containsEntry("itemVersion", 2);
        assertThat(result.version()).isEqualTo(6L);
    }

    @Test
    void patch_ignoresAnItemVersionTheCallerSends() {
        assertThat(useCase.patch(ORG, "race", id, "rounds", "r1", null, Map.of("itemVersion", 99)).itemVersion()).isEqualTo(2L);
    }

    @Test
    void remove_withAStaleItemVersion_isAConflict() {
        assertThatThrownBy(() -> useCase.remove(ORG, "race", id, "rounds", "r1", 7L)).isInstanceOf(ConflictException.class);
        assertThat(storedRounds()).hasSize(1);
    }

    @Test
    void remove_dropsTheItem() {
        var result = useCase.remove(ORG, "race", id, "rounds", "r1", null);

        assertThat(result.item()).isNull();
        assertThat(storedRounds()).isEmpty();
    }

    @Test
    void anUnknownItem_isNotFound() {
        assertThatThrownBy(() -> useCase.remove(ORG, "race", id, "rounds", "nope", null)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> useCase.patch(ORG, "race", id, "rounds", "nope", null, Map.of())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void anAttributeThatIsNotAnEmbeddedCollection_isNotFound() {
        assertThatThrownBy(() -> useCase.list(ORG, "race", id, "name")).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> useCase.list(ORG, "race", id, "missing")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void aSingleEmbeddedObject_isABadRequest() {
        assertThatThrownBy(() -> useCase.append(ORG, "race", id, "field", Map.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anObjectOfAnotherOrganization_isNotFound() {
        when(repository.findByIdAndOrgKey(id, "other")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.list("other", "race", id, "rounds")).isInstanceOf(NotFoundException.class);
    }

    // region nested collections
    private static final CollectionAddress RESULTS_OF_R1 = CollectionAddress.of("rounds").within("r1", "results");

    @Test
    void nested_listReadsTheCollectionOfTheItemItIsNestedIn() {
        var items = useCase.list(ORG, "race", id, RESULTS_OF_R1);

        assertThat(items.items()).extracting(item -> item.get("id")).containsExactly("x1");
        assertThat(items.version()).isEqualTo(5L);
    }

    @Test
    void nested_appendChangesOnlyTheNestedCollectionAndBumpsTheObject() {
        var result = useCase.append(ORG, "race", id, RESULTS_OF_R1, Map.of("score", 9));

        assertThat(result.item()).containsEntry("score", 9).containsEntry("itemVersion", 1);
        assertThat(result.version()).isEqualTo(6L);
        assertThat(storedRounds().get(0).get("results")).asList().hasSize(2);
        assertThat(storedRounds().get(0)).containsEntry("label", "first");
    }

    @Test
    void nested_patchCountsTheItemVersionOfTheNestedItem() {
        var result = useCase.patch(ORG, "race", id, RESULTS_OF_R1, "x1", 1L, Map.of("score", 7));

        assertThat(result.itemVersion()).isEqualTo(2L);
        assertThatThrownBy(() -> useCase.patch(ORG, "race", id, RESULTS_OF_R1, "x1", 1L, Map.of("score", 8)))
                .isInstanceOf(EmbeddedCollectionUseCase.ItemVersionConflictException.class);
    }

    @Test
    void nested_removeTakesOutOnlyTheNestedItem() {
        useCase.remove(ORG, "race", id, RESULTS_OF_R1, "x1", null);

        assertThat(storedRounds().get(0).get("results")).asList().isEmpty();
        assertThat(storedRounds()).hasSize(1);
    }

    @Test
    void nested_anUnknownOwnerItemIsNotFound() {
        assertThatThrownBy(() -> useCase.list(ORG, "race", id, CollectionAddress.of("rounds").within("nope", "results")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void nested_anAttributeThatIsNotAnEmbeddedCollectionOfTheComponent_isNotFound() {
        assertThatThrownBy(() -> useCase.list(ORG, "race", id, CollectionAddress.of("rounds").within("r1", "score")))
                .isInstanceOf(NotFoundException.class);
    }
    // endregion
}
