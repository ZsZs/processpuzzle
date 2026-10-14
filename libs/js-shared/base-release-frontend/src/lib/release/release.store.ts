import { computed, inject } from '@angular/core';
import { patchState, signalStore, withComputed, withMethods, withState } from '@ngrx/signals';
import { httpErrorMessage } from '@processpuzzle/util';
import { firstValueFrom } from 'rxjs';
import { PendingChange, PendingRelease, Release } from './release';
import { ReleaseService } from './release.service';

type ReleaseState = {
  pending: PendingRelease | undefined;
  history: Release[];
  /** The release the last Publish made, to confirm it until the next load. */
  released: Release | undefined;
  /** Whether the designer has confirmed the current warnings. Any reload asks again. */
  warningsAcknowledged: boolean;
  isBusy: boolean;
  /** A failure that carried no pending state — no network, 403. A refused release is a pending state, not this. */
  error: string | undefined;
};

const initialState: ReleaseState = { pending: undefined, history: [], released: undefined, warningsAcknowledged: false, isBusy: false, error: undefined };

/**
 * The Release panel's state: what the organization's next release would carry, what objects to it, and the
 * releases made so far.
 *
 * **Publish only what was shown.** The server plans again inside the release and refuses on its own; Publish
 * is enabled only for a pending state the designer is looking at — something pending, no errors, warnings
 * confirmed. A refusal replaces that state with the one the server found, so what is shown is what refused.
 */
export const ReleaseStore = signalStore(
  withState(initialState),
  withComputed((store) => ({
    errors: computed(() => (store.pending()?.problems ?? []).filter((problem) => problem.severity === 'error')),
    warnings: computed(() => (store.pending()?.problems ?? []).filter((problem) => problem.severity === 'warning')),
    /** Pending changes by kind, in release order. */
    changesByKind: computed(() => {
      const groups = new Map<string, PendingChange[]>();
      for (const change of store.pending()?.changes ?? []) groups.set(change.kind, [...(groups.get(change.kind) ?? []), change]);
      return [...groups.entries()].map(([kind, changes]) => ({ kind, changes }));
    }),
  })),
  withComputed((store) => ({
    canPublish: computed(
      () => !store.isBusy() && !!store.pending()?.changes.length && store.errors().length === 0 && (store.warnings().length === 0 || store.warningsAcknowledged()),
    ),
  })),
  withMethods((store, service = inject(ReleaseService)) => {
    async function load(): Promise<void> {
      patchState(store, { isBusy: true, error: undefined, warningsAcknowledged: false });
      try {
        const [pending, history] = await Promise.all([firstValueFrom(service.pending()), firstValueFrom(service.history())]);
        patchState(store, { pending, history });
      } catch (error: unknown) {
        patchState(store, { error: httpErrorMessage(error) });
      } finally {
        patchState(store, { isBusy: false });
      }
    }

    return {
      load,
      acknowledgeWarnings(acknowledged: boolean): void {
        patchState(store, { warningsAcknowledged: acknowledged });
      },
      async publish(): Promise<void> {
        if (!store.canPublish()) return;
        patchState(store, { isBusy: true, error: undefined, released: undefined });
        try {
          const outcome = await firstValueFrom(service.publish(store.warningsAcknowledged()));
          if ('refused' in outcome) {
            patchState(store, { pending: outcome.refused, warningsAcknowledged: false, isBusy: false });
            return;
          }
          patchState(store, { isBusy: false });
          await load();
          patchState(store, { released: outcome.released });
        } catch (error: unknown) {
          patchState(store, { error: httpErrorMessage(error), isBusy: false });
        }
      },
    };
  }),
);
