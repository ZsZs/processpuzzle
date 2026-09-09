import { describe, expect, it } from 'vitest';
import { modelerIconUrl } from './modeler-icons';
import { WorkflowElementKind } from './workflow-graph';

describe('modelerIconUrl', () => {
  const kinds: WorkflowElementKind[] = ['role', 'artifact', 'task', 'tool', 'workflow'];

  /**
   * The folder a consuming application has to copy the library's `src/assets/modeler` into — the same
   * convention `assets/i18n/base_workflow` follows. A relative URL, so it resolves against the document
   * base href of whatever application hosts the diagram.
   */
  it('resolves every kind to a symbol in the copied asset folder', () => {
    expect(kinds.map(modelerIconUrl)).toEqual([
      'assets/modeler/Role.svg',
      'assets/modeler/Artifact.svg',
      'assets/modeler/Task.svg',
      'assets/modeler/Tool.svg',
      'assets/modeler/Workflow.svg',
    ]);
  });

  it('gives each kind a symbol of its own', () => {
    expect(new Set(kinds.map(modelerIconUrl)).size).toBe(kinds.length);
  });
});
