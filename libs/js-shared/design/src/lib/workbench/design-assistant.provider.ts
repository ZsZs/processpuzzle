import { InjectionToken, Provider } from '@angular/core';

/**
 * Whether the design workbench offers the design assistant beside its designers. Off unless the hosting application
 * says otherwise with {@link provideDesignAssistant}: the assistant is a backend feature an application stack enables
 * (`processpuzzle.ai.assistant.enabled`), and a toggle that led to a 404 would be worse than none.
 */
export const DESIGN_ASSISTANT_ENABLED = new InjectionToken<boolean>('DESIGN_ASSISTANT_ENABLED', {
  providedIn: 'root',
  factory: () => false,
});

/**
 * Turns the design assistant on in the design workbench. An application adds this to its providers when its backend
 * runs the assistant; the panel's translations (`assets/i18n/base_ai`) have to be copied like any other feature's.
 */
export function provideDesignAssistant(): Provider {
  return { provide: DESIGN_ASSISTANT_ENABLED, useValue: true };
}
