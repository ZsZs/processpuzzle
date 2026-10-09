import {
  ARTIFACT_DEFINITION_I18N_SCOPE,
  TASK_DEFINITION_I18N_SCOPE,
  TOOL_DEFINITION_I18N_SCOPE,
  WORKFLOW_I18N_SCOPE,
  WORKFLOW_ROLE_DEFINITION_I18N_SCOPE,
} from '../../base-workflow.i18n';
import { EventDirection } from '../definition/workflow';
import { WorkflowElementKind } from './workflow-graph';

/**
 * What each element kind is *called*, as a transloco key.
 *
 * The entity's own `_self` key rather than a label of the modeler's own: a node kind and a routable
 * aggregate are the same thing seen twice, so `role` is whatever the Roles list and the Role form already
 * call it — in all five languages, already translated. A `base_workflow.modeler.*` block beside them would
 * be the same five words maintained twice, free to drift.
 */
const ELEMENT_I18N_SCOPES: Record<Exclude<WorkflowElementKind, EventKind>, string> = {
  role: WORKFLOW_ROLE_DEFINITION_I18N_SCOPE,
  artifact: ARTIFACT_DEFINITION_I18N_SCOPE,
  task: TASK_DEFINITION_I18N_SCOPE,
  tool: TOOL_DEFINITION_I18N_SCOPE,
  workflow: WORKFLOW_I18N_SCOPE,
};

/**
 * The two events are the exception, named by the Workflow Modeler's own labels. The end event is no entity
 * at all, and a start event's entity name — *Start Event* — beside an *End* that is not one would read
 * oddly; the legend names them as the pair BPMN makes of them. An intermediate event is named by its
 * direction — a throw and a catch are two symbols, and the legend explains both.
 */
type EventKind = 'start' | 'end' | 'event';
const EVENT_NAME_KEYS: Record<EventKind, string> = {
  start: `${WORKFLOW_I18N_SCOPE}.modeler.start`,
  end: `${WORKFLOW_I18N_SCOPE}.modeler.end`,
  event: `${WORKFLOW_I18N_SCOPE}.modeler.event`,
};
const DIRECTION_NAME_KEYS: Record<EventDirection, string> = {
  [EventDirection.THROW]: `${WORKFLOW_I18N_SCOPE}.modeler.throw_event`,
  [EventDirection.CATCH]: `${WORKFLOW_I18N_SCOPE}.modeler.catch_event`,
};

/**
 * The key naming one kind — `base_workflow.workflow_role_definition._self` and its siblings; for an
 * intermediate event, the one its direction picks.
 */
export function modelerElementNameKey(kind: WorkflowElementKind, direction?: EventDirection): string {
  if (kind === 'event' && direction) return DIRECTION_NAME_KEYS[direction];
  return kind === 'start' || kind === 'end' || kind === 'event' ? EVENT_NAME_KEYS[kind] : `${ELEMENT_I18N_SCOPES[kind]}._self`;
}

/**
 * The event symbols that are not a direction: a timer's clock, and the two rings a boundary event is drawn
 * with — solid when firing cancels its task, dashed when the task runs on. Explained by the legend beside
 * the throw and the catch, and named by the modeler's own labels for the same reason they are.
 */
export type EventMarker = 'timer' | 'boundary' | 'non_interrupting_boundary';
const MARKER_NAME_KEYS: Record<EventMarker, string> = {
  timer: `${WORKFLOW_I18N_SCOPE}.modeler.timer_event`,
  boundary: `${WORKFLOW_I18N_SCOPE}.modeler.boundary_event`,
  non_interrupting_boundary: `${WORKFLOW_I18N_SCOPE}.modeler.non_interrupting_boundary_event`,
};

/** The key naming one of the {@link EventMarker} symbols. */
export function modelerEventMarkerNameKey(marker: EventMarker): string {
  return MARKER_NAME_KEYS[marker];
}
