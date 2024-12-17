import { patchState, signalStoreFeature, withMethods, withState } from '@ngrx/signals';

export interface EntityTabsState {
  activeTabs: string[];
  currentTab: string | undefined;
}

const INITIAL_TABS_STATE: EntityTabsState = {
  activeTabs: [],
  currentTab: undefined,
};

export function BaseEntityTabsStore() {
  return signalStoreFeature(
    withState<EntityTabsState>(INITIAL_TABS_STATE),
    withMethods((store) => {
      function reset(): void {
        const activeTabs: string[] = [];
        patchState(store, { activeTabs, currentTab: undefined });
      }
      function tabIsActive(tabName: string): void {
        let activeTabs: Array<string>;
        if (store.activeTabs().indexOf(tabName) == -1) {
          activeTabs = store.activeTabs().concat([tabName]);
        } else {
          activeTabs = store.activeTabs();
        }
        patchState(store, { activeTabs, currentTab: tabName });
      }
      function tabIsInactive(tabName: string): void {
        const activeTabs = store.activeTabs().filter((tab) => tab !== tabName);
        patchState(store, { activeTabs, currentTab: undefined });
      }
      return { reset, tabIsActive, tabIsInactive };
    }),
  );
}
