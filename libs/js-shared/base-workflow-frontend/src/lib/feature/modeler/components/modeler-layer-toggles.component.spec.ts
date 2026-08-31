import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { beforeEach, describe, expect, it } from 'vitest';
import { ModelerLayers, ModelerLayerTogglesComponent } from './modeler-layer-toggles.component';

const ALL_ON: ModelerLayers = { lanes: true, data: true, tools: true };

describe('ModelerLayerTogglesComponent', () => {
  let fixture: ComponentFixture<ModelerLayerTogglesComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        // Flat dotted keys: TestTranslocoLoader drops nested objects when no scope is configured.
        provideTranslocoTesting({
          translations: {
            en: {
              'base_workflow.workflow.modeler.lanes': 'Lanes',
              'base_workflow.workflow.modeler.data': 'Work products',
              'base_workflow.workflow.modeler.tools': 'Tools',
            },
          },
        }),
      ],
    });
    fixture = TestBed.createComponent(ModelerLayerTogglesComponent);
  });

  function render(layers: ModelerLayers = ALL_ON): void {
    fixture.componentRef.setInput('layers', layers);
    fixture.componentRef.setInput('labelScope', 'base_workflow.workflow.modeler');
    fixture.detectChanges();
  }

  function checkbox(layer: keyof ModelerLayers): HTMLInputElement {
    return fixture.debugElement.query(By.css(`[data-testid="modeler-toggle-${layer}"] input`)).nativeElement;
  }

  it('offers the three layers, outermost structure first', () => {
    render();

    expect(fixture.debugElement.queryAll(By.css('.toggle')).map((toggle) => toggle.nativeElement.textContent.trim())).toEqual(['Lanes', 'Work products', 'Tools']);
  });

  // Every layer defaults to on: the whole workflow is the useful first sight of it, and a toggle exists to
  // take something away.
  it('shows each layer as the host says it is', () => {
    render({ lanes: true, data: false, tools: true });

    expect([checkbox('lanes').checked, checkbox('data').checked, checkbox('tools').checked]).toEqual([true, false, true]);
  });

  /**
   * Emits the whole set rather than the one that changed, so the host holds one signal instead of three —
   * and so a rebuild of the graph reads a single consistent value rather than three that may disagree
   * mid-update.
   */
  it('emits the whole set with just the clicked layer flipped', () => {
    render();
    let emitted: ModelerLayers | undefined;
    fixture.componentInstance.layersChange.subscribe((layers) => (emitted = layers));

    checkbox('data').click();

    expect(emitted).toEqual({ lanes: true, data: false, tools: true });
  });

  it('flips a layer back on', () => {
    render({ lanes: true, data: false, tools: true });
    let emitted: ModelerLayers | undefined;
    fixture.componentInstance.layersChange.subscribe((layers) => (emitted = layers));

    checkbox('data').click();

    expect(emitted).toEqual(ALL_ON);
  });

  // The labels hang under a scope the host names, because `base-workflow.i18n.spec.ts` asserts that every
  // top-level block of the bundle is an entity scope — a `base_workflow.modeler.*` block would fail it.
  it('resolves its labels under the scope the host gives it', () => {
    fixture.componentRef.setInput('layers', ALL_ON);
    fixture.componentRef.setInput('labelScope', 'nothing.of.the.kind');
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('nothing.of.the.kind.lanes');
  });
});
