import { beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { SamplesComponent } from './samples.component';

describe('base-states SamplesComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideTranslocoTesting({ translations: { en: {} } }), provideRouter([{ path: 'base-states/samples/:sample', component: SamplesComponent }])],
    });
  });

  const selectedButtonAt = async (sample: string): Promise<string> => {
    const harness = await RouterTestingHarness.create();
    const component = await harness.navigateByUrl(`/base-states/samples/${sample}`, SamplesComponent);
    return component.selectedButton();
  };

  it('highlights the toggle of the sample being shown: state-machine-definition', async () => {
    expect(await selectedButtonAt('state-machine-definition')).toBe('state-machine-definition');
  });

  it('highlights nothing for a sample it does not know', async () => {
    expect(await selectedButtonAt('something-else')).toBe('');
  });
});
