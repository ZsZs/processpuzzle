import { BaseEntityContainerComponent } from './base-entity-container.component';
import { setupContainerComponentTest } from '../test-setup';
import { describe, expect, it } from 'vitest';

describe('BaseEntityContainerComponent', () => {
  it('should create', async () => {
    const { component } = await setupContainerComponentTest(BaseEntityContainerComponent);

    expect(component).toBeTruthy();
  });
});
