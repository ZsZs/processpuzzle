import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { WorkingContextService } from '@processpuzzle/base-entity';
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

  it.each([['test-entity'], ['test-entity-component'], ['related-entity'], ['trunk-data'], ['dynamic-entity'], ['demo-root'], ['demo-component-one'], ['demo-component-two']])('highlights the toggle of the sample being shown: %s', async (sample) => {
    expect(await selectedButtonAt(sample)).toBe(sample);
  });

  it('highlights nothing for a sample it does not know', async () => {
    expect(await selectedButtonAt('something-else')).toBe('');
  });
});

describe('SamplesComponent context object banner', () => {
  const bannerOf = async (remembered: string | undefined, entity: Record<string, unknown> | undefined): Promise<string> => {
    const load = vi.fn().mockResolvedValue(entity);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideTranslocoTesting({ translations: { en: {} } }),
        provideRouter([{ path: 'samples/:sample', component: SamplesComponent }]),
        { provide: WorkingContextService, useValue: { current: () => undefined, lastId: () => remembered, load } },
      ],
    });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/samples/demo-root', SamplesComponent);
    await harness.fixture.whenStable();
    harness.fixture.detectChanges();
    return (harness.routeNativeElement as HTMLElement).querySelector('[data-testid="context-object-banner"]')?.textContent?.trim() ?? '';
  };

  it('names the remembered context object', async () => {
    expect(await bannerOf('r1', { id: 'r1', name: 'Demo Root Alpha' })).toBe('Context object: Demo Root Alpha');
  });

  it('falls back to the id for an object without a name', async () => {
    expect(await bannerOf('r1', { id: 'r1' })).toBe('Context object: r1');
  });

  it('says none is selected when nothing is remembered', async () => {
    expect(await bannerOf(undefined, undefined)).toBe('Context object: none selected');
  });

  it('says none is selected when the remembered object no longer loads', async () => {
    expect(await bannerOf('gone', undefined)).toBe('Context object: none selected');
  });
});
