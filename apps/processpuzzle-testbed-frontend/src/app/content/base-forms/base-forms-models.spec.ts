import { TestBed } from '@angular/core/testing';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, FlexboxDescriptor, FormControlType } from '@processpuzzle/base-entity';
import { beforeEach, describe, expect, it } from 'vitest';
import { EmbeddedComponent } from './embedded-component/embedded-component';
import { createEmbeddedComponentDescriptor } from './embedded-component/embedded-component.descriptors';
import { EmbeddedComponentMapper } from './embedded-component/embedded-component.mapper';
import { EmbeddedDetail } from './embedded-detail/embedded-detail';
import { createEmbeddedDetailDescriptor } from './embedded-detail/embedded-detail.descriptors';
import { EmbeddedDetailMapper } from './embedded-detail/embedded-detail.mapper';
import { RelatedEntity } from './related-entity/related-entity';
import { createRelatedEntityDescriptor } from './related-entity/related-entity.descriptors';
import { RelatedEntityMapper } from './related-entity/related-entity.mapper';
import { TestEntity, TestEnum } from './test-entity/test-entity';
import { createTestEntityDescriptor } from './test-entity/test-entity.descriptors';
import { TestEntityMapper } from './test-entity/test-entity.mapper';
import { TestEntityComponent } from './test-entity-component/test-entity-component';
import { createTestEntityComponentDescriptor } from './test-entity-component/test-entity-component.descriptors';
import { TestEntityComponentMapper } from './test-entity-component/test-entity-component.mapper';
import { TrunkData } from './trunk-data/trunk-data';
import { createTrunkDataDescriptor } from './trunk-data/trunk-data.descriptors';
import { TrunkDataMapper } from './trunk-data/trunk-data.mapper';

function attributes(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? attributes(descriptor.attrDescriptors) : [descriptor as BaseEntityAttrDescriptor]));
}

describe('base-form sample models and descriptors', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [TestEntityMapper, TestEntityComponentMapper, RelatedEntityMapper, TrunkDataMapper, EmbeddedComponentMapper, EmbeddedDetailMapper],
    });
  });

  it('maps a test entity without losing nested embedded rows, associations, or component ids', () => {
    const mapper = TestBed.inject(TestEntityMapper);
    const entity = mapper.fromDto({
      id: 'test-1',
      name: 'Example',
      enumValue: TestEnum.VALUE_THREE,
      components: ['component-1', { id: 'component-2' }, {}],
      embeddedComponents: [{ id: 'embedded-1', name: 'Address', embeddedDetails: [{ id: 'detail-1', name: 'Street', note: 'Main' }] }],
      relatedEntities: [{ id: 'related-1', name: 'Customer', description: 'Primary' }],
      additionalProperties: { source: 'sample' },
    });

    expect(entity).toMatchObject({
      id: 'test-1',
      name: 'Example',
      enumValue: 'VALUE_THREE',
      components: ['component-1', 'component-2'],
      additionalProperties: { source: 'sample' },
    });
    expect(entity.embeddedComponents?.[0]).toBeInstanceOf(EmbeddedComponent);
    expect(entity.embeddedComponents?.[0].embeddedDetails[0]).toBeInstanceOf(EmbeddedDetail);
    expect(entity.relatedEntities?.[0]).toBeInstanceOf(RelatedEntity);
    expect(mapper.toDto(entity)).toMatchObject({ enumValue: TestEnum.VALUE_THREE });
  });

  it('uses stable supplied values and useful defaults for all sample entities', () => {
    expect(new TestEntity({ id: 'entity', name: 'Name' })).toMatchObject({ id: 'entity', name: 'Name', boolean: true, number: 1, tags: undefined });
    expect(new TestEntityComponent('component', 'Name', 'Description', 'entity')).toMatchObject({ id: 'component', testEntityId: 'entity' });
    expect(new RelatedEntity('related', 'Name', 'Description')).toMatchObject({ id: 'related', description: 'Description' });
    expect(new TrunkData('trunk', 'key', 42, 'Description')).toMatchObject({ id: 'trunk', key: 'key', value: 42 });
    expect(new EmbeddedComponent('embedded', 'Name', 'Description', [new EmbeddedDetail('detail')])).toMatchObject({ id: 'embedded', embeddedDetails: [{ id: 'detail' }] });
    expect(new EmbeddedDetail('detail', 'Name', 'Note')).toMatchObject({ id: 'detail', note: 'Note' });
  });

  it('maps standalone and embedded sample entities through their concrete constructors', () => {
    expect(TestBed.inject(TestEntityComponentMapper).fromDto({ id: 'component', testEntityId: 'parent' })).toBeInstanceOf(TestEntityComponent);
    expect(TestBed.inject(RelatedEntityMapper).fromDto({ id: 'related' })).toBeInstanceOf(RelatedEntity);
    expect(TestBed.inject(TrunkDataMapper).fromDto({ id: 'trunk', value: 'value' })).toBeInstanceOf(TrunkData);
    expect(TestBed.inject(EmbeddedComponentMapper).fromDto({ id: 'embedded', embeddedDetails: [{ id: 'detail' }] }).embeddedDetails[0]).toBeInstanceOf(EmbeddedDetail);
    expect(TestBed.inject(EmbeddedDetailMapper).toDto(new EmbeddedDetail('detail'))).toMatchObject({ id: 'detail' });
  });

  it('describes containment and associations so generated forms use the correct editing behavior', () => {
    const testEntity = createTestEntityDescriptor();
    const testEntityAttrs = attributes(testEntity.attrDescriptors);
    const component = createTestEntityComponentDescriptor();
    const embeddedComponent = createEmbeddedComponentDescriptor();
    const embeddedDetail = createEmbeddedDetailDescriptor();

    expect(testEntity.entityName).toBe('Test Entity');
    expect(testEntityAttrs.find((attr) => attr.attrName === 'lookup')).toMatchObject({ linkedEntityType: 'Trunk Data' });
    expect(testEntityAttrs.find((attr) => attr.attrName === 'components')).toMatchObject({ formControlType: FormControlType.COMPONENTS, linkedEntityType: 'Test Entity Component', hideInTable: true });
    expect(testEntity.embeddedAttrFor('Embedded Component')).toMatchObject({ attrName: 'embeddedComponents' });
    expect(testEntityAttrs.find((attr) => attr.attrName === 'relatedEntities')).toMatchObject({ formControlType: FormControlType.RELATED_ENTITIES, linkedEntityType: 'Related Entity' });
    expect(component.parentReferenceAttrName()).toBe('testEntityId');
    expect(embeddedComponent).toMatchObject({ componentParents: ['Test Entity'], isEmbedded: true });
    expect(embeddedComponent.embeddedAttrFor('Embedded Detail')).toMatchObject({ attrName: 'embeddedDetails' });
    expect(embeddedDetail).toMatchObject({ componentParents: ['Embedded Component'], isEmbedded: true });
  });

  it('keeps lookup and association roots separate from embedded components', () => {
    const related = createRelatedEntityDescriptor();
    const trunk = createTrunkDataDescriptor();

    expect(related.isComponent()).toBe(false);
    expect(attributes(related.attrDescriptors).map((attr) => attr.attrName)).toEqual(['name', 'description']);
    expect(attributes(trunk.attrDescriptors).find((attr) => attr.attrName === 'key')).toMatchObject({ required: true, isLinkToDetails: true });
    expect(trunk.componentIdentification()).toBe('key');
  });
});
