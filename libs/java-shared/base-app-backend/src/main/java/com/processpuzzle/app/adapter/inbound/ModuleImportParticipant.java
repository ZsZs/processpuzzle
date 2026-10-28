package com.processpuzzle.app.adapter.inbound;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.processpuzzle.app.adapter.inbound.dto.ModuleYamlDocument;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.app.domain.ModuleDefinition;
import com.processpuzzle.app.domain.ModuleDefinitionRepository;
import com.processpuzzle.app.model.ModuleDefinitionInput;
import com.processpuzzle.app.usecase.DeleteModuleDefinition;
import com.processpuzzle.app.usecase.ImportModuleDefinitions;
import com.processpuzzle.app.usecase.ImportOutcome;
import com.processpuzzle.core.definition.DefinitionImportParticipant;
import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.definition.ImportedItem;
import com.processpuzzle.core.definition.ParticipantResult;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Imports a Business Starter's module files, whose root is {@code moduleDefinitions:} as in the bundled
 * default-apps files, through {@link ImportModuleDefinitions}. The key is the module key.
 *
 * <p>A starter's modules are released as they are imported, through {@link ModuleReleaseParticipant} — the one
 * way a module goes live — so the application the same bundle publishes mounts released modules and its layout
 * carries them. That releases every draft of the organization, which at this point are the starter's own: the
 * importer has removed the rest. A module's uploaded translations and assets are not part of the file.
 */
@Component
public class ModuleImportParticipant implements DefinitionImportParticipant {

    private final ImportModuleDefinitions importModules;
    private final DeleteModuleDefinition deleteModule;
    private final ModuleDefinitionRepository repository;
    private final ModuleReleaseParticipant release;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public ModuleImportParticipant(ImportModuleDefinitions importModules, DeleteModuleDefinition deleteModule,
                                   ModuleDefinitionRepository repository, ModuleReleaseParticipant release) {
        this.importModules = importModules;
        this.deleteModule = deleteModule;
        this.repository = repository;
        this.release = release;
    }

    @Override
    public String kind() {
        return DefinitionKinds.MODULE;
    }

    @Override
    public int order() {
        return DefinitionKinds.MODULE_ORDER;
    }

    @Override
    public ParticipantResult apply(String orgKey, String fileName, byte[] yaml) {
        List<String> keys;
        try {
            keys = keys(yaml);
        } catch (IOException e) {
            return ParticipantResult.rejected(List.of(fileName + ": not a readable module file: " + e.getMessage()));
        }
        Set<String> existing = drafts(orgKey).keySet();
        ImportOutcome outcome;
        try {
            outcome = importModules.execute(orgKey, new ByteArrayInputStream(yaml));
        } catch (IOException | RuntimeException e) {
            return ParticipantResult.rejected(List.of(fileName + ": " + e.getMessage()));
        }
        if (!outcome.errors().isEmpty()) {
            return ParticipantResult.rejected(outcome.errors().stream().map(error -> fileName + ": " + error).toList());
        }
        release.release(orgKey);

        List<ImportedItem> items = keys.stream()
                .map(key -> new ImportedItem(key, existing.contains(key) ? ImportedItem.Action.UPDATE : ImportedItem.Action.CREATE))
                .toList();
        return new ParticipantResult(items, List.of());
    }

    /**
     * Deletes the drafts, then releases, which removes the released rows whose draft is gone. Flushed between and
     * after: Hibernate runs inserts before deletes, and the import that follows may reuse a key.
     */
    @Override
    public List<String> removeAll(String orgKey) {
        List<String> keys = List.copyOf(drafts(orgKey).keySet());
        keys.forEach(key -> deleteModule.execute(orgKey, key));
        repository.flush();
        release.release(orgKey);
        repository.flush();
        return keys;
    }

    @Override
    public Map<String, String> fingerprints(String orgKey, Set<String> keys) {
        Map<String, String> fingerprints = new LinkedHashMap<>();
        drafts(orgKey).forEach((key, module) -> {
            if (keys.contains(key)) {
                fingerprints.put(key, ModuleReleaseParticipant.fingerprint(module, null, null));
            }
        });
        return fingerprints;
    }

    private Map<String, ModuleDefinition> drafts(String orgKey) {
        Map<String, ModuleDefinition> byKey = new LinkedHashMap<>();
        repository.findByOrgKeyAndChannel(orgKey, Channel.DRAFT).forEach(module -> byKey.put(module.getKey(), module));
        return byKey;
    }

    private List<String> keys(byte[] yaml) throws IOException {
        ModuleYamlDocument document = yamlMapper.readValue(yaml, ModuleYamlDocument.class);
        return document == null ? List.of() : document.moduleDefinitions().stream()
                .filter(Objects::nonNull)
                .map(ModuleDefinitionInput::getKey)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }
}
