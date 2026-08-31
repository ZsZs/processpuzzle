import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { beforeEach, describe, expect, it } from 'vitest';
import { laneNodeId, WORKFLOW_LANE_TYPE, WorkflowLaneNode } from '../../../domain/modeler/workflow-graph';
import { WorkflowLaneNodeComponent } from './workflow-lane-node.component';

function lane(overrides: Partial<WorkflowLaneNode['data']> = {}): WorkflowLaneNode {
  return {
    id: laneNodeId('clerk'),
    type: WORKFLOW_LANE_TYPE,
    isGroup: true,
    highlighted: false,
    position: { x: 0, y: 0 },
    size: { width: 800, height: 108 },
    autoSize: false,
    data: { kind: 'role', label: 'Order Clerk', ...overrides },
  };
}

describe('WorkflowLaneNodeComponent', () => {
  let fixture: ComponentFixture<WorkflowLaneNodeComponent>;

  beforeEach(() => {
    fixture = TestBed.createComponent(WorkflowLaneNodeComponent);
  });

  function render(node: WorkflowLaneNode = lane()): HTMLElement {
    fixture.componentRef.setInput('node', node);
    fixture.detectChanges();
    return fixture.debugElement.query(By.css('[data-testid="workflow-lane"]')).nativeElement;
  }

  it('names the lane after the role that performs in it', () => {
    expect(render().textContent).toContain('Order Clerk');
  });

  it('draws the role symbol, whatever the lane holds', () => {
    expect(render().querySelector('img')?.getAttribute('src')).toBe('assets/modeler/Role.svg');
  });

  /**
   * The one thing a lane template must not do. Its tasks are ng-diagram nodes of their own, positioned by
   * the layout to land inside this box — anything rendered in the body would be painted under them.
   */
  it('leaves the band’s body empty, because its tasks are drawn over it', () => {
    const body = render().querySelector('.lane__header')?.nextElementSibling;

    expect(body).toBeNull();
  });

  // Same dashed-red vocabulary the element template uses: a reference the catalog does not resolve is a
  // fact about the model worth seeing, not something to hide.
  it('marks a lane whose role the catalog does not hold', () => {
    expect(render(lane({ unresolved: true })).getAttribute('data-unresolved')).toBe('true');
  });

  it('leaves a resolved lane unmarked', () => {
    expect(render().getAttribute('data-unresolved')).toBeNull();
  });

  it('leaves the box to the layout, stating no size of its own', () => {
    render();

    expect(fixture.nativeElement.style.width).toBe('');
    expect(fixture.nativeElement.style.height).toBe('');
  });
});
