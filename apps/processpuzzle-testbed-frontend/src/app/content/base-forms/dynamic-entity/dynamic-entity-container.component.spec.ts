import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { HttpClient, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideMarkdown } from 'ngx-markdown';
import { DynamicEntityContainerComponent } from './dynamic-entity-container.component';

describe('DynamicEntityContainerComponent', () => {
  let component: DynamicEntityContainerComponent;
  let fixture: ComponentFixture<DynamicEntityContainerComponent>;
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    // `clipboard` on <markdown> reaches for the global clipboard.js, which the application loads through
    // project.json's `scripts` but the test bundle does not; without a stand-in ngx-markdown throws while
    // rendering, and an unhandled rejection fails the run even though every assertion passes.
    (globalThis as Record<string, unknown>)['ClipboardJS'] = class {
      destroy(): void {
        /* nothing to release */
      }
    };

    TestBed.configureTestingModule({
      imports: [DynamicEntityContainerComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideMarkdown({ loader: HttpClient }), provideRouter([])],
    });
    fixture = TestBed.createComponent(DynamicEntityContainerComponent);
    component = fixture.componentInstance;
    httpTesting = TestBed.inject(HttpTestingController);
    await fixture.whenStable();
    // The hints are an asset rather than an inline template, so rendering them is an HTTP round trip.
    httpTesting.expectOne(component.hintsSource).flush('### A fully dynamic entity');
    await fixture.whenStable();
  });

  afterEach(() => {
    httpTesting.verify();
    delete (globalThis as Record<string, unknown>)['ClipboardJS'];
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('renders the hints asset above the outlet the generated screens activate in', () => {
    const element: HTMLElement = fixture.nativeElement;

    expect(element.querySelector('h3')?.textContent).toContain('A fully dynamic entity');
    expect(element.querySelector('router-outlet')).toBeTruthy();
  });
});
