import { describe, expect, it, vi } from 'vitest';
import { FormControlType } from '../../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../../base-entity/base-entity-attr.descriptor';
import { TestEntity } from '../../test-entity';
import { setupFormControlTest } from '../../../test-setup';
import { AddressComponent } from './address.component';
import { AddressGeocoder } from './address-geocoder';
import { GeoPoint, PostalAddress } from './postal-address';

const BUDAPEST: PostalAddress = { country: 'HU', city: 'Budapest', postalCode: '1011', street: 'Fő utca 1.' };

interface SetupOptions {
  value?: unknown;
  showMap?: boolean;
  mapProvider?: 'OPENSTREETMAP' | 'GOOGLE';
  required?: boolean;
  disabled?: boolean;
  defaultCountry?: string;
  found?: GeoPoint;
}

async function setupAddress(options: SetupOptions = {}) {
  const config = new BaseEntityAttrDescriptor('address', FormControlType.ADDRESS, 'Address');
  config.showMap = options.showMap;
  config.mapProvider = options.mapProvider;
  config.required = options.required === true;
  config.disabled = options.disabled === true;
  config.defaultCountry = options.defaultCountry;
  const entity = new TestEntity('1', 'name');
  if (options.value !== undefined) Reflect.set(entity, 'address', options.value);
  const geocode = vi.fn(async () => options.found);
  const harness = await setupFormControlTest(AddressComponent, config, entity, [{ provide: AddressGeocoder, useValue: { geocode } }]);
  harness.fixture.detectChanges();
  const component = harness.component as AddressComponent<TestEntity>;
  const element = harness.fixture.nativeElement as HTMLElement;
  const input = (field: string) => element.querySelector<HTMLInputElement>(`[data-testid="address-${field}"]`);
  return { ...harness, component, geocode, control: component.formGroup.get('address')!, element, input };
}

function type(input: HTMLInputElement | null, text: string): void {
  if (!input) throw new Error('no such input');
  input.value = text;
  input.dispatchEvent(new Event('input'));
  input.dispatchEvent(new Event('blur'));
}

