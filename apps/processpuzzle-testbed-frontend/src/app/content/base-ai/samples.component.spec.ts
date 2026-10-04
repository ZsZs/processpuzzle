import { beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { SAMPLE_PHOTO_URL, SamplesComponent } from './samples.component';
import { BOAT_PATH, RECOGNIZE_PATH } from './boat-sample.routes';
import { OBSERVATION_PATH, RACE_PATH, REGISTRATION_PATH } from './race-sample.routes';

describe('base-ai SamplesComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideTranslocoTesting({ translations: { en: {} } }), provideRouter([{ path: 'base-ai/samples/:sample', component: SamplesComponent }])],
    });
  });

  const render = async (sample: string) => {
    const harness = await RouterTestingHarness.create();
    const component = await harness.navigateByUrl(`/base-ai/samples/${sample}`, SamplesComponent);
    return { component, element: harness.routeNativeElement as HTMLElement };
  };

  it.each([['recognition-profile'], [BOAT_PATH], [RACE_PATH], [REGISTRATION_PATH], [RECOGNIZE_PATH], [OBSERVATION_PATH]])('highlights the toggle of the sample being shown: %s', async (sample) => {
    expect((await render(sample)).component.selectedButton()).toBe(sample);
  });

  it('highlights nothing for a sample it does not know', async () => {
    expect((await render('something-else')).component.selectedButton()).toBe('');
  });

  it('walks the user through the sample, linking the photo to try', async () => {
    const { element } = await render(BOAT_PATH);
    expect(element.querySelectorAll('ol li')).toHaveLength(7);
    expect(element.querySelector(`a[href="${SAMPLE_PHOTO_URL}"]`)).not.toBeNull();
  });
});
