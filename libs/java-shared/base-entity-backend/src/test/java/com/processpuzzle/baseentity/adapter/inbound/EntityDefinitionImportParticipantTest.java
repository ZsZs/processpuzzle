package com.processpuzzle.baseentity.adapter.inbound;

import com.processpuzzle.baseentity.definition.adapters.inbound.EntityDefinitionMapper;
import com.processpuzzle.baseentity.definition.domain.BaseEntityAttribute;
import com.processpuzzle.baseentity.definition.domain.BaseEntityDefinition;
import com.processpuzzle.baseentity.definition.domain.ValueKind;
import com.processpuzzle.baseentity.definition.usecases.inbound.FindEntityDefinitionsByCodesUseCase;
import com.processpuzzle.baseentity.definition.usecases.inbound.ImportEntityDefinitions;
import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.definition.ImportedItem;
import com.processpuzzle.core.definition.ParticipantResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntityDefinitionImportParticipantTest {

    private static final String ORG = "acme";
    private static final String FILE = "entities/sales.yaml";

    @Mock
    private ImportEntityDefinitions importDefinitions;

    @Mock
    private FindEntityDefinitionsByCodesUseCase findByCodes;

    private EntityDefinitionImportParticipant participant;

    @BeforeEach
    void setUp() {
        participant = new EntityDefinitionImportParticipant(importDefinitions, findByCodes, new EntityDefinitionMapper());
    }

    @Test
    void runsFirstAsTheEntityKind() {
        assertThat(participant.kind()).isEqualTo(DefinitionKinds.ENTITY);
        assertThat(participant.order()).isEqualTo(DefinitionKinds.ENTITY_ORDER);
    }

    @Test
    void reportsCreatedAndUpdatedCodes() throws IOException {
        when(importDefinitions.execute(eq(ORG), any()))
                .thenReturn(new ImportEntityDefinitions.Outcome(List.of("invoice"), List.of("partner"), List.of()));

        ParticipantResult result = participant.apply(ORG, FILE, bytes("entityDefinitions: []"));

        assertThat(result.isRejected()).isFalse();
        assertThat(result.items()).containsExactly(
                new ImportedItem("invoice", ImportedItem.Action.CREATE),
                new ImportedItem("partner", ImportedItem.Action.UPDATE));
    }

    @Test
    void passesTheFileContentToTheImport() throws IOException {
        when(importDefinitions.execute(eq(ORG), any()))
                .thenReturn(new ImportEntityDefinitions.Outcome(List.of(), List.of(), List.of()));

        participant.apply(ORG, FILE, bytes("entityDefinitions: []"));

        ArgumentCaptor<InputStream> input = ArgumentCaptor.forClass(InputStream.class);
        verify(importDefinitions).execute(eq(ORG), input.capture());
        assertThat(new String(input.getValue().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("entityDefinitions: []");
    }

    @Test
    void prefixesEveryErrorWithTheFileName() throws IOException {
        when(importDefinitions.execute(eq(ORG), any()))
                .thenReturn(new ImportEntityDefinitions.Outcome(List.of(), List.of(), List.of("first", "second")));

        ParticipantResult result = participant.apply(ORG, FILE, bytes("whatever"));

        assertThat(result.isRejected()).isTrue();
        assertThat(result.items()).isEmpty();
        assertThat(result.errors()).containsExactly(FILE + ": first", FILE + ": second");
    }

    @Test
    void anUnreadableFileIsRejected() throws IOException {
        when(importDefinitions.execute(eq(ORG), any())).thenThrow(new IOException("bad yaml"));

        ParticipantResult result = participant.apply(ORG, FILE, bytes(": : :"));

        assertThat(result.isRejected()).isTrue();
        assertThat(result.errors()).containsExactly(FILE + ": bad yaml");
    }

    @Test
    void aRuntimeFailureIsRejected() throws IOException {
        when(importDefinitions.execute(eq(ORG), any())).thenThrow(new IllegalStateException("boom"));

        ParticipantResult result = participant.apply(ORG, FILE, bytes("entityDefinitions: []"));

        assertThat(result.errors()).containsExactly(FILE + ": boom");
    }

    @Test
    void malformedYamlThroughTheRealImportIsRejectedNotThrown() {
        ImportEntityDefinitions realImport = new ImportEntityDefinitions(null, null, new EntityDefinitionMapper(), null, null);
        EntityDefinitionImportParticipant real =
                new EntityDefinitionImportParticipant(realImport, findByCodes, new EntityDefinitionMapper());

        ParticipantResult result = real.apply(ORG, FILE, bytes("entityDefinitions: [ unclosed"));

        assertThat(result.isRejected()).isTrue();
        assertThat(result.errors()).singleElement().asString().startsWith(FILE + ": ");
    }

    @Test
    void fingerprintsIgnoreIdsVersionsAndTimestamps() {
        BaseEntityDefinition stored = definition("partner", "Partner");
        BaseEntityDefinition sameButRestored = definition("partner", "Partner");
        sameButRestored.setId(UUID.randomUUID());
        sameButRestored.setVersion(42L);
        sameButRestored.setCreatedAt(Instant.parse("2020-01-01T00:00:00Z"));
        sameButRestored.setUpdatedAt(Instant.parse("2021-01-01T00:00:00Z"));
        sameButRestored.setCreatedBy("someone-else");
        sameButRestored.getAttributes().getFirst().setId(UUID.randomUUID());

        when(findByCodes.find(ORG, Set.of("partner"))).thenReturn(List.of(stored), List.of(sameButRestored));

        String first = participant.fingerprints(ORG, Set.of("partner")).get("partner");
        String second = participant.fingerprints(ORG, Set.of("partner")).get("partner");

        assertThat(first).isNotBlank().isEqualTo(second);
        assertThat(first).doesNotContain("\"id\"", "\"version\"", "createdAt", "updatedAt", "createdBy");
    }

    @Test
    void aChangedNameChangesTheFingerprint() {
        when(findByCodes.find(ORG, Set.of("partner")))
                .thenReturn(List.of(definition("partner", "Partner")), List.of(definition("partner", "Business partner")));

        String first = participant.fingerprints(ORG, Set.of("partner")).get("partner");
        String second = participant.fingerprints(ORG, Set.of("partner")).get("partner");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void fingerprintsAreKeyedByCodeAndOmitUnknownCodes() {
        when(findByCodes.find(ORG, Set.of("partner", "ghost"))).thenReturn(List.of(definition("partner", "Partner")));

        Map<String, String> fingerprints = participant.fingerprints(ORG, Set.of("partner", "ghost"));

        assertThat(fingerprints).containsOnlyKeys("partner");
    }

    private static BaseEntityDefinition definition(String code, String name) {
        BaseEntityAttribute attribute = BaseEntityAttribute.builder()
                .id(UUID.randomUUID())
                .code("email")
                .name("Email")
                .valueKind(ValueKind.TEXT)
                .build();
        BaseEntityDefinition definition = BaseEntityDefinition.builder()
                .id(UUID.randomUUID())
                .orgKey(ORG)
                .code(code)
                .name(name)
                .version(1L)
                .attributes(new ArrayList<>(List.of(attribute)))
                .build();
        definition.setCreatedAt(Instant.parse("2026-10-05T10:00:00Z"));
        definition.setUpdatedAt(Instant.parse("2026-10-05T11:00:00Z"));
        definition.setCreatedBy("seeder");
        return definition;
    }

    private static byte[] bytes(String content) {
        return content.getBytes(StandardCharsets.UTF_8);
    }
}
