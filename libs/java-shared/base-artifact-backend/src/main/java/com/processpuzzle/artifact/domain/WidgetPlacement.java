package com.processpuzzle.artifact.domain;

/**
 * WIDGET blocks only. Same enum and meaning as shared-api.yaml's canonical
 * {@code WidgetRef.placement}. STANDALONE renders at this block's own position in the
 * artifact's flat block list. REFERENCED means it renders only where something else points
 * at its id — a container widget's {@code props.childIds}, or a Tiptap {@code widgetEmbed}
 * node — see {@link com.processpuzzle.artifact.usecase.service.ArtifactReferentialIntegrityChecker}.
 */
public enum WidgetPlacement {
    STANDALONE,
    REFERENCED
}
