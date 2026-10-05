package com.processpuzzle.starter.usecase;

import com.processpuzzle.core.definition.DefinitionImportParticipant;
import com.processpuzzle.core.definition.ImportedItem;
import com.processpuzzle.core.definition.ParticipantResult;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A participant over an in-memory store. Each non-blank line of a file is one definition key;
 * a line {@code !reason} makes the file be refused with that reason.
 */
class FakeParticipant implements DefinitionImportParticipant {

    final Map<String, Map<String, String>> store = new LinkedHashMap<>();
    final List<String> calls;
    private final String kind;
    private final int order;

    FakeParticipant(String kind, int order, List<String> calls) {
        this.kind = kind;
        this.order = order;
        this.calls = calls;
    }

    @Override
    public String kind() {
        return kind;
    }

    @Override
    public int order() {
        return order;
    }

    @Override
    public ParticipantResult apply(String orgKey, String fileName, byte[] yaml) {
        calls.add(kind + ":" + fileName);
        Map<String, String> org = store.computeIfAbsent(orgKey, k -> new LinkedHashMap<>());
        List<ImportedItem> items = new ArrayList<>();
        for (String line : new String(yaml, StandardCharsets.UTF_8).split("\n")) {
            String key = line.trim();
            if (key.startsWith("!")) {
                return ParticipantResult.rejected(List.of(key.substring(1)));
            }
            if (!key.isEmpty()) {
                items.add(new ImportedItem(key, org.containsKey(key) ? ImportedItem.Action.UPDATE : ImportedItem.Action.CREATE));
                org.put(key, "{\"key\":\"" + key + "\"}");
            }
        }
        return new ParticipantResult(items, List.of());
    }

    @Override
    public Map<String, String> fingerprints(String orgKey, Set<String> keys) {
        Map<String, String> result = new LinkedHashMap<>();
        store.getOrDefault(orgKey, Map.of()).forEach((key, value) -> {
            if (keys.contains(key)) {
                result.put(key, value);
            }
        });
        return result;
    }
}
