import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { APPLICATION_CONTEXT } from '../app-context/application-context';
import { testApplicationContext } from '../app-context/test-application-context';
import { AppTitleComponent } from './app-title.component';

describe('AppTitleComponent', () => {
  function render(title: string | undefined, context = testApplicationContext({ name: 'Demo Application' }), withContext = true) {
    TestBed.configureTestingModule({ imports: [AppTitleComponent], providers: withContext ? [{ provide: APPLICATION_CONTEXT, useValue: context }] : [] });
    const fixture = TestBed.createComponent(AppTitleComponent);
    fixture.componentRef.setInput('title', title);
    fixture.detectChanges();
    return fixture;
  }

  const heading = (fixture: ReturnType<typeof render>) => fixture.nativeElement.querySelector('h1.pp-app-title')?.textContent?.trim();

  it('shows the name of the application by default', () => {
    expect(heading(render(undefined))).toBe('Demo Application');
  });

  it('lets the title prop override the name', () => {
    expect(heading(render('Handbook'))).toBe('Handbook');
  });

  it('follows a renamed application', () => {
    const context = testApplicationContext({ name: 'Before' });
    const fixture = render(undefined, context);

    context.name.set('After');
    fixture.detectChanges();

    expect(heading(fixture)).toBe('After');
  });

  it('renders nothing outside an application with no title set', () => {
    expect(heading(render(undefined, undefined, false))).toBeUndefined();
  });
});
