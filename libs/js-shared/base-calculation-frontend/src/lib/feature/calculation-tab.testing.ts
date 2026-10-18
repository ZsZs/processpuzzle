import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { ORDER_TOTAL_DTO, SERVICE_ROOT } from '../domain/test-calculation-definition';

/** The providers a tab spec needs: HTTP under test, translations echoing their keys, and the service root. */
export function configureCalculationTabTest(): void {
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideTranslocoTesting({ translations: { en: {} } }),
      { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { CALCULATION_SERVICE_ROOT: SERVICE_ROOT } } },
    ],
  });
}

/** Answers the store's list request on init with the seeded order total. */
export function flushDefinitions(rows: object[] = [ORDER_TOTAL_DTO]): HttpTestingController {
  const controller = TestBed.inject(HttpTestingController);
  controller.expectOne(`${SERVICE_ROOT}/calculation-definitions`).flush(rows);
  return controller;
}

export function byTestId(root: HTMLElement, testId: string): HTMLElement | null {
  return root.querySelector<HTMLElement>(`[data-testid="${testId}"]`);
}

/** Lets a component's awaited request chain finish — its `finally` included — and renders the outcome. */
export async function settle(fixture: { whenStable(): Promise<unknown>; detectChanges(): void }): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve));
  await fixture.whenStable();
  fixture.detectChanges();
}
