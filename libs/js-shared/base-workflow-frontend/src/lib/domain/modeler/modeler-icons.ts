import { WorkflowElementKind } from './workflow-graph';

/**
 * Where the modeler's symbols come from: the five SVGs in
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
};

/** The symbol one kind is drawn with. */
export function modelerIconUrl(kind: WorkflowElementKind): string {
  return `${MODELER_ASSET_FOLDER}/${ICON_FILE_NAMES[kind]}`;
}
