import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatFormField, MatHint, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatProgressBar } from '@angular/material/progress-bar';
import { MatSelect, MatOption } from '@angular/material/select';
import { TranslocoPipe } from '@jsverse/transloco';
import { AiProvider, AiSubscription, AiSubscriptionService } from '../domain/ai-subscription.service';

/** Transloco prefix of this screen's texts, in the org-admin scope. */
export const AI_SUBSCRIPTION_TRANSLOCO_PREFIX = 'org_admin.ai_subscription';

/**
 * The organization's AI subscription: which model service the design assistant uses, and the organization's own key for
 * it. The platform pays for no assistant use, so without a subscription the assistant is off for everyone.
 *
 * **The key is write-only.** The backend never returns it, so the field starts empty and a blank value on save keeps the
 * stored key; the screen says "a key is stored" instead. A subscription that does not exist yet needs a key.
 */
@Component({
  selector: 'pp-ai-subscription',
  standalone: true,
  imports: [ReactiveFormsModule, MatButton, MatFormField, MatHint, MatLabel, MatInput, MatProgressBar, MatSelect, MatOption, TranslocoPipe],
  template: `
    @if (loading()) {
      <mat-progress-bar mode="indeterminate"></mat-progress-bar>
    } @else {
      <section class="ai-subscription">
        <h2>{{ 'org_admin.ai_subscription.title' | transloco }}</h2>
        <p>{{ (current() ? 'org_admin.ai_subscription.active' : 'org_admin.ai_subscription.none') | transloco }}</p>
        <form [formGroup]="form" (ngSubmit)="onSave()">
          <mat-form-field>
            <mat-label>{{ 'org_admin.ai_subscription.provider' | transloco }}</mat-label>
            <mat-select id="ai-provider" formControlName="provider">
              <mat-option value="MISTRAL">Mistral</mat-option>
              <mat-option value="OPENAI_COMPATIBLE">{{ 'org_admin.ai_subscription.openaiCompatible' | transloco }}</mat-option>
            </mat-select>
          </mat-form-field>
          <mat-form-field>
            <mat-label>{{ 'org_admin.ai_subscription.model' | transloco }}</mat-label>
            <input id="ai-model" matInput formControlName="model" autocomplete="off" />
          </mat-form-field>
          @if (needsBaseUrl()) {
            <mat-form-field>
              <mat-label>{{ 'org_admin.ai_subscription.baseUrl' | transloco }}</mat-label>
              <input id="ai-base-url" matInput formControlName="baseUrl" autocomplete="off" placeholder="https://api.example.com/v1" />
              <mat-hint>{{ 'org_admin.ai_subscription.baseUrlHint' | transloco }}</mat-hint>
            </mat-form-field>
          }
          <mat-form-field>
            <mat-label>{{ 'org_admin.ai_subscription.apiKey' | transloco }}</mat-label>
            <input id="ai-api-key" matInput type="password" formControlName="apiKey" autocomplete="new-password" />
            <mat-hint>{{ (current() ? 'org_admin.ai_subscription.keyStored' : 'org_admin.ai_subscription.keyRequired') | transloco }}</mat-hint>
          </mat-form-field>
          @if (error()) {
            <p id="ai-error" class="ai-subscription__error">{{ error() }}</p>
          }
          @if (saved()) {
            <p id="ai-saved">{{ 'org_admin.ai_subscription.saved' | transloco }}</p>
          }
          <div class="ai-subscription__actions">
            <button id="save-ai-subscription" type="submit" mat-raised-button color="primary" [disabled]="busy() || !canSave()">
              {{ 'org_admin.ai_subscription.save' | transloco }}
            </button>
            @if (current()) {
              <button id="remove-ai-subscription" type="button" mat-button [disabled]="busy()" (click)="onRemove()">
                {{ 'org_admin.ai_subscription.remove' | transloco }}
              </button>
            }
          </div>
        </form>
      </section>
    }
  `,
  styles: [
    `
      .ai-subscription {
        display: flex;
        flex-direction: column;
        padding: 12px 16px;
        max-width: 560px;
      }

      .ai-subscription form {
        display: flex;
        flex-direction: column;
      }

      .ai-subscription__actions {
        display: flex;
        column-gap: 8px;
      }

      .ai-subscription__error {
        color: var(--mat-sys-error, #b3261e);
      }
    `,
  ],
})
export class AiSubscriptionComponent implements OnInit {
  private readonly service = inject(AiSubscriptionService);

  readonly current = signal<AiSubscription | null>(null);
  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly saved = signal(false);
  readonly error = signal('');

  readonly form = inject(FormBuilder).nonNullable.group({
    provider: ['MISTRAL' as AiProvider, Validators.required],
    model: ['', [Validators.required, Validators.maxLength(200)]],
    baseUrl: [''],
    apiKey: [''],
  });
  private readonly provider = signal<AiProvider>('MISTRAL');
  readonly needsBaseUrl = computed(() => this.provider() === 'OPENAI_COMPATIBLE');
  private readonly formState = signal(0);

  constructor() {
    this.form.controls.provider.valueChanges.subscribe((value) => this.provider.set(value));
    this.form.valueChanges.subscribe(() => this.formState.update((n) => n + 1));
  }

  /** Whether the form could be sent: a model, an address when one is needed, and a key when none is stored yet. */
  readonly canSave = computed(() => {
    this.formState();
    const value = this.form.getRawValue();
    return value.model.trim() !== '' && (!this.needsBaseUrl() || value.baseUrl.trim() !== '') && (this.current() !== null || value.apiKey.trim() !== '');
  });

  ngOnInit(): void {
    this.service.find().subscribe({
      next: (subscription) => {
        this.show(subscription);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('The AI subscription could not be read.');
        this.loading.set(false);
      },
    });
  }

  onSave(): void {
    if (!this.canSave()) return;
    const value = this.form.getRawValue();
    this.busy.set(true);
    this.saved.set(false);
    this.error.set('');
    this.service
      .save({
        provider: value.provider,
        model: value.model.trim(),
        baseUrl: value.provider === 'OPENAI_COMPATIBLE' ? value.baseUrl.trim() : undefined,
        apiKey: value.apiKey.trim() || undefined,
      })
      .subscribe({
        next: (subscription) => {
          this.show(subscription);
          this.saved.set(true);
          this.busy.set(false);
        },
        error: (failure: HttpErrorResponse) => {
          this.error.set(failure.error?.message ?? failure.error?.detail ?? 'The AI subscription could not be saved.');
          this.busy.set(false);
        },
      });
  }

  onRemove(): void {
    this.busy.set(true);
    this.saved.set(false);
    this.error.set('');
    this.service.remove().subscribe({
      next: () => {
        this.show(null);
        this.busy.set(false);
      },
      error: () => {
        this.error.set('The AI subscription could not be removed.');
        this.busy.set(false);
      },
    });
  }

  /** Fills the form from what is stored; the key field is always cleared, as the key is never returned. */
  private show(subscription: AiSubscription | null): void {
    this.current.set(subscription);
    this.form.reset({ provider: subscription?.provider ?? 'MISTRAL', model: subscription?.model ?? '', baseUrl: subscription?.baseUrl ?? '', apiKey: '' });
    this.provider.set(this.form.controls.provider.value);
  }
}
