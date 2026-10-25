package com.processpuzzle.app.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AppRolesTest {

    @Test
    void collectsRolesFromRoutesAndNestedNavigationWithoutDuplicates() {
        NavNode child = new NavNode("child", "Child", null, null, "p", List.of("auditor", "editor"), List.of());
        NavNode group = new NavNode("group", "Group", null, null, null, List.of("editor"), List.of(child));
        AppRoute route = new AppRoute("p", "P", null, null, List.of("manager", "editor"), null);
        AppGraph graph = new AppGraph(null, null, List.of(new Region("sidenav", List.of(group), List.of())),
                List.of(route), List.of());

        assertThat(AppRoles.of(graph)).containsExactlyInAnyOrder("manager", "editor", "auditor");
    }

    @Test
    void anEmptyGraphNamesNoRoles() {
        assertThat(AppRoles.of(AppGraph.empty())).isEmpty();
    }
}
