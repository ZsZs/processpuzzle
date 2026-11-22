import { HttpClient, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { provideMarkdown } from 'ngx-markdown';
import { describe, expect, it } from 'vitest';
import { OverviewComponent } from './overview.component';

const README_ROOT = 'https://raw.githubusercontent.com/ZsZs/processpuzzle/refs/heads/develop';

describe('OverviewComponent', () => {
  const translations = {
    en: {
      'base-calendar.intro_heading': 'ProcessPuzzle Calendar',
      'base-calendar.frontend_heading': 'Frontend',
      'base-calendar.backend_heading': 'Backend',
      'base-calendar.intro_paragraph_1': 'First paragraph',
      'base-calendar.intro_paragraph_2': 'Second paragraph',
      'base-calendar.intro_frontend_desc': 'About the frontend',
      'base-calendar.intro_backend_desc': 'About the backend',
    },
  };

  function render() {
    TestBed.configureTestingModule({
      imports: [OverviewComponent],
      providers: [provideTranslocoTesting({ translations }), provideHttpClient(), provideHttpClientTesting(), provideMarkdown({ loader: HttpClient })],
    });
    const fixture = TestBed.createComponent(OverviewComponent);
    fixture.detectChanges();
    return { fixture, http: TestBed.inject(HttpTestingController) };
  }

  it('introduces the feature and its two libraries', () => {
    const { fixture } = render();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    for (const expected of ['ProcessPuzzle Calendar', 'First paragraph', 'Second paragraph', '@processpuzzle/base-calendar', 'About the frontend', 'base-calendar-backend', 'About the backend']) {
      expect(text).toContain(expected);
    }
  });

  it('shows the README of each library, read from the repository', () => {
    const { http } = render();

    expect(http.expectOne(`${README_ROOT}/libs/js-shared/base-calendar-frontend/README.md`).request.method).toBe('GET');
    expect(http.expectOne(`${README_ROOT}/libs/java-shared/base-calendar-backend/README.md`).request.method).toBe('GET');
  });
});
