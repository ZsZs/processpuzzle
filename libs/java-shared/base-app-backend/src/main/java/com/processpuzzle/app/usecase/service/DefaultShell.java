package com.processpuzzle.app.usecase.service;

import com.processpuzzle.app.domain.AppGraph;
import com.processpuzzle.app.domain.AppRoute;
import com.processpuzzle.app.domain.NavNode;
import com.processpuzzle.app.domain.Region;
import com.processpuzzle.app.domain.RouteTarget;
import com.processpuzzle.app.domain.Widget;
import com.processpuzzle.app.domain.WidgetPlacement;
import java.util.List;
import java.util.Map;

/**
 * The platform's default application shell, which a new organization starts with when
 * {@code base-app.default-shell} is on (business-starters design §11.4): the customer's own frame, which no starter
 * replaces — a logo, the application's title, the sidenav, a version button in the footer, and a home page that is one
 * {@code module-launcher}, a card for every mounted module that brought one.
 *
 * <p>It names no module and no entity, so it is the same for every organization, and a domain starter adds to it only
 * through {@link ModuleMountMerger}: a mount, a sidenav entry, a launcher card.
 */
public final class DefaultShell {

    public static final String HOME_PATH = "home";
    public static final String LAUNCHER_WIDGET = "module-launcher";

    private DefaultShell() {
    }

    public static AppGraph graph() {
        Region header = new Region("header", List.of(), List.of(
                widget("header-logo", "app-logo"),
                widget("header-title", "app-title"),
                widget("header-spacer", "spacer")));
        Region sidenav = new Region("sidenav", List.of(
                new NavNode("nav-home", "Home", null, "home", HOME_PATH, List.of(), List.of())), List.of());
        Region footer = new Region("footer", List.of(), List.of(widget("footer-version", "version-button")));
        AppRoute home = new AppRoute(HOME_PATH, "Home", null, "home", List.of(),
                RouteTarget.ofWidgets(List.of(widget("home-launcher", LAUNCHER_WIDGET))));
        return new AppGraph(null, null, List.of(header, sidenav, footer), List.of(home), List.of());
    }

    private static Widget widget(String id, String type) {
        return new Widget(id, type, Map.of(), WidgetPlacement.STANDALONE);
    }
}
