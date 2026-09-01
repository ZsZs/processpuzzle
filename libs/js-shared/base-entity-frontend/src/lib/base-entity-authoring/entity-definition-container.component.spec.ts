import { describe, expect, it, vi } from 'vitest';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { DynamicEntityRegistry } from '../base-entity-definition/dynamic-entity.registry';
import { EntityDefinitionContainerComponent } from './entity-definition-container.component';

/**
 * Stands in for `base-entity-container`, whose own rendering — the tabs, the toolbar, the status bar and the
 * outlet the child routes render in — is tested where it lives. Mounting the real one here would drag in the
 * router, the HTTP client and a store, none of which this component touches.
 */
@Component({ selector: 'base-entity-container', standalone: true, template: '<p>container</p>' })
class ContainerStubComponent {}

function setup() {
  const reset = vi.fn();

  TestBed.resetTestingModule();
  TestBed.configureTestingModule({ providers: [{ provide: DynamicEntityRegistry, useValue: { reset } }] });
  TestBed.overrideComponent(EntityDefinitionContainerComponent, { set: { imports: [ContainerStubComponent] } });

  return { fixture: TestBed.createComponent(EntityDefinitionContainerComponent), reset };
}

describe('EntityDefinitionContainerComponent', () => {
  /** No input binding: the container resolves its descriptor and store from `ACTIVE_ENTITY_FACADE`. */
  it('renders the generic container for the definition resolved from the active facade', () => {
    const { fixture } = setup();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('base-entity-container')).toBeTruthy();
  });

  it('keeps the run-time caches while the section is open', () => {
    const { fixture, reset } = setup();
    fixture.detectChanges();

    expect(reset).not.toHaveBeenCalled();
  });

  /**
   * The whole reason this component exists: a definition edited here decides what every
   * `EntityScreenResolver`-mounted screen and the Preview tab should render next, and a session-long cache
   * would otherwise keep serving the descriptor built before the edit.
   */
  it('discards the synthesized descriptors when the author leaves the section', () => {
    const { fixture, reset } = setup();
    fixture.detectChanges();

    fixture.destroy();

    expect(reset).toHaveBeenCalledOnce();
  });
});
