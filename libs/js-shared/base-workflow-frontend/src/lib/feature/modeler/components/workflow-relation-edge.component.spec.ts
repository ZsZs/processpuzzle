import { Component, input } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Edge, EdgeLabelPosition, NgDiagramBaseEdgeComponent, NgDiagramBaseEdgeLabelComponent, NgDiagramDefaultEdgeLabelComponent } from 'ng-diagram';
import { beforeEach, describe, expect, it } from 'vitest';
import { WORKFLOW_RELATION_EDGE_TYPE, WorkflowEdgeData, WorkflowRelation } from '../../../domain/modeler/workflow-graph';
import { WorkflowRelationEdgeComponent } from './workflow-relation-edge.component';

/**
 * Stand-ins for the three ng-diagram components this template composes, exactly as base-state's
 * `transition-edge.component.spec.ts` does. None of them can render outside a live diagram: the first two
 * read geometry off the engine and the label chip injects the base edge it is drawn on. Substituting all
 * three keeps this spec about what the template itself decides — the dash pattern, the fading, the
 * arrowhead and the label — and leaves how a path is drawn to the library's own tests.
 */
@Component({ selector: 'ng-diagram-base-edge', standalone: true, template: '<ng-content />' })
class StubBaseEdgeComponent {
  readonly edge = input.required<Edge>();
  readonly targetArrowhead = input<string>();
  readonly strokeDasharray = input<string | undefined>();
  readonly strokeOpacity = input<number | undefined>();
}

@Component({ selector: 'ng-diagram-base-edge-label', standalone: true, template: '<ng-content />' })
class StubBaseEdgeLabelComponent {
  readonly id = input<string>();
  readonly positionOnEdge = input<EdgeLabelPosition>();
}

@Component({ selector: 'ng-diagram-default-edge-label', standalone: true, template: '<ng-content />' })
class StubDefaultEdgeLabelComponent {}

describe('WorkflowRelationEdgeComponent', () => {
  const edgeOf = (data?: WorkflowEdgeData): Edge<WorkflowEdgeData> =>
    ({ id: 'a->b', type: WORKFLOW_RELATION_EDGE_TYPE, source: 'a', target: 'b', data }) as Edge<WorkflowEdgeData>;

  let fixture: ComponentFixture<WorkflowRelationEdgeComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [WorkflowRelationEdgeComponent] })
      .overrideComponent(WorkflowRelationEdgeComponent, {
        remove: { imports: [NgDiagramBaseEdgeComponent, NgDiagramBaseEdgeLabelComponent, NgDiagramDefaultEdgeLabelComponent] },
        add: { imports: [StubBaseEdgeComponent, StubBaseEdgeLabelComponent, StubDefaultEdgeLabelComponent] },
      })
      .compileComponents();

    fixture = TestBed.createComponent(WorkflowRelationEdgeComponent);
  });

  const baseEdge = () => (fixture.nativeElement as HTMLElement).querySelector('ng-diagram-base-edge') as HTMLElement;
  const label = () => (fixture.nativeElement as HTMLElement).querySelector('ng-diagram-default-edge-label');

  /**
   * What the template actually passed down, read off the stub rather than out of the DOM: these are signal
   * inputs, and Angular reflects none of them as an attribute.
   */
  const stroke = () => fixture.debugElement.query(By.directive(StubBaseEdgeComponent)).componentInstance as StubBaseEdgeComponent;

  function render(data?: WorkflowEdgeData): void {
    fixture.componentRef.setInput('edge', edgeOf(data));
    fixture.detectChanges();
  }

  it('says which relation it is drawing', () => {
    render({ relation: 'tool' });

    expect(baseEdge().getAttribute('data-relation')).toBe('tool');
  });

  /**
   * The distinction the whole template exists for, and BPMN's: control flow is solid, association is
   * dotted. A diagram where a dependency and a tool call looked alike would be a diagram of nothing in
   * particular.
   */
  it('draws control flow solid and every association dotted', () => {
    render({ relation: 'sequence' });
    expect(stroke().strokeDasharray()).toBeUndefined();

    (['input', 'output', 'start', 'tool'] as WorkflowRelation[]).forEach((relation) => {
      render({ relation });
      expect(stroke().strokeDasharray()).toBe('2 4');
    });
  });

  it('points every relation, since every one of them has a direction', () => {
    render({ relation: 'input' });

    expect(stroke().targetArrowhead()).toBe('ng-diagram-arrow');
  });

  // A real ordering, but inferred from the row order of a form rather than stated — so it is dashed and
  // faded, and must not read as firmly as the dependency beside it.
  it('draws an implied ordering more faintly than a stated one', () => {
    render({ relation: 'implicit' });
    const impliedDash = stroke().strokeDasharray();
    const impliedOpacity = stroke().strokeOpacity() as number;

    render({ relation: 'sequence' });

    expect(impliedDash).toBe('6 5');
    expect(impliedOpacity).toBeLessThan(stroke().strokeOpacity() as number);
  });

  /**
   * Not optional. ng-diagram's default template draws `data.label` and is not exported, so a custom
   * template that omitted it would silently lose every `ANY` join marker and every tool operation name.
   */
  it('writes the label at the edge’s midpoint', () => {
    render({ relation: 'sequence', label: 'any' });

    expect(label()?.textContent).toContain('any');
  });

  it('draws no label chip when there is nothing to write', () => {
    render({ relation: 'sequence' });

    expect(label()).toBeNull();
  });

  // `data` is absent on an edge ng-diagram's own linking created. `validateConnection` refuses those, so one
  // should not arise — but an edge template is not where to find out.
  it('falls back to control flow for an edge with no data at all', () => {
    render(undefined);

    expect(stroke().strokeDasharray()).toBeUndefined();
    expect(stroke().strokeOpacity()).toBe(1);
  });
});
