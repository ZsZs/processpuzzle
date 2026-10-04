import { Component, computed, DestroyRef, effect, inject, input, signal, Signal } from '@angular/core';
import { ROUTER_OUTLET_DATA } from '@angular/router';
import { MatButton } from '@angular/material/button';
import { TranslocoPipe } from '@jsverse/transloco';
import type { BaseEntityDescriptor } from '@processpuzzle/base-entity';
import { firstValueFrom } from 'rxjs';
import { ENTITY_ENROLLMENT_I18N_SCOPE } from '../../base-ai.i18n';
import { Enrollment } from '../../domain/enrollment/enrollment';
import { EnrollmentService } from '../../domain/enrollment/enrollment.service';
import { RecognitionProfile } from '../../domain/profile/recognition-profile';
import { ProfiledEntityRegistry } from '../../domain/profile/profiled-entity.registry';

/** How often a gallery with PENDING photos is re-read. The vision server needs seconds per photo on CPU. */
export const ENROLLMENT_POLL_MS = 3000;

/**
 * Re-reads before polling gives up — ten minutes. Without a vision server (a CI stack, a stopped container)
 * a photo stays PENDING indefinitely, and the backend's own poller resumes it when the server is back; the
 * tab need not keep asking. Reopening the tab, or synchronizing, starts again.
 */
export const ENROLLMENT_MAX_POLLS = 200;

/**
 * The Enrollment tab of a **subject** — a `Boat`, say — mounted at `<entity>/<id>/enrollment` beside that
 * entity's Details form, and contributed onto it by {@link EntityEnrollmentTabContributor} for every entity
 * type that has a recognition profile.
 *
 * It shows what recognition made of the subject's photos: watch them being processed, and see what came out
 * of each — the crop the detector cut, the identifier OCR read on it, and whether that reading agrees with
 * the identifier registered on the subject. The photos themselves are the subject's, in the attribute the
 * profile's `galleryAttributeKey` names, and are added or removed on its Details form; the gallery follows
 * them on every save. Synchronize asks for that now; Rebuild discards the gallery and enrolls them afresh.
 *
 * While any photo is PENDING the gallery is re-read every {@link ENROLLMENT_POLL_MS}; processing happens on
 * the vision server and the backend learns of it by callback, so polling the backend is all this needs.
 */
