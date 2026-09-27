import { signal } from '@angular/core';
import { ApplicationContext, NavMenuItem } from './application-context';

/** A writable stand-in for the shell's context, for specs of the widgets that read it. */
export function testApplicationContext(init: { name?: string; logoUrl?: string; navItems?: NavMenuItem[] } = {}) {
  return {
    name: signal(init.name ?? ''),
    logoUrl: signal<string | undefined>(init.logoUrl),
    navItems: signal<NavMenuItem[]>(init.navItems ?? []),
  } satisfies ApplicationContext;
}
