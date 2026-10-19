import { describe, expect, it } from 'vitest';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { TASK_DASHBOARD_ROUTE } from '@processpuzzle/util';
import { TaskDashboardButtonComponent } from './task-dashboard-button.component';

@Component({ template: '' })
class BlankComponent {}

describe('TaskDashboardButtonComponent', () => {
  async function renderAt(url: string, route?: string) {
    TestBed.configureTestingModule({
      imports: [TaskDashboardButtonComponent],
      providers: [
        provideRouter([
          { path: 'home', component: BlankComponent },
          { path: 'workflow-dashboard', component: BlankComponent },
          { path: 'base-workflow/samples/workflow-dashboard', component: BlankComponent },
        ]),
        ...(route ? [{ provide: TASK_DASHBOARD_ROUTE, useValue: route }] : []),
      ],
    });
    await TestBed.inject(Router).navigateByUrl(url);
    const fixture = TestBed.createComponent(TaskDashboardButtonComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('leads to the dashboard at the root by default', async () => {
    const link = (await renderAt('/home')).querySelector('a');

    expect(link?.getAttribute('href')).toBe('/workflow-dashboard');
    expect(link?.getAttribute('aria-label')).toBe('Task Dashboard Button');
  });

  it('leads wherever the host mounts the dashboard', async () => {
    const link = (await renderAt('/home', '/base-workflow/samples/workflow-dashboard')).querySelector('a');

    expect(link?.getAttribute('href')).toBe('/base-workflow/samples/workflow-dashboard');
  });

  it('marks itself while the dashboard is open', async () => {
    const link = (await renderAt('/workflow-dashboard')).querySelector('a');

    expect(link?.classList.contains('task-dashboard-button--active')).toBe(true);
    expect(link?.getAttribute('aria-current')).toBe('page');
  });

  it('is not marked elsewhere', async () => {
    const link = (await renderAt('/home')).querySelector('a');

    expect(link?.classList.contains('task-dashboard-button--active')).toBe(false);
  });
});
