package com.processpuzzle.app.domain;

import java.util.List;

/**
 * The entire nested metadata graph of an application — theme, layout, regions and pages — as
 * one immutable value.
 *
 * <p>It is persisted as a single JSON document in one column (see {@code AppGraphConverter}),
 * not normalized into tables. That follows the precedent {@code RuleDefinition} set for its
 * {@code extendsRuleId} tree: keep graph shapes out of the relational model and validate
 * referential integrity in the service layer. Nothing queries inside the graph — the list
 * endpoint returns header-only summaries — so separate columns would buy nothing while costing
 * a recursive {@code @OneToMany} mapping with ordering columns.
 *
 * <p>Bundling all four parts into one record also keeps the converter's target type concrete
 * rather than parameterized, which is what lets Hibernate resolve it at all.
 *
 * @param theme the tenant's look; {@code null} until the designer picks one
 * @param layout region arrangement; {@code null} means the frontend defaults apply
 * @param regions declared shell regions; empty on a freshly provisioned app
 * @param pages the app's pages; empty on a freshly provisioned app
 */
public record AppGraph(
        Theme theme,
        Layout layout,
        List<Region> regions,
        List<AppPage> pages) {

    public AppGraph {
        regions = regions == null ? List.of() : List.copyOf(regions);
        pages = pages == null ? List.of() : List.copyOf(pages);
    }

    /** The graph of a freshly provisioned application: no theme, no layout, no regions, no pages. */
    public static AppGraph empty() {
        return new AppGraph(null, null, List.of(), List.of());
    }

    /** Returns a copy of this graph with {@code regions} replaced — used by role filtering. */
    public AppGraph withRegions(List<Region> replacement) {
        return new AppGraph(theme, layout, replacement, pages);
    }

    /** Finds a page by id, or {@code null} when this graph declares no such page. */
    public AppPage findPage(String pageId) {
        if (pageId == null) {
            return null;
        }
        return pages.stream().filter(page -> pageId.equals(page.id())).findFirst().orElse(null);
    }
}
