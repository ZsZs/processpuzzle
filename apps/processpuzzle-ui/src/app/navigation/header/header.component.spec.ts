import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter, Router } from '@angular/router';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { AuthButtonComponent } from '@processpuzzle/auth';
import { AUTHENTICATION_SERVICE } from '@processpuzzle/auth/domain';
import { LanguageSelectorComponent } from '@processpuzzle/base-widget';
import { setUpTranslocoTestBed, TranslocoTestConfig } from '@processpuzzle/test-util';
import { LayoutService, NavigateBackComponent } from '@processpuzzle/util';
import { createAppRoutes } from '../../app.routes';
import { HeaderComponent } from './header.component';

@Component({ selector: 'pp-auth-button', template: '' })
class MockAuthButtonComponent {}

@Component({ selector: 'pp-navigate-back', template: '' })
class MockNavigateBackComponent {}

@Component({ selector: 'pp-language-selector', template: '' })
class MockLanguageSelectorComponent {}

describe('HeaderComponent', () => {
  const testConfig: TranslocoTestConfig = { translations: { en: { navigation: { home: 'Home', admin: { users: 'Users' } } } } };
  let component: HeaderComponent;
  let fixture: ComponentFixture<HeaderComponent>;

  beforeEach(async () => {
    TestBed.overrideComponent(HeaderComponent, {
      remove: { imports: [AuthButtonComponent, NavigateBackComponent, LanguageSelectorComponent] },
      add: { imports: [MockAuthButtonComponent, MockNavigateBackComponent, MockLanguageSelectorComponent] },
    });
    const result = await setUpTranslocoTestBed(HeaderComponent, testConfig, {
      // The real routes, for the tenant a URL would have named: `navigationItems` reads
      // `Router.config`, so a `provideRouter([])` here would test nothing.
      providers: [LayoutService, provideRouter(createAppRoutes('acme')), { provide: AUTHENTICATION_SERVICE, useValue: { isAuthenticated: signal(false) } }],
    });
    component = result.component;
    fixture = result.fixture;
  });

  it('Should create component', () => {
    expect(component).toBeTruthy();
  });

  it('template structure contains: mat-toolbar', () => {
    expect(fixture.debugElement.query(By.css('mat-toolbar')).nativeElement).toBeTruthy();
  });

  it('offers home and the tenant admin branch, by absolute link', () => {
    expect(component.routes.map((route) => route.link)).toEqual(['/home', '/acme/admin']);
  });

  it('sidenavToggle emits, so the shell can collapse the sidenav', () => {
    const emitted: unknown[] = [];
    component.toggleSideNav.subscribe((value) => emitted.push(value));
    component.sidenavToggle();
    expect(emitted).toEqual([undefined]);
  });

  it('the logo navigates home, by click and by keyboard', async () => {
    const navigateByUrl = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);

    await component.onLogoClick();
    expect(navigateByUrl).toHaveBeenCalledWith('/');

    navigateByUrl.mockClear();
    await component.onLogoKeyPress(new KeyboardEvent('keydown', { key: 'Enter' }));
    expect(navigateByUrl).toHaveBeenCalledWith('/');

    // Anything else must be left to the browser — swallowing Tab would trap focus in the toolbar.
    navigateByUrl.mockClear();
    await component.onLogoKeyPress(new KeyboardEvent('keydown', { key: 'Tab' }));
    expect(navigateByUrl).not.toHaveBeenCalled();
  });
});
