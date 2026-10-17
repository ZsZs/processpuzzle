import { Component, computed, DestroyRef, effect, ElementRef, inject, OnInit, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MatIconButton } from '@angular/material/button';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatIcon } from '@angular/material/icon';
import { MatInput } from '@angular/material/input';
import { MatOption, MatSelect } from '@angular/material/select';
import { MatTooltip } from '@angular/material/tooltip';
import { TranslocoPipe } from '@jsverse/transloco';
import { injectActiveLang } from '@processpuzzle/util';
import { BaseEntity } from '../../base-entity/base-entity';
import { EntityLabelPipe } from '../../i18n/entity-label.pipe';
import { BaseFormControlComponent } from '../base-form-control.component';
import { addressFormatOf, formatAddress } from './address-formats';
import { AddressGeocoder } from './address-geocoder';
import { DEFAULT_MAP_PROVIDER, googleMapsUrl, mapEmbedUrl } from './address-map';
import { countryOfLanguageTag, countryOptions } from './countries';
import { ADDRESS_LINE_FIELDS, addressOf, GeoPoint, geoPointOf, isAddressEmpty, LocationSource, PostalAddress } from './postal-address';

type AddressFields = {
  country: FormControl<string | null>;
  street: FormControl<string | null>;
  addressLine2: FormControl<string | null>;
  postalCode: FormControl<string | null>;
  city: FormControl<string | null>;
  region: FormControl<string | null>;
  latitude: FormControl<number | null>;
  longitude: FormControl<number | null>;
};

/**
 * An `ADDRESS` attribute: a country, and below it the address fields that country writes, in its order.
 *
 * The attribute is one form control holding a {@link PostalAddress}; the fields are a form group of this
 * component's own, published to that control on every change. Only the fields the selected country uses are
 * published, so switching countries drops what the new layout has no place for — and switching back restores
 * it, since the group still holds it. An address without a single line of text publishes `null`: the country a
 * new address starts with is a convenience, not a value.
 *
 * With `showMap` the address is also shown on a map. Its coordinates are derived from the address when an
 * address field is left or the country changes, and can be edited; once the user types a coordinate the pin is
 * theirs, and only the Locate button derives it again. A click on the map opens the location in Google Maps.
 */
