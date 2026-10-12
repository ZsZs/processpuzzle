import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor } from '@processpuzzle/base-entity';
import { describe, expect, it } from 'vitest';
import de from '../../../assets/i18n/base_workflow/de.json';
import en from '../../../assets/i18n/base_workflow/en.json';
import es from '../../../assets/i18n/base_workflow/es.json';
import fr from '../../../assets/i18n/base_workflow/fr.json';
import hu from '../../../assets/i18n/base_workflow/hu.json';
import { createArtifactDefinitionDescriptor } from './artifact-definition.descriptors';
import { createEventUseDescriptor } from './event-use.descriptors';
import { createRequiredStartArtifactDescriptor } from './required-start-artifact.descriptors';
import { createRoleDefinitionDescriptor } from './role-definition.descriptors';
import { createStartEventDescriptor } from './start-event.descriptors';
import { createStepDefinitionDescriptor } from './step-definition.descriptors';
import { createTaskArtifactStateDescriptor } from './task-artifact-state.descriptors';
import { createTaskDefinitionDescriptor } from './task-definition.descriptors';
import { createToolDefinitionDescriptor } from './tool-definition.descriptors';
import { createToolOperationDescriptor } from './tool-operation.descriptors';
import { createWorkflowTaskAssignmentDescriptor } from './workflow-task-assignment.descriptors';
import { createWorkflowArtifactUseDescriptor, createWorkflowRoleUseDescriptor, createWorkflowToolUseDescriptor } from './workflow-use.descriptors';
import { createWorkflowDescriptor } from './workflow.descriptors';

/**
 * Every field of the designer's workflow forms carries a tooltip, in every language the library ships — the
 * tooltips are what tell the person authoring a workflow what each field expects. The run-time forms
 * (instances, task results) are read-only and deliberately not covered.
 */
describe('base-workflow form tooltips', () => {
  const descriptors: BaseEntityDescriptor[] = [
    createWorkflowDescriptor(),
    createStartEventDescriptor(),
    createRequiredStartArtifactDescriptor(),
    createWorkflowRoleUseDescriptor(),
    createWorkflowArtifactUseDescriptor(),
    createWorkflowToolUseDescriptor(),
    createWorkflowTaskAssignmentDescriptor(),
    createTaskArtifactStateDescriptor(),
    createEventUseDescriptor(),
    createRoleDefinitionDescriptor(),
    createTaskDefinitionDescriptor(),
    createStepDefinitionDescriptor(),
    createArtifactDefinitionDescriptor(),
    createToolDefinitionDescriptor(),
    createToolOperationDescriptor(),
  ];
  const keys = descriptors.flatMap((descriptor) => attributesOf(descriptor.attrDescriptors).map((attr) => attr.tooltipI18nKey()));

  it.each(Object.entries({ en, de, es, fr, hu }))('has a tooltip for every form field in %s', (_lang, translation) => {
    const missing = keys.filter((key) => typeof resolve({ base_workflow: translation }, key) !== 'string');

    expect(keys.length).toBeGreaterThan(0);
    expect(missing).toEqual([]);
  });
});

function attributesOf(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => {
    if (descriptor instanceof FlexboxDescriptor) return attributesOf(descriptor.attrDescriptors);
    return descriptor instanceof BaseEntityAttrDescriptor ? [descriptor] : [];
  });
}

function resolve(translation: object, key: string | undefined): unknown {
  return key?.split('.').reduce<unknown>((node, segment) => (node as Record<string, unknown> | undefined)?.[segment], translation);
}
