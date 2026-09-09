import { beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { SamplesComponent } from './samples.component';
import { ORDER_PATH, SPECIAL_ORDER_PATH } from './rule-sample.routes';

describe('base-rule SamplesComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideTranslocoTesting({ translations: { en: {} } }), provideRouter([{ path: 'base-rule/samples/:sample', component: SamplesComponent }])],
    });
  });

  const selectedSampleAt = async (sample: string): Promise<string> => {
    const harness = await RouterTestingHarness.create();
    const component = await harness.navigateByUrl(`/base-rule/samples/${sample}`, SamplesComponent);
    return component.selectedSample();
  };

  it.each([[ORDER_PATH], [SPECIAL_ORDER_PATH]])('highlights the toggle of the sample being shown: %s', async (sample) => {
    expect(await selectedSampleAt(sample)).toBe(sample);
  });

  it('does not mistake special-order for order', async () => {
    // `special-order` ends in `order`; a substring test would light the wrong toggle on every Special Order
    // screen, which is why the URL is matched by whole segments.
    expect(await selectedSampleAt(SPECIAL_ORDER_PATH)).not.toBe(ORDER_PATH);
  });

  it('highlights nothing for a sample it does not know', async () => {
    expect(await selectedSampleAt('something-else')).toBe('');
  });
});
