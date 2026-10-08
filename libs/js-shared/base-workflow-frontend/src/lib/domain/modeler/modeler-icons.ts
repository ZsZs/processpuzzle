import { EventDirection } from '../definition/workflow';
import { WorkflowElementKind } from './workflow-graph';

/**
 * Where the modeler's symbols come from: the SVGs in
 * `libs/js-shared/base-workflow-frontend/src/assets/modeler`, published with the package by
 * `ng-package.json`'s `assets` entry.
 *
 * A URL rather than an inlined `<svg>`, and a URL relative to the document base href rather than one this
 * library resolves for itself. Both are the same convention this library's translations already follow: a
 * consuming application copies the folder into its own `assets/modeler` (see the testbed's `project.json`,
 * next to the `assets/i18n/base_workflow` copy) and the browser fetches and caches each file once, however
 * many nodes draw it. An asset copy that was forgotten shows as a broken image — nothing else in the
 * diagram depends on it.
 */
const MODELER_ASSET_FOLDER = 'assets/modeler';

/**
 * File name per kind. Capitalized as the files are: they are authored artwork, and renaming them to match a
 * code convention would only make the next drop of new symbols a rename as well.
 */
const ICON_FILE_NAMES: Record<WorkflowElementKind, string> = {
  role: 'Role.svg',
  artifact: 'Artifact.svg',
  task: 'Task.svg',
  tool: 'Tool.svg',
  workflow: 'Workflow.svg',
  // One symbol for both events: BPMN tells a start from an end by the circle's border, which the node
  // template draws, not by the artwork inside it.
  start: 'Event.svg',
  end: 'Event.svg',
  // An intermediate event without a direction yet — the blank row an `Add` opens on.
  event: 'Event.svg',
};

/**
 * An intermediate event's symbol by direction, which BPMN *does* tell apart by the marker: filled for an
 * event the workflow throws, outlined for one it catches.
 */
const EVENT_ICON_FILE_NAMES: Record<EventDirection, string> = {
  [EventDirection.THROW]: 'EventThrow.svg',
  [EventDirection.CATCH]: 'EventCatch.svg',
};

/**
 * A timer event's symbol — BPMN's clock, whether the timer is intermediate or on a task's boundary. It wins
 * over the direction: a timer catches nothing from the catalog, and the clock is what says what it waits for.
 */
const TIMER_ICON_FILE_NAME = 'EventTimer.svg';

/**
 * The symbol one kind is drawn with — for an intermediate event, the one its direction picks, or the clock
 * when it waits for a timer.
 */
export function modelerIconUrl(kind: WorkflowElementKind, direction?: EventDirection, timer?: boolean): string {
  const fileName = kind === 'event' && timer ? TIMER_ICON_FILE_NAME : kind === 'event' && direction ? EVENT_ICON_FILE_NAMES[direction] : ICON_FILE_NAMES[kind];
  return `${MODELER_ASSET_FOLDER}/${fileName}`;
}