@Component({
  selector: 'base-address',
  standalone: true,
  imports: [ReactiveFormsModule, MatFormField, MatLabel, MatInput, MatSelect, MatOption, MatIconButton, MatIcon, MatTooltip, EntityLabelPipe, TranslocoPipe],
  template: `
    @if (config().visible) {
      <fieldset class="pp-address" [id]="config().attrName" [formGroup]="fields">
        <legend class="pp-address__title">{{ config().i18nKey() | ppLabel: config().label }}</legend>
        <div class="pp-address__line">
          <mat-form-field class="pp-address__country">
            <mat-label>{{ 'base_entity.address.country' | transloco }}</mat-label>
            <mat-select formControlName="country" data-testid="address-country" (selectionChange)="onAddressChanged()">
              @for (country of countries(); track country.code) {
                <mat-option [value]="country.code">{{ country.name }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
        </div>
        @for (line of format().lines; track $index) {
          <div class="pp-address__line">
            @for (part of line; track part.field) {
              <mat-form-field [style.flex-grow]="part.grow">
                <mat-label>{{ 'base_entity.address.' + part.label | transloco }}</mat-label>
                <input matInput [formControlName]="part.field" [attr.data-testid]="'address-' + part.field" (blur)="onAddressChanged()" />
              </mat-form-field>
            }
          </div>
        }
        @if (config().showMap) {
          <div class="pp-address__line pp-address__location">
            <mat-form-field>
              <mat-label>{{ 'base_entity.address.latitude' | transloco }}</mat-label>
              <input matInput type="number" step="any" min="-90" max="90" formControlName="latitude" data-testid="address-latitude" (input)="onCoordinateTyped()" />
            </mat-form-field>
            <mat-form-field>
              <mat-label>{{ 'base_entity.address.longitude' | transloco }}</mat-label>
              <input matInput type="number" step="any" min="-180" max="180" formControlName="longitude" data-testid="address-longitude" (input)="onCoordinateTyped()" />
            </mat-form-field>
            @if (isEditable()) {
              <button
                mat-icon-button
                type="button"
                class="pp-address__locate"
                data-testid="address-locate"
                [disabled]="locating()"
                [matTooltip]="'base_entity.address.locate' | transloco"
                [attr.aria-label]="'base_entity.address.locate' | transloco"
                (click)="locate()"
              >
                <mat-icon>my_location</mat-icon>
              </button>
            }
          </div>
          @if (notFound()) {
            <p class="pp-address__hint" data-testid="address-not-found">{{ 'base_entity.address.not_found' | transloco }}</p>
          }
          @if (mapUrl()) {
            <div class="pp-address__map">
              <iframe #mapFrame [title]="'base_entity.address.map' | transloco" loading="lazy" referrerpolicy="no-referrer-when-downgrade" tabindex="-1"></iframe>
              <!-- The map is a third-party page the click would otherwise land in; this link sits over it. -->
              <a
                class="pp-address__map-link"
                data-testid="address-map-link"
                [href]="openUrl()"
                target="_blank"
                rel="noopener noreferrer"
                [attr.aria-label]="'base_entity.address.open_in_google_maps' | transloco"
                [matTooltip]="'base_entity.address.open_in_google_maps' | transloco"
              ></a>
            </div>
          } @else {
            <p class="pp-address__hint" data-testid="address-no-location">{{ 'base_entity.address.no_location' | transloco }}</p>
          }
        }
      </fieldset>
    }
  `,
  styles: [
    `
      :host {
        display: block;
        margin-bottom: 16px;
      }
      .pp-address {
        margin: 0;
        padding: 0;
        border: none;
        min-width: 0;
      }
      .pp-address__title {
        margin: 0 0 8px;
        padding: 0;
        font-size: 1rem;
        font-weight: 500;
      }
      .pp-address__line {
        display: flex;
        flex-wrap: wrap;
        gap: 0 12px;
      }
      .pp-address__line mat-form-field {
        flex: 1 1 0;
        min-width: 120px;
      }
      .pp-address__locate {
        margin-top: 8px;
      }
      .pp-address__hint {
        margin: 0 0 8px;
        font-size: 0.85rem;
        color: rgba(0, 0, 0, 0.6);
      }
      .pp-address__map {
        position: relative;
        height: 240px;
        border-radius: 4px;
        overflow: hidden;
      }
      .pp-address__map iframe {
        width: 100%;
        height: 100%;
        border: 0;
      }
      .pp-address__map-link {
        position: absolute;
        inset: 0;
        cursor: pointer;
      }
    `,
  ],
})
export class AddressComponent<Entity extends BaseEntity> extends BaseFormControlComponent<Entity> implements OnInit {
  readonly fields = new FormGroup<AddressFields>({
    country: new FormControl<string | null>(null),
    street: new FormControl<string | null>(null),
    addressLine2: new FormControl<string | null>(null),
    postalCode: new FormControl<string | null>(null),
    city: new FormControl<string | null>(null),
    region: new FormControl<string | null>(null),
    latitude: new FormControl<number | null>(null),
    longitude: new FormControl<number | null>(null),
  });
  readonly locating = signal(false);
  readonly notFound = signal(false);

  private readonly activeLang = injectActiveLang();
  private readonly geocoder = inject(AddressGeocoder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly country = signal<string | undefined>(undefined);
  private readonly location = signal<GeoPoint | undefined>(undefined);
  private locationSource: LocationSource | undefined;
  /** The address the coordinates were last derived for — leaving a field without changing it asks nobody. */
  private geocodedFor = '';

  readonly format = computed(() => addressFormatOf(this.country()));
  readonly countries = computed(() => countryOptions(this.activeLang()));
  readonly mapUrl = computed<string | undefined>(() => {
    const point = this.location();
    return point ? mapEmbedUrl(this.config().mapProvider ?? DEFAULT_MAP_PROVIDER, point) : undefined;
  });
  readonly openUrl = computed(() => {
    const point = this.location();
    return point ? googleMapsUrl(point) : undefined;
  });

  private readonly mapFrame = viewChild<ElementRef<HTMLIFrameElement>>('mapFrame');

  constructor() {
    super();
    // Assigned rather than bound: `[src]` on an iframe demands a SafeResourceUrl, i.e. switching Angular's
    // sanitization off. The URL needs none — `mapEmbedUrl` builds it from two finite numbers and a constant host.
    effect(() => {
      const frame = this.mapFrame()?.nativeElement;
      const url = this.mapUrl();
      if (frame && url && frame.src !== url) frame.src = url;
    });
  }

  // region Angular lifecycle hooks
  ngOnInit(): void {
    const address = addressOf(this.value());
    const country = address.country ?? this.config().defaultCountry ?? countryOfLanguageTag(globalThis.navigator?.language);
    this.fields.reset(
      {
        country: country ?? null,
        street: address.street ?? null,
        addressLine2: address.addressLine2 ?? null,
        postalCode: address.postalCode ?? null,
        city: address.city ?? null,
        region: address.region ?? null,
        latitude: address.latitude ?? null,
        longitude: address.longitude ?? null,
      },
      { emitEvent: false },
    );
    this.country.set(country);
    this.location.set(this.config().showMap ? geoPointOf(address) : undefined);
    this.locationSource = address.locationSource;
    if (this.location()) this.geocodedFor = this.queryOf(this.currentAddress());

    if (this.config().required) {
      [this.fields.controls.country, this.fields.controls.street, this.fields.controls.city].forEach((control) => control.addValidators(Validators.required));
    }
    this.fields.updateValueAndValidity({ emitEvent: false });

    const control = this.control();
    if (control) {
      // The fields' own validity is the attribute's: a required address with no street must block the Save.
      control.addValidators(this.fieldsValidator);
      control.updateValueAndValidity({ emitEvent: false });
      if (control.disabled || this.config().disabled) this.fields.disable({ emitEvent: false });
    }

    this.fields.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.publish());
  }

