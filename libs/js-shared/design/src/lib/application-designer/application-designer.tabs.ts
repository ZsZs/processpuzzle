/** One tab of the Application section: the child route it opens, its icon and its `design` scope label. */
export interface ApplicationDesignerTab {
  /** Path of the child route, relative to `application`. */
  path: string;
  icon: string;
  /** Key in the `design` transloco scope — the scope the `/design` branch registers. */
  label: string;
}

/**
 * The tabs of the Application section, in the order they are shown.
 *
 * An application, the modules it mounts and the widget types those place are three views of one
 * authoring subject, so they share a page instead of three sidenav entries. Each tab is a child route of
 * `application`, contributed by the library that owns the entity — `BASE_APP_ROUTES` for the first two,
 * `BASE_WIDGET_ROUTES` for the third.
 *
 * Deliberately a list of its own rather than derived from the children's `data.menuTitle`: those keys
 * belong to this library's scope but are declared in base-app's routes, and deriving from them would
 * entrench that inversion and make the label unusable by any other host. `design.routes.spec.ts` asserts
 * every `path` below is among the `application` route's children, which is what keeps the two in step.
 *
 * The file is separate from `design.routes.ts` because that module imports the component that imports
 * this list; declaring it there would close the cycle.
 */
export const APPLICATION_DESIGNER_TABS: ApplicationDesignerTab[] = [
  { path: 'app-definition', icon: 'web', label: 'design.applications' },
  { path: 'module-definition', icon: 'extension', label: 'design.modules' },
  { path: 'widget-definition', icon: 'widgets', label: 'design.widgets' },
];
