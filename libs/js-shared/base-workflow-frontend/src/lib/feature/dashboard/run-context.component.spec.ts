import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { WorkflowDashboardStore } from '../../domain/dashboard/workflow-dashboard.store';
import { OTHER_WORKFLOW_INSTANCE_DTO } from '../../domain/execution/test-workflow-instance';
import { RunContextComponent } from './run-context.component';
import { flushDashboardCatalogs, provideDashboardTesting, RUN_ID } from './test-dashboard';

describe('RunContextComponent', () => {
  let fixture: ComponentFixture<RunContextComponent>;
  let store: InstanceType<typeof WorkflowDashboardStore>;

  const render = (): HTMLElement => {
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [RunContextComponent], providers: [provideDashboardTesting()] }).compileComponents();
    store = TestBed.inject(WorkflowDashboardStore);
    fixture = TestBed.createComponent(RunContextComponent);
    flushDashboardCatalogs(TestBed.inject(HttpTestingController));
  });

  it('draws nothing without an open run', () => {
    expect(render().querySelector('[data-testid="run-context"]')).toBeNull();
  });

  // The start event's parameters are namespaced under its id in the context; they read as dotted keys.
  it('lists the run’s variables, an event’s parameters under dotted keys', () => {
    store.selectInstance(RUN_ID);
    const host = render();

    expect(host.querySelector('.context__heading')?.textContent?.trim()).toBe('Context variables');
    expect(host.querySelector('[data-testid="context-head-key"]')?.textContent?.trim()).toBe('Key');
    expect(host.querySelector('[data-testid="context-head-value"]')?.textContent?.trim()).toBe('Value');
    expect(Array.from(host.querySelectorAll('.context__key:not(.context__head)')).map((key) => key.textContent?.trim())).toEqual([
      'channel',
      'priority',
      'order-created.subject',
      'order-created.orderNumber',
      'new_order',
    ]);
    expect(host.querySelector('[data-testid="context-value-order-created.orderNumber"]')?.textContent?.trim()).toBe('ORD-1001');
  });

  it('says so when the run holds no context', () => {
    store.selectInstance(OTHER_WORKFLOW_INSTANCE_DTO.id);

    expect(render().querySelector('[data-testid="run-context-none"]')?.textContent?.trim()).toBe('This run holds no context variables.');
  });
});
