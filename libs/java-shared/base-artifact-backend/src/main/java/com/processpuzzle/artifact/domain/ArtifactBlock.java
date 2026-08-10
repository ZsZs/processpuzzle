package com.processpuzzle.artifact.domain;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.Serializable;
import java.util.Map;

/**
 * One entry in an artifact's flat block list. Fields relevant to the other {@link BlockKind}
 * are left null — mirrors {@code ArtifactBlockInput} in base-artifact-api.yaml exactly, which
 * keeps {@code ArtifactMapper} a straight field-for-field copy.
 *
 * <p>{@code content} is kept as a raw {@link JsonNode} rather than given a Java shape: it is a
 * ProseMirror/Tiptap document, opaque to the backend except for the one narrow scan
 * {@link com.processpuzzle.artifact.usecase.service.ArtifactReferentialIntegrityChecker}
 * performs for {@code widgetEmbed} nodes. A rigid POJO would break on every Tiptap extension
 * upgrade; walking a tree does not.
 *
 * @param editable TEXT only — true (default) = Tiptap-editable, false = static/read-only
 * @param content  TEXT only — opaque Tiptap JSON document
 * @param placement WIDGET only
 * @param type     WIDGET only — widget registry key, shared key space with base-app's WidgetRef.type
 * @param props    WIDGET only — static config; container widgets carry {@code childIds} here,
 *                 not as a structural field, so this record never nests itself
 * @param inputBindings  WIDGET only — widget prop name -> this artifact's inputPort name
 * @param outputBindings WIDGET only — widget event name -> this artifact's outputPort name
 */
public record ArtifactBlock(
        String id,
        BlockKind kind,
        Boolean editable,
        JsonNode content,
        WidgetPlacement placement,
        String type,
        Map<String, Object> props,
        Map<String, String> inputBindings,
        Map<String, String> outputBindings) implements Serializable {

    private static final long serialVersionUID = 1L;

    public ArtifactBlock {
        props = props == null ? Map.of() : Map.copyOf(props);
        inputBindings = inputBindings == null ? Map.of() : Map.copyOf(inputBindings);
        outputBindings = outputBindings == null ? Map.of() : Map.copyOf(outputBindings);
    }

    public boolean isWidget() {
        return kind == BlockKind.WIDGET;
    }

    public boolean isReferenced() {
        return isWidget() && placement == WidgetPlacement.REFERENCED;
    }
}
