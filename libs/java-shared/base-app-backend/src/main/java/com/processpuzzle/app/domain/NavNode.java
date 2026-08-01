package com.processpuzzle.app.domain;

import java.util.List;

/**
 * One entry in the sidenav navigation tree, as persisted inside {@link AppGraph}. Named
 * {@code NavNode} rather than {@code NavItem} to avoid a clash with the generated
 * {@code com.processpuzzle.app.model.NavItem}.
 *
 * <p>A node with {@link #children()} and no {@link #pageId()} is a non-navigable group.
 *
 * @param id unique within the app
 * @param label default label, in the organization's default language
 * @param translocoId translation key preferred over {@code label} by the frontend
 * @param icon Material or FontAwesome icon name
 * @param pageId {@link AppPage#id()} this entry navigates to; {@code null} for group nodes
 * @param roles roles allowed to see this entry; empty means any authenticated member
 * @param children nested entries forming a nav group
 */
public record NavNode(
        String id,
        String label,
        String translocoId,
        String icon,
        String pageId,
        List<String> roles,
        List<NavNode> children) {

    public NavNode {
        roles = roles == null ? List.of() : List.copyOf(roles);
        children = children == null ? List.of() : List.copyOf(children);
    }

    /** Returns a copy of this node with {@code children} replaced — used by role filtering. */
    public NavNode withChildren(List<NavNode> replacement) {
        return new NavNode(id, label, translocoId, icon, pageId, roles, replacement);
    }
}
