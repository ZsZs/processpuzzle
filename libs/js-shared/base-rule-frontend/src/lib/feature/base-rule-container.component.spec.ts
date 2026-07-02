import { TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import { describe, expect, it } from 'vitest';
import { mock } from 'vitest-mock-extended';
import { BASE_ENTITY_FACADE_REGISTRY, BaseFormNavigatorSingletonStore, type NavigationPayload, NavigatorCommand } from '@processpuzzle/base-entity';
import { BaseRule, Severity } from '../domain/base-rule';
import { BaseRuleStore } from '../domain/base-rule.store';
import { BaseRuleContainerComponent } from './base-rule-container.component';
import { BaseRuleDryRunDialog, BaseRuleDryRunDialogResult } from './base-rule-dry-run.dialog';

interface NavigatorStub {
  popResponsePayload: (attrName?: string) => NavigationPayload | undefined;
  navigateToRelatedList: (relatedTypeName: string, returnTo?: string, payload?: NavigationPayload) => Promise<void>;
  determineCurrentUrl: () => string;
}

describe('BaseRuleContainerComponent', () => {
  const testRule = new BaseRule('r1', 'positive-quantities', 'desc', 'Order', 'entity.qty > 0', Severity.WARNING);

  async function setup(opts: { pendingResponse?: unknown; dialogResult?: BaseRuleDryRunDialogResult } = {}) {
    const dialog = mock<MatDialog>();
    const dialogRef = mock<MatDialogRef<BaseRuleDryRunDialog, BaseRuleDryRunDialogResult>>();
    dialogRef.afterClosed.mockReturnValue(of(opts.dialogResult));
    dialog.open.mockReturnValue(dialogRef as unknown as MatDialogRef<BaseRuleDryRunDialog>);

    const storeStub = { currentEntity: signal<BaseRule | undefined>(testRule) };
    const navigator = mock<NavigatorStub>();
    navigator.popResponsePayload.mockReturnValue(
      opts.pendingResponse ? { command: NavigatorCommand.SELECT_OR_CREATE, attrName: 'baseRuleDryRun', payload: opts.pendingResponse as object } : undefined,
    );
    navigator.determineCurrentUrl.mockReturnValue('/base-rule/r1/details');
    navigator.navigateToRelatedList.mockResolvedValue(undefined);

    await TestBed.configureTestingModule({
      imports: [BaseRuleContainerComponent, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        { provide: MatDialog, useValue: dialog },
        { provide: BaseRuleStore, useValue: storeStub },
        { provide: BASE_ENTITY_FACADE_REGISTRY, useValue: {} },
        { provide: BaseFormNavigatorSingletonStore, useValue: navigator },
      ],
    })
      .overrideComponent(BaseRuleContainerComponent, {
        set: {
          template: `<ng-template #dryRunActionsTpl><button id="dry-run">Dry run...</button></ng-template>`,
          imports: [],
        },
      })
      .compileComponents();

    const fixture = TestBed.createComponent(BaseRuleContainerComponent);
    return { fixture, component: fixture.componentInstance, dialog, dialogRef, navigator, storeStub };
  }

  it('wires a dry-run template getter onto the descriptor', async () => {
    const { fixture, component } = await setup();
    fixture.detectChanges();

    const templateGetter = component.baseEntityDescriptor.extraFormActionsTemplate;
    expect(templateGetter).toBeInstanceOf(Function);
    expect(templateGetter?.()).toBeTruthy();
  });

  it('onDryRun() opens the dry-run dialog with the current rule', async () => {
    const { fixture, component, dialog } = await setup();
    fixture.detectChanges();

    component.onDryRun();

    expect(dialog.open).toHaveBeenCalledWith(BaseRuleDryRunDialog, expect.objectContaining({ data: { rule: testRule, prefilledEntity: undefined } }));
  });

  it('onDryRun() is a no-op when there is no current entity', async () => {
    const { fixture, component, dialog, storeStub } = await setup();
    storeStub.currentEntity.set(undefined);
    fixture.detectChanges();

    component.onDryRun();

    expect(dialog.open).not.toHaveBeenCalled();
  });

  it('navigates to the related list when the dialog closes with a pick action', async () => {
    const { fixture, component, navigator } = await setup({ dialogResult: { action: 'pick' } });
    fixture.detectChanges();

    component.onDryRun();
    await Promise.resolve();

    expect(navigator.navigateToRelatedList).toHaveBeenCalledWith(
      testRule.context,
      '/base-rule/r1/details',
      expect.objectContaining({ command: NavigatorCommand.SELECT_OR_CREATE, attrName: 'baseRuleDryRun' }),
    );
  });

  it('does not navigate when the dialog closes without a pick action', async () => {
    const { fixture, component, navigator } = await setup({ dialogResult: undefined });
    fixture.detectChanges();

    component.onDryRun();
    await Promise.resolve();

    expect(navigator.navigateToRelatedList).not.toHaveBeenCalled();
  });

  it('reopens the dialog with the picked entity when a response payload is pending on init', async () => {
    const pickedEntity = { id: 'o-1', quantity: 3 };
    const { fixture, dialog } = await setup({ pendingResponse: pickedEntity });

    fixture.detectChanges();

    expect(dialog.open).toHaveBeenCalledWith(BaseRuleDryRunDialog, expect.objectContaining({ data: { rule: testRule, prefilledEntity: pickedEntity } }));
  });
});
