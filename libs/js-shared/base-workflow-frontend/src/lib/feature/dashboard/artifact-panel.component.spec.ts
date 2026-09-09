import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { beforeEach, describe, expect, it } from 'vitest';
import { ArtifactType } from '../../domain/definition/artifact-definition';
import { ResolvedArtifact } from '../../domain/dashboard/dashboard-task';
import { ArtifactInstance } from '../../domain/execution/workflow-instance';
import { ArtifactPanelComponent } from './artifact-panel.component';

describe('ArtifactPanelComponent', () => {
  const order: ResolvedArtifact = {
    artifactDefinitionId: 'order-entity',
    direction: 'input',
    name: 'Order Entity',
    type: ArtifactType.ENTITY,
    instance: new ArtifactInstance({ id: 'a1', artifactDefinitionId: 'order-entity', name: 'Order Entity', type: ArtifactType.ENTITY, currentState: 'CONFIRMED' }),
  };
  const invoice: ResolvedArtifact = { artifactDefinitionId: 'fulfillment-invoice', direction: 'output', name: 'Fulfillment Invoice', type: ArtifactType.DOCUMENT, instance: undefined };

  let fixture: ComponentFixture<ArtifactPanelComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ArtifactPanelComponent],
      providers: [
        provideTranslocoTesting({
          translations: {
            en: {
              'base_workflow.task_instance.dashboard.artifacts_in': 'Reads',
              'base_workflow.task_instance.dashboard.artifacts_out': 'Writes',
              'base_workflow.task_instance.dashboard.artifacts_none': 'This task declares no artifacts.',
              'base_workflow.task_instance.dashboard.not_created': 'not created yet',
            },
          },
        }),
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ArtifactPanelComponent);
  });

  const render = (inputs: ResolvedArtifact[], outputs: ResolvedArtifact[]): HTMLElement => {
    fixture.componentRef.setInput('inputs', inputs);
    fixture.componentRef.setInput('outputs', outputs);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  };

  // "Reads" and "Writes" rather than "Inputs" and "Outputs": the user is being told what to look at, not
  // shown a signature.
  it('lays out what the task reads and what it writes, in that order', () => {
    const host = render([order], [invoice]);

    expect(Array.from(host.querySelectorAll('.panel__heading')).map((heading) => heading.textContent?.trim())).toEqual(['Reads', 'Writes']);
    expect(host.querySelector('[data-testid="artifact-inputs"] [data-testid="artifact-chip-order-entity"]')).not.toBeNull();
    expect(host.querySelector('[data-testid="artifact-outputs"] [data-testid="artifact-chip-fulfillment-invoice"]')).not.toBeNull();
  });

  /**
   * Both columns even when one is empty: "writes nothing" is information about the task, and a lone column
   * that silently shifted position would read as the other one.
   */
  it('keeps both columns when only one side is declared', () => {
    const host = render([order], []);

    expect(host.querySelectorAll('.panel__column')).toHaveLength(2);
    expect(host.querySelector('[data-testid="artifact-outputs"] .panel__dash')).not.toBeNull();
  });

  // A task that declares neither gets one sentence instead of two empty columns.
  it('collapses to a sentence when the task declares nothing at all', () => {
    const host = render([], []);

    expect(host.querySelector('.panel')).toBeNull();
    expect(host.querySelector('[data-testid="artifacts-empty"]')?.textContent?.trim()).toBe('This task declares no artifacts.');
  });

  it('marks an output nothing has produced yet rather than hiding it', () => {
    const host = render([], [invoice]);

    expect(host.querySelector('[data-testid="artifact-not-created"]')).not.toBeNull();
  });
});
