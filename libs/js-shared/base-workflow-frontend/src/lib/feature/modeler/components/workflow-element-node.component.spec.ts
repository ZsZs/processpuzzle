import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Node, provideNgDiagram } from 'ng-diagram';
import { beforeEach, describe, expect, it } from 'vitest';
import { WORKFLOW_NODE_TYPE, WorkflowNodeData } from '../../../domain/modeler/workflow-graph';
import { WorkflowElementNodeComponent } from './workflow-element-node.component';

function nodeOf(data: WorkflowNodeData): Node<WorkflowNodeData> {
  return { id: 'role:clerk', type: WORKFLOW_NODE_TYPE, position: { x: 0, y: 0 }, data };
}

describe('WorkflowElementNodeComponent', () => {
  let fixture: ComponentFixture<WorkflowElementNodeComponent>;

  beforeEach(async () => {
    // The four ports and the selected-state host directive resolve ng-diagram's component-scoped services,
    // which in the application the canvas provides.
    await TestBed.configureTestingModule({ imports: [WorkflowElementNodeComponent], providers: [provideNgDiagram()] }).compileComponents();
    fixture = TestBed.createComponent(WorkflowElementNodeComponent);
  });

  function render(data: WorkflowNodeData): HTMLElement {
    fixture.componentRef.setInput('node', nodeOf(data));
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>('.element') as HTMLElement;
  }

  it('draws the element as its name and its description', () => {
    const card = render({ kind: 'role', label: 'Order Clerk', description: 'Enters and verifies orders.' });

    expect(card.querySelector('.element__label')?.textContent?.trim()).toBe('Order Clerk');
    expect(card.querySelector('.element__description')?.textContent?.trim()).toBe('Enters and verifies orders.');
  });

  it('omits the description line for an element that has none', () => {
    const card = render({ kind: 'role', label: 'Order Clerk' });

    expect(card.querySelector('.element__description')).toBeNull();
  });

  /**
   * One template for all five kinds, which is the whole reason this component is shared: what distinguishes
   * a role from an artifact on screen is its symbol, so the kind picks an icon and nothing else.
   */
  it('picks the symbol from the kind', () => {
    expect(render({ kind: 'role', label: 'Order Clerk' }).querySelector('img')?.getAttribute('src')).toBe('assets/modeler/Role.svg');
    expect(render({ kind: 'artifact', label: 'Order Entity' }).querySelector('img')?.getAttribute('src')).toBe('assets/modeler/Artifact.svg');
    expect(render({ kind: 'task', label: 'Review Order' }).querySelector('img')?.getAttribute('src')).toBe('assets/modeler/Task.svg');
  });

  it('marks the kind on the card, so a test and a stylesheet can both find it', () => {
    expect(render({ kind: 'artifact', label: 'Order Entity' }).dataset['testid']).toBe('workflow-node-artifact');
  });

  // The element the diagram was opened from. Unmarked unless the converter said so — the whole
  // organisation is on screen either way.
  it('rings only the highlighted element', () => {
    expect(render({ kind: 'role', label: 'Order Clerk', highlighted: true }).dataset['highlighted']).toBe('true');
    expect(render({ kind: 'role', label: 'Order Manager' }).dataset['highlighted']).toBeUndefined();
  });

  it('marks a reference the catalog does not resolve', () => {
    expect(render({ kind: 'artifact', label: 'deleted-artifact', unresolved: true }).dataset['unresolved']).toBe('true');
    expect(render({ kind: 'artifact', label: 'Order Entity' }).dataset['unresolved']).toBeUndefined();
  });

  /**
   * Four ports, so an edge can anchor sensibly whichever way the layout put the two nodes. Nothing
   * persists an anchor here; the ports are what let ng-diagram pick one.
   */
  it('offers an anchor on each side', () => {
    render({ kind: 'role', label: 'Order Clerk' });

    expect(Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('ng-diagram-port')).length).toBe(4);
  });
});
