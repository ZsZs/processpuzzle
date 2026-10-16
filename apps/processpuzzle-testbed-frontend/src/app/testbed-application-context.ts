import { computed, inject, Provider, signal } from '@angular/core';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { DesignRouteService } from '@processpuzzle/design';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { APPLICATION_CONTEXT, type ApplicationContext } from '@processpuzzle/widgets';

/**
 * The testbed as the application its chrome widgets — the header's `pp-app-logo` and `pp-app-title` — are
 * placed in: the same widgets a base-app application puts in its header, fed here by hand rather than from an
 * `AppDefinition`. The name follows the Design Tools, which share the testbed's header.
 *
 * Application-wide, because the testbed *is* the application. A base-app shell rendered inside it, as the
 * designer's Preview tab does, provides its own context to its own subtree, which takes precedence there.
 */
export function provideTestbedApplicationContext(): Provider {
  return { provide: APPLICATION_CONTEXT, useFactory: testbedApplicationContext };
}

function testbedApplicationContext(): ApplicationContext {
  const isDesignRoute = inject(DesignRouteService).isDesignRoute;
  return {
    name: computed(() => (isDesignRoute() ? 'Design Tools' : 'ProcessPuzzle Testbed')),
    logoUrl: signal('assets/processpuzzle-logo-with-title.jpg'),
    navItems: signal([]),
  };
}
