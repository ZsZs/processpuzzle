import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { beforeEach, describe, expect, it } from 'vitest';
import { ArtifactType } from '../../domain/definition/artifact-definition';
import { ResolvedArtifact } from '../../domain/dashboard/dashboard-task';
import { ArtifactInstance } from '../../domain/execution/workflow-instance';
import { ArtifactChipComponent } from './artifact-chip.component';

describe('ArtifactChipComponent', () => {
  let fixture: ComponentFixture<ArtifactChipComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ArtifactChipComponent],
      providers: [provideTranslocoTesting({ translations: { en: { 'base_workflow.task_instance.dashboard.not_created': 'not created yet' } } })],
    }).compileComponents();
    fixture = TestBed.createComponent(ArtifactChipComponent);
  });

  const render = (artifact: ResolvedArtifact): HTMLElement => {
    fixture.componentRef.setInput('artifact', artifact);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  };

  const resolved = (overrides: Partial<ResolvedArtifact> = {}): ResolvedArtifact => ({
    artifactDefinitionId: 'order-entity',
    direction: 'input',
    name: 'Order Entity',
    type: ArtifactType.ENTITY,
    instance: undefined,
    ...overrides,
  });

  const instance = (currentState?: string) => new ArtifactInstance({ id: 'a1', artifactDefinitionId: 'order-entity', name: 'Order Entity', type: ArtifactType.ENTITY, currentState });

  // Treatment 1: the state is the literal string from the API, beside the type, and nothing is inferred
  // from it — see the class comment on why coloring by meaning would be a contract change.
  it('shows the type and the state exactly as the API spelled them', () => {
    const host = render(resolved({ instance: instance('under_investigation') }));

    expect(host.querySelector('.chip__state')?.textContent?.trim()).toBe('entity · under_investigation');
  });

  // Treatment 2: no state machine is attached, so there is no state segment to show.
  it('shows only the type when no state machine is attached', () => {
    const host = render(resolved({ instance: instance(undefined) }));

    expect(host.querySelector('.chip__state')?.textContent?.trim()).toBe('entity');
    expect(host.querySelector('[data-testid="artifact-not-created"]')).toBeNull();
  });

  // Treatment 3: a declared output nothing has produced. Kept and marked rather than hidden — it is what
  // tells the user what the task is for.
  it('marks a declared artifact nothing has produced yet', () => {
    const host = render(resolved({ direction: 'output', instance: undefined }));

    expect(host.querySelector('[data-testid="artifact-not-created"]')?.textContent?.trim()).toBe('not created yet');
    expect(host.querySelector('.chip')?.classList.contains('chip--pending')).toBe(true);
  });

  // The instance's own name is what this run called it; the catalog's is only the fallback the store applies.
  it('names the artifact and marks it with the modeler’s own symbol', () => {
    const host = render(resolved({ instance: instance('CONFIRMED') }));

    expect(host.querySelector('.chip__name')?.textContent?.trim()).toBe('Order Entity');
    expect(host.querySelector('img')?.getAttribute('src')).toBe('assets/modeler/Artifact.svg');
  });

  // An unproduced output still has a type, from the catalog entry the store resolved it against.
  it('falls back to the catalog type when there is no instance', () => {
    const host = render(resolved({ type: ArtifactType.DOCUMENT, instance: undefined }));

    expect(host.querySelector('[data-testid="artifact-chip-order-entity"]')).not.toBeNull();
  });
});
