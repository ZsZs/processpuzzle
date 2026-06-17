import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture } from '@angular/core/testing';
import { BaseFormsComponent } from './base-forms.component';
import { provideRouter } from '@angular/router';
import { MarkdownComponent, provideMarkdown } from 'ngx-markdown';
import { HttpClient, provideHttpClient } from '@angular/common/http';
import { provideAnimations } from '@angular/platform-browser/animations';
import { setUpTranslocoTestBed, TranslocoTestConfig } from '@processpuzzle/test-util';

describe('BaseFormsComponent', () => {
  const testConfig: TranslocoTestConfig = { translations: { en: {} } };
  let component: BaseFormsComponent;
  let fixture: ComponentFixture<BaseFormsComponent>;

  beforeEach(async () => {
    const result = await setUpTranslocoTestBed(BaseFormsComponent, testConfig, {
      imports: [MarkdownComponent],
      providers: [provideAnimations(), provideHttpClient(), provideMarkdown({ loader: HttpClient }), provideRouter([])],
    });
    component = result.component;
    fixture = result.fixture;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
    expect(fixture).toBeTruthy();
  });
});
