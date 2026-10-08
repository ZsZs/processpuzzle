import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { beforeEach, describe, expect, it } from 'vitest';
import { ModelerLegendComponent } from './modeler-legend.component';

describe('ModelerLegendComponent', () => {
  let fixture: ComponentFixture<ModelerLegendComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ModelerLegendComponent],
      providers: [
        provideTranslocoTesting({
          translations: {
            en: {
              'base_workflow.workflow_role_definition._self': 'Role',
              'base_workflow.artifact_definition._self': 'Artifact',
              'base_workflow.workflow.modeler.throw_event': 'Throw event',
              'base_workflow.workflow.modeler.catch_event': 'Catch event',
              'base_workflow.workflow.modeler.timer_event': 'Timer event',
              'base_workflow.workflow.modeler.boundary_event': 'Boundary event (interrupting)',
              'base_workflow.workflow.modeler.non_interrupting_boundary_event': 'Boundary event (non-interrupting)',
            },
          },
        }),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ModelerLegendComponent);
    fixture.componentRef.setInput('kinds', ['role', 'artifact']);
    fixture.detectChanges();
  });

  const items = () => Array.from((fixture.nativeElement as HTMLElement).querySelectorAll<HTMLElement>('[data-testid^="modeler-legend-"]'));

  it('explains the kinds it was given, in the order they were given', () => {
    expect(items().map((item) => item.dataset['testid'])).toEqual(['modeler-legend-role', 'modeler-legend-artifact']);
  });

  // From the entity's own `_self` key, so the legend and the Roles screen cannot disagree on the word.
  it('names each kind as its own screens name it', () => {
    expect(items().map((item) => item.textContent?.trim())).toEqual(['Role', 'Artifact']);
  });

  it('shows each kind next to the symbol the nodes draw it with', () => {
    expect(items().map((item) => item.querySelector('img')?.getAttribute('src'))).toEqual(['assets/modeler/Role.svg', 'assets/modeler/Artifact.svg']);
  });

  // One kind, several symbols: an event is explained as its throw and its catch, its timer, and both rings.
  it('explains an event as a throw, a catch, a timer and the two boundary events, each with its own symbol', () => {
    fixture.componentRef.setInput('kinds', ['role', 'event']);
    fixture.detectChanges();

    expect(items().map((item) => item.dataset['testid'])).toEqual([
      'modeler-legend-role',
      'modeler-legend-event-throw',
      'modeler-legend-event-catch',
      'modeler-legend-event-timer',
      'modeler-legend-event-boundary',
      'modeler-legend-event-non-interrupting-boundary',
    ]);
    expect(items().map((item) => item.textContent?.trim())).toEqual([
      'Role',
      'Throw event',
      'Catch event',
      'Timer event',
      'Boundary event (interrupting)',
      'Boundary event (non-interrupting)',
    ]);
    expect(items().map((item) => item.querySelector('img')?.getAttribute('src'))).toEqual([
      'assets/modeler/Role.svg',
      'assets/modeler/EventThrow.svg',
      'assets/modeler/EventCatch.svg',
      'assets/modeler/EventTimer.svg',
      'assets/modeler/EventCatch.svg',
      'assets/modeler/EventCatch.svg',
    ]);
  });

  // The rings are what tells the two boundary events apart, so the legend draws them: solid, then dashed.
  it('draws each boundary entry inside the ring its node is drawn with', () => {
    fixture.componentRef.setInput('kinds', ['event']);
    fixture.detectChanges();

    const ringOf = (testId: string) => items().find((item) => item.dataset['testid'] === testId)?.querySelector('.legend__ring');
    expect(ringOf('modeler-legend-event-catch')).toBeNull();
    expect(ringOf('modeler-legend-event-boundary')?.classList.contains('legend__ring--dashed')).toBe(false);
    expect(ringOf('modeler-legend-event-non-interrupting-boundary')?.classList.contains('legend__ring--dashed')).toBe(true);
  });

  // Only what it draws: the Tasks perspective passes a longer list and reuses this unchanged.
  it('explains nothing when a perspective passes no kinds', () => {
    fixture.componentRef.setInput('kinds', []);
    fixture.detectChanges();

    expect(items()).toEqual([]);
  });
});