@Component({
  selector: 'pp-entity-enrollment-tab',
  standalone: true,
  imports: [MatButton, TranslocoPipe],
  template: `
    <div class="pp-enrollment">
      @if (isLoading()) {
        <p class="pp-enrollment__note">{{ scope + '.loading' | transloco }}</p>
      } @else if (!profile()) {
        <p class="pp-enrollment__note">{{ scope + '.noProfile' | transloco }}</p>
      } @else {
        <header class="pp-enrollment__header">
          <div class="pp-enrollment__summary">
            <span class="pp-enrollment__label">{{ scope + '.registered' | transloco }}</span>
            <span class="pp-enrollment__identifier" data-testid="registered-identifier">{{ enrollment()?.identifierText || (scope + '.noIdentifier' | transloco) }}</span>
            <span class="pp-enrollment__chip" [attr.data-status]="status()" data-testid="enrollment-status">{{ scope + '.status.' + status() | transloco }}</span>
          </div>
          <p class="pp-enrollment__note">
            {{ scope + '.intro' | transloco: { detectorClass: profile()?.detectorClass, attribute: profile()?.galleryAttributeKey } }}
          </p>
        </header>

        <div class="pp-enrollment__actions">
          <button mat-flat-button type="button" [disabled]="isWorking()" (click)="synchronize()" data-testid="synchronize">{{ scope + '.synchronize' | transloco }}</button>
          @if (photos().length > 0) {
            @if (confirmingRebuild()) {
              <button mat-flat-button type="button" class="pp-enrollment__danger" (click)="rebuild()" data-testid="confirm-rebuild">{{ scope + '.confirmRebuild' | transloco }}</button>
              <button mat-stroked-button type="button" (click)="confirmingRebuild.set(false)">{{ scope + '.cancel' | transloco }}</button>
            } @else {
              <button mat-stroked-button type="button" [disabled]="isWorking()" (click)="confirmingRebuild.set(true)" data-testid="rebuild">{{ scope + '.rebuild' | transloco }}</button>
            }
          }
        </div>
        @if (error(); as message) {
          <p class="pp-enrollment__error" role="alert">{{ message }}</p>
        }

        @if (photos().length === 0) {
          <p class="pp-enrollment__note">{{ scope + '.empty' | transloco }}</p>
        } @else {
          <ul class="pp-enrollment__grid">
            @for (photo of photos(); track photo.photoId) {
              <li class="pp-enrollment__card" [attr.data-status]="photo.status" data-testid="enrollment-photo">
                <img [src]="photo.cropUrl || photo.photoUrl" [alt]="scope + '.photoAlt' | transloco" loading="lazy" />
                <div class="pp-enrollment__card-body">
                  <span class="pp-enrollment__chip" [attr.data-status]="photo.status">{{ scope + '.photoStatus.' + photo.status | transloco }}</span>
                  @if (photo.observedIdentifierText) {
                    <span class="pp-enrollment__reading">
                      {{ scope + '.observed' | transloco }} <strong data-testid="observed-identifier">{{ photo.observedIdentifierText }}</strong>
                    </span>
                    @if (photo.identifierMismatch) {
                      <span class="pp-enrollment__warning" role="alert">{{ scope + '.mismatch' | transloco }}</span>
                    }
                  } @else if (photo.status === 'ENROLLED' && profile()?.identifierAttributeKey) {
                    <span class="pp-enrollment__note">{{ scope + '.noReading' | transloco }}</span>
                  }
                  @if (photo.failureReason) {
                    <span class="pp-enrollment__note">{{ photo.failureReason }}</span>
                  }
                </div>
              </li>
            }
          </ul>
        }
      }
    </div>
  `,
  styles: [
    `
      .pp-enrollment {
        display: flex;
        flex-direction: column;
        gap: 12px;
        background-color: #ffffff;
        border-radius: 6px;
        padding: 16px 20px 24px;
      }
      .pp-enrollment__summary {
        display: flex;
        align-items: baseline;
        flex-wrap: wrap;
        gap: 8px;
      }
      .pp-enrollment__label {
        font-size: 12px;
        text-transform: uppercase;
        letter-spacing: 0.04em;
        color: #666;
      }
      .pp-enrollment__identifier {
        font-size: 16px;
        font-weight: 600;
      }
      .pp-enrollment__chip {
        padding: 1px 8px;
        border-radius: 10px;
        font-size: 12px;
        background: #e0e0e0;
      }
      .pp-enrollment__chip[data-status='READY'],
      .pp-enrollment__chip[data-status='ENROLLED'] {
        background: var(--pp-color-light-green, rgb(92, 218, 207));
        color: var(--pp-color-dark-blue, rgb(24, 111, 206));
      }
      .pp-enrollment__chip[data-status='FAILED'],
      .pp-enrollment__chip[data-status='NO_SUBJECT'],
      .pp-enrollment__chip[data-status='AMBIGUOUS'] {
        background: #f8d7da;
        color: #842029;
      }
      .pp-enrollment__actions {
        display: flex;
        gap: 8px;
      }
      .pp-enrollment__danger {
        --mat-button-filled-container-color: #d9534f;
      }
      .pp-enrollment__grid {
        list-style: none;
        margin: 0;
        padding: 0;
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
        gap: 12px;
      }
      .pp-enrollment__card {
        display: flex;
        flex-direction: column;
        border: 1px solid #cccccc;
        border-radius: 4px;
        overflow: hidden;
      }
      .pp-enrollment__card img {
        width: 100%;
        height: 180px;
        object-fit: contain;
        background: #f5f5f5;
      }
      .pp-enrollment__card-body {
        display: flex;
        flex-direction: column;
        align-items: flex-start;
        gap: 6px;
        padding: 8px;
      }
      .pp-enrollment__reading {
        font-size: 13px;
      }
      .pp-enrollment__warning,
      .pp-enrollment__error {
        font-size: 12px;
        color: #d9534f;
        margin: 0;
      }
      .pp-enrollment__note {
        margin: 0;
        font-size: 12px;
        color: #666;
      }
    `,
  ],
})
export class EntityEnrollmentTabComponent {
  /** Bound from the route's `:entityId` by `withComponentInputBinding()`, as on the Details form. */
  readonly entityId = input.required<string>();

