import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { DynamicEntityRegistry } from './dynamic-entity.registry';
import { ENTITY_PREVIEW_READ_ONLY, EntityPreviewMode } from './entity-preview-mode';

describe('EntityPreviewMode', () => {
  const dynamicEntities = { descriptorOf: (name: string | undefined) => (name === 'Order' ? {} : undefined) };

  function previewMode(readOnly?: boolean): EntityPreviewMode {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        { provide: DynamicEntityRegistry, useValue: dynamicEntities },
        ...(readOnly === undefined ? [] : [{ provide: ENTITY_PREVIEW_READ_ONLY, useValue: readOnly }]),
      ],
    });
    return TestBed.inject(EntityPreviewMode);
  }

  it('makes the screens of a definition-built entity read-only while the host previews drafts', () => {
    expect(previewMode(true).isReadOnly('Order')).toBe(true);
  });

  it('leaves the compiled entity types editable, even in the preview', () => {
    expect(previewMode(true).isReadOnly('EntityDefinition')).toBe(false);
    expect(previewMode(true).isReadOnly(undefined)).toBe(false);
  });

  it('leaves everything editable unless the host says otherwise', () => {
    expect(previewMode().isReadOnly('Order')).toBe(false);
    expect(previewMode(false).isReadOnly('Order')).toBe(false);
  });
});
