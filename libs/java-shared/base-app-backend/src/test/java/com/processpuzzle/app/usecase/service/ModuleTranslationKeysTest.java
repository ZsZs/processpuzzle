package com.processpuzzle.app.usecase.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.processpuzzle.app.AppTestFixtures;
import com.processpuzzle.app.domain.AppRoute;
import com.processpuzzle.app.domain.ModuleDefinition;
import com.processpuzzle.core.definition.Channel;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModuleTranslationKeysTest {

    private static ModuleDefinition module(String scope, String translocoId, AppRoute... routes) {
        ModuleDefinition module = new ModuleDefinition("org", "order-admin", "Order administration", Channel.DRAFT);
        module.setTranslocoScope(scope);
        module.setTranslocoId(translocoId);
        module.setRoutes(List.of(routes));
        return module;
    }

    private static AppRoute route(String path, String title, String translocoId) {
        return new AppRoute(path, title, translocoId, null, List.of(), AppTestFixtures.appRoute(path, title).target());
    }

    @Test
    void anElementWithoutATranslocoIdGetsADerivedKey() {
        var keys = ModuleTranslationKeys.of(module(null, null, route("orders/:id", "Order", null)));

        assertThat(keys).containsExactly(
                java.util.Map.entry("module.name", "Order administration"),
                java.util.Map.entry("route.orders/:id", "Order"));
    }

    @Test
    void aTranslocoIdInsideTheScopeIsKeyedRelativeToIt() {
        var keys = ModuleTranslationKeys.of(module("order_admin", "order_admin.module.name",
                route("orders", "All orders", "order_admin.route.orders")));

        assertThat(keys).containsOnlyKeys("module.name", "route.orders");
    }

    @Test
    void aTranslocoIdOutsideTheScopeIsNotTheModulesToTranslate() {
        var keys = ModuleTranslationKeys.of(module("order_admin", null, route("orders", "Orders", "base_app.menu.orders")));

        assertThat(keys).containsOnlyKeys("module.name");
    }

    @Test
    void theScopeDefaultsToTheModuleKey() {
        assertThat(ModuleTranslationKeys.scopeOf(module(" ", null))).isEqualTo("order-admin");
        assertThat(ModuleTranslationKeys.scopeOf(module("order_admin", null))).isEqualTo("order_admin");
    }

    @Test
    void theFirstElementToClaimAKeyKeepsIt() {
        var keys = ModuleTranslationKeys.of(module(null, null,
                route("a", "First", "order-admin.shared"), route("b", "Second", "order-admin.shared")));

        assertThat(keys).containsEntry("shared", "First");
    }

    @Test
    void anElementWithoutTextIsLeftOut() {
        var keys = ModuleTranslationKeys.of(module(null, null, route("a", " ", null)));

        assertThat(keys).containsOnlyKeys("module.name");
    }
}
