import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HeaderComponent } from './header.component';
import { By } from '@angular/platform-browser';
import { Component, signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { LanguageSelectorComponent, LikeButtonComponent, ShareButtonComponent } from '@processpuzzle/widgets';
import { NavigateBackComponent } from '@processpuzzle/util';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { AuthButtonComponent } from '@processpuzzle/auth';
import { AUTHENTICATION_SERVICE } from '@processpuzzle/auth/domain';
// eslint-disable-next-line @nx/enforce-module-boundaries -- a stub for the logo widget's dependency, as the imports above
import { ObjectStoreService } from '@processpuzzle/base-entity';

@Component({ selector: 'pp-like-button', template: `<p>Mock Like Button</p>` })
class MockLikeButtonComponent {}

@Component({ selector: 'pp-share-button', template: `<p>Mock Share Button</p>` })
class MockShareButtonComponent {}

@Component({ selector: 'pp-auth-button', template: `<p>Mock Auth Button</p>` })
class MockAuthButtonComponent {}

@Component({ selector: 'pp-navigate-back', template: `<p>Mock Navigate Back</p>` })
class MockNavigateBackComponent {}

@Component({ selector: 'pp-language-selector', template: `<p>Mock Language Selector</p>` })
class MockLanguageSelectorComponent {}

describe('HeaderComponent', () => {
  let component: HeaderComponent;
  let fixture: ComponentFixture<HeaderComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        HeaderComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: {} },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
          preloadLangs: true,
        }),
      ],
      providers: [
        provideRouter([]),
        { provide: AUTHENTICATION_SERVICE, useValue: { isAuthenticated: signal(false) } },
        // The logo comes from the application context's URL, so the object store is never asked.
        { provide: ObjectStoreService, useValue: {} },
      ],
    })
      .overrideComponent(HeaderComponent, {
        remove: { imports: [LikeButtonComponent, ShareButtonComponent, AuthButtonComponent, NavigateBackComponent, LanguageSelectorComponent] },
        add: { imports: [MockLikeButtonComponent, MockShareButtonComponent, MockAuthButtonComponent, MockNavigateBackComponent, MockLanguageSelectorComponent] },
      })
      .compileComponents();

    fixture = TestBed.createComponent(HeaderComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('Should create component', () => {
    expect(component).toBeTruthy();
  });

  it('template structure contains: mat-toolbar:', () => {
    const matToolbar = fixture.debugElement.query(By.css('mat-toolbar')).nativeElement;
    expect(matToolbar).toBeTruthy();
  });

  it('shows the testbed through the shared logo and title widgets, the logo linking home', () => {
    const logo = fixture.debugElement.query(By.css('pp-app-logo a')).nativeElement as HTMLAnchorElement;
    const image = fixture.debugElement.query(By.css('pp-app-logo img')).nativeElement as HTMLImageElement;
    const title = fixture.debugElement.query(By.css('pp-app-title h1')).nativeElement as HTMLElement;

    expect(logo.getAttribute('href')).toBe('/home');
    expect(image.getAttribute('src')).toBe('assets/processpuzzle-logo-with-title.jpg');
    expect(image.getAttribute('alt')).toBe('ProcessPuzzle Testbed');
    expect(title.textContent?.trim()).toBe('ProcessPuzzle Testbed');
  });

  it('emits a sidenav toggle request', () => {
    const toggle = vi.fn();
    component.toggleSideNav.subscribe(toggle);

    component.sidenavToggle();

    expect(toggle).toHaveBeenCalledWith(undefined);
  });
});
