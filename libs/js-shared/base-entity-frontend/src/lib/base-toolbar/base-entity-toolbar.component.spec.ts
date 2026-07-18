import { BaseEntityToolbarComponent } from './base-entity-toolbar.component';
import { setupContainerComponentTest } from '../../test-setup';
import { By } from '@angular/platform-browser';
import { BaseUrlSegments } from '../base-form-navigator/base-url-segments';
import { TestEntity } from '../test-entity';
import { describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';
import { PdfExportService } from '../pdf-service/pdf-export.service';

describe('BaseEntityToolbarComponent', () => {
  describe('sanity tests', () => {
    it('should create', async () => {
      const { component } = await setupContainerComponentTest(BaseEntityToolbarComponent);
      expect(component).toBeTruthy();
    });
  });

  describe('template structure contain:', () => {
    it('when small device menu, otherwise buttons', async () => {
      const { fixture, breakpointObserver } = await setupContainerComponentTest(BaseEntityToolbarComponent);
      const matToolbar = fixture.debugElement.query(By.css('mat-toolbar')).nativeElement;
      expect(matToolbar).toBeTruthy();

      let addButton = fixture.debugElement.query(By.css('mat-toolbar button[data-testid="testEntity-new"]')).nativeElement;
      expect(addButton).toBeTruthy();
      let deleteButton = fixture.debugElement.query(By.css('mat-toolbar button[data-testid="testEntity-delete"]')).nativeElement;
      expect(deleteButton).toBeTruthy();

      breakpointObserver.resize(599);
      fixture.detectChanges();
      const menu = fixture.debugElement.query(By.css('mat-toolbar mat-menu')).nativeElement;
      expect(menu).toBeTruthy();

      const menuButton = fixture.debugElement.query(By.css('button[test-id=menu-button]')).nativeElement;
      menuButton.click(); // make menu list visible
      addButton = fixture.debugElement.query(By.css('button[data-testid="testEntity-new"]')).nativeElement;
      expect(addButton).toBeTruthy();
      deleteButton = fixture.debugElement.query(By.css('button[data-testid="testEntity-delete"]')).nativeElement;
      expect(deleteButton).toBeTruthy();
    });

    it('when activated route aint RouteSegments.LIST_ROUTE toolbar is not displayed', async () => {
      const { fixture, formNavigator } = await setupContainerComponentTest(BaseEntityToolbarComponent);
      await formNavigator.navigateToDetails('TestEntity', '1');
      fixture.detectChanges();
      const matToolbar = fixture.debugElement.query(By.css('mat-toolbar'));
      expect(matToolbar).toBeFalsy();
    });
  });

  describe('angular lifecycle hooks:', () => {
    it('onInit() takes reference to store from BaseEntityListOptions', async () => {
      const { component } = await setupContainerComponentTest(BaseEntityToolbarComponent);
      expect(component.store).toBeTruthy();
    });
  });

  describe('component actions:', () => {
    it('onAddEntity() calls form navigator with id=new', async () => {
      const { component, formNavigator } = await setupContainerComponentTest(BaseEntityToolbarComponent<TestEntity>);
      vi.spyOn(formNavigator, 'navigateToDetails');

      (component as BaseEntityToolbarComponent<TestEntity>).onAddEntity();

      expect(formNavigator.navigateToDetails).toHaveBeenCalledWith('TestEntity', BaseUrlSegments.NewEntity);
    });

    it('onDeleteEntities()', async () => {
      // SETUP:
      const { fixture, component } = await setupContainerComponentTest(BaseEntityToolbarComponent);
      component.store.selectEntity('1');
      component.store.selectEntity('2');
      vi.spyOn(component.store, 'delete');
      fixture.detectChanges();

      // EXERCISE:
      (component as BaseEntityToolbarComponent<TestEntity>).onDeleteEntities();

      // VERIFY:
      expect(component.store.delete).toHaveBeenNthCalledWith(1, '1');
      expect(component.store.delete).toHaveBeenNthCalledWith(2, '2');
    });

    it('shows the PDF export button when the list has entities', async () => {
      const { fixture } = await setupContainerComponentTest(BaseEntityToolbarComponent);
      const pdfButton = fixture.debugElement.query(By.css('button[data-testid="testEntity-pdf-export"]'));
      expect(pdfButton).toBeTruthy();
    });

    it('onExportPdf() runs the export with descriptor columns when the dialog is confirmed', async () => {
      const { component } = await setupContainerComponentTest(BaseEntityToolbarComponent<TestEntity>);
      const dialog = TestBed.inject(MatDialog);
      vi.spyOn(dialog, 'open').mockReturnValue({ afterClosed: () => of({ orientation: 'landscape', pageSize: 'a4', includeFooter: true }) } as ReturnType<MatDialog['open']>);
      const pdfExportService = TestBed.inject(PdfExportService);
      const exportSpy = vi.spyOn(pdfExportService, 'export').mockResolvedValue({ success: true, filename: 'testentity-export.pdf', rowCount: 2 });

      await (component as BaseEntityToolbarComponent<TestEntity>).onExportPdf();

      expect(exportSpy).toHaveBeenCalledTimes(1);
      const [entities, columns] = exportSpy.mock.calls[0];
      expect(entities.length).toBeGreaterThan(0);
      expect(columns.length).toBeGreaterThan(0);
    });

    it('onExportPdf() does nothing when the dialog is cancelled', async () => {
      const { component } = await setupContainerComponentTest(BaseEntityToolbarComponent<TestEntity>);
      const dialog = TestBed.inject(MatDialog);
      vi.spyOn(dialog, 'open').mockReturnValue({ afterClosed: () => of(undefined) } as ReturnType<MatDialog['open']>);
      const exportSpy = vi.spyOn(TestBed.inject(PdfExportService), 'export');

      await (component as BaseEntityToolbarComponent<TestEntity>).onExportPdf();

      expect(exportSpy).not.toHaveBeenCalled();
    });

    it('onDoFilter()', async () => {
      // SETUP:
      const { fixture, component } = await setupContainerComponentTest(BaseEntityToolbarComponent);
      vi.spyOn(component.store, 'doFilter');

      //EXERCISE:
      const filterInput = fixture.debugElement.query(By.css('input[data-testid="testEntity-filter"]')).nativeElement;
      filterInput.value = 'A';
      filterInput.dispatchEvent(new KeyboardEvent('keyup', { code: 'A' }));
      fixture.detectChanges();

      // VERIFY:
      expect(component.store.doFilter).toHaveBeenCalledWith('A');
    });
  });
});
