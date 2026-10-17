import { Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { AbstractControl } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';
import { TranslocoPipe } from '@jsverse/transloco';
import { BaseEntity } from '../../base-entity/base-entity';
import { DEFAULT_DATE_TIME_FORMAT } from '../../base-entity/date-format';
import { DateFormatPipe } from '../../i18n/date-format.pipe';
import { EntityLabelPipe } from '../../i18n/entity-label.pipe';
import { BaseFormControlComponent } from '../base-form-control.component';
import { googleMapsUrl } from '../address/address-map';
import { formatGeoLocation, GeoLocation, geoLocationOf } from './geo-location';
import { GeoLocator, GeoLocatorError, GeoLocatorFailure } from './geo-locator';

/**
 * A `GEO_LOCATION` attribute: the device's location at the moment the user pressed the button.
 *
 * The location is shown, never typed — coordinates are what the device read, not what someone claims. Pressing
 * the button again replaces it with a fresh reading, and the new value dirties the form, so Save is offered.
 * The button is disabled where no location can be had: a browser without the Geolocation API, an insecure
 * origin, or a user who has refused this site location access.
 */
@Component({
  selector: 'base-geo-location',
  standalone: true,
  imports: [MatButton, MatIcon, MatTooltip, EntityLabelPipe, TranslocoPipe, DateFormatPipe],
  template: `
    @if (config().visible) {
      <div class="pp-geo-location" [id]="config().attrName">
        <span class="pp-geo-location__title">{{ config().i18nKey() | ppLabel: config().label }}</span>
        <div class="pp-geo-location__line">
          @if (location(); as point) {
            <span class="pp-geo-location__value" data-testid="geo-location-value">{{ text() }}</span>
            <a class="pp-geo-location__open" data-testid="geo-location-map-link" [href]="openUrl()" target="_blank" rel="noopener noreferrer" [matTooltip]="'base_entity.geo_location.open_in_google_maps' | transloco">
              <mat-icon>map</mat-icon>
            </a>
            @if (point.capturedAt) {
              <span class="pp-geo-location__captured" data-testid="geo-location-captured">
                {{ 'base_entity.geo_location.captured_at' | transloco: { time: point.capturedAt | ppDate: capturedAtFormat } }}
              </span>
            }
          } @else {
            <span class="pp-geo-location__empty" data-testid="geo-location-empty">{{ 'base_entity.geo_location.none' | transloco }}</span>
          }
        </div>
        @if (isEditable()) {
          <span [matTooltip]="unavailable() ? ('base_entity.geo_location.unavailable' | transloco) : ''">
            <button mat-stroked-button type="button" data-testid="geo-location-capture" [disabled]="unavailable() || locating()" (click)="capture()">
              <mat-icon>my_location</mat-icon>
              {{ (location() ? 'base_entity.geo_location.recapture' : 'base_entity.geo_location.capture') | transloco }}
            </button>
          </span>
        }
        @if (failure(); as reason) {
          <p class="pp-geo-location__error" data-testid="geo-location-error">{{ 'base_entity.geo_location.error.' + reason | transloco }}</p>
        }
      </div>
    }
  `,
  styles: [
    `
      :host {
        display: block;
        margin-bottom: 16px;
      }
      .pp-geo-location {
        display: flex;
        flex-direction: column;
        align-items: flex-start;
        gap: 6px;
      }
      .pp-geo-location__title {
        font-size: 1rem;
        font-weight: 500;
      }
      .pp-geo-location__line {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: 4px 12px;
      }
      .pp-geo-location__value {
        font-family: monospace;
      }
      .pp-geo-location__open {
        display: inline-flex;
        color: inherit;
      }
      .pp-geo-location__captured,
      .pp-geo-location__empty,
      .pp-geo-location__error {
        margin: 0;
        font-size: 0.85rem;
        color: rgba(0, 0, 0, 0.6);
      }
      .pp-geo-location__error {
        color: var(--mat-sys-error, #b3261e);
      }
    `,
  ],
})
export class GeoLocationComponent<Entity extends BaseEntity> extends BaseFormControlComponent<Entity> implements OnInit {
  readonly capturedAtFormat = DEFAULT_DATE_TIME_FORMAT;
  readonly locating = signal(false);
  readonly failure = signal<GeoLocatorError | undefined>(undefined);
  readonly location = signal<GeoLocation | undefined>(undefined);
  readonly text = computed(() => formatGeoLocation(this.location()));
  readonly openUrl = computed(() => {
    const point = this.location();
    return point ? googleMapsUrl(point) : undefined;
  });

  private readonly locator = inject(GeoLocator);
  private readonly destroyRef = inject(DestroyRef);
  private readonly denied = signal(false);
  private readonly available = signal(true);
  /** No API, or a refusal the user has to lift in the browser's site settings — pressing would do nothing. */
  readonly unavailable = computed(() => !this.available() || this.denied());

  // region Angular lifecycle hooks
  ngOnInit(): void {
    this.location.set(geoLocationOf(this.value()));
    this.available.set(this.locator.isAvailable());
    if (!this.available()) return;
    let destroyed = false;
    this.destroyRef.onDestroy(() => (destroyed = true));
    void this.locator.isDenied((denied) => !destroyed && this.denied.set(denied)).then((denied) => !destroyed && this.denied.set(denied));
  }

  // endregion

  // region event handling methods
  /** A disabled attribute or control shows the location without the button. */
  isEditable(): boolean {
    return !this.config().disabled && this.control()?.enabled !== false;
  }

  /** Reads the current location and makes it the attribute's value, replacing any earlier one. */
  async capture(): Promise<void> {
    this.locating.set(true);
    this.failure.set(undefined);
    try {
      const location = await this.locator.locate();
      this.location.set(location);
      const control = this.control();
      if (!control) return;
      control.setValue(location);
      control.markAsDirty();
      control.markAsTouched();
    } catch (error) {
      const reason = error instanceof GeoLocatorFailure ? error.reason : 'POSITION_UNAVAILABLE';
      this.failure.set(reason);
      if (reason === 'PERMISSION_DENIED') this.denied.set(true);
      if (reason === 'UNAVAILABLE') this.available.set(false);
    } finally {
      this.locating.set(false);
    }
  }

  // endregion

  // region protected, private helper methods
  private control(): AbstractControl | null {
    return this.formGroup?.get(this.config().attrName) ?? null;
  }

  // endregion
}
