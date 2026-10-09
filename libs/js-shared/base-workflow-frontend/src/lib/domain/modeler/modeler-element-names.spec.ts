import { describe, expect, it } from 'vitest';
import { EventDirection } from '../definition/workflow';
import englishBundle from '../../../assets/i18n/base_workflow/en.json';
import { EventMarker, modelerElementNameKey, modelerEventMarkerNameKey } from './modeler-element-names';
import { WorkflowElementKind } from './workflow-graph';

describe('modelerElementNameKey', () => {
  /**
   * The entity's own `_self` key, which is the point: a node kind and a routable aggregate are the same
   * thing seen twice, so the legend cannot come to call a role something other than the Roles screen does.
   */
  it('names each kind through the entity key already translated for its screens', () => {
    const kinds: WorkflowElementKind[] = ['role', 'artifact', 'task', 'tool', 'workflow'];

    expect(kinds.map((kind) => modelerElementNameKey(kind))).toEqual([
      'base_workflow.workflow_role_definition._self',
      'base_workflow.artifact_definition._self',
      'base_workflow.task_definition._self',
      'base_workflow.tool_definition._self',
      'base_workflow.workflow._self',
    ]);
  });

  // The end event is no entity at all, so the two events are named by the modeler's own pair of labels.
  it('names the start and end events through the modeler labels', () => {
    expect(modelerElementNameKey('start')).toBe('base_workflow.workflow.modeler.start');
    expect(modelerElementNameKey('end')).toBe('base_workflow.workflow.modeler.end');
  });

  it('names an intermediate event by its direction', () => {
    expect(modelerElementNameKey('event', EventDirection.THROW)).toBe('base_workflow.workflow.modeler.throw_event');
    expect(modelerElementNameKey('event', EventDirection.CATCH)).toBe('base_workflow.workflow.modeler.catch_event');
    expect(modelerElementNameKey('event')).toBe('base_workflow.workflow.modeler.event');
  });

  // The symbols an event has besides its direction, named by the modeler's own labels too.
  it('names the timer and the two boundary rings through the modeler labels', () => {
    expect(modelerEventMarkerNameKey('timer')).toBe('base_workflow.workflow.modeler.timer_event');
    expect(modelerEventMarkerNameKey('boundary')).toBe('base_workflow.workflow.modeler.boundary_event');
    expect(modelerEventMarkerNameKey('non_interrupting_boundary')).toBe('base_workflow.workflow.modeler.non_interrupting_boundary_event');
  });

  // The bundle spec keeps the five locales in step; this keeps the keys named here in the bundle.
  it('names every marker by a key the bundle has', () => {
    const modeler = (englishBundle as { workflow: { modeler: Record<string, unknown> } }).workflow.modeler;
    const markers: EventMarker[] = ['timer', 'boundary', 'non_interrupting_boundary'];

    markers.forEach((marker) => expect(modeler[modelerEventMarkerNameKey(marker).split('.').pop() as string]).toEqual(expect.any(String)));
  });
});
