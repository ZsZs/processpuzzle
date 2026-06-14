import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HeaderComponent } from './header.component';
import { By } from '@angular/platform-browser';
import { Component, signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { LanguageSelectorComponent, LikeButtonComponent, NavigateBackComponent, ShareButtonComponent } from '@processpuzzle/widgets';
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

  it.skip('template structure contains: mat-toolbar:', () => {
    const matToolbar = fixture.debugElement.query(By.css('mat-toolbar')).nativeElement;
    expect(matToolbar).toBeTruthy();
  });
});
