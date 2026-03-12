import { BaseEntityStatusbarComponent } from './base-entity-statusbar.component';
import { setupContainerComponentTest } from '../../test-setup';
import { describe, expect, it } from 'vitest';

describe('BaseStatusbarComponent', () => {
  it('should create', async () => {
    const { component } = await setupContainerComponentTest(BaseEntityStatusbarComponent);
    expect(component).toBeTruthy();
  });
});
