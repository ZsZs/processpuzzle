import { describe, expect, it, vi } from 'vitest';
import { FormControlType } from '../../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../../base-entity/base-entity-attr.descriptor';
import { TestEntity } from '../../test-entity';
import { setupFormControlTest } from '../../../test-setup';
import { GeoLocation } from './geo-location';
import { GeoLocationComponent } from './geo-location.component';
import { GeoLocator, GeoLocatorFailure } from './geo-locator';

const STORED: GeoLocation = { latitude: 47.4979, longitude: 19.0402, accuracy: 12, capturedAt: '2026-10-17T08:00:00.000Z' };
const FRESH: GeoLocation = { latitude: 48.2082, longitude: 16.3738, accuracy: 5, capturedAt: '2026-10-17T09:00:00.000Z' };

interface SetupOptions {
  value?: unknown;
  disabled?: boolean;
  available?: boolean;
  denied?: boolean;
  locate?: () => Promise<GeoLocation>;
}

async function setupGeoLocation(options: SetupOptions = {}) {
  const config = new BaseEntityAttrDescriptor('location', FormControlType.GEO_LOCATION, 'Location');
  config.disabled = options.disabled === true;
  const entity = new TestEntity('1', 'name');
  if (options.value !== undefined) Reflect.set(entity, 'location', options.value);
  const locator = {
    isAvailable: vi.fn(() => options.available !== false),
    isDenied: vi.fn(async (_onChange?: (denied: boolean) => void) => options.denied === true),
    locate: vi.fn(options.locate ?? (async () => FRESH)),
  };
  const harness = await setupFormControlTest(GeoLocationComponent, config, entity, [{ provide: GeoLocator, useValue: locator }]);
  await harness.fixture.whenStable();
  harness.fixture.detectChanges();
  const component = harness.component as GeoLocationComponent<TestEntity>;
  const control = component.formGroup.get('location');
  if (!control) throw new Error('the location control was not registered');
  const element = harness.fixture.nativeElement as HTMLElement;
  const byTestId = <E extends HTMLElement>(id: string) => element.querySelector<E>(`[data-testid="geo-location-${id}"]`);
  return { ...harness, component, control, element, locator, byTestId };
}

describe('GeoLocationComponent', () => {
  it('shows the stored location read-only, with a link to Google Maps, and leaves the control pristine', async () => {
    const { byTestId, control, element } = await setupGeoLocation({ value: STORED });

    expect(byTestId('value')?.textContent).toContain('47.497900, 19.040200 (±12 m)');
    expect(byTestId<HTMLAnchorElement>('map-link')?.href).toContain('query=47.4979,19.0402');
    expect(byTestId('captured')).not.toBeNull();
    expect(element.querySelector('input')).toBeNull();
    expect(control.pristine).toBe(true);
  });

  it('says so when no location was recorded yet', async () => {
    const { byTestId } = await setupGeoLocation();

    expect(byTestId('empty')).not.toBeNull();
    expect(byTestId('value')).toBeNull();
  });

  it('records the current location on a press and dirties the control, so Save is offered', async () => {
    const { byTestId, control, fixture, locator } = await setupGeoLocation({ value: STORED });

    byTestId<HTMLButtonElement>('capture')?.click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(locator.locate).toHaveBeenCalledOnce();
    expect(control.value).toEqual(FRESH);
    expect(control.dirty).toBe(true);
    expect(byTestId('value')?.textContent).toContain('48.208200, 16.373800');
  });

  it('disables the button where the browser offers no location', async () => {
    const { byTestId, locator } = await setupGeoLocation({ available: false });

    expect(byTestId<HTMLButtonElement>('capture')?.disabled).toBe(true);
    expect(locator.isDenied).not.toHaveBeenCalled();
  });

  it('disables the button when the user has refused this site location access', async () => {
    const { byTestId } = await setupGeoLocation({ denied: true });

    expect(byTestId<HTMLButtonElement>('capture')?.disabled).toBe(true);
  });

  it('follows a change of the permission', async () => {
    const { byTestId, locator, fixture } = await setupGeoLocation();
    const onChange = locator.isDenied.mock.calls[0][0];

    onChange?.(true);
    fixture.detectChanges();

    expect(byTestId<HTMLButtonElement>('capture')?.disabled).toBe(true);
  });

  it('reports a refused reading, disables the button and keeps the stored value', async () => {
    const { component, control, byTestId, fixture } = await setupGeoLocation({
      value: STORED,
      locate: () => Promise.reject(new GeoLocatorFailure('PERMISSION_DENIED')),
    });

    await component.capture();
    fixture.detectChanges();

    expect(component.failure()).toBe('PERMISSION_DENIED');
    expect(byTestId('error')).not.toBeNull();
    expect(byTestId<HTMLButtonElement>('capture')?.disabled).toBe(true);
    expect(control.value).toEqual(STORED);
    expect(control.pristine).toBe(true);
  });

  it('keeps the button usable after a timeout, so the user can try again', async () => {
    const { component, byTestId, fixture } = await setupGeoLocation({ locate: () => Promise.reject(new GeoLocatorFailure('TIMEOUT')) });

    await component.capture();
    fixture.detectChanges();

    expect(component.failure()).toBe('TIMEOUT');
    expect(byTestId<HTMLButtonElement>('capture')?.disabled).toBe(false);
  });

  it('treats an unexpected error as an unavailable position', async () => {
    const { component } = await setupGeoLocation({ locate: () => Promise.reject(new Error('boom')) });

    await component.capture();

    expect(component.failure()).toBe('POSITION_UNAVAILABLE');
  });

  it('marks the browser unavailable when the API turns out to be missing', async () => {
    const { component } = await setupGeoLocation({ locate: () => Promise.reject(new GeoLocatorFailure('UNAVAILABLE')) });

    await component.capture();

    expect(component.unavailable()).toBe(true);
  });

  it('offers no button on a disabled attribute', async () => {
    const { byTestId } = await setupGeoLocation({ value: STORED, disabled: true });

    expect(byTestId('capture')).toBeNull();
    expect(byTestId('value')).not.toBeNull();
  });
});
