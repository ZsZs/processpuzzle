package com.processpuzzle.document.domain;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.Serializable;
import java.util.Map;

/**
 * One entry in a document's flat block list. Fields relevant to the other {@link BlockKind}
 * are left null — mirrors {@code DocumentBlockInput} in base-document-api.yaml exactly, which
 * keeps {@code DocumentMapper} a straight field-for-field copy.
 *
 * <p>{@code content} is kept as a raw {@link JsonNode} rather than given a Java shape: it is a
 * ProseMirror/Tiptap document, opaque to the backend except for the one narrow scan
 * {@link com.processpuzzle.document.usecase.service.DocumentReferentialIntegrityChecker}
 * performs for {@code widgetEmbed} nodes. A rigid POJO would break on every Tiptap extension
 * upgrade; walking a tree does not.
 *
 * @param editable TEXT only — true (default) = Tiptap-editable, false = static/read-only
 * @param content  TEXT only — opaque Tiptap JSON document
 * @param placement WIDGET only
 * @param type     WIDGET only — widget registry key, shared key space with base-app's WidgetRef.type
 * @param props    WIDGET only — static config; container widgets carry {@code childIds} here,
 *                 not as a structural field, so this record never nests itself
 * @param inputBindings  WIDGET only — widget prop name -> this document's inputPort name
 * @param outputBindings WIDGET only — widget event name -> this document's outputPort name
 */
public record DocumentBlock(
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

    public DocumentBlock {
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
