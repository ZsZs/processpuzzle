import { patchState, signalStoreFeature, withMethods, withState } from '@ngrx/signals';

export interface EntityContainerState {
  filterKey: string | undefined;
}

const INITIAL_CONTAINER_STATE: EntityContainerState = {
  filterKey: undefined,
};

export function BaseEntityContainerStore() {
  return signalStoreFeature(
    withState<EntityContainerState>(INITIAL_CONTAINER_STATE),
    withMethods((store) => {
      function doFilter(filterKey: string): void {
        patchState(store, { filterKey });
      }
      return { doFilter };
    }),
  );
}
