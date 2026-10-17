import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, FormGroup } from '@angular/forms';
import { provideRouter } from '@angular/router';
import { BaseEntityDescriptorRegistry, FormControlType } from '@processpuzzle/base-entity';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { provideLogger } from 'ngx-logging-kit';
import { describe, expect, it } from 'vitest';
import { EVENT_DEFINITION_ENTITY_NAME } from '../workflow-entity-names';
import { EventParametersAttrDescriptor, EventParametersControlComponent } from './event-parameters-control.component';

const CATALOG = [
  {
    id: 'InvoiceIssued',
    kind: 'MESSAGE',
    parameters: [
      { name: 'order', type: 'ENTITY_REF', entityType: 'order', required: true },
      { name: 'invoiceNumber', type: 'STRING', required: true },
      { name: 'note', type: 'STRING' },
    ],
  },
  { id: 'OrderCreatedEvent', kind: 'SYSTEM', subjectType: 'order', parameters: [{ name: 'orderNumber', type: 'STRING', required: true }] },
  { id: 'Bare', kind: 'SIGNAL', parameters: [] },
];

describe('EventParametersControlComponent', () => {
  function render(eventId: string | undefined, mapping: Record<string, string> = {}, catalog = signal(CATALOG)) {
    const registry = { getStore: (name: string) => (name === EVENT_DEFINITION_ENTITY_NAME ? { entities: catalog } : undefined), getDescriptor: () => undefined };
    TestBed.configureTestingModule({
      imports: [EventParametersControlComponent],
      providers: [provideRouter([]), provideLogger({ level: 7 }), provideTranslocoTesting({ translations: {} }), { provide: BaseEntityDescriptorRegistry, useValue: registry }],
    });
    const formGroup = new FormGroup({ eventDefinitionId: new FormControl(eventId), payloadMapping: new FormControl<Record<string, string>>(mapping) });
    const fixture = TestBed.createComponent(EventParametersControlComponent);
    fixture.componentRef.setInput('config', new EventParametersAttrDescriptor('eventDefinitionId'));
    fixture.componentRef.setInput('entity', { id: 'issued' });
    fixture.componentRef.setInput('entityName', 'Workflow Event Use');
    fixture.componentRef.setInput('value', undefined);
    (fixture.componentInstance as unknown as { formGroup: FormGroup }).formGroup = formGroup;
    fixture.detectChanges();
    const shown = () => fixture.componentInstance.parameters().map((parameter) => [parameter.name, parameter.type, parameter.required, parameter.mapped]);
    const element = (testId: string) => fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
    return { fixture, formGroup, shown, element };
  }

  it('is a disabled custom control, so the form never saves it', () => {
    const descriptor = new EventParametersAttrDescriptor('eventType');

    expect(descriptor.formControlType).toBe(FormControlType.CUSTOM);
    expect(descriptor.component).toBe(EventParametersControlComponent);
    expect(descriptor.disabled).toBe(true);
    expect(descriptor.hideInTable).toBe(true);
  });

  it("lists the event's parameters, marking the ones the payload mapping names", () => {
    const { shown, element } = render('InvoiceIssued', { order: '$.new_order' });

    expect(shown()).toEqual([
      ['order', 'ENTITY_REF order', true, true],
      ['invoiceNumber', 'STRING', true, false],
      ['note', 'STRING', false, false],
    ]);
    expect(element('event-parameter-invoiceNumber')).not.toBeNull();
  });

  it('puts the implicit subject of a SYSTEM event first', () => {
    const { shown } = render('OrderCreatedEvent');

    expect(shown()).toEqual([
      ['subject', 'ENTITY_REF order', true, false],
      ['orderNumber', 'STRING', true, false],
    ]);
  });

  it('follows the event and the mapping as they are edited', () => {
    const { fixture, formGroup, shown } = render('Bare');
    expect(shown()).toEqual([]);

    formGroup.get('eventDefinitionId')?.setValue('InvoiceIssued');
    formGroup.get('payloadMapping')?.setValue({ invoiceNumber: '$.number' });
    fixture.detectChanges();

    expect(shown().filter(([, , , mapped]) => mapped)).toEqual([['invoiceNumber', 'STRING', true, true]]);
  });

  it('says so for an event without parameters', () => {
    const { element } = render('Bare');

    expect(element('event-parameters-none')).not.toBeNull();
  });

  // Not "no parameters": with no event, an unknown one or no catalog, the control cannot tell.
  it.each([undefined, 'Unknown'])('shows nothing for event %s', (eventId) => {
    const { element } = render(eventId);

    expect(element('event-parameters-none')).toBeNull();
    expect(element('event-parameters-list')).toBeNull();
  });

  it('fills in once the catalog has loaded', () => {
    const catalog = signal<typeof CATALOG>([]);
    const { fixture, shown } = render('InvoiceIssued', {}, catalog);
    expect(shown()).toEqual([]);

    catalog.set(CATALOG);
    fixture.detectChanges();

    expect(shown().map(([name]) => name)).toEqual(['order', 'invoiceNumber', 'note']);
  });
});
