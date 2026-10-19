import { ComponentFixture } from '@angular/core/testing';
import { setUpTranslocoTestBed, TranslocoTestConfig } from '@processpuzzle/test-util';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { PendingRelease, Release } from './release';
import { ReleasePanelComponent } from './release-panel.component';
import { ReleaseService } from './release.service';

const release: Release = { releaseNumber: 4, releasedAt: '2026-10-11T10:00:00Z', releasedBy: 'designer', items: [{ kind: 'app', key: 'app', change: 'changed' }] };
const warning = { severity: 'warning' as const, kind: 'app', key: 'app', errorId: 'release.unresolved-reference', message: 'Mounts a missing module.' };
const error = { ...warning, severity: 'error' as const, message: 'Renders a missing entity.' };

function pending(problems: PendingRelease['problems'] = []): PendingRelease {
  return {
    changes: [
      { kind: 'module', key: 'claims', change: 'added' },
      { kind: 'module', key: 'orders', change: 'removed' },
      { kind: 'app', key: 'app', change: 'changed' },
    ],
    problems,
  };
}

describe('ReleasePanelComponent', () => {
  const testConfig: TranslocoTestConfig = { translations: { en: {} } };
  const service = { pending: vi.fn(), publish: vi.fn(), history: vi.fn(), pendingSteps: vi.fn(), replacePendingSteps: vi.fn() };
  let fixture: ComponentFixture<ReleasePanelComponent>;

  async function render(): Promise<HTMLElement> {
    const result = await setUpTranslocoTestBed(ReleasePanelComponent, testConfig, {
      providers: [{ provide: ReleaseService, useValue: service }],
    });
    fixture = result.fixture;
    await settle();
    return fixture.nativeElement as HTMLElement;
  }

  async function settle(): Promise<void> {
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function byTestId(element: HTMLElement, id: string): HTMLElement | null {
    return element.querySelector(`[data-testid="${id}"]`);
  }

  beforeEach(() => {
    vi.resetAllMocks();
    service.pending.mockReturnValue(of(pending()));
    service.history.mockReturnValue(of([release]));
    service.pendingSteps.mockReturnValue(of([]));
  });

  it('lists the pending changes, naming each kind once', async () => {
    const element = await render();

    expect(byTestId(element, 'release-change-module-claims')).not.toBeNull();
    expect(byTestId(element, 'release-change-module-orders')).not.toBeNull();
    expect(byTestId(element, 'release-change-app-app')).not.toBeNull();
    const kindCells = [...element.querySelectorAll('[data-testid="release-changes"] tbody tr td:first-child')].map((cell) => cell.textContent?.trim());
    expect(kindCells.filter((text) => text).length).toBe(2);
  });

  it('lists the release history', async () => {
    const element = await render();

    expect(byTestId(element, 'release-history')?.textContent).toContain('#4');
    expect(byTestId(element, 'release-no-history')).toBeNull();
  });

  it('says so when nothing is pending or released, and disables Publish', async () => {
    service.pending.mockReturnValue(of({ changes: [], problems: [] }));
    service.history.mockReturnValue(of([]));
    const element = await render();

    expect(byTestId(element, 'release-nothing-pending')).not.toBeNull();
    expect(byTestId(element, 'release-no-history')).not.toBeNull();
    expect((byTestId(element, 'release-publish') as HTMLButtonElement).disabled).toBe(true);
  });

  it('shows errors and offers no way past them', async () => {
    service.pending.mockReturnValue(of(pending([error])));
    const element = await render();

    expect(byTestId(element, 'release-problems')?.textContent).toContain('Renders a missing entity.');
    expect(byTestId(element, 'release-acknowledge')).toBeNull();
    expect((byTestId(element, 'release-publish') as HTMLButtonElement).disabled).toBe(true);
  });

  it('enables Publish over warnings once they are confirmed', async () => {
    service.pending.mockReturnValue(of(pending([warning])));
    const element = await render();
    const publish = byTestId(element, 'release-publish') as HTMLButtonElement;

    expect(byTestId(element, 'release-problems')?.textContent).toContain('Mounts a missing module.');
    expect(publish.disabled).toBe(true);

    (byTestId(element, 'release-acknowledge')?.querySelector('input') as HTMLInputElement).click();
    await settle();

    expect(publish.disabled).toBe(false);
  });

  it('publishes and confirms the release it made', async () => {
    service.publish.mockReturnValue(of({ released: release }));
    const element = await render();

    (byTestId(element, 'release-publish') as HTMLButtonElement).click();
    await settle();

    expect(service.publish).toHaveBeenCalledWith(false);
    expect(byTestId(element, 'release-done')).not.toBeNull();
  });

  it('offers the resolution of a problem, and applies it with the value chosen', async () => {
    const removed = {
      ...warning,
      kind: 'entity',
      key: 'customer',
      message: 'customer.customerName is removed.',
      resolutions: [
        {
          step: { kind: 'entity', key: 'customer', operation: 'renameAttribute', subject: 'customerName', parameters: {} },
          input: 'to',
          inputKind: 'text' as const,
          choices: ['name', 'fullName'],
        },
      ],
    };
    service.pending.mockReturnValue(of(pending([removed])));
    service.replacePendingSteps.mockReturnValue(of([]));
    const element = await render();
    const apply = byTestId(element, 'resolution-apply') as HTMLButtonElement;

    expect(byTestId(element, 'resolution-renameAttribute')).not.toBeNull();
    expect(apply.disabled).toBe(true);

    const choice = byTestId(element, 'resolution-choice') as HTMLSelectElement;
    choice.value = 'fullName';
    choice.dispatchEvent(new Event('change'));
    await settle();
    apply.click();
    await settle();

    expect(service.replacePendingSteps).toHaveBeenCalledWith([
      { kind: 'entity', key: 'customer', operation: 'renameAttribute', subject: 'customerName', id: undefined, parameters: { to: 'fullName' } },
    ]);
  });

  it('fills with a typed value, and with a yes/no', async () => {
    const unfilled = {
      ...error,
      kind: 'entity',
      key: 'invoice',
      resolutions: [
        { step: { kind: 'entity', key: 'invoice', operation: 'fillAttribute', subject: 'total' }, input: 'value', inputKind: 'number' as const },
        { step: { kind: 'entity', key: 'invoice', operation: 'fillAttribute', subject: 'paid' }, input: 'value', inputKind: 'boolean' as const },
      ],
    };
    service.pending.mockReturnValue(of(pending([unfilled])));
    service.replacePendingSteps.mockReturnValue(of([]));
    const element = await render();

    const value = byTestId(element, 'resolution-value') as HTMLInputElement;
    expect(value.type).toBe('number');
    value.value = '7';
    value.dispatchEvent(new Event('input'));
    await settle();
    (element.querySelectorAll('[data-testid="resolution-apply"]')[0] as HTMLButtonElement).click();
    await settle();

    expect(service.replacePendingSteps).toHaveBeenLastCalledWith([expect.objectContaining({ subject: 'total', parameters: { value: 7 } })]);

    (byTestId(element, 'resolution-flag')?.querySelector('input') as HTMLInputElement).click();
    await settle();
    (element.querySelectorAll('[data-testid="resolution-apply"]')[1] as HTMLButtonElement).click();
    await settle();

    expect(service.replacePendingSteps).toHaveBeenLastCalledWith([expect.objectContaining({ subject: 'paid', parameters: { value: true } })]);
  });

  it('lists the pending steps with what each would do, and removes one', async () => {
    const step = { id: 's-1', kind: 'entity', key: 'invoice', operation: 'fillAttribute', subject: 'currency', parameters: { value: 'EUR' } };
    service.pendingSteps.mockReturnValue(of([step]));
    service.pending.mockReturnValue(of({ ...pending(), steps: [{ step, outcome: 'skipped' as const, reason: 'No such attribute.' }] }));
    service.replacePendingSteps.mockReturnValue(of([]));
    const element = await render();

    expect(byTestId(element, 'release-step-0')?.textContent).toContain('No such attribute.');
    expect(byTestId(element, 'release-step-0')?.textContent).toContain('EUR');

    (byTestId(element, 'release-step-remove-0') as HTMLButtonElement).click();
    await settle();

    expect(service.replacePendingSteps).toHaveBeenCalledWith([]);
  });

  it('counts the steps a past release ran', async () => {
    const step = { kind: 'entity', key: 'invoice', operation: 'fillAttribute', subject: 'currency' };
    service.history.mockReturnValue(of([{ ...release, steps: [{ step, outcome: 'applied' as const, affectedObjects: 2 }] }]));
    const element = await render();

    expect(byTestId(element, 'release-history')?.textContent).toContain('base_release.release.steps-run');
  });

  it('shows a failure that carried no pending state', async () => {
    service.pending.mockReturnValue(throwError(() => new Error('offline')));
    const element = await render();

    expect(byTestId(element, 'release-error')).not.toBeNull();
  });
});