  protected readonly scope = ENTITY_ENROLLMENT_I18N_SCOPE;

  /** The subject's descriptor, handed down by `BaseEntityTabsComponent`'s outlet — see base-state's tab. */
  private readonly outletData = inject(ROUTER_OUTLET_DATA, { optional: true }) as Signal<BaseEntityDescriptor | undefined> | null;
  private readonly registry = inject(ProfiledEntityRegistry);
  private readonly service = inject(EnrollmentService);

  private readonly profileSignal = signal<RecognitionProfile | undefined>(undefined);
  private readonly enrollmentSignal = signal<Enrollment | undefined>(undefined);
  private readonly loadingSignal = signal(true);
  private readonly workingSignal = signal(false);
  private readonly errorSignal = signal<string | undefined>(undefined);
  private pollTimer?: ReturnType<typeof setTimeout>;
  private polls = 0;

  protected readonly isLoading = this.loadingSignal.asReadonly();
  protected readonly profile = this.profileSignal.asReadonly();
  protected readonly enrollment = this.enrollmentSignal.asReadonly();
  protected readonly error = this.errorSignal.asReadonly();
  protected readonly isWorking = this.workingSignal.asReadonly();
  protected readonly photos = computed(() => this.enrollmentSignal()?.photos ?? []);
  protected readonly status = computed(() => this.enrollmentSignal()?.status ?? 'NOT_ENROLLED');
  protected readonly confirmingRebuild = signal(false);

  constructor() {
    // An effect, as on the State Machine tab: the router reuses this component when only `:entityId`
    // changes, and the descriptor arrives through the outlet.
    effect(() => {
      const entityName = this.outletData?.()?.entityName;
      const objectId = this.entityId();
      void this.load(entityName, objectId);
    });
    inject(DestroyRef).onDestroy(() => clearTimeout(this.pollTimer));
  }

  /** Enrolls the photos new on the subject and drops the gone ones — normally already done on its save. */
  protected async synchronize(): Promise<void> {
    await this.work((profile) => firstValueFrom(this.service.synchronize(profile.entityName, this.entityId())));
  }

  /** Discards the gallery and enrolls the subject's photos afresh, after a change of detector, say. */
  protected async rebuild(): Promise<void> {
    this.confirmingRebuild.set(false);
    await this.work(async (profile) => {
      await firstValueFrom(this.service.deleteAll(profile.entityName, this.entityId()));
      return firstValueFrom(this.service.synchronize(profile.entityName, this.entityId()));
    });
  }

  private async work(action: (profile: RecognitionProfile) => Promise<Enrollment>): Promise<void> {
    const profile = this.profileSignal();
    if (!profile) return;
    this.errorSignal.set(undefined);
    this.workingSignal.set(true);
    try {
      const enrollment = await action(profile);
      this.polls = 0;
      this.show(enrollment);
    } catch (error) {
      this.errorSignal.set(messageOf(error));
    } finally {
      this.workingSignal.set(false);
    }
  }

  private async load(entityName: string | undefined, objectId: string | undefined): Promise<void> {
    clearTimeout(this.pollTimer);
    this.polls = 0;
    this.loadingSignal.set(true);
    try {
      const profile = await this.registry.profileFor(entityName);
      this.profileSignal.set(profile);
      this.enrollmentSignal.set(undefined);
      if (profile && objectId) await this.refresh();
    } finally {
      this.loadingSignal.set(false);
    }
  }

  private async refresh(): Promise<void> {
    const profile = this.profileSignal();
    if (!profile) return;
    const enrollment = await firstValueFrom(this.service.find(profile.entityName, this.entityId())).catch(() => undefined);
    this.show(enrollment);
  }

  private show(enrollment: Enrollment | undefined): void {
    this.enrollmentSignal.set(enrollment);
    clearTimeout(this.pollTimer);
    if (enrollment?.status === 'PROCESSING' && this.polls < ENROLLMENT_MAX_POLLS) {
      this.polls++;
      this.pollTimer = setTimeout(() => void this.refresh(), ENROLLMENT_POLL_MS);
    }
  }
}

/** A readable message: the backend's `errorText` where there is one. */
function messageOf(error: unknown): string {
  const body = (error as { error?: { errorText?: string } })?.error;
  if (body?.errorText) return body.errorText;
  return error instanceof Error ? error.message : String(error);
}
