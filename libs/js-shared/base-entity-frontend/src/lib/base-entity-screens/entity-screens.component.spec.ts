import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, input } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { FormControlType } from '../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../base-entity/base-entity-attr.descriptor';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { beforeEach, describe, expect, it } from 'vitest';
import { BaseEntityScreensComponent, ENTITY_DESCRIPTOR_ROUTE_DATA_KEY, REQUESTED_ENTITY_ROUTE_DATA_KEY } from './entity-screens.component';

/**
 * Stands in for `base-entity-container`, whose own rendering — the tabs, the toolbar, the status bar and the
 * outlet the child routes render in — is base-entity's to test. It has to declare the same input, or the
 * template's binding is an unknown property on it.
 */
@Component({ selector: 'base-entity-container', standalone: true, template: '<p>container</p>' })
class ContainerStubComponent {
  readonly entityDescriptor = input<BaseEntityDescriptor | undefined>(undefined);
}

function orderDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: 'Order',
    entityTitle: 'Order',
    attrDescriptors: [new BaseEntityAttrDescriptor('orderNumber', FormControlType.TEXT_BOX, 'Order #', undefined, true)],
  });
}

function render(data: Record<string, unknown>) {
  TestBed.configureTestingModule({
    providers: [provideHttpClient(), provideHttpClientTesting(), provideTranslocoTesting({ translations: {} }), { provide: ActivatedRoute, useValue: { snapshot: { data } } }],
  });
  TestBed.overrideComponent(BaseEntityScreensComponent, { set: { imports: [ContainerStubComponent] } });

  const fixture = TestBed.createComponent(BaseEntityScreensComponent);
  fixture.detectChanges();
  return fixture;
}

describe('BaseEntityScreensComponent', () => {
  beforeEach(() => {
    TestBed.resetTestingModule();
  });

  it('renders the entity container for the descriptor the renderer resolved', () => {
    const descriptor = orderDescriptor();

    const fixture = render({ [REQUESTED_ENTITY_ROUTE_DATA_KEY]: 'Order', [ENTITY_DESCRIPTOR_ROUTE_DATA_KEY]: descriptor });

    expect(fixture.nativeElement.querySelector('base-entity-container')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.base-entity-screens-unresolved')).toBeNull();
  });

  /**
   * A definition may name an entity that has been renamed, or one whose definitions this deployment's
   * backend does not serve. The nav item that links here still has to render something, and naming the
   * entity is what makes the cause legible.
   */
  it('names the entity it could not resolve, rather than rendering nothing', () => {
    const fixture = render({ [REQUESTED_ENTITY_ROUTE_DATA_KEY]: 'Gone' });

    expect(fixture.nativeElement.querySelector('base-entity-container')).toBeNull();
    expect(fixture.nativeElement.querySelector('.base-entity-screens-unresolved').textContent).toContain("No entity type registered for 'Gone' yet.");
  });

  it('renders the unresolved state for a route naming no entity at all', () => {
    const fixture = render({});

    expect(fixture.nativeElement.querySelector('.base-entity-screens-unresolved')).toBeTruthy();
  });
});
