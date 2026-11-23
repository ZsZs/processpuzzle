package com.processpuzzle.baseentity.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.baseentity.instances.domain.EntityObject;
import com.processpuzzle.baseentity.instances.domain.EntityObjectRepository;
import com.processpuzzle.baseentity.instances.domain.EntityObjectScope;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityAttributeView;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityAttributeView.ValueKindView;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionLookupPort;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionView;
import com.processpuzzle.baseentity.instances.usecases.outbound.MeasureView;
import com.processpuzzle.baseentity.instances.usecases.outbound.MeasureView.Kind;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SeededInstanceUpgraderTest {

    private static final String ORG = "processpuzzle-testbed";
    private static final String CODE = "item";
    private static final MeasureView KG = new MeasureView(Kind.QUANTITY, "MASS", null, null, null, null, "kg", null);

    private EntityObjectScope objects;
    private EntityObjectRepository repository;
    private EntityDefinitionLookupPort definitions;
    private SeededInstanceUpgrader upgrader;
    private EntityDefinitionView definition;

    @BeforeEach
    void setUp() {
        objects = mock(EntityObjectScope.class);
        repository = mock(EntityObjectRepository.class);
        definitions = mock(EntityDefinitionLookupPort.class);
        upgrader = new SeededInstanceUpgrader(objects, repository, definitions);
        definition = new EntityDefinitionView(CODE, false, List.of(new EntityAttributeView(
                "weight", ValueKindView.REFERENCE, false, false, null, false, false, false, false, KG)));
        when(definitions.findByCode(ORG, CODE)).thenReturn(Optional.of(definition));
    }

    @Test
    void aBareValueIsRewrittenAsItsMeasureObject() {
        EntityObject bare = object(Map.of("name", "bolt", "weight", 2));
        when(objects.findAll(ORG, CODE)).thenReturn(List.of(bare));
        when(repository.replacePayloadKeepingVersion(any(), anyString(), any(), any())).thenReturn(1);

        assertThat(upgrader.upgrade(ORG, CODE)).isEqualTo(1);

        verify(repository).replacePayloadKeepingVersion(eq(bare.getId()), eq(ORG), eq(bare.getVersion()),
                eq(Map.of("name", "bolt", "weight", Map.of("amount", "2", "unit", "kg", "baseAmount", 2.0))));
    }

    @Test
    void anUpToDateObjectIsNotWritten() {
        EntityObject current = object(Map.of("name", "bolt", "weight", Map.of("amount", "2", "unit", "kg", "baseAmount", 2.0)));
        when(objects.findAll(ORG, CODE)).thenReturn(List.of(current));

        assertThat(upgrader.upgrade(ORG, CODE)).isZero();
        verify(repository, never()).replacePayloadKeepingVersion(any(), anyString(), any(), any());
    }

    @Test
    void aDefinitionThatIsNotThereIsSkipped() {
        when(definitions.findByCode(ORG, "gone")).thenReturn(Optional.empty());

        assertThat(upgrader.upgrade(ORG, "gone")).isZero();
        verify(objects, never()).findAll(anyString(), anyString());
    }

    private static EntityObject object(Map<String, Object> payload) {
        return EntityObject.builder()
                .id(UUID.randomUUID())
                .orgKey(ORG)
                .entityDefinitionCode(CODE)
                .payload(payload)
                .version(3L)
                .build();
    }
}
