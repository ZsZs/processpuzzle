import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture } from '@angular/core/testing';
import { BaseWorkflowsComponent } from './base-workflows.component';
import { provideRouter } from '@angular/router';
import { MarkdownComponent, provideMarkdown } from 'ngx-markdown';
import { HttpClient, provideHttpClient } from '@angular/common/http';
import { setUpTranslocoTestBed, TranslocoTestConfig } from '@processpuzzle/test-util';

describe('BaseWorkflowsComponent', () => {
  const testConfig: TranslocoTestConfig = { translations: { en: {} } };
  let component: BaseWorkflowsComponent;
  let fixture: ComponentFixture<BaseWorkflowsComponent>;

  beforeEach(async () => {
    const result = await setUpTranslocoTestBed(BaseWorkflowsComponent, testConfig, {
      imports: [MarkdownComponent],
      providers: [provideHttpClient(), provideMarkdown({ loader: HttpClient }), provideRouter([])],
    });
    component = result.component;
    fixture = result.fixture;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
    expect(fixture).toBeTruthy();
  });
});
