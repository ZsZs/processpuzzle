package com.processpuzzle.baseentity.instances.usecases;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.baseentity.common.ConflictException;
import com.processpuzzle.baseentity.instances.domain.EntityObject;
import com.processpuzzle.baseentity.instances.domain.EntityObjectRepository;
import com.processpuzzle.baseentity.instances.domain.EntityObjectScope;
import com.processpuzzle.baseentity.instances.usecases.inbound.CreateEntityInstanceUseCase;
import com.processpuzzle.baseentity.instances.usecases.inbound.ReinstateEntityInstanceUseCase;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionLookupPort;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionView;
import com.processpuzzle.baseentity.instances.usecases.outbound.PayloadValidatorPort;
import java.time.Instant;
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

/** The type's identifiers are natural keys: unique among the non-retired objects. */
@ExtendWith(MockitoExtension.class)
class NaturalKeyTest {

    private static final String ORG = "acme";

    @Mock
    private EntityObjectRepository repository;
    @Mock
    private EntityDefinitionLookupPort definitions;
    @Mock
    private PayloadValidatorPort validator;
    @Mock
    private ApplicationEventPublisher events;

    private EntityObjectScope scope;

    @BeforeEach
    void setUp() {
        scope = new EntityObjectScope(repository, definitions);
        lenient().when(definitions.findByCode(ORG, "club")).thenReturn(Optional.of(
            new EntityDefinitionView("club", false, List.of(), null, "Club", List.of(List.of("country", "name")))));
    }

    private EntityObject stored(Map<String, Object> payload) {
        return EntityObject.builder().id(UUID.randomUUID()).orgKey(ORG).entityDefinitionCode("club").payload(payload).build();
    }

    @Test
    void aRepeatedKeyIsAConflict_whichNamesIt() {
        when(repository.findAllByOrgKeyAndEntityDefinitionCode(ORG, "club")).thenReturn(List.of(stored(Map.of("country", "HU", "name", "BYC"))));

        assertThatThrownBy(() -> scope.requireUniqueKeys(ORG, "club", Map.of("country", "HU", "name", " BYC "), null))
            .isInstanceOf(ConflictException.class).hasMessageContaining("country = 'HU', name = 'BYC'");
    }

    @Test
    void anotherValueInOneAttributeIsAnotherKey() {
        when(repository.findAllByOrgKeyAndEntityDefinitionCode(ORG, "club")).thenReturn(List.of(stored(Map.of("country", "HU", "name", "BYC"))));

        assertThatCode(() -> scope.requireUniqueKeys(ORG, "club", Map.of("country", "AT", "name", "BYC"), null)).doesNotThrowAnyException();
    }

    @Test
    void anObjectIsNotItsOwnDuplicate() {
        EntityObject own = stored(Map.of("country", "HU", "name", "BYC"));
        when(repository.findAllByOrgKeyAndEntityDefinitionCode(ORG, "club")).thenReturn(List.of(own));

        assertThatCode(() -> scope.requireUniqueKeys(ORG, "club", Map.of("country", "HU", "name", "BYC"), own.getId())).doesNotThrowAnyException();
    }

    @Test
    void aRetiredObjectFreesItsKey() {
        EntityObject retired = stored(Map.of("country", "HU", "name", "BYC"));
        retired.setRetiredAt(Instant.now());
        when(repository.findAllByOrgKeyAndEntityDefinitionCode(ORG, "club")).thenReturn(List.of(retired));

        assertThatCode(() -> scope.requireUniqueKeys(ORG, "club", Map.of("country", "HU", "name", "BYC"), null)).doesNotThrowAnyException();
    }

    @Test
    void aMissingValueLeavesTheKeyUnconstrained() {
        assertThatCode(() -> scope.requireUniqueKeys(ORG, "club", Map.of("name", "BYC"), null)).doesNotThrowAnyException();
        verify(repository, org.mockito.Mockito.never()).findAllByOrgKeyAndEntityDefinitionCode(any(), any());
    }

    @Test
    void numbersCompareByValue() {
        when(definitions.findByCode(ORG, "boat")).thenReturn(Optional.of(
            new EntityDefinitionView("boat", false, List.of(), null, null, List.of(List.of("sailNumber")))));
        EntityObject boat = EntityObject.builder().id(UUID.randomUUID()).orgKey(ORG).entityDefinitionCode("boat")
            .payload(Map.of("sailNumber", 7)).build();
        when(repository.findAllByOrgKeyAndEntityDefinitionCode(ORG, "boat")).thenReturn(List.of(boat));

        assertThatThrownBy(() -> scope.requireUniqueKeys(ORG, "boat", Map.of("sailNumber", 7.0), null)).isInstanceOf(ConflictException.class);
    }

    @Test
    void createRefusesARepeatedKey() {
        when(repository.findAllByOrgKeyAndEntityDefinitionCode(ORG, "club")).thenReturn(List.of(stored(Map.of("country", "HU", "name", "BYC"))));
        CreateEntityInstanceUseCase create = new CreateEntityInstanceUseCase(repository, scope, definitions, validator, events);

        assertThatThrownBy(() -> create.create(ORG, "club", Map.of("country", "HU", "name", "BYC"))).isInstanceOf(ConflictException.class);
        verify(repository, org.mockito.Mockito.never()).saveAndFlush(any());
    }

    @Test
    void reinstatingIsRefusedWhileAnotherObjectHoldsTheKey() {
        EntityObject retired = stored(Map.of("country", "HU", "name", "BYC"));
        retired.setRetiredAt(Instant.now());
        when(repository.findByIdAndOrgKey(retired.getId(), ORG)).thenReturn(Optional.of(retired));
        when(repository.findAllByOrgKeyAndEntityDefinitionCode(ORG, "club"))
            .thenReturn(List.of(retired, stored(Map.of("country", "HU", "name", "BYC"))));
        ReinstateEntityInstanceUseCase reinstate = new ReinstateEntityInstanceUseCase(repository, scope, events);

        assertThatThrownBy(() -> reinstate.reinstate(ORG, "club", retired.getId())).isInstanceOf(ConflictException.class);
    }
}
