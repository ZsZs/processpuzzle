import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { WorkflowNodeData } from '../../../domain/modeler/workflow-graph';
import { WorkflowSelectionService } from './workflow-selection.service';

describe('WorkflowSelectionService', () => {
  const task: WorkflowNodeData = { kind: 'task', elementId: 'review-order', label: 'Review Order' };
  const role: WorkflowNodeData = { kind: 'role', elementId: 'clerk', label: 'Order Clerk' };

  let service: WorkflowSelectionService;

  beforeEach(() => {
    service = TestBed.configureTestingModule({}).inject(WorkflowSelectionService);
  });

  it('starts with nothing selected', () => {
    expect(service.selectedElement()).toBeUndefined();
    expect(service.selectedRelation()).toBeUndefined();
    expect(service.selectedElementIsLane()).toBe(false);
  });

  it('holds the selected element', () => {
    service.selectElement(task);

    expect(service.selectedElement()).toBe(task);
  });

  /**
   * A lane's data *is* a role's data, so nothing in it says which of the two was clicked — the flag is what
   * lets the panel say "Lane" rather than "Role" for a band.
   */
  it('remembers that the selection was a lane rather than a card', () => {
    service.selectElement(role, true);

    expect(service.selectedElementIsLane()).toBe(true);
  });

  it('holds the selected relation', () => {
    service.selectRelation({ relation: 'sequence' });

    expect(service.selectedRelation()).toEqual({ relation: 'sequence' });
  });

  // The two panels are alternatives, so one selection has to clear the other or both would render at once.
  it('clears the element when a relation is selected', () => {
    service.selectElement(task);
    service.selectRelation({ relation: 'input' });

    expect(service.selectedElement()).toBeUndefined();
  });

  it('clears the relation when an element is selected', () => {
    service.selectRelation({ relation: 'input' });
    service.selectElement(task);

    expect(service.selectedRelation()).toBeUndefined();
  });

  // Selecting a card after a lane must not leave the lane flag standing.
  it('drops the lane flag when a card is selected next', () => {
    service.selectElement(role, true);
    service.selectElement(task);

    expect(service.selectedElementIsLane()).toBe(false);
  });

  it('drops the lane flag when a relation is selected next', () => {
    service.selectElement(role, true);
    service.selectRelation({ relation: 'tool' });

    expect(service.selectedElementIsLane()).toBe(false);
  });

  it('clears everything', () => {
    service.selectElement(role, true);
    service.clear();

    expect(service.selectedElement()).toBeUndefined();
    expect(service.selectedRelation()).toBeUndefined();
    expect(service.selectedElementIsLane()).toBe(false);
  });
});
