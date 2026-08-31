import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { beforeEach, describe, expect, it } from 'vitest';
import { WorkflowEdgeData } from '../../../domain/modeler/workflow-graph';
import { WorkflowRelationPropertiesPanelComponent } from './workflow-relation-properties-panel.component';

describe('WorkflowRelationPropertiesPanelComponent', () => {
  let fixture: ComponentFixture<WorkflowRelationPropertiesPanelComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WorkflowRelationPropertiesPanelComponent],
      providers: [
        // Flat dotted keys: TestTranslocoLoader drops nested objects when no scope is configured.
        provideTranslocoTesting({
          translations: {
            en: {
              'base_workflow.workflow.modeler.properties.relation_heading': 'Relation',
              'base_workflow.workflow.modeler.properties.relation': 'Kind',
              'base_workflow.workflow.modeler.properties.label': 'Label',
              'base_workflow.workflow.modeler.properties.relations.sequence': 'Depends on',
              'base_workflow.workflow.modeler.properties.relations.implicit': 'Runs after (declaration order)',
              'base_workflow.workflow.modeler.properties.relations.input': 'Reads',
              'base_workflow.workflow.modeler.properties.relations.output': 'Writes',
              'base_workflow.workflow.modeler.properties.relations.tool': 'Calls',
              'base_workflow.workflow.modeler.properties.relations.start': 'Required at start',
              'base_workflow.workflow.modeler.properties.relations.unknown': 'Related to',
            },
          },
        }),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(WorkflowRelationPropertiesPanelComponent);
  });

  async function render(relation: WorkflowEdgeData): Promise<void> {
    fixture.componentRef.setInput('relation', relation);
    fixture.detectChanges();
    await fixture.whenStable();
  }

  function query(testid: string): HTMLElement | null {
    return (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>(`[data-testid="${testid}"]`);
  }

  it('should compile and create component', async () => {
    await render({ relation: 'sequence' });

    expect(fixture.componentInstance).toBeTruthy();
  });

  /**
   * The panel's real job. The canvas distinguishes the six relations by dash pattern and opacity, which tells
   * a reader that two edges differ without telling them how.
   */
  it.each([
    ['sequence', 'Depends on'],
    ['implicit', 'Runs after (declaration order)'],
    ['input', 'Reads'],
    ['output', 'Writes'],
    ['tool', 'Calls'],
    ['start', 'Required at start'],
  ] as const)('names the %s relation in words', async (relation, expected) => {
    await render({ relation });

    expect(query('relation-kind')?.textContent).toContain(expected);
  });

  /**
   * A Roles-perspective edge sets no `relation` — one relation on screen needs no distinguishing — so this
   * panel has no subject there today. A key still beats rendering the string `undefined` if it is ever given
   * one.
   */
  it('falls back to a generic word for an edge that names no relation', async () => {
    await render({});

    expect(query('relation-kind')?.textContent).toContain('Related to');
  });

  /**
   * The `ANY` join marker, a tool step's operation name, a required start artifact's state. Drawn on the edge
   * at a size chosen for a chip rather than for reading.
   */
  it('shows the edge label when it carries one', async () => {
    await render({ relation: 'tool', label: 'runCreditCheck' });

    expect(query('relation-label')?.textContent).toContain('runCreditCheck');
  });

  it('omits the label row when there is none', async () => {
    await render({ relation: 'sequence' });

    expect(query('relation-label')).toBeNull();
  });
});
