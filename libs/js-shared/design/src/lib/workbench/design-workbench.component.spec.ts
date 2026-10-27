import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Provider } from '@angular/core';
import { ComponentFixture } from '@angular/core/testing';
import { MatSidenav } from '@angular/material/sidenav';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { setUpTranslocoTestBed } from '@processpuzzle/test-util';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { describe, expect, it } from 'vitest';
import { provideDesignAssistant } from './design-assistant.provider';
import { DesignWorkbenchComponent } from './design-workbench.component';

describe('DesignWorkbenchComponent', () => {
  async function setUp(providers: Provider[] = []): Promise<ComponentFixture<DesignWorkbenchComponent>> {
    const { fixture } = await setUpTranslocoTestBed(
      DesignWorkbenchComponent,
      { translations: { en: {} } },
      {
        providers: [
          provideRouter([]),
          provideHttpClient(),
          provideHttpClientTesting(),
          { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { BACKEND_SERVICE_ROOT: 'https://backend/organizations/acme' } } },
          ...providers,
        ],
      },
    );
    fixture.detectChanges();
    return fixture;
  }

  it('renders the sections with no assistant unless the application provides it', async () => {
    const fixture = await setUp();

    expect(fixture.debugElement.query(By.css('router-outlet'))).toBeTruthy();
    expect(fixture.debugElement.query(By.css('pp-assistant-panel'))).toBeNull();
    expect(fixture.debugElement.query(By.css('.pp-design-workbench__toggle'))).toBeNull();
  });

  it('offers the assistant in an end drawer that the toggle opens and closes', async () => {
    const fixture = await setUp([provideDesignAssistant()]);
    const drawer = fixture.debugElement.query(By.directive(MatSidenav)).componentInstance as MatSidenav;
    const toggle = fixture.debugElement.query(By.css('.pp-design-workbench__toggle'));

    expect(fixture.debugElement.query(By.css('pp-assistant-panel'))).toBeTruthy();
    expect(drawer.position).toBe('end');
    expect(drawer.opened).toBe(false);
    expect(toggle.attributes['aria-pressed']).toBe('false');

    toggle.nativeElement.click();
    fixture.detectChanges();

    expect(drawer.opened).toBe(true);
    expect(toggle.attributes['aria-pressed']).toBe('true');

    toggle.nativeElement.click();
    fixture.detectChanges();

    expect(drawer.opened).toBe(false);
  });
});
