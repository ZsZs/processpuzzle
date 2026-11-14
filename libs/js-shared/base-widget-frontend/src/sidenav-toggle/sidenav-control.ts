import { InjectionToken, Signal } from '@angular/core';

/**
 * What a chrome widget may do with the sidenav of the application it is placed in. Declared here, below
 * base-app, like {@link ApplicationContext}; base-app's shell provides it. Optional everywhere it is injected:
 * outside a shell, or in an application without a sidenav, `sidenav-toggle` renders nothing.
 */
export interface SidenavControl {
  /** Whether there is a sidenav to show or hide right now. */
  readonly available: Signal<boolean>;
  readonly opened: Signal<boolean>;
  toggle(): void;
}

export const SIDENAV_CONTROL = new InjectionToken<SidenavControl>('SIDENAV_CONTROL');
