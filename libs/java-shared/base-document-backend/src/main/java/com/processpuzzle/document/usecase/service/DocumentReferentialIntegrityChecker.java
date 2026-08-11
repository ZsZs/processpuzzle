package com.processpuzzle.document.usecase.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.processpuzzle.document.domain.DocumentBlock;
import com.processpuzzle.document.domain.DocumentContent;
import com.processpuzzle.document.domain.DocumentInputPort;
import com.processpuzzle.document.domain.DocumentOutputPort;
import com.processpuzzle.document.domain.DocumentPorts;
import com.processpuzzle.document.usecase.DocumentValidationProblem;
import com.processpuzzle.rule.domain.Severity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks referential integrity of one locale's {@link DocumentContent} against the document's
 * language-invariant {@link DocumentPorts}: unique block ids; every {@code widgetEmbed} node's
 * {@code blockId}, and every WIDGET block's {@code props.childIds} entry, resolving to a declared
 * WIDGET block with placement REFERENCED (error); every {@code inputBindings}/{@code outputBindings}
 * value naming a declared port (error); and a REFERENCED widget block pointed at by nothing at all
 * (warning — a widget can legitimately be declared before it's placed).
 *
 * <p>Ports and content arrive as separate arguments because they live in separate places: ports are
 * invariant and belong to the document, content is per locale. That is also why references never
 * cross a locale boundary — a {@code childIds} entry resolves within the same translation, so two
 * languages may reuse the same block ids without colliding.
 *
 * <p>Widget {@code props} are otherwise not inspected — each widget type owns its own props
 * shape, including how it interprets {@code childIds} beyond "these ids must exist." Tiptap
 * {@code content} is walked generically as a {@link JsonNode} tree rather than deserialized
 * into a rigid shape, for the same reason {@link DocumentBlock} keeps it opaque.
 *
 * <p>Used by {@code ValidateDocument} (every translation, for designer feedback),
 * {@code PublishDocumentTranslation} (one translation, as its gate) and
 * {@code DeleteDocumentBlock} (a single-block subset check before removal). Problems come back
 * without a locale attributed — see {@link DocumentValidationProblem#withLocale}.
 */
@Component
public class DocumentReferentialIntegrityChecker {

    private static final String WIDGET_EMBED_NODE_TYPE = "widgetEmbed";
    private static final String CHILD_IDS_PROP = "childIds";

    public List<DocumentValidationProblem> check(DocumentPorts ports, DocumentContent content) {
        List<DocumentValidationProblem> problems = new ArrayList<>();
        List<DocumentBlock> blocks = content.blocks();

        Map<String, DocumentBlock> byId = indexById(blocks, problems);
        Set<String> declaredInputPorts = names(ports.inputPorts(), DocumentInputPort::name);
        Set<String> declaredOutputPorts = names(ports.outputPorts(), DocumentOutputPort::name);
        Set<String> referencedFrom = new HashSet<>();

        for (int i = 0; i < blocks.size(); i++) {
            DocumentBlock block = blocks.get(i);
            String basePath = "/blocks/" + i;

            if (block.isWidget()) {
                checkBindings(block, basePath, "inputBindings", declaredInputPorts, problems);
                checkBindings(block, basePath, "outputBindings", declaredOutputPorts, problems);
                checkChildIds(block, basePath, byId, referencedFrom, problems);
            }
            if (block.content() != null) {
                scanForWidgetEmbeds(block.content(), basePath + "/content", byId, referencedFrom, problems);
            }
        }

        checkOrphans(blocks, referencedFrom, problems);
        return problems;
    }

    /**
     * Widget blocks present in the source locale but missing from a translation, reported as
     * WARNINGs against the translation.
     *
     * <p>Deliberately not an error, and deliberately only about widgets. Translators legitimately
     * split and merge prose, so demanding an identical block skeleton per locale would fight the
     * job rather than help it — but a widget is functionality, not wording, and one silently
     * absent from a language is worth telling someone about. Compared by block id, which is how
     * corresponding blocks across locales are related when a translation was seeded from the
     * source.
     */
    public List<DocumentValidationProblem> checkWidgetCoverage(DocumentContent sourceContent,
                                                              String sourceLocale,
                                                              DocumentContent translationContent) {
        List<DocumentValidationProblem> problems = new ArrayList<>();
        Set<String> translated = new HashSet<>(translationContent.widgetBlockIds());
        for (String widgetBlockId : sourceContent.widgetBlockIds()) {
            if (!translated.contains(widgetBlockId)) {
                problems.add(new DocumentValidationProblem(
                        "/blocks",
                        "document.validation.widget-missing-from-translation",
                        "Widget block '" + widgetBlockId + "' exists in the source locale '" + sourceLocale
                                + "' but not in this translation.",
                        Severity.WARNING));
            }
        }
        return problems;
    }

    /**
     * The subset relevant to deleting one block: is {@code blockId} pointed at by anything
     * else in this translation? Reuses the same scan as {@link #check}, but only reports
     * references to the block being deleted.
     */
    public List<String> referencesTo(DocumentContent content, String blockId) {
        List<String> referencingBlockIds = new ArrayList<>();
        for (DocumentBlock block : content.blocks()) {
            if (block.id().equals(blockId)) {
                continue;
            }
            if (childIdsOf(block).contains(blockId)) {
                referencingBlockIds.add(block.id());
            } else if (block.content() != null && containsWidgetEmbed(block.content(), blockId)) {
                referencingBlockIds.add(block.id());
            }
        }
        return referencingBlockIds;
    }

    private Map<String, DocumentBlock> indexById(List<DocumentBlock> blocks, List<DocumentValidationProblem> problems) {
        Map<String, DocumentBlock> byId = new HashMap<>();
        for (int i = 0; i < blocks.size(); i++) {
            DocumentBlock block = blocks.get(i);
            if (byId.putIfAbsent(block.id(), block) != null) {
                problems.add(new DocumentValidationProblem(
                        "/blocks/" + i + "/id",
                        "document.validation.duplicate-block-id",
                        "Block id '" + block.id() + "' is used more than once in this translation."));
            }
        }
        return byId;
    }

    private static <T> Set<String> names(List<T> items, java.util.function.Function<T, String> nameOf) {
        Set<String> result = new HashSet<>();
        for (T item : items) {
            result.add(nameOf.apply(item));
        }
        return result;
    }

    private void checkBindings(DocumentBlock block, String basePath, String bindingKind,
                                Set<String> declaredPortNames, List<DocumentValidationProblem> problems) {
        Map<String, String> bindings = "inputBindings".equals(bindingKind)
                ? block.inputBindings() : block.outputBindings();
        bindings.forEach((propOrEvent, portName) -> {
            if (!declaredPortNames.contains(portName)) {
                problems.add(new DocumentValidationProblem(
                        basePath + "/" + bindingKind + "/" + propOrEvent,
                        "document.validation.unknown-port",
                        "'" + portName + "' is not a declared " +
                                ("inputBindings".equals(bindingKind) ? "input" : "output") + " port."));
            }
        });
    }

    @SuppressWarnings("unchecked")
    private List<String> childIdsOf(DocumentBlock block) {
        Object raw = block.props().get(CHILD_IDS_PROP);
        if (raw instanceof List<?> list) {
            List<String> ids = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof String s) {
                    ids.add(s);
                }
            }
            return ids;
        }
        return List.of();
    }

    private void checkChildIds(DocumentBlock block, String basePath, Map<String, DocumentBlock> byId,
                                Set<String> referencedFrom, List<DocumentValidationProblem> problems) {
        List<String> childIds = childIdsOf(block);
        for (String childId : childIds) {
            DocumentBlock target = byId.get(childId);
            if (target == null || !target.isReferenced()) {
                problems.add(new DocumentValidationProblem(
                        basePath + "/props/" + CHILD_IDS_PROP,
                        "document.validation.dangling-child-id",
                        "props.childIds references block '" + childId +
                                "', which is not a REFERENCED WIDGET block in this translation."));
            } else {
                referencedFrom.add(childId);
            }
        }
    }

    private void scanForWidgetEmbeds(JsonNode node, String path, Map<String, DocumentBlock> byId,
                                      Set<String> referencedFrom, List<DocumentValidationProblem> problems) {
        if (node == null || node.isMissingNode()) {
            return;
        }
        if (node.isObject() && WIDGET_EMBED_NODE_TYPE.equals(textOrNull(node.get("type")))) {
            JsonNode attrs = node.get("attrs");
            String blockId = attrs == null ? null : textOrNull(attrs.get("blockId"));
            if (blockId == null) {
                problems.add(new DocumentValidationProblem(
                        path, "document.validation.malformed-widget-embed",
                        "widgetEmbed node has no attrs.blockId."));
            } else {
                DocumentBlock target = byId.get(blockId);
                if (target == null || !target.isReferenced()) {
                    problems.add(new DocumentValidationProblem(
                            path, "document.validation.dangling-widget-embed",
                            "widgetEmbed references block '" + blockId +
                                    "', which is not a REFERENCED WIDGET block in this translation."));
                } else {
                    referencedFrom.add(blockId);
                }
            }
        }
        int i = 0;
        var fields = node.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            scanForWidgetEmbeds(entry.getValue(), path + "/" + entry.getKey(), byId, referencedFrom, problems);
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                scanForWidgetEmbeds(child, path + "/" + i++, byId, referencedFrom, problems);
            }
        }
    }

    private boolean containsWidgetEmbed(JsonNode node, String blockId) {
        if (node == null || node.isMissingNode()) {
            return false;
        }
        if (node.isObject() && WIDGET_EMBED_NODE_TYPE.equals(textOrNull(node.get("type")))) {
            JsonNode attrs = node.get("attrs");
            if (attrs != null && blockId.equals(textOrNull(attrs.get("blockId")))) {
                return true;
            }
        }
        var fields = node.fields();
        while (fields.hasNext()) {
            if (containsWidgetEmbed(fields.next().getValue(), blockId)) {
                return true;
            }
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                if (containsWidgetEmbed(child, blockId)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void checkOrphans(List<DocumentBlock> blocks, Set<String> referencedFrom,
                               List<DocumentValidationProblem> problems) {
        for (int i = 0; i < blocks.size(); i++) {
            DocumentBlock block = blocks.get(i);
            if (block.isReferenced() && !referencedFrom.contains(block.id())) {
                problems.add(new DocumentValidationProblem(
                        "/blocks/" + i,
                        "document.validation.orphaned-widget",
                        "Block '" + block.id() + "' is REFERENCED but nothing points at it yet.",
                        Severity.WARNING));
            }
        }
    }

    private static String textOrNull(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText(null);
    }
}
