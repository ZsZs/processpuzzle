package com.processpuzzle.app.domain;

import java.util.List;

/**
 * One navigable page, as persisted inside {@link AppGraph}. Named {@code AppPage} rather than
 * {@code Page} to avoid a clash with {@code org.springframework.data.domain.Page}, which the
 * mapper needs for paginated summaries.
 *
 * @param id unique within the app; used verbatim as the route path segment
 * @param title default page title, in the organization's default language
 * @param translocoId translation key preferred over {@code title} by the frontend
 * @param widgets top-level widgets rendered in the content region, in declaration order
 */
public record AppPage(
        String id,
        String title,
        String translocoId,
        List<Widget> widgets) {

    public AppPage {
        widgets = widgets == null ? List.of() : List.copyOf(widgets);
    }
}
