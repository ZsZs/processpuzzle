package com.processpuzzle.app.domain;

/**
 * How the shell arranges its regions, as persisted inside {@link AppGraph}. Which regions
 * exist is decided by {@link Region}, not here.
 *
 * <p>{@code preset} and {@code sidenavMode} are plain strings for the same reason as in
 * {@link Theme} — see that class.
 *
 * @param preset coarse arrangement, e.g. {@code sidenav-left}
 * @param sidenavMode Angular Material sidenav mode: {@code side}, {@code over} or {@code push}
 * @param sidenavCollapsible whether the user may collapse the sidenav
 * @param sidenavOpenByDefault whether the sidenav starts open
 * @param contentMaxWidth CSS length capping the content region's width; {@code null} means full width
 */
public record Layout(
        String preset,
        String sidenavMode,
        Boolean sidenavCollapsible,
        Boolean sidenavOpenByDefault,
        String contentMaxWidth) {
}
