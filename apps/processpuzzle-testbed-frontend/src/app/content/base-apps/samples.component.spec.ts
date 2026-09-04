import { beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { SamplesComponent } from './samples.component';

describe('base-apps SamplesComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideTranslocoTesting({ translations: { en: {} } }), provideRouter([{ path: 'base-apps/samples/:sample', component: SamplesComponent }])],
    });
  });

  const selectedButtonAt = async (sample: string): Promise<string> => {
    const harness = await RouterTestingHarness.create();
    const component = await harness.navigateByUrl(`/base-apps/samples/${sample}`, SamplesComponent);
    return component.selectedButton();
  };

  it('highlights the toggle of the sample being shown: app-definition', async () => {
    expect(await selectedButtonAt('app-definition')).toBe('app-definition');
  });

  it('highlights nothing for a sample it does not know', async () => {
    expect(await selectedButtonAt('something-else')).toBe('');
  });
});
