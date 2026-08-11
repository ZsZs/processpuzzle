package com.processpuzzle.app.domain;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

/**
 * Where a {@link Widget} renders. Same enum and meaning as shared-api.yaml's canonical
 * {@code WidgetRef.placement}, and as base-document's block-level equivalent.
 *
 * <p>STANDALONE renders at the widget's own position in the enclosing flat {@code widgets}
 * list. REFERENCED means it does not render there — only where something else points at its
 * id, i.e. a container widget's {@code props.childIds}. This is what replaces nesting: a tab
 * group and its tabs are siblings in one flat list, related by id.
 */
public enum WidgetPlacement {

    /**
     * Also the fallback for a persisted value this release does not know — see
     * {@link AppGraphConverter}. {@link Theme} keeps its enum-valued fields as plain strings for
     * that same reason; this one stays a real enum because validation and rendering branch on it,
     * and because a two-constant structural flag is not the open-ended vocabulary a theme name is.
     */
    @JsonEnumDefaultValue
    STANDALONE,

    REFERENCED
}
