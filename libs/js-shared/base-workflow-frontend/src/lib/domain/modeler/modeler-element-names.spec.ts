import { describe, expect, it } from 'vitest';
import { modelerElementNameKey } from './modeler-element-names';
import { WorkflowElementKind } from './workflow-graph';

describe('modelerElementNameKey', () => {
  /**
   * The entity's own `_self` key, which is the point: a node kind and a routable aggregate are the same
   * thing seen twice, so the legend cannot come to call a role something other than the Roles screen does.
   */
  it('names each kind through the entity key already translated for its screens', () => {
    const kinds: WorkflowElementKind[] = ['role', 'artifact', 'task', 'tool', 'workflow'];

    expect(kinds.map(modelerElementNameKey)).toEqual([
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
});
