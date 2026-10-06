import { computed, inject } from '@angular/core';
import { withDevtools } from '@angular-architects/ngrx-toolkit';
import { patchState, signalStore, withComputed, withMethods, withState } from '@ngrx/signals';
import { httpErrorMessage } from '@processpuzzle/util';
import { firstValueFrom } from 'rxjs';
import { CatalogStarter, ImportReport, InstalledStarter, StarterSelection } from './starter';
import { StarterService } from './starter.service';

type StarterImportState = {
  catalog: CatalogStarter[];
  /** The starter and version the user picked. Picking another discards every report about the previous one. */
  selection: StarterSelection | undefined;
  /** The dry run of {@link StarterImportState.selection}. */
  preview: ImportReport | undefined;
  /** The applied install of {@link StarterImportState.selection}. */
  applied: ImportReport | undefined;
  installed: InstalledStarter[];
  isBusy: boolean;
  /** A failure that carried no report — no network, 403, 404, 503. A refused install is a report, not this. */
  error: string | undefined;
};

const initialState: StarterImportState = {
  catalog: [],
  selection: undefined,
  preview: undefined,
  applied: undefined,
  installed: [],
  isBusy: false,
  error: undefined,
};

/**
 * The install screen's state: pick a catalog starter, preview, then install.
 *
 * **Install only what was previewed.** The Install action is enabled only once a dry run of the *current*
 * selection came back `would-apply`; picking another starter or version clears the preview. The server would
 * refuse a bad install anyway — this is about the user seeing what will change, deletions included, before
 * it changes, which is the point of the dry run in the design.
 */
export const StarterImportStore = signalStore(
  withState(initialState),
  withComputed((store) => ({
    canPreview: computed(() => !!store.selection() && !store.isBusy()),
    canApply: computed(() => !!store.selection() && !store.isBusy() && !store.applied() && store.preview()?.status === 'would-apply'),
    /** The report to show: the applied one once there is one, the preview before. */
    report: computed(() => store.applied() ?? store.preview()),
    selectedStarter: computed(() => store.catalog().find((starter) => starter.id === store.selection()?.starterId)),
  })),
  withMethods((store, service = inject(StarterService)) => {
    async function run(dryRun: boolean): Promise<void> {
      const selection = store.selection();
      if (!selection) return;
      patchState(store, { isBusy: true, error: undefined });
      try {
        const report = await firstValueFrom(service.install(selection, dryRun));
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

    async function loadCatalog(): Promise<void> {
      try {
        patchState(store, { catalog: await firstValueFrom(service.listCatalog()) });
      } catch (error: unknown) {
        patchState(store, { error: httpErrorMessage(error) });
      }
    }

    return {
      /** Selects a starter at `version`, or at its newest version when none is given. */
      select(starterId: string, version?: string): void {
        const starter = store.catalog().find((candidate) => candidate.id === starterId);
        const chosen = version ?? starter?.versions[0]?.version;
        const selection = starter && chosen ? { starterId, version: chosen } : undefined;
        patchState(store, { selection, preview: undefined, applied: undefined, error: undefined });
      },
      preview: () => run(true),
      apply: () => run(false),
      loadCatalog,
      loadInstalled,
    };
  }),
  withDevtools('StarterImport'),
);
