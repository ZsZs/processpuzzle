import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { Observable } from 'rxjs';
import { describe, expect, it } from 'vitest';
import { RelatedEntityContainerComponent } from './related-entity/related-entity-container.component';
import { RelatedEntityService } from './related-entity/related-entity.service';
import { RelatedEntityStore } from './related-entity/related-entity.store';
import { TestEntityContainerComponent } from './test-entity/test-entity-container.component';
import { TestEntityService } from './test-entity/test-entity.service';
import { TestEntityStore } from './test-entity/test-entity.store';
import { TestEntityComponentContainerComponent } from './test-entity-component/test-entity-component-container.component';
import { TestEntityComponentService } from './test-entity-component/test-entity-component.service';
import { TestEntityComponentStore } from './test-entity-component/test-entity-component.store';
import { TrunkDataContainerComponent } from './trunk-data/trunk-data-container.component';
import { TrunkDataService } from './trunk-data/trunk-data.service';
import { TrunkDataStore } from './trunk-data/trunk-data.store';

const runtimeConfiguration = {
  BACKEND_SERVICE_ROOT: 'https://api.example.test',
  APP_SERVICE_ROOT: 'https://api.example.test',
};

describe('base-form services and containers', () => {
  it('uses the configured backend root and each entity resource when loading samples', () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIGURATION, useValue: runtimeConfiguration },
      ],
    });
    const requests = TestBed.inject(HttpTestingController);
    const services: Array<[string, (id: string) => Observable<{ id: string } | void>]> = [
      ['test-entity', (id) => TestBed.inject(TestEntityService).findById(id)],
      ['test-entity-component', (id) => TestBed.inject(TestEntityComponentService).findById(id)],
      ['related-entity', (id) => TestBed.inject(RelatedEntityService).findById(id)],
      ['trunk-data', (id) => TestBed.inject(TrunkDataService).findById(id)],
    ];

    for (const [resource, load] of services) {
      let id: string | undefined;
      load(resource).subscribe((entity) => (id = entity?.id));
      requests.expectOne(`/${resource}/${resource}`).flush({ id: resource });
      expect(id).toBe(resource);
    }
    requests.verify();
  });

  it('binds each generated container descriptor to its corresponding store', () => {
    const testEntityStore = {};
    const componentStore = {};
    const relatedStore = {};
    const trunkStore = {};
    TestBed.configureTestingModule({
      providers: [
        { provide: TestEntityStore, useValue: testEntityStore },
        { provide: TestEntityComponentStore, useValue: componentStore },
        { provide: RelatedEntityStore, useValue: relatedStore },
        { provide: TrunkDataStore, useValue: trunkStore },
      ],
    });

    const containers = TestBed.runInInjectionContext(() => [
      new TestEntityContainerComponent(),
      new TestEntityComponentContainerComponent(),
      new RelatedEntityContainerComponent(),
      new TrunkDataContainerComponent(),
    ]);

    expect(containers.map((container) => ('baseEntityDescriptor' in container ? container.baseEntityDescriptor.store : container.entityDescriptor.store))).toEqual([
      testEntityStore,
      componentStore,
      relatedStore,
      trunkStore,
    ]);
  });
});
