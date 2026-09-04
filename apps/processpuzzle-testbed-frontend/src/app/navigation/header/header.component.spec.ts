import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HeaderComponent } from './header.component';
import { By } from '@angular/platform-browser';
import { Component, signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { LanguageSelectorComponent, LikeButtonComponent, ShareButtonComponent } from '@processpuzzle/base-widget';
import { NavigateBackComponent } from '@processpuzzle/util';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { AuthButtonComponent } from '@processpuzzle/auth';
import { AUTHENTICATION_SERVICE } from '@processpuzzle/auth/domain';

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

  it('navigates home for a logo click or an accessible keyboard activation', async () => {
    const navigateByUrl = vi.spyOn(component.router, 'navigateByUrl').mockResolvedValue(true);
    const preventDefault = vi.fn();

    await component.onLogoClick();
    await component.onLogoKeyPress({ key: 'Enter', preventDefault } as unknown as KeyboardEvent);
    await component.onLogoKeyPress({ code: 'Space', preventDefault } as unknown as KeyboardEvent);
    await component.onLogoKeyPress({ key: 'Escape', preventDefault } as unknown as KeyboardEvent);

    expect(navigateByUrl).toHaveBeenCalledTimes(3);
    expect(navigateByUrl).toHaveBeenCalledWith('/');
    expect(preventDefault).toHaveBeenCalledTimes(2);
  });

  it('emits a sidenav toggle request', () => {
    const toggle = vi.fn();
    component.toggleSideNav.subscribe(toggle);

    component.sidenavToggle();

    expect(toggle).toHaveBeenCalledWith(undefined);
  });
});
