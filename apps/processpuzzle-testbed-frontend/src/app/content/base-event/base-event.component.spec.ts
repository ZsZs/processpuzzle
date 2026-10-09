import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture } from '@angular/core/testing';
import { BaseEventComponent } from './base-event.component';
import { provideRouter } from '@angular/router';
import { MarkdownComponent, provideMarkdown } from 'ngx-markdown';
import { HttpClient, provideHttpClient } from '@angular/common/http';
import { setUpTranslocoTestBed, TranslocoTestConfig } from '@processpuzzle/test-util';

describe('BaseEventComponent', () => {
  const testConfig: TranslocoTestConfig = { translations: { en: {} } };
  let component: BaseEventComponent;
  let fixture: ComponentFixture<BaseEventComponent>;

  beforeEach(async () => {
    const result = await setUpTranslocoTestBed(BaseEventComponent, testConfig, {
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