  // endregion

  // region event handling methods
  /** A disabled attribute or control shows the address without a Locate button. */
  isEditable(): boolean {
    return !this.config().disabled && this.control()?.enabled !== false;
  }

  /** An address field was left or the country changed — the moment to place the pin, if it is ours to place. */
  onAddressChanged(): void {
    this.country.set(this.fields.controls.country.value ?? undefined);
    if (!this.config().showMap || this.locationSource === 'MANUAL' || !this.isEditable()) return;
    const address = this.currentAddress();
    if (isAddressEmpty(address)) return;
    const query = this.queryOf(address);
    if (query === this.geocodedFor) return;
    void this.locate(false);
  }

  /** The user typed a coordinate: from now on the pin stays where they put it. */
  onCoordinateTyped(): void {
    this.locationSource = 'MANUAL';
    this.notFound.set(false);
    this.publish();
  }

  /**
   * Derives the coordinates from the address. Pressed as the Locate button (`force`), it also takes the pin
   * back from the user; called after a field was left, it gives up if the user typed a coordinate meanwhile.
   */
  async locate(force = true): Promise<void> {
    const address = this.currentAddress();
    if (isAddressEmpty(address)) return;
    this.geocodedFor = this.queryOf(address);
    this.locating.set(true);
    try {
      const point = await this.geocoder.geocode(address, this.activeLang());
      if (!force && this.locationSource === 'MANUAL') return;
      this.notFound.set(!point);
      if (!point) return;
      this.locationSource = 'GEOCODED';
      this.fields.patchValue({ latitude: point.latitude, longitude: point.longitude }, { emitEvent: false });
      this.publish();
    } finally {
      this.locating.set(false);
    }
  }

  // endregion

  // region protected, private helper methods
  private readonly fieldsValidator = (): ValidationErrors | null => (this.fields.invalid ? { address: true } : null);

  private control(): AbstractControl | null {
    return this.formGroup?.get(this.config().attrName) ?? null;
  }

  private publish(): void {
    const address = this.currentAddress();
    this.location.set(this.config().showMap ? geoPointOf(address) : undefined);
    const control = this.control();
    if (!control) return;
    control.setValue(isAddressEmpty(address) && !geoPointOf(address) ? null : address);
    control.markAsDirty();
    control.markAsTouched();
  }

  /** What the fields hold, reduced to the parts the selected country uses — the value the attribute is saved with. */
  private currentAddress(): PostalAddress {
    const raw = this.fields.getRawValue();
    const used = new Set(addressFormatOf(raw.country ?? undefined).lines.flatMap((line) => line.map((part) => part.field)));
    const address: PostalAddress = {};
    if (raw.country) address.country = raw.country;
    for (const field of ADDRESS_LINE_FIELDS) {
      const text = raw[field]?.trim();
      if (text && used.has(field)) address[field] = text;
    }
    if (this.config().showMap) {
      const point = geoPointOf({ latitude: raw.latitude ?? undefined, longitude: raw.longitude ?? undefined });
      if (point) {
        address.latitude = point.latitude;
        address.longitude = point.longitude;
        if (this.locationSource) address.locationSource = this.locationSource;
      }
    }
    return address;
  }

  private queryOf(address: PostalAddress): string {
    return formatAddress(address);
  }

  // endregion
}
