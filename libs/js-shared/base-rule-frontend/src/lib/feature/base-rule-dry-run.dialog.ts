import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatIcon } from '@angular/material/icon';
import { MatInput } from '@angular/material/input';
import { MAT_DIALOG_DATA, MatDialogActions, MatDialogClose, MatDialogContent, MatDialogRef, MatDialogTitle } from '@angular/material/dialog';
import { BaseRule } from '../domain/base-rule';
import { BaseRuleEvaluatorService } from '../domain/base-rule-evaluator.service';
import { RuleEvaluationResult } from '../domain/rule-evaluation-result';

export interface BaseRuleDryRunDialogData {
  rule: BaseRule;
  prefilledEntity?: unknown;
}

export type BaseRuleDryRunDialogResult = { action: 'pick' } | undefined;

@Component({
  selector: 'pp-base-rule-dry-run-dialog',
  standalone: true,
  imports: [FormsModule, MatDialogTitle, MatDialogContent, MatDialogActions, MatDialogClose, MatButton, MatFormField, MatLabel, MatInput, MatIcon],
  template: `
    <h2 mat-dialog-title>Dry run: {{ data.rule.name }}</h2>
    <mat-dialog-content>
      <p><strong>Expression:</strong> <code>{{ data.rule.expression || '(empty)' }}</code></p>
      <p><strong>Context:</strong> {{ data.rule.context || '(none)' }}</p>
      <div class="entity-json-row">
        <mat-form-field appearance="outline" class="entity-json-field">
          <mat-label>Entity JSON</mat-label>
          <textarea matInput rows="6" [(ngModel)]="entityJson" [attr.data-testid]="'dry-run-entity-input'"></textarea>
        </mat-form-field>
        @if (data.rule.context) {
          <button type="button" mat-stroked-button class="pick-button" (click)="onPick()" [attr.data-testid]="'dry-run-pick-button'">
            <mat-icon>list</mat-icon>
            Pick from {{ data.rule.context }}...
          </button>
        }
      </div>
      @if (result(); as r) {
        @if (r.error) {
          <p class="dry-run-error"><strong>Error:</strong> {{ r.error }}</p>
        } @else if (r.passed) {
          <p class="dry-run-passed"><strong>Passed.</strong></p>
        } @else {
          <p class="dry-run-failed"><strong>Violated ({{ r.severity }}):</strong> {{ r.message }}</p>
        }
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button type="button" mat-button [mat-dialog-close]="undefined">Close</button>
      <button type="button" mat-raised-button color="primary" (click)="onRun()">Run</button>
    </mat-dialog-actions>
  `,
  styles: [
    `.entity-json-row { display: flex; align-items: flex-start; gap: 8px; }`,
    `.entity-json-field { flex: 1 1 auto; }`,
    `.pick-button { margin-top: 4px; white-space: nowrap; }`,
    `.dry-run-passed { color: var(--mat-sys-primary, #2e7d32); }`,
    `.dry-run-failed { color: var(--mat-sys-error, #c62828); }`,
    `.dry-run-error  { color: var(--mat-sys-error, #c62828); font-family: monospace; }`,
  ],
})
export class BaseRuleDryRunDialog {
  protected readonly data = inject<BaseRuleDryRunDialogData>(MAT_DIALOG_DATA);
  private readonly evaluator = inject(BaseRuleEvaluatorService);
  private readonly dialogRef = inject(MatDialogRef<BaseRuleDryRunDialog, BaseRuleDryRunDialogResult>);
  protected readonly result = signal<RuleEvaluationResult | undefined>(undefined);
  protected entityJson: string;

  constructor() {
    this.entityJson = this.data.prefilledEntity !== undefined ? JSON.stringify(this.data.prefilledEntity, null, 2) : '{}';
  }

  onRun(): void {
    let entity: unknown;
    try {
      entity = JSON.parse(this.entityJson);
    } catch (err) {
      this.result.set({
        ruleId: this.data.rule.id,
        ruleName: this.data.rule.name,
        passed: false,
        severity: this.data.rule.severity,
        error: `Invalid entity JSON: ${err instanceof Error ? err.message : String(err)}`,
      });
      return;
    }
    this.result.set(this.evaluator.evaluate(entity, this.data.rule));
  }

  onPick(): void {
    this.dialogRef.close({ action: 'pick' });
  }
}
