import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { EmbeddedComponentFacade } from './embedded-component/embedded-component.facade';
import { EmbeddedComponent } from './embedded-component/embedded-component';
import { EmbeddedDetailFacade } from './embedded-detail/embedded-detail.facade';
import { EmbeddedDetail } from './embedded-detail/embedded-detail';
import { RelatedEntityFacade } from './related-entity/related-entity.facade';
import { RelatedEntityMapper } from './related-entity/related-entity.mapper';
import { RelatedEntityService } from './related-entity/related-entity.service';
import { RelatedEntityStore } from './related-entity/related-entity.store';
import { TestEntityFacade } from './test-entity/test-entity.facade';
import { TestEntityMapper } from './test-entity/test-entity.mapper';
import { TestEntityService } from './test-entity/test-entity.service';
import { TestEntityStore } from './test-entity/test-entity.store';
import { TestEntityComponentFacade } from './test-entity-component/test-entity-component.facade';
import { TestEntityComponentMapper } from './test-entity-component/test-entity-component.mapper';
import { TestEntityComponentService } from './test-entity-component/test-entity-component.service';
import { TestEntityComponentStore } from './test-entity-component/test-entity-component.store';
import { TrunkDataFacade } from './trunk-data/trunk-data.facade';
import { TrunkDataMapper } from './trunk-data/trunk-data.mapper';
import { TrunkDataService } from './trunk-data/trunk-data.service';
import { TrunkDataStore } from './trunk-data/trunk-data.store';

describe('base-form facades', () => {
  it('connects each routable facade to its mapper, service, store, and descriptor', () => {
    const testEntityMapper = {};
    const testEntityService = {};
    const testEntityStore = {};
    const componentMapper = {};
    const componentService = {};
    const componentStore = {};
    const relatedMapper = {};
    const relatedService = {};
    const relatedStore = {};
    const trunkMapper = {};
    const trunkService = {};
    const trunkStore = {};
    TestBed.configureTestingModule({
      providers: [
        TestEntityFacade, TestEntityComponentFacade, RelatedEntityFacade, TrunkDataFacade, EmbeddedComponentFacade, EmbeddedDetailFacade,
        { provide: TestEntityMapper, useValue: testEntityMapper }, { provide: TestEntityService, useValue: testEntityService }, { provide: TestEntityStore, useValue: testEntityStore },
        { provide: TestEntityComponentMapper, useValue: componentMapper }, { provide: TestEntityComponentService, useValue: componentService }, { provide: TestEntityComponentStore, useValue: componentStore },
        { provide: RelatedEntityMapper, useValue: relatedMapper }, { provide: RelatedEntityService, useValue: relatedService }, { provide: RelatedEntityStore, useValue: relatedStore },
        { provide: TrunkDataMapper, useValue: trunkMapper }, { provide: TrunkDataService, useValue: trunkService }, { provide: TrunkDataStore, useValue: trunkStore },
      ],
    });

    const testEntity = TestBed.inject(TestEntityFacade);
    const component = TestBed.inject(TestEntityComponentFacade);
    const related = TestBed.inject(RelatedEntityFacade);
    const trunk = TestBed.inject(TrunkDataFacade);

    expect(testEntity.mapper).toBe(testEntityMapper);
    expect(testEntity.service).toBe(testEntityService);
    expect(testEntity.descriptor.store).toBe(testEntityStore);
    expect(component).toMatchObject({ entityName: 'Test Entity Component', mapper: componentMapper, service: componentService });
    expect(component.descriptor.store).toBe(componentStore);
    expect(related).toMatchObject({ entityName: 'Related Entity', mapper: relatedMapper, service: relatedService });
    expect(related.descriptor.store).toBe(relatedStore);
    expect(trunk).toMatchObject({ entityName: 'Trunk Data', mapper: trunkMapper, service: trunkService });
    expect(trunk.descriptor.store).toBe(trunkStore);
    expect(TestBed.inject(EmbeddedComponentFacade)).toMatchObject({ entityName: 'Embedded Component', entityType: EmbeddedComponent });
    expect(TestBed.inject(EmbeddedDetailFacade)).toMatchObject({ entityName: 'Embedded Detail', entityType: EmbeddedDetail });
  });
});
