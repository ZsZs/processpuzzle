import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Node, provideNgDiagram } from 'ng-diagram';
import { beforeEach, describe, expect, it } from 'vitest';
import { STATE_NODE_TYPE, StateNodeData } from '../../../domain/modeler/graph/state-machine-graph';
import { State } from '../../../domain/state-machine-definition';
import { StateNodeComponent } from './state-node.component';

/**
 * What a reader of the diagram can tell about a state without clicking it. The three shapes are UML's, so
 * the assertions are about *which* shape a state gets — the geometry itself is CSS and is not testable
 * here — and about the name staying legible on the two that are drawn as discs.
 */
describe('StateNodeComponent', () => {
  let fixture: ComponentFixture<StateNodeComponent>;

  const nodeOf = (state: State, initial: boolean): Node<StateNodeData> => ({
    id: state.key,
    type: STATE_NODE_TYPE,
    position: { x: 0, y: 0 },
    data: { state, label: state.name || state.key, initial },
  });

  const render = async (state: State, initial = false) => {
    fixture.componentRef.setInput('node', nodeOf(state, initial));
    fixture.detectChanges();
    await fixture.whenStable();
  };

  const shape = () => (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>('[data-testid^="state-node-"]');

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StateNodeComponent],
      // The ports and the selected-state directive resolve ng-diagram's component-scoped services, which
      // the canvas provides in the application.
      providers: [provideNgDiagram()],
    }).compileComponents();

    fixture = TestBed.createComponent(StateNodeComponent);
  });

  it('draws the state the machine starts in as the initial disc', async () => {
    await render(new State({ key: 'DRAFT', name: 'Draft' }), true);

    expect(shape()?.dataset['testid']).toBe('state-node-start');
    expect(shape()?.textContent?.trim()).toBe('Draft');
  });

  it('draws a terminal state as the final disc', async () => {
    await render(new State({ key: 'CLOSED', name: 'Closed', terminal: true }));

    expect(shape()?.dataset['testid']).toBe('state-node-end');
    expect(shape()?.textContent?.trim()).toBe('Closed');
  });

  it('draws every other state as a labelled box', async () => {
    await render(new State({ key: 'REVIEW', name: 'Review', description: 'Waiting for an approver' }));

    expect(shape()?.dataset['testid']).toBe('state-node-state');
    expect(shape()?.querySelector('.title')?.textContent?.trim()).toBe('Review');
    expect(shape()?.querySelector('.description')?.textContent?.trim()).toBe('Waiting for an approver');
  });

  /**
   * A disc has no room for a description without ceasing to be a disc, so the shape carries it as a
   * tooltip and the properties panel is where it is read.
   */
  it('keeps a disc a disc, showing its description as a tooltip instead', async () => {
    await render(new State({ key: 'CLOSED', name: 'Closed', terminal: true, description: 'Nothing follows' }));

    expect(shape()?.querySelector('.description')).toBeNull();
    expect(shape()?.querySelector<HTMLElement>('.disc-label')?.title).toBe('Nothing follows');
  });

  it('dims a locked state whichever shape it has', async () => {
    await render(new State({ key: 'DRAFT', name: 'Draft', locked: true }), true);
    expect(shape()?.classList.contains('locked')).toBe(true);

    await render(new State({ key: 'REVIEW', name: 'Review', locked: true }));
    expect(shape()?.classList.contains('locked')).toBe(true);
  });
});
