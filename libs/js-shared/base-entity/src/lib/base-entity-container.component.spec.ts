import { TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { patchState } from '@ngrx/signals';
import { describe, expect, it, vi } from 'vitest';
import { setupContainerComponentTest } from '../test-setup';
import { BaseEntityContainerComponent } from './base-entity-container.component';

describe('BaseEntityContainerComponent', () => {
  it('should create', async () => {
    const { component } = await setupContainerComponentTest(BaseEntityContainerComponent);

    expect(component).toBeTruthy();
  });

  it('shows a snackbar when the store surfaces an error', async () => {
    const { component, store, fixture } = await setupContainerComponentTest(BaseEntityContainerComponent);
    const container = component as BaseEntityContainerComponent;
    container.store = store as unknown as BaseEntityContainerComponent['store'];
    const snackBar = TestBed.inject(MatSnackBar);
    const openSpy = vi.spyOn(snackBar, 'open').mockReturnValue({} as ReturnType<MatSnackBar['open']>);

    patchState(store as unknown as Parameters<typeof patchState>[0], { error: 'Something went wrong' });
    TestBed.flushEffects();
    fixture.detectChanges();

    expect(openSpy).toHaveBeenCalledWith('Something went wrong', 'Close', expect.objectContaining({ duration: 5000 }));
  });
});
