package com.processpuzzle.widget.domain;

/**
 * Publication state of a {@link WidgetDefinition}. Derived, never stored: a definition is PUBLISHED
 * exactly when {@code publishedVersion} equals {@code version}. Same rule as base-app's
 * AppDefinition, and for the same reason — a stored copy would be a second source of truth that can
 * disagree with the counters.
 */
public enum WidgetDefinitionStatus {
    DRAFT,
    PUBLISHED
}
