import { beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { SamplesComponent } from './samples.component';

describe('SamplesComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideTranslocoTesting({ translations: { en: {} } }), provideRouter([{ path: 'samples/:sample', component: SamplesComponent }])],
    });
  });

  const selectedButtonAt = async (sample: string): Promise<string> => {
    const harness = await RouterTestingHarness.create();
    const component = await harness.navigateByUrl(`/samples/${sample}`, SamplesComponent);
    return component.selectedButton();
  };

  it.each([['test-entity'], ['test-entity-component'], ['related-entity'], ['trunk-data'], ['dynamic-entity']])('highlights the toggle of the sample being shown: %s', async (sample) => {
    expect(await selectedButtonAt(sample)).toBe(sample);
  });

  it('highlights nothing for a sample it does not know', async () => {
    expect(await selectedButtonAt('something-else')).toBe('');
  });
});
