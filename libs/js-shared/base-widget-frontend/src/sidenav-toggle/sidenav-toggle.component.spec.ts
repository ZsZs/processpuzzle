import { signal } from '@angular/core';
import { setUpTranslocoTestBed } from '@processpuzzle/test-util';
import { describe, expect, it, vi } from 'vitest';
import widgetsEn from '../assets/i18n/widgets/en.json';
import { SIDENAV_CONTROL, SidenavControl } from './sidenav-control';
import { SidenavToggleComponent } from './sidenav-toggle.component';

describe('SidenavToggleComponent', () => {
  async function render(control?: SidenavControl) {
    const { fixture } = await setUpTranslocoTestBed(
      SidenavToggleComponent,
      { scope: 'widgets', translations: { en: {}, 'widgets/en': widgetsEn } },
      { providers: control ? [{ provide: SIDENAV_CONTROL, useValue: control }] : [] },
    );
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('renders nothing without a sidenav control', async () => {
    expect((await render()).querySelector('button')).toBeNull();
  });

  it('renders nothing when the application has no sidenav', async () => {
    const control = { available: signal(false), opened: signal(true), toggle: vi.fn() };
    expect((await render(control)).querySelector('button')).toBeNull();
  });

  it('toggles the sidenav and labels the action', async () => {
    const control = { available: signal(true), opened: signal(true), toggle: vi.fn() };
    const host = await render(control);
    const button = host.querySelector('[test-id="sidenav-toggle"]') as HTMLButtonElement;

    expect(button.getAttribute('aria-label')).toBe(widgetsEn.sidenav_toggle.hide);
    button.click();
    expect(control.toggle).toHaveBeenCalledOnce();
  });
});
