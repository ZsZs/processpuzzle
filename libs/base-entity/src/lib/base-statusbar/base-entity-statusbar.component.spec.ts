import { BaseEntityStatusbarComponent } from './base-entity-statusbar.component';
import { setupContainerComponentTest } from '../../test-setup';

describe('BaseStatusbarComponent', () => {
  it('should create', async () => {
    const { component } = await setupContainerComponentTest(BaseEntityStatusbarComponent);
    expect(component).toBeTruthy();
  });
});
