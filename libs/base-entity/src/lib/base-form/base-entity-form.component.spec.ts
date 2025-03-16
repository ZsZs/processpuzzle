import { By } from '@angular/platform-browser';
import { TestBed } from '@angular/core/testing';
import { TestEntity } from '../test-entity';
import { FormControlType } from '../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../base-entity/base-entity-attr.descriptor';
import { setupFormComponentTest } from '../../test-setup';

describe('GenericEntityFormComponent', () => {
  const labelConfig = new BaseEntityAttrDescriptor('description', FormControlType.LABEL);
  const checkboxConfig: BaseEntityAttrDescriptor = new BaseEntityAttrDescriptor('boolean', FormControlType.CHECKBOX, 'Is project approved from the business?', undefined, false);
  const testEntity: TestEntity = new TestEntity('1', 'test-entity', 'description of the entity', true);

  describe('sanity tests', () => {
    it('should create', async () => {
      const { component } = await setupFormComponentTest([labelConfig, checkboxConfig], testEntity);
      expect(component).toBeTruthy();
    });
  });

  describe('template structure contains:', () => {
    it('mat-card, mat-card-content, mat-card-actions', async () => {
      const { fixture } = await setupFormComponentTest([labelConfig, checkboxConfig], testEntity);
      const matCard = fixture.debugElement.query(By.css('mat-card')).nativeElement;
      expect(matCard).toBeTruthy();

      const matCardContent = fixture.debugElement.query(By.css('mat-card-content')).nativeElement;
      expect(matCardContent).toBeTruthy();

      const matCardActions = fixture.debugElement.query(By.css('mat-card-actions')).nativeElement;
      expect(matCardActions).toBeTruthy();
    });

    it('form and the form controls', async () => {
      const { fixture } = await setupFormComponentTest([labelConfig, checkboxConfig], testEntity);
      const form = fixture.debugElement.query(By.css('form')).nativeElement;
      expect(form).toBeTruthy();

      const checkbox = fixture.debugElement.query(By.css('form > base-checkbox')).nativeElement;
      expect(checkbox).toBeTruthy();

      const label = fixture.debugElement.query(By.css('form > base-label')).nativeElement;
      expect(label).toBeTruthy();
    });
  });

  describe('form actions:', () => {
    it('onCancel()', async () => {
      // SETUP:
      const { fixture, component } = await setupFormComponentTest([labelConfig, checkboxConfig], testEntity);
      jest.spyOn(component, 'onCancel');
      const cancelButton = fixture.debugElement.query(By.css('mat-card-actions > button#cancel')).nativeElement;

      // EXERCISE:
      cancelButton.click();

      // VERIFY:
      expect(cancelButton).toBeTruthy();
      expect(component.onCancel).toHaveBeenCalled();
    });

    it('onSubmit(), when its an existing object updates it in store.', async () => {
      // SETUP:
      const { fixture, component, store } = await setupFormComponentTest([labelConfig, checkboxConfig], testEntity);
      fixture.detectChanges();
      await fixture.whenStable().then(() => {
        const checkbox = fixture.debugElement.query(By.css('form input[type=checkbox]')).nativeElement;
        checkbox.click(); // trigger form changes (dirty)
        checkbox.dispatchEvent(new Event('input'));
        fixture.detectChanges();
        const submitButton = fixture.debugElement.query(By.css('mat-card-actions > button#submit')).nativeElement;
        jest.spyOn(component, 'onSubmit');
        jest.spyOn(store, 'update');
        jest.spyOn(store, 'setCurrentEntity');
        jest.spyOn(store, 'navigateBack');

        // EXERCISE:
        submitButton.click();

        // VERIFY:
        expect(component.onSubmit).toHaveBeenCalled();
        expect(store.update).toHaveBeenCalledWith({ ...testEntity, ...component.baseEntityForm.value }, testEntity.id);
        expect(store.setCurrentEntity).toHaveBeenCalledWith(undefined);
        expect(store.navigateBack).toHaveBeenCalled();
      });
    });

    it('onSubmit(), when its new object saves it in store.', async () => {
      // SETUP:
      const { fixture, component, store } = await setupFormComponentTest([labelConfig, checkboxConfig], undefined, true);
      TestBed.flushEffects();
      fixture.detectChanges();
      await fixture.whenStable().then(() => {
        const checkbox = fixture.debugElement.query(By.css('form input[type=checkbox]')).nativeElement;
        checkbox.click(); // trigger form changes (dirty)
        component.baseEntityForm.get('description')?.setValue('hello world');
        checkbox.dispatchEvent(new Event('input'));
        fixture.detectChanges();
        const submitButton = fixture.debugElement.query(By.css('mat-card-actions > button#submit')).nativeElement;
        jest.spyOn(component, 'onSubmit');
        jest.spyOn(store, 'add');
        jest.spyOn(store, 'setCurrentEntity');
        jest.spyOn(store, 'navigateBack');

        // EXERCISE:
        submitButton.click();

        // VERIFY:
        expect(component.onSubmit).toHaveBeenCalled();
        expect(store.add).toHaveBeenCalledWith({ ...component.entity(), ...component.baseEntityForm.value });
        expect(store.setCurrentEntity).toHaveBeenCalledWith(undefined);
        expect(store.navigateBack).toHaveBeenCalled();
      });
    });
  });
});
