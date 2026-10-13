import { beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { SamplesComponent } from './samples.component';

describe('base-release SamplesComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideTranslocoTesting({ translations: { en: {} } }), provideRouter([{ path: 'base-release/samples', component: SamplesComponent }])],
    });
  });

  it('offers no sample yet, so nothing is highlighted', async () => {
    const harness = await RouterTestingHarness.create();
    const component = await harness.navigateByUrl('/base-release/samples', SamplesComponent);

    expect(component.tabs).toEqual([]);
    expect(component.selectedButton()).toBe('');
  });
});
