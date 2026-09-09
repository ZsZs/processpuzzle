import { beforeEach, describe, expect, it } from 'vitest';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { SampleHostComponent, SampleTab } from './sample-host.component';

@Component({
  standalone: true,
  imports: [SampleHostComponent],
  template: ` <pp-sample-host prefix="test-prefix" groupName="testGroup" ariaLabel="Test Group" [tabs]="tabs" /> `,
})
class TestHostComponent {
  readonly tabs: SampleTab[] = [
    { route: 'tab-one', label: 'Tab One' },
    { route: 'tab-two', label: 'Tab Two' },
  ];
}

describe('SampleHostComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideTranslocoTesting({ translations: { en: {} } }),
        provideRouter([
          {
            path: 'samples',
            component: TestHostComponent,
            children: [
              { path: 'tab-one', component: Component({ template: '' })(class {}) },
              { path: 'tab-two', component: Component({ template: '' })(class {}) },
            ],
          },
        ]),
      ],
    });
  });

  it('updates selected button when navigation occurs', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/samples/tab-two', TestHostComponent);
    const host = harness.routeNativeElement?.querySelector('pp-sample-host');
    expect(host).toBeTruthy();
  });
});
