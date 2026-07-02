import { computed } from '@angular/core';
import { patchState, signalStore, withComputed, withMethods, withState } from '@ngrx/signals';
import { withDevtools } from '@angular-architects/ngrx-toolkit';
import { RuleEvaluationResult } from './rule-engine';

interface RuleViolationsState {
  entityName: string | undefined;
  violations: RuleEvaluationResult[];
}

const INITIAL_STATE: RuleViolationsState = {
  entityName: undefined,
  violations: [],
};

export const RuleViolationsSingletonStore = signalStore(
  { providedIn: 'root' },
  withState<RuleViolationsState>(INITIAL_STATE),
  withDevtools('Rule Violations'),
  withMethods((store) => ({
    setViolations(entityName: string, violations: RuleEvaluationResult[]): void {
      patchState(store, { entityName, violations });
    },
    clearViolations(): void {
      patchState(store, INITIAL_STATE);
    },
  })),
  withComputed((store) => ({
    errorCount: computed(() => store.violations().filter((v) => v.severity === 'ERROR').length),
    warningCount: computed(() => store.violations().filter((v) => v.severity === 'WARNING').length),
    infoCount: computed(() => store.violations().filter((v) => v.severity === 'INFO').length),
    hasViolations: computed(() => store.violations().length > 0),
  })),
);
