import { Component, input } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Edge, EdgeLabelPosition, NgDiagramBaseEdgeComponent, NgDiagramBaseEdgeLabelComponent, NgDiagramDefaultEdgeLabelComponent } from 'ng-diagram';
import { beforeEach, describe, expect, it } from 'vitest';
import { TRANSITION_EDGE_TYPE, TransitionEdgeData } from '../../../domain/modeler/graph/state-machine-graph';
import { Transition } from '../../../domain/state-machine-definition';
import { EdgeContextMenuService } from '../services/edge-context-menu.service';
import { TransitionEdgeComponent } from './transition-edge.component';

/**
 * Stand-ins for the three ng-diagram components this template composes. None of them can render outside a
 * live diagram: the first two read geometry off the engine — "Library engine not initialized yet" without
 * one — and the label chip injects the base edge it is drawn on. Substituting all three keeps this spec
 * about what the template itself decides: the arrowhead, the label text, and the right-click. How a path
 * is drawn is the library's business and is asserted in the library's own tests.
 */
@Component({ selector: 'ng-diagram-base-edge', standalone: true, template: '<ng-content />' })
class StubBaseEdgeComponent {
  readonly edge = input.required<Edge>();
  readonly targetArrowhead = input<string>();
}

@Component({ selector: 'ng-diagram-base-edge-label', standalone: true, template: '<ng-content />' })
class StubBaseEdgeLabelComponent {
  readonly id = input<string>();
  readonly positionOnEdge = input<EdgeLabelPosition>();
}

@Component({ selector: 'ng-diagram-default-edge-label', standalone: true, template: '<ng-content />' })
class StubDefaultEdgeLabelComponent {}

describe('TransitionEdgeComponent', () => {
  const transition = new Transition({ key: 'confirm', sourceStateKey: 'DRAFT', targetStateKey: 'DELIVERED' });
  const edgeOf = (data?: TransitionEdgeData): Edge<TransitionEdgeData> =>
    ({
      id: 'confirm',
      type: TRANSITION_EDGE_TYPE,
      source: 'DRAFT',
      target: 'DELIVERED',
      data,
    }) as Edge<TransitionEdgeData>;

  let fixture: ComponentFixture<TransitionEdgeComponent>;
  let contextMenu: EdgeContextMenuService;

  const render = (edge = edgeOf({ transition, label: 'confirm' })) => {
    fixture.componentRef.setInput('edge', edge);
    fixture.detectChanges();
  };
  const baseEdge = () => (fixture.nativeElement as HTMLElement).querySelector('ng-diagram-base-edge');
  const label = () => (fixture.nativeElement as HTMLElement).querySelector('ng-diagram-default-edge-label');
  const rightClick = (event = new MouseEvent('contextmenu', { clientX: 120, clientY: 90, cancelable: true, bubbles: true })) => {
    baseEdge()?.dispatchEvent(event);
    return event;
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TransitionEdgeComponent],
      // Component-scoped in the application, where the canvas that hosts the diagram provides it.
      providers: [EdgeContextMenuService],
    })
      .overrideComponent(TransitionEdgeComponent, {
        remove: { imports: [NgDiagramBaseEdgeComponent, NgDiagramBaseEdgeLabelComponent, NgDiagramDefaultEdgeLabelComponent] },
        add: { imports: [StubBaseEdgeComponent, StubBaseEdgeLabelComponent, StubDefaultEdgeLabelComponent] },
      })
      .compileComponents();

    fixture = TestBed.createComponent(TransitionEdgeComponent);
    contextMenu = fixture.debugElement.injector.get(EdgeContextMenuService);
  });

  it('opens the routing menu on its own edge, at the pointer', () => {
    render();

    rightClick();

    expect(contextMenu.target()).toEqual({ edgeId: 'confirm', clientX: 120, clientY: 90 });
  });

  // Otherwise the browser's own menu covers ours.
  it("suppresses the browser's menu", () => {
    render();

    expect(rightClick().defaultPrevented).toBe(true);
  });

  // What the converter wrote there: the transition's name, or its trigger, or its key.
  it('writes the label on the edge', () => {
    render();

    expect(label()?.textContent?.trim()).toBe('confirm');
  });

  // A transition has a direction, and an undirected line does not show it.
  it('points at its target state', () => {
    render();

    expect(fixture.debugElement.children[0].componentInstance.targetArrowhead()).toBe('ng-diagram-arrow');
  });

  /**
   * An edge ng-diagram's linking created carries no `data` at all. `validateConnection` refuses those, so
   * one should not arise — but the label is not where that would be worth finding out.
   */
  it('draws no label when the edge carries no data', () => {
    render(edgeOf(undefined));

    expect(label()).toBeNull();
  });
});
