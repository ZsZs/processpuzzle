import { computed, inject } from '@angular/core';
import { withDevtools } from '@angular-architects/ngrx-toolkit';
import { patchState, signalStore, withComputed, withMethods, withState } from '@ngrx/signals';
import { httpErrorMessage } from '@processpuzzle/util';
import { firstValueFrom } from 'rxjs';
import { CatalogStarter, CatalogStarterVersion, ImportReport, InstalledStarter, StarterSelection, UpgradeReport } from './starter';
import { StarterService } from './starter.service';

/** Numeric order of dotted versions (`1.0.10` after `1.0.2`); a qualifier such as `-rc1` is ignored. */
function compareVersions(a: string, b: string): number {
  const parts = (version: string): number[] => version.split('-')[0].split('.').map((part) => Number.parseInt(part, 10) || 0);
  const left = parts(a);
  const right = parts(b);
  for (let i = 0; i < Math.max(left.length, right.length); i++) {
    const difference = (left[i] ?? 0) - (right[i] ?? 0);
    if (difference !== 0) return difference;
  }
  return 0;
}

/**
 * The versions of `starter` that can be picked. Once it is installed, nothing newer than the version on offer to the
 * organization (or, with none on offer, than the installed one) is listed: the catalog knows versions the platform has
 * not offered yet, and an upgrade can reach only the offered one.
 */
function selectable(starter: CatalogStarter, installed: readonly InstalledStarter[]): readonly CatalogStarterVersion[] {
  const own = installed.find((candidate) => candidate.starterId === starter.id);
  if (!own) return starter.versions;
  const newest = own.upgradeAvailable && own.offeredVersion ? own.offeredVersion : own.version;
  return starter.versions.filter((version) => compareVersions(version.version, newest) <= 0);
}

function isInstalled(selection: StarterSelection | undefined, installed: readonly InstalledStarter[]): boolean {
  return !!selection && installed.some((i) => i.starterId === selection.starterId && i.version === selection.version);
}

type StarterImportState = {
  catalog: CatalogStarter[];
  /** The starter and version the user picked. Picking another discards every report about the previous one. */
  selection: StarterSelection | undefined;
  /** The dry run of {@link StarterImportState.selection}. */
  preview: ImportReport | undefined;
  /** The applied install of {@link StarterImportState.selection}. */
  applied: ImportReport | undefined;
  installed: InstalledStarter[];
  /** The installed starter an upgrade is being previewed or applied for. */
  upgradeStarterId: string | undefined;
  /** The dry run of the upgrade of {@link StarterImportState.upgradeStarterId}. */
  upgradePreview: UpgradeReport | undefined;
  /** The applied upgrade. */
  upgradeApplied: UpgradeReport | undefined;
  /** The installed starter that is being previewed or removed. */
  uninstallStarterId: string | undefined;
  /** The dry run of removing {@link StarterImportState.uninstallStarterId}. */
  uninstallPreview: ImportReport | undefined;
  /** The applied removal. */
  uninstallApplied: ImportReport | undefined;
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
  upgradeStarterId: undefined,
  upgradePreview: undefined,
  upgradeApplied: undefined,
  uninstallStarterId: undefined,
  uninstallPreview: undefined,
  uninstallApplied: undefined,
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
    selectableVersions: computed((): readonly CatalogStarterVersion[] => {
      const starter = store.catalog().find((candidate) => candidate.id === store.selection()?.starterId);
      return starter ? selectable(starter, store.installed()) : [];
    }),
    /** The selected version is the one the organization has already: there is nothing to preview or install. */
    selectionInstalled: computed(() => isInstalled(store.selection(), store.installed())),
    canPreview: computed(() => !!store.selection() && !store.isBusy() && !isInstalled(store.selection(), store.installed())),
    canApply: computed(() => !!store.selection() && !store.isBusy() && !store.applied() && store.preview()?.status === 'would-apply'),
    /** The report to show: the applied one once there is one, the preview before. */
    report: computed(() => store.applied() ?? store.preview()),
    /** The upgrade report to show: the applied one once there is one, the preview before. */
    upgradeReport: computed(() => store.upgradeApplied() ?? store.upgradePreview()),
    /** Applying is possible only for the target a dry run just showed, and only if that dry run would apply. */
    canApplyUpgrade: computed(
      () => !!store.upgradeStarterId() && !store.isBusy() && !store.upgradeApplied() && store.upgradePreview()?.status === 'would-apply',
    ),
    /** The removal report to show: the applied one once there is one, the preview before. */
    uninstallReport: computed(() => store.uninstallApplied() ?? store.uninstallPreview()),
    /** Removing is possible only for the starter a dry run just showed, and only if that dry run would apply. */
    canApplyUninstall: computed(
      () => !!store.uninstallStarterId() && !store.isBusy() && !store.uninstallApplied() && store.uninstallPreview()?.status === 'would-apply',
    ),
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

    async function runUpgrade(dryRun: boolean): Promise<void> {
      const starterId = store.upgradeStarterId();
      if (!starterId) return;
      patchState(store, { isBusy: true, error: undefined });
      try {
        // The target the user saw: applying never goes further than the preview did.
        const version = dryRun ? undefined : store.upgradePreview()?.toVersion;
        const report = await firstValueFrom(service.upgrade(starterId, dryRun, version));
        patchState(store, dryRun ? { upgradePreview: report } : { upgradeApplied: report });
        if (!dryRun && report.status === 'applied') await loadInstalled();
      } catch (error: unknown) {
        patchState(store, { error: httpErrorMessage(error) });
      } finally {
        patchState(store, { isBusy: false });
      }
    }

    async function runUninstall(dryRun: boolean): Promise<void> {
      const starterId = store.uninstallStarterId();
      if (!starterId) return;
      patchState(store, { isBusy: true, error: undefined });
      try {
        const report = await firstValueFrom(service.uninstall(starterId, dryRun));
        patchState(store, dryRun ? { uninstallPreview: report } : { uninstallApplied: report });
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
        const chosen = version ?? (starter ? selectable(starter, store.installed())[0]?.version : undefined);
        const selection = starter && chosen ? { starterId, version: chosen } : undefined;
        patchState(store, { selection, preview: undefined, applied: undefined, error: undefined });
      },
      preview: () => run(true),
      apply: () => run(false),
      /** Plans the upgrade of an installed starter to the version on offer, writing nothing. */
      previewUpgrade(starterId: string): Promise<void> {
        patchState(store, { upgradeStarterId: starterId, upgradePreview: undefined, upgradeApplied: undefined, error: undefined });
        return runUpgrade(true);
      },
      applyUpgrade: () => runUpgrade(false),
      /** Plans the removal of an installed domain starter, writing nothing. */
      previewUninstall(starterId: string): Promise<void> {
        patchState(store, { uninstallStarterId: starterId, uninstallPreview: undefined, uninstallApplied: undefined, error: undefined });
        return runUninstall(true);
      },
      applyUninstall: () => runUninstall(false),
      /** Drops the removal being previewed or reported. */
      cancelUninstall(): void {
        patchState(store, { uninstallStarterId: undefined, uninstallPreview: undefined, uninstallApplied: undefined });
      },
      loadCatalog,
      loadInstalled,
    };
  }),
  withDevtools('StarterImport'),
);
