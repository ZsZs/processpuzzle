import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { beforeEach, describe, expect, it } from 'vitest';
import { WorkflowNodeData } from '../../../domain/modeler/workflow-graph';
import { WorkflowElementPropertiesPanelComponent } from './workflow-element-properties-panel.component';

describe('WorkflowElementPropertiesPanelComponent', () => {
  const task: WorkflowNodeData = { kind: 'task', elementId: 'review-order', label: 'Review Order', description: 'Check the order for completeness.' };

  let fixture: ComponentFixture<WorkflowElementPropertiesPanelComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WorkflowElementPropertiesPanelComponent],
      providers: [
        // Flat dotted keys: TestTranslocoLoader drops nested objects when no scope is configured.
        provideTranslocoTesting({
          translations: {
            en: {
              'base_workflow.task_definition._self': 'Task',
              'base_workflow.workflow_role_definition._self': 'Role',
              'base_workflow.workflow.modeler.properties.lane': 'Lane',
              'base_workflow.workflow.modeler.properties.name': 'Name',
              'base_workflow.workflow.modeler.properties.description': 'Description',
              'base_workflow.workflow.modeler.properties.id': 'Identifier',
              'base_workflow.workflow.modeler.properties.unresolved': 'This reference does not resolve to a catalog entry.',
              'base_workflow.task_definition.preconditionRuleId': 'Precondition Rule',
              'base_workflow.task_definition.postconditionRuleId': 'Postcondition Rule',
              'base_workflow.task_definition.steps': 'Steps',
            },
          },
        }),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(WorkflowElementPropertiesPanelComponent);
  });

  async function render(element: WorkflowNodeData, isLane = false): Promise<void> {
    fixture.componentRef.setInput('element', element);
    fixture.componentRef.setInput('isLane', isLane);
    fixture.detectChanges();
    await fixture.whenStable();
  }

  function query(testid: string): HTMLElement | null {
    return (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>(`[data-testid="${testid}"]`);
  }

  it('should compile and create component', async () => {
    await render(task);

    expect(fixture.componentInstance).toBeTruthy();
  });

  it('shows what the element is called, and what it is', async () => {
    await render(task);

    expect(query('element-name')?.textContent).toContain('Review Order');
    expect(query('element-kind')?.textContent).toContain('Task');
  });

  /**
   * The entity's own `_self` key rather than a label of the modeler's own: a node kind and a routable
   * aggregate are the same thing seen twice, so a task is called whatever the Tasks list already calls it.
   */
  it('names the kind with the word the entity itself uses', async () => {
    await render({ kind: 'role', elementId: 'clerk', label: 'Order Clerk' });

    expect(query('element-kind')?.textContent).toContain('Role');
  });

  // A lane's data is a role's data, so only the flag can tell the band from the card.
  it('calls a lane a lane rather than a role', async () => {
    await render({ kind: 'role', elementId: 'clerk', label: 'Order Clerk' }, true);

    expect(query('element-kind')?.textContent).toContain('Lane');
  });

  it('shows the id behind the node, without the diagram prefix', async () => {
    await render(task);

    expect(query('element-id')?.textContent).toContain('review-order');
  });

  it('shows the description when there is one', async () => {
    await render(task);

    expect(query('element-description')?.textContent).toContain('Check the order for completeness.');
  });

  it('omits the description row entirely when there is none', async () => {
    await render({ kind: 'task', elementId: 'review-order', label: 'Review Order' });

    expect(query('element-description')).toBeNull();
  });

  it('shows a task’s guards and its steps in order, under the Task form’s own labels', async () => {
    await render({ ...task, task: { preconditionRuleId: 'order-complete', postconditionRuleId: 'order-reviewed', steps: ['Open', 'Check', 'Sign'] } });

    expect(query('element-precondition')?.textContent).toContain('order-complete');
    expect(query('element-postcondition')?.textContent).toContain('order-reviewed');
    expect(Array.from(query('element-steps')?.querySelectorAll('li') ?? []).map((item) => item.textContent?.trim())).toEqual(['Open', 'Check', 'Sign']);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Precondition Rule');
  });

  it('omits the guard and step rows a task does not have', async () => {
    await render({ ...task, task: { steps: [] } });

    expect(query('element-precondition')).toBeNull();
    expect(query('element-postcondition')).toBeNull();
    expect(query('element-steps')).toBeNull();
  });

  /**
   * A dangling id is an ordinary state of this model rather than a fault — `dependsOn` and a task's performer
   * are authored through free TAGS controls — and the card only marks it by its styling. Saying it in words
   * next to the id that did not resolve is usually the whole diagnosis.
   */
  it('says so when the reference resolves to nothing', async () => {
    await render({ kind: 'task', elementId: 'ghost-task', label: 'ghost-task', unresolved: true });

    expect(query('element-unresolved')).not.toBeNull();
    expect(query('element-id')?.textContent).toContain('ghost-task');
  });

  it('says nothing about resolution when the reference is fine', async () => {
    await render(task);

    expect(query('element-unresolved')).toBeNull();
  });

  // The same symbol the node is drawn with, so the panel is visibly about the thing that was clicked.
  it('shows the kind symbol', async () => {
    await render(task);

    expect((fixture.nativeElement as HTMLElement).querySelector('img')?.getAttribute('src')).toContain('Task.svg');
  });
});
