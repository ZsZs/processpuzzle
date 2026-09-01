import { beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { WORKFLOW_DASHBOARD_PATH } from '@processpuzzle/base-workflow';
import { SamplesComponent } from './samples.component';

describe('base-workflows SamplesComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideTranslocoTesting({ translations: { en: {} } }), provideRouter([{ path: 'base-workflows/samples/:sample', component: SamplesComponent }])],
    });
  });

  const selectedButtonAt = async (sample: string): Promise<string> => {
    const harness = await RouterTestingHarness.create();
    const component = await harness.navigateByUrl(`/base-workflows/samples/${sample}`, SamplesComponent);
    return component.selectedButton();
  };

  // My Tasks first, because it is the screen an end user works from — the rest are what a designer authors
  // beforehand. `WORKFLOW_DASHBOARD_PATH` rather than the literal, so the toggle and the route it links to
  // cannot drift apart.
  it.each([[WORKFLOW_DASHBOARD_PATH], ['workflow'], ['tool-definition'], ['workflow-instance']])('highlights the toggle of the sample being shown: %s', async (sample) => {
    expect(await selectedButtonAt(sample)).toBe(sample);
  });

  // Through the harness rather than `new SamplesComponent()`: the component reads a `viewChild`, which only
  // resolves inside an injection context.
  it('offers the dashboard as the first toggle', async () => {
    const harness = await RouterTestingHarness.create();
    const component = await harness.navigateByUrl(`/base-workflows/samples/${WORKFLOW_DASHBOARD_PATH}`, SamplesComponent);

    expect(component.tabs.map((tab) => tab.route)).toEqual([WORKFLOW_DASHBOARD_PATH, 'workflow', 'tool-definition', 'workflow-instance']);
  });

  it('highlights nothing for a sample it does not know', async () => {
    expect(await selectedButtonAt('something-else')).toBe('');
  });
});
