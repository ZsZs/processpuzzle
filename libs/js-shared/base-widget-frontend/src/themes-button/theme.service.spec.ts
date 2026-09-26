import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { DOCUMENT_THEME_STORAGE_KEY, ThemeService } from './theme.service';

describe('ThemeService', () => {
  const root = document.documentElement;

  beforeEach(() => localStorage.clear());
  afterEach(() => {
    TestBed.resetTestingModule();
    root.className = '';
    localStorage.clear();
  });

  describe('a scoped instance', () => {
    let service: ThemeService;
    beforeEach(() => {
      TestBed.configureTestingModule({ providers: [{ provide: ThemeService, useClass: ThemeService }] });
      service = TestBed.inject(ThemeService);
    });

    it('wears the ProcessPuzzle look, light, when nothing is declared or chosen', () => {
      expect(service.selection()).toEqual({ preset: 'processpuzzle', scheme: 'light' });
      expect(service.themeClass()).toBe('pp-theme-processpuzzle pp-scheme-light');
    });

    it('lets a choice override the declared defaults field by field', () => {
      service.setDefaults({ preset: 'azure-blue', scheme: 'dark' });
      service.selectScheme('auto');

      expect(service.selection()).toEqual({ preset: 'azure-blue', scheme: 'auto' });
    });

    it('returns to the defaults on reset', () => {
      service.setDefaults({ preset: 'azure-blue' });
      service.selectPreset('rose-red');
      service.reset();

      expect(service.selection().preset).toBe('azure-blue');
      expect(service.hasChoice()).toBe(false);
    });

    it('persists under the key it was given, and forgets the entry on reset', () => {
      service.persistUnder('pp-theme:crm');
      service.selectPreset('indigo-pink');
      expect(JSON.parse(localStorage.getItem('pp-theme:crm') ?? '{}')).toEqual({ preset: 'indigo-pink' });

      service.reset();
      expect(localStorage.getItem('pp-theme:crm')).toBeNull();
    });

    it('drops a stored value that is no longer a preset instead of applying it', () => {
      localStorage.setItem('pp-theme:crm', JSON.stringify({ preset: 'retired-theme', scheme: 'dark' }));

      service.persistUnder('pp-theme:crm');

      expect(service.choice()).toEqual({ scheme: 'dark' });
    });

    it('keeps choices in memory only without a key', () => {
      service.persistUnder(null);
      service.selectPreset('purple-green');

      expect(localStorage.length).toBe(0);
      expect(service.selection().preset).toBe('purple-green');
    });

    it('never touches the document', () => {
      service.selectPreset('purple-green');
      TestBed.tick();

      expect(root.classList.contains('pp-theme-purple-green')).toBe(false);
    });
  });

  describe('the root instance', () => {
    it('leaves the host application alone until the user chooses', () => {
      TestBed.inject(ThemeService);
      TestBed.tick();

      expect([...root.classList].some((name) => name.startsWith('pp-theme-'))).toBe(false);
    });

    it('themes the document with the choice, and restores it from storage', () => {
      TestBed.inject(ThemeService).selectPreset('deeppurple-amber');
      TestBed.tick();
      expect([...root.classList]).toEqual(expect.arrayContaining(['pp-theme-deeppurple-amber', 'pp-scheme-light']));

      TestBed.resetTestingModule();
      root.className = '';
      TestBed.inject(ThemeService);
      TestBed.tick();
      expect(root.classList.contains('pp-theme-deeppurple-amber')).toBe(true);
      expect(JSON.parse(localStorage.getItem(DOCUMENT_THEME_STORAGE_KEY) ?? '{}')).toEqual({ preset: 'deeppurple-amber' });
    });

    it('swaps rather than accumulates classes', () => {
      const service = TestBed.inject(ThemeService);
      service.selectPreset('rose-red');
      TestBed.tick();
      service.selectPreset('cyan-orange');
      TestBed.tick();

      expect([...root.classList].filter((name) => name.startsWith('pp-theme-'))).toEqual(['pp-theme-cyan-orange']);
    });
  });
});