describe('AddressComponent', () => {
  it('lays the fields out the way the selected country writes them', async () => {
    const { element, fixture, component } = await setupAddress({ value: BUDAPEST });
    const order = () => Array.from(element.querySelectorAll('input[data-testid]')).map((input) => input.getAttribute('data-testid'));

    expect(order()).toEqual(['address-city', 'address-postalCode', 'address-street', 'address-addressLine2']);

    component.fields.controls.country.setValue('US');
    component.onAddressChanged();
    fixture.detectChanges();
    expect(order()).toEqual(['address-street', 'address-addressLine2', 'address-city', 'address-region', 'address-postalCode']);
  });

  it('fills the fields from the stored address and leaves the control pristine', async () => {
    const { input, control } = await setupAddress({ value: BUDAPEST });

    expect(input('street')?.value).toBe('Fő utca 1.');
    expect(input('city')?.value).toBe('Budapest');
    expect(control.pristine).toBe(true);
  });

  it('publishes the edited address to the attribute and dirties it', async () => {
    const { input, control } = await setupAddress({ value: BUDAPEST });

    type(input('street'), 'Fő utca 2.');

    expect(control.value).toEqual({ ...BUDAPEST, street: 'Fő utca 2.' });
    expect(control.dirty).toBe(true);
  });

  it('starts a new address in the default country but publishes nothing until a line is typed', async () => {
    const { component, control, input } = await setupAddress({ defaultCountry: 'DE' });

    expect(component.fields.controls.country.value).toBe('DE');
    expect(control.value).toBeUndefined();

    type(input('city'), 'Berlin');
    expect(control.value).toEqual({ country: 'DE', city: 'Berlin' });

    type(input('city'), '  ');
    expect(control.value).toBeNull();
  });

  it('leaves out the fields the selected country has no place for', async () => {
    const { component, control } = await setupAddress({ value: { country: 'US', street: '1 Main St', city: 'Springfield', region: 'IL', postalCode: '62701' } });

    component.fields.controls.country.setValue('DE');

    expect(control.value).toEqual({ country: 'DE', street: '1 Main St', city: 'Springfield', postalCode: '62701' });
  });

  it('makes a required address invalid until country, street and city are filled', async () => {
    const { control, input } = await setupAddress({ required: true, defaultCountry: 'DE' });

    expect(control.valid).toBe(false);
    type(input('street'), 'Unter den Linden 1');
    type(input('city'), 'Berlin');
    expect(control.valid).toBe(true);
  });

  it('disables the fields of a disabled attribute', async () => {
    const { component } = await setupAddress({ value: BUDAPEST, disabled: true, showMap: true });

    expect(component.fields.disabled).toBe(true);
    expect(component.isEditable()).toBe(false);
  });

  describe('without showMap', () => {
    it('shows no map, no coordinates and asks no geocoder', async () => {
      const { element, input, geocode } = await setupAddress({ value: BUDAPEST });

      type(input('street'), 'Fő utca 3.');

      expect(element.querySelector('[data-testid="address-latitude"]')).toBeNull();
      expect(element.querySelector('iframe')).toBeNull();
      expect(geocode).not.toHaveBeenCalled();
    });

    it('drops stored coordinates from the published value', async () => {
      const { input, control } = await setupAddress({ value: { ...BUDAPEST, latitude: 47.5, longitude: 19.04 } });

      type(input('street'), 'Fő utca 3.');

      expect(control.value).toEqual({ ...BUDAPEST, street: 'Fő utca 3.' });
    });
  });

  describe('with showMap', () => {
    it('shows the stored location on an OpenStreetMap map that links to Google Maps', async () => {
      const { element } = await setupAddress({ value: { ...BUDAPEST, latitude: 47.5, longitude: 19.04 }, showMap: true });

      expect(element.querySelector('iframe')?.getAttribute('src')).toContain('https://www.openstreetmap.org/export/embed.html');
      expect(element.querySelector('[data-testid="address-map-link"]')?.getAttribute('href')).toBe('https://www.google.com/maps/search/?api=1&query=47.5,19.04');
    });

    it('draws the map with Google when the attribute says so', async () => {
      const { element } = await setupAddress({ value: { ...BUDAPEST, latitude: 47.5, longitude: 19.04 }, showMap: true, mapProvider: 'GOOGLE' });

      expect(element.querySelector('iframe')?.getAttribute('src')).toContain('https://maps.google.com/maps?q=47.5,19.04');
    });

    it('says there is no location yet when the address has no coordinates', async () => {
      const { element } = await setupAddress({ value: BUDAPEST, showMap: true });

      expect(element.querySelector('iframe')).toBeNull();
      expect(element.querySelector('[data-testid="address-no-location"]')).not.toBeNull();
    });

    it('derives the coordinates from the address when a field is left', async () => {
      const { input, control, geocode, fixture, element } = await setupAddress({ value: BUDAPEST, showMap: true, found: { latitude: 47.49, longitude: 19.03 } });

      type(input('street'), 'Fő utca 2.');
      await fixture.whenStable();
      fixture.detectChanges();

      expect(geocode).toHaveBeenCalledWith({ ...BUDAPEST, street: 'Fő utca 2.' }, expect.any(String));
      expect(control.value).toEqual({ ...BUDAPEST, street: 'Fő utca 2.', latitude: 47.49, longitude: 19.03, locationSource: 'GEOCODED' });
      expect(element.querySelector('iframe')).not.toBeNull();
    });

    it('does not ask again when a field is left unchanged', async () => {
      const { component, geocode } = await setupAddress({ value: { ...BUDAPEST, latitude: 47.5, longitude: 19.04 }, showMap: true });

      component.onAddressChanged();

      expect(geocode).not.toHaveBeenCalled();
    });

    it('keeps a pin the user typed when the address changes, until Locate is pressed', async () => {
      const { input, control, geocode, component } = await setupAddress({ value: BUDAPEST, showMap: true, found: { latitude: 47.49, longitude: 19.03 } });

      type(input('latitude'), '47.1');
      type(input('longitude'), '19.1');
      type(input('street'), 'Fő utca 9.');
      expect(geocode).not.toHaveBeenCalled();
      expect(control.value).toMatchObject({ latitude: 47.1, longitude: 19.1, locationSource: 'MANUAL' });

      await component.locate();
      expect(control.value).toMatchObject({ latitude: 47.49, longitude: 19.03, locationSource: 'GEOCODED' });
    });

    it('says so when the address cannot be found, and keeps the coordinates', async () => {
      const { component, control, fixture, element } = await setupAddress({ value: { ...BUDAPEST, latitude: 47.5, longitude: 19.04 }, showMap: true });

      await component.locate();
      fixture.detectChanges();

      expect(element.querySelector('[data-testid="address-not-found"]')).not.toBeNull();
      expect(control.pristine).toBe(true);
    });
  });
});
