package com.processpuzzle.app.domain;

import java.util.List;

/**
 * A shell region, as persisted inside {@link AppGraph}. Per-type configuration is modelled as
 * sibling fields rather than a type hierarchy, mirroring the contract: {@code navItems} applies
 * to {@code sidenav}, {@code widgets} to {@code header} and {@code footer}, and {@code content}
 * uses neither — it is populated per route from an {@link AppRoute}.
 *
 * <p>{@code type} is a plain string for the same reason as in {@link Theme} — see that class.
 *
 * @param type {@code header}, {@code sidenav}, {@code content} or {@code footer}
 * @param navItems sidenav only — the navigation tree
 * @param widgets header / footer only — static region content
 */
public record Region(
        String type,
        List<NavNode> navItems,
        List<Widget> widgets) {

    public Region {
        navItems = navItems == null ? List.of() : List.copyOf(navItems);
        widgets = widgets == null ? List.of() : List.copyOf(widgets);
    }

    /** Returns a copy of this region with {@code navItems} replaced — used by role filtering. */
    public Region withNavItems(List<NavNode> replacement) {
        return new Region(type, replacement, widgets);
    }
}
