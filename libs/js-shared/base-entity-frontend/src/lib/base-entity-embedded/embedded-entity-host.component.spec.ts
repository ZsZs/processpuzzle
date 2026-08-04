import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { ACTIVE_ENTITY_FACADE } from '../base-entity-facade/active-entity-facade.token';
import { EmbeddedEntityHostComponent } from './embedded-entity-host.component';

describe('EmbeddedEntityHostComponent', () => {
  const store = { error: signal(undefined) };
  const descriptor = new BaseEntityDescriptor({ attrDescriptors: [], entityName: 'Embedded Component', componentParent: 'Test Entity', isEmbedded: true, store });

  async function setup() {
    await TestBed.configureTestingModule({
      imports: [EmbeddedEntityHostComponent, NoopAnimationsModule],
      providers: [provideRouter([]), { provide: ACTIVE_ENTITY_FACADE, useValue: { descriptor, store } }],
    }).compileComponents();

    const fixture = TestBed.createComponent(EmbeddedEntityHostComponent);
    fixture.detectChanges();
    return fixture;
  }

  /**
   * The whole point of the host: an embedded level's rows are listed on its owner's form, and the breadcrumb
   * in the status bar says where the user is — so a tab bar, toolbar and status bar of its own would repeat
   * both inside the owner's.
   */
  it('renders nothing but the outlet its level’s form is activated in', async () => {
    const fixture = await setup();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('base-entity-tabs')).toBeNull();
    expect(host.querySelector('base-entity-toolbar')).toBeNull();
    expect(host.querySelector('base-entity-statusbar')).toBeNull();
    expect(host.querySelector('router-outlet')).not.toBeNull();
  });

  it('takes its descriptor from the facade the branch route provides', async () => {
    const fixture = await setup();

    expect(Reflect.get(fixture.componentInstance, 'entityDescriptor')()).toBe(descriptor);
  });
});
