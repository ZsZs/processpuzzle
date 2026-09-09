import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { BaseEntityDescriptor } from '@processpuzzle/base-entity';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { BaseDocumentStore } from '../domain/base-document.store';
import { BaseDocumentContainerComponent } from './base-document-container.component';
import { DOCUMENT_CONTENT_TAB } from './document-content-tab';

/** `entityDescriptor` is protected on the component; the spec asserts on it as the tabs component sees it. */
type ContainerInternals = { entityDescriptor: BaseEntityDescriptor };

describe('BaseDocumentContainerComponent', () => {
  let component: BaseDocumentContainerComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { DOCUMENT_SERVICE_ROOT: 'http://localhost:3000/organizations/processpuzzle-testbed' } } },
      ],
    });
    component = TestBed.runInInjectionContext(() => new BaseDocumentContainerComponent());
  });

  /**
   * `BaseEntityTabsComponent` takes its store from `entityDescriptor().store` and calls `tabIsActive` on it
   * from an effect, so an unbound store there is not a latent flaw but an immediate crash on opening the
   * list. `DocumentFacade` binding the store into its own descriptor instance does not cover this one.
   */
  it('hands the tabs a descriptor bound to the store the content editor reads', () => {
    const descriptor = (component as unknown as ContainerInternals).entityDescriptor;
    expect(descriptor.store).toBe(TestBed.inject(BaseDocumentStore));
  });

  /**
   * The same constant BASE_DOCUMENT_ROUTES mounts as `document/<id>/content`. Declared in one place because
   * the segment is what ties the link to the route — two literals could drift into a tab that navigates to a
   * URL nothing matches.
   */
  it('declares the content tab the routes mount', () => {
    const descriptor = (component as unknown as ContainerInternals).entityDescriptor;
    expect(descriptor.extraTabs).toEqual([DOCUMENT_CONTENT_TAB]);
  });
});
