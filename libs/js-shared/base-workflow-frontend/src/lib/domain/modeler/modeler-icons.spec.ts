import { describe, expect, it } from 'vitest';
import { EventDirection } from '../definition/workflow';
import { modelerIconUrl } from './modeler-icons';
import { WorkflowElementKind } from './workflow-graph';

describe('modelerIconUrl', () => {
  const kinds: WorkflowElementKind[] = ['role', 'artifact', 'task', 'tool', 'workflow'];
  const eventKinds: WorkflowElementKind[] = ['start', 'end'];

  /**
   * The folder a consuming application has to copy the library's `src/assets/modeler` into — the same
   * convention `assets/i18n/base_workflow` follows. A relative URL, so it resolves against the document
   * base href of whatever application hosts the diagram.
   */
  it('resolves every kind to a symbol in the copied asset folder', () => {
    expect(kinds.map((kind) => modelerIconUrl(kind))).toEqual([
      'assets/modeler/Role.svg',
      'assets/modeler/Artifact.svg',
      'assets/modeler/Task.svg',
      'assets/modeler/Tool.svg',
      'assets/modeler/Workflow.svg',
    ]);
  });

  it('gives each kind a symbol of its own', () => {
    expect(new Set(kinds.map((kind) => modelerIconUrl(kind))).size).toBe(kinds.length);
  });

  // The circle's border tells a start from an end, not the artwork.
  it('draws both events with the one event symbol', () => {
    expect(eventKinds.map((kind) => modelerIconUrl(kind))).toEqual(['assets/modeler/Event.svg', 'assets/modeler/Event.svg']);
  });

  // BPMN does tell an intermediate throw from a catch by the marker: filled against outlined.
  it('draws an intermediate event filled when thrown and outlined when caught', () => {
    expect(modelerIconUrl('event', EventDirection.THROW)).toBe('assets/modeler/EventThrow.svg');
    expect(modelerIconUrl('event', EventDirection.CATCH)).toBe('assets/modeler/EventCatch.svg');
  });

  it('falls back to the plain event symbol for an event without a direction yet', () => {
    expect(modelerIconUrl('event')).toBe('assets/modeler/Event.svg');
  });

  it('ignores a direction on any other kind', () => {
    expect(modelerIconUrl('start', EventDirection.THROW)).toBe('assets/modeler/Event.svg');
  });
});
