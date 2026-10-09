/** One tab of the Workflows section: the child route it opens, its icon and its `design` scope label. */
export interface WorkflowDesignerTab {
  /** Path of the child route, relative to `workflows`. */
  path: string;
  icon: string;
  /** Key in the `design` transloco scope — the scope the `/design` branch registers. */
  label: string;
}

/**
 * The tabs of the Workflows section, in the order they are shown.
 *
 * Seven views of one authoring subject, so they share a page instead of seven sidenav entries. The order
 * is the order a tenant fills the catalog in: the workflow first, because it is what the author came for,
 * then the definitions it composes — roles, tasks, artifacts, tools, and the events its start events and
 * throw/catch events name — and last the runs those produce, which are read-only.
 *
 * Every path is contributed by `BASE_WORKFLOW_ROUTES`, except `event-definition`, which is
 * `BASE_EVENT_ROUTES`' — base-workflow names an event by id and never imports base-event, so this page is
 * where the two meet. Every one of them is
 * `snakeCaseName(entityName)`, which is not a style choice: `BaseFormNavigatorSingletonStore` builds a
 * details URL from the entity name, so a renamed segment silently breaks the Name column and Edit
 * navigation of that branch.
 *
 * Deliberately a list of its own rather than derived from the children's `data.menuTitle`: those keys
 * belong to base-workflow's `base_workflow` scope, while the tab bar translates from this library's
 * `design` scope — the sidenav beside it has the same split. `design.routes.spec.ts` asserts every
 * `path` below is among the `workflows` route's children, which is what keeps the two in step.
 *
 * The file is separate from `design.routes.ts` because that module imports the component that imports
 * this list; declaring it there would close the cycle. Same arrangement as
 * `APPLICATION_DESIGNER_TABS`.
 */
export const WORKFLOW_DESIGNER_TABS: WorkflowDesignerTab[] = [
  { path: 'workflow', icon: 'schema', label: 'design.workflow-definitions' },
  { path: 'workflow-role-definition', icon: 'badge', label: 'design.workflow-roles' },
  { path: 'task-definition', icon: 'assignment', label: 'design.workflow-tasks' },
  { path: 'artifact-definition', icon: 'inventory_2', label: 'design.workflow-artifacts' },
  { path: 'tool-definition', icon: 'build', label: 'design.workflow-tools' },
  { path: 'event-definition', icon: 'bolt', label: 'design.workflow-events' },
  { path: 'workflow-instance', icon: 'play_circle', label: 'design.workflow-instances' },
];
