import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { provideNgDiagram } from 'ng-diagram';
import { beforeEach, describe, expect, it } from 'vitest';
import { ElementPaletteComponent } from './element-palette.component';

describe('ElementPaletteComponent', () => {
  let fixture: ComponentFixture<ElementPaletteComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ElementPaletteComponent],
      // ng-diagram's `PaletteService` is component-scoped, supplied by `provideNgDiagram()` — in the
      // application by the canvas that hosts this palette, here by the test.
      providers: [
        provideNgDiagram(),
        provideTranslocoTesting({
          translations: {
            en: {
              'base_state.state_machine_definition.modeler.palette.title': 'Elements',
              'base_state.state_machine_definition.modeler.palette.start': 'Start',
              'base_state.state_machine_definition.modeler.palette.end': 'End',
              'base_state.state_machine_definition.modeler.palette.state': 'State',
            },
          },
        }),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ElementPaletteComponent);
    fixture.detectChanges();
  });

  const symbols = () => Array.from((fixture.nativeElement as HTMLElement).querySelectorAll<HTMLElement>('[data-testid^="palette-"]'));

  it('offers the three symbols a machine is drawn from, in the order it runs', () => {
    expect(symbols().map((symbol) => symbol.dataset['testid'])).toEqual(['palette-start', 'palette-end', 'palette-state']);
  });

  it('labels them from the scope, so a translation is what the user reads', () => {
    expect(symbols().map((symbol) => symbol.textContent?.trim())).toEqual(['Start', 'End', 'State']);
    expect((fixture.nativeElement as HTMLElement).querySelector('h4')?.textContent?.trim()).toBe('Elements');
  });

  // Each symbol has to be draggable, which is what the wrapper contributes — a plain div would render
  // identically and do nothing.
  it('wraps every symbol in a draggable palette item', () => {
    const wrappers = (fixture.nativeElement as HTMLElement).querySelectorAll('ng-diagram-palette-item');

    expect(wrappers.length).toBe(3);
    expect(Array.from(wrappers).every((wrapper) => wrapper.contains(wrapper.querySelector('[data-testid^="palette-"]')))).toBe(true);
  });

  /**
   * The preview is what follows the cursor mid-drag. Drawn from the same markup on purpose, so what is
   * picked up looks like what was grabbed — hence two copies of each symbol in the DOM.
   */
  it('previews each symbol as itself', () => {
    const previews = (fixture.nativeElement as HTMLElement).querySelectorAll('ng-diagram-palette-item-preview .pp-palette__symbol');

    expect(Array.from(previews).map((preview) => preview.textContent?.trim())).toEqual(['Start', 'End', 'State']);
  });
});
