import { computed, inject } from '@angular/core';
import { withDevtools } from '@angular-architects/ngrx-toolkit';
import { patchState, signalStore, withComputed, withMethods, withState } from '@ngrx/signals';
import { httpErrorMessage } from '@processpuzzle/util';
import { firstValueFrom } from 'rxjs';
import { ImportReport, InstalledStarter } from './starter';
import { StarterService } from './starter.service';

type StarterImportState = {
  /** The bundle the user picked. Picking another discards every report about the previous one. */
  bundle: File | undefined;
  /** The dry run of {@link StarterImportState.bundle}. */
  preview: ImportReport | undefined;
  /** The applied import of {@link StarterImportState.bundle}. */
  applied: ImportReport | undefined;
  installed: InstalledStarter[];
  isBusy: boolean;
  /** A failure that carried no report — no network, 403, 500. A refused import is a report, not this. */
  error: string | undefined;
};

const initialState: StarterImportState = {
  bundle: undefined,
  preview: undefined,
  applied: undefined,
  installed: [],
  isBusy: false,
  error: undefined,
};

/**
 * The import screen's state: preview first, then apply.
 *
 * **Apply only what was previewed.** The Import action is enabled only once a dry run of the *current*
 * bundle came back `would-apply`; picking a different file clears the preview. The server would refuse a
 * bad bundle anyway — this is about the user seeing what will change before it changes, which is the
 * point of the dry run in the design.
 */
export const StarterImportStore = signalStore(
  withState(initialState),
  withComputed((store) => ({
    canPreview: computed(() => !!store.bundle() && !store.isBusy()),
    canApply: computed(() => !!store.bundle() && !store.isBusy() && !store.applied() && store.preview()?.status === 'would-apply'),
    /** The report to show: the applied one once there is one, the preview before. */
    report: computed(() => store.applied() ?? store.preview()),
  })),
  withMethods((store, service = inject(StarterService)) => {
    async function run(dryRun: boolean): Promise<void> {
      const bundle = store.bundle();
      if (!bundle) return;
      patchState(store, { isBusy: true, error: undefined });
      try {
        const report = await firstValueFrom(service.importBundle(bundle, dryRun));
        patchState(store, dryRun ? { preview: report } : { applied: report });
        if (!dryRun && report.status === 'applied') await loadInstalled();
      } catch (error: unknown) {
        patchState(store, { error: httpErrorMessage(error) });
      } finally {
        patchState(store, { isBusy: false });
      }
    }

    async function loadInstalled(): Promise<void> {
      try {
        patchState(store, { installed: await firstValueFrom(service.listInstalled()) });
      } catch (error: unknown) {
        patchState(store, { error: httpErrorMessage(error) });
      }
    }

    return {
      selectBundle(bundle: File | undefined): void {
        patchState(store, { bundle, preview: undefined, applied: undefined, error: undefined });
      },
      preview: () => run(true),
      apply: () => run(false),
      loadInstalled,
    };
  }),
  withDevtools('StarterImport'),
);
