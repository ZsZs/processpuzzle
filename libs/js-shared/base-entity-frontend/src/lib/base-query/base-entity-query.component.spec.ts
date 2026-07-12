import { TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { By } from '@angular/platform-browser';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { BaseEntityQueryComponent } from './base-entity-query.component';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { BaseEntityAttrDescriptor } from '../base-entity/base-entity-attr.descriptor';
import { FormControlType } from '../base-entity/abstact-attr.descriptor';
import { RsqlEditorDialog } from '../query-editor/rsql-editor.dialog';
import { TestEntity } from '../test-entity';
import { TestEntityStore } from '../test-entity.store';

describe('BaseEntityQueryComponent', () => {
  let store: { load: ReturnType<typeof vi.fn> };
  let entityDescriptor: BaseEntityDescriptor;
  let dialogRefStub: { afterClosed: ReturnType<typeof vi.fn> };
  let matDialogStub: { open: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    store = { load: vi.fn() };
    dialogRefStub = { afterClosed: vi.fn().mockReturnValue(of(null)) };
    matDialogStub = { open: vi.fn().mockReturnValue(dialogRefStub) };
    const nameDescriptor = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX);
    entityDescriptor = new BaseEntityDescriptor({
      store: TestEntityStore,
      attrDescriptors: [nameDescriptor],
      entityName: 'TestEntity',
      entityTitle: 'Test Entity',
    });
    entityDescriptor.store = store;

    await TestBed.configureTestingModule({
      imports: [BaseEntityQueryComponent, NoopAnimationsModule],
      providers: [provideTranslocoTesting({ translations: {} }), { provide: MatDialog, useValue: matDialogStub }],
    }).compileComponents();
  });

  function createComponent() {
    const fixture = TestBed.createComponent(BaseEntityQueryComponent<TestEntity>);
    fixture.componentRef.setInput('entityDescriptor', entityDescriptor);
    fixture.detectChanges();
    return { fixture, component: fixture.componentInstance };
  }

  it('takes the store reference from entityDescriptor on ngOnInit', () => {
    const { component } = createComponent();
    expect(component.store).toBe(store);
  });

  describe('onSendQuery()', () => {
    it('trims the value, sets appliedQuery and calls store.load with the query', () => {
      const { component } = createComponent();
      component.queryValue.set('  name==foo  ');

      component.onSendQuery();

      expect(store.load).toHaveBeenCalledWith({ query: 'name==foo' });
      expect(component.queryValue()).toBe('name==foo');
      expect(component.appliedQuery()).toBe('name==foo');
      expect(component.queryActive()).toBe(true);
    });

    it('does nothing when the query is empty or whitespace only', () => {
      const { component } = createComponent();
      component.queryValue.set('   ');

      component.onSendQuery();

      expect(store.load).not.toHaveBeenCalled();
      expect(component.appliedQuery()).toBeUndefined();
    });

    it('fires when Enter is pressed in the input', () => {
      const { fixture, component } = createComponent();
      component.queryValue.set('name==foo');
      fixture.detectChanges();

      const input = fixture.debugElement.query(By.css('input')).nativeElement as HTMLInputElement;
      input.dispatchEvent(new KeyboardEvent('keyup', { key: 'Enter' }));

      expect(store.load).toHaveBeenCalledWith({ query: 'name==foo' });
    });
  });

  describe('onClearQuery()', () => {
    it('resets queryValue and appliedQuery and calls store.load with an empty condition', () => {
      const { fixture, component } = createComponent();
      component.queryValue.set('name==foo');
      component.onSendQuery();
      store.load.mockClear();
      fixture.detectChanges();

      const clearButton = fixture.debugElement.query(By.css('button[data-testid="testEntity-query-clear"]')).nativeElement as HTMLButtonElement;
      clearButton.click();

      expect(component.queryValue()).toBe('');
      expect(component.appliedQuery()).toBeUndefined();
      expect(component.queryActive()).toBe(false);
      expect(store.load).toHaveBeenCalledWith({});
    });
  });

  describe('onOpenEditor()', () => {
    it('opens the RsqlEditorDialog with the current query as initial data', () => {
      const { component } = createComponent();
      component.queryValue.set('name==foo');

      component.onOpenEditor();

      expect(matDialogStub.open).toHaveBeenCalledTimes(1);
      const [dialogType, config] = matDialogStub.open.mock.calls[0];
      expect(dialogType).toBe(RsqlEditorDialog);
      expect(config.data).toEqual({ initialQuery: 'name==foo' });
      expect(config.injector).toBeDefined();
    });

    it('updates queryValue when the dialog closes with a non-null result', () => {
      dialogRefStub.afterClosed.mockReturnValue(of('name==bar'));
      const { component } = createComponent();

      component.onOpenEditor();

      expect(component.queryValue()).toBe('name==bar');
    });

    it('leaves queryValue unchanged when the dialog closes with null', () => {
      dialogRefStub.afterClosed.mockReturnValue(of(null));
      const { component } = createComponent();
      component.queryValue.set('name==foo');

      component.onOpenEditor();

      expect(component.queryValue()).toBe('name==foo');
    });
  });

  describe('template', () => {
    it('renders the send button (disabled when empty) while no query is applied', () => {
      const { fixture, component } = createComponent();
      component.queryValue.set('');
      fixture.detectChanges();

      const sendButton = fixture.debugElement.query(By.css('button[data-testid="testEntity-query-send"]'))
        .nativeElement as HTMLButtonElement;
      expect(sendButton.disabled).toBe(true);
    });

    it('swaps the send button for a clear button once a query has been applied', () => {
      const { fixture, component } = createComponent();
      component.queryValue.set('name==foo');
      component.onSendQuery();
      fixture.detectChanges();

      const clearButton = fixture.debugElement.query(By.css('button[data-testid="testEntity-query-clear"]'));
      const sendButton = fixture.debugElement.query(By.css('button[data-testid="testEntity-query-send"]'));
      expect(clearButton).toBeTruthy();
      expect(sendButton).toBeFalsy();
    });

    it('triggers onOpenEditor when the editor button is clicked', () => {
      const spy = vi.fn().mockReturnValue({ afterClosed: () => of(null) } as unknown as MatDialogRef<unknown>);
      matDialogStub.open.mockImplementation(spy);
      const { fixture } = createComponent();

      const editorButton = fixture.debugElement.query(By.css('button[data-testid="testEntity-query-editor-open"]')).nativeElement as HTMLButtonElement;
      editorButton.click();

      expect(spy).toHaveBeenCalled();
    });
  });
});
