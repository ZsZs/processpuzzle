import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { mockLanguageConfig, setUpTranslocoTestBed } from '@processpuzzle/test-util';
import { LanguageSelectorComponent } from './language-selector.component';
import widgetsDe from '../assets/i18n/widgets/de.json';
import widgetsEn from '../assets/i18n/widgets/en.json';

describe('LanguageSelectorComponent', () => {
  let fixture: ComponentFixture<LanguageSelectorComponent>;

  const getTriggerButton = (): HTMLButtonElement => fixture.debugElement.query(By.css('button[aria-label="Select Language Button"]')).nativeElement as HTMLButtonElement;

  beforeEach(async () => {
    // Render the real language list (no overrideComponent) so the component's inline template
    // is instrumented for coverage; mockLanguageConfig feeds the list its available languages.
    const testVars = await setUpTranslocoTestBed(
      LanguageSelectorComponent,
      { scope: 'widgets', translations: { en: {}, de: {}, 'widgets/en': widgetsEn, 'widgets/de': widgetsDe } },
      {
        imports: [MatIconModule, MatButtonModule, MatMenuModule, NoopAnimationsModule],
        providers: [{ provide: RUNTIME_CONFIGURATION, useValue: mockLanguageConfig }],
      },
    );
    fixture = testVars.fixture;
  });

  it('should render the language trigger button', () => {
    const button = getTriggerButton();
    expect(button).toBeTruthy();
    expect(button.getAttribute('aria-label')).toBe('Select Language Button');
    expect(fixture.debugElement.query(By.css('mat-icon')).nativeElement.textContent).toContain('language');
  });

  it('should open the menu and render the language selection list when the trigger is clicked', () => {
    getTriggerButton().click();
    fixture.detectChanges();

    const panel = document.querySelector('.mat-mdc-menu-panel');
    expect(panel).toBeTruthy();
    expect(panel?.querySelector('mat-selection-list')).toBeTruthy();
  });
});
