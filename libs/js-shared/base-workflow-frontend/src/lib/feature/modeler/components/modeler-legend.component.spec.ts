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

  // One kind, two symbols: an intermediate event is explained as its throw and its catch.
  it('explains an event as a throw and a catch, each with its own symbol', () => {
    fixture.componentRef.setInput('kinds', ['role', 'event']);
    fixture.detectChanges();

    expect(items().map((item) => item.dataset['testid'])).toEqual(['modeler-legend-role', 'modeler-legend-event-throw', 'modeler-legend-event-catch']);
    expect(items().map((item) => item.textContent?.trim())).toEqual(['Role', 'Throw event', 'Catch event']);
    expect(items().map((item) => item.querySelector('img')?.getAttribute('src'))).toEqual([
      'assets/modeler/Role.svg',
      'assets/modeler/EventThrow.svg',
      'assets/modeler/EventCatch.svg',
    ]);
  });

  // Only what it draws: the Tasks perspective passes a longer list and reuses this unchanged.
  it('explains nothing when a perspective passes no kinds', () => {
    fixture.componentRef.setInput('kinds', []);
    fixture.detectChanges();

    expect(items()).toEqual([]);
  });
});
