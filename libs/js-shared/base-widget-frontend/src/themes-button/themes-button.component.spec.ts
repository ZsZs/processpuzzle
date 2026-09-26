import { ANIMATION_MODULE_TYPE } from '@angular/core';
import { ComponentFixture } from '@angular/core/testing';
import { setUpTranslocoTestBed } from '@processpuzzle/test-util';
import { beforeEach, describe, expect, it } from 'vitest';
import widgetsEn from '../assets/i18n/widgets/en.json';
import { ThemeSelection } from './theme-presets';
import { ThemeService } from './theme.service';
import { ThemesButtonComponent } from './themes-button.component';

describe('ThemesButtonComponent', () => {
  let fixture: ComponentFixture<ThemesButtonComponent>;
  let theme: ThemeService;
  let emitted: ThemeSelection[];

  const byTestId = (id: string): HTMLElement | null => document.querySelector(`[test-id="${id}"]`);

  function openMenu(): void {
    (fixture.nativeElement.querySelector('[test-id="themes-button"]') as HTMLButtonElement).click();
    fixture.detectChanges();
  }

  beforeEach(async () => {
    // A scoped instance, so the root one never puts classes on the test document.
    const testVars = await setUpTranslocoTestBed(
      ThemesButtonComponent,
      { scope: 'widgets', translations: { en: {}, 'widgets/en': widgetsEn } },
      { providers: [{ provide: ThemeService, useClass: ThemeService }, { provide: ANIMATION_MODULE_TYPE, useValue: 'NoopAnimations' }] },
    );
    fixture = testVars.fixture;
    theme = fixture.debugElement.injector.get(ThemeService);
    emitted = [];
    fixture.componentInstance.themeChange.subscribe((selection) => emitted.push(selection));
  });

  it('offers every preset, by its translated name, with the current one checked', () => {
    openMenu();

    const items = [...document.querySelectorAll('[role="menuitemradio"]')];
    expect(items).toHaveLength(9);
    expect(byTestId('theme-indigo-pink')?.textContent).toContain('Indigo & Pink');
    expect(byTestId('theme-processpuzzle')?.getAttribute('aria-checked')).toBe('true');
    expect(byTestId('theme-rose-red')?.getAttribute('aria-checked')).toBe('false');
  });

  it('selects a preset and reports the resulting selection', () => {
    openMenu();

    byTestId('theme-purple-green')?.click();

    expect(theme.selection().preset).toBe('purple-green');
    expect(emitted).toEqual([{ preset: 'purple-green', scheme: 'light' }]);
  });

  it('switches the colour scheme', () => {
    openMenu();

    (byTestId('scheme-dark')?.querySelector('button') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(theme.selection().scheme).toBe('dark');
    expect(emitted.at(-1)).toEqual({ preset: 'processpuzzle', scheme: 'dark' });
  });

  it('offers a reset only once something was chosen, and it returns to the defaults', () => {
    theme.setDefaults({ preset: 'azure-blue' });
    openMenu();
    expect(byTestId('theme-reset')).toBeNull();

    theme.selectPreset('rose-red');
    fixture.detectChanges();
    byTestId('theme-reset')?.click();

    expect(theme.selection().preset).toBe('azure-blue');
    expect(emitted.at(-1)).toEqual({ preset: 'azure-blue', scheme: 'light' });
  });
});
