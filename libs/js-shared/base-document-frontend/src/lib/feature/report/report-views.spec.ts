import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, convertToParamMap, Router, ROUTER_OUTLET_DATA } from '@angular/router';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { BehaviorSubject, of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { BlockKind, Document, DocumentInputPort, DocumentKind, DocumentStatus, DocumentTranslationSummary, PortType } from '../../domain/base-document';
import { BaseDocumentService } from '../../domain/base-document.service';
import { BaseDocumentStore } from '../../domain/base-document.store';
import { ReportSigningContext } from '../signature/report-signing-context';
import { DocumentDataContext } from './document-data-context';
import { DocumentPreviewTabComponent } from './document-preview-tab.component';
import { DocumentReportViewComponent } from './document-report-view.component';
import { EntityReportRegistry, EntityReportTemplate } from './entity-report.registry';
import { EntityReportsTabComponent } from './entity-reports-tab.component';
import { ENTITY_REPORTS_TAB, EntityReportsTabContributor, provideEntityReportsTab } from './entity-reports-tab.contributor';
import { ENTITY_TAB_CONTRIBUTORS, BaseEntityDescriptor } from '@processpuzzle/base-entity';

const SERVICE_ROOT = 'http://localhost:3000/organizations/processpuzzle-testbed';
const ORDER_PORT = Object.assign(new DocumentInputPort('order', PortType.ENTITY_REF, true), { entityType: 'Order' });
const BLOCKS = [{ id: 'intro', kind: BlockKind.TEXT, content: { type: 'doc', content: [{ type: 'paragraph', content: [{ type: 'text', text: 'Invoice' }] }] } }];

const settle = async (fixture: ComponentFixture<unknown>) => {
  for (let turn = 0; turn < 4; turn++) await new Promise<void>((resolve) => setTimeout(resolve, 0));
  fixture.detectChanges();
};

function fakeContext() {
  return {
    bind: vi.fn().mockResolvedValue(undefined),
    resolve: vi.fn(),
    listObjects: vi.fn().mockResolvedValue([{ id: 'o-1', label: 'A-1' }]),
    loading: signal(false),
    error: signal<string | undefined>(undefined),
    missingRequiredPorts: signal<string[]>([]),
  };
}

function baseProviders() {
  return [
    provideHttpClient(),
    provideHttpClientTesting(),
    provideTranslocoTesting({ translations: {} }),
    { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { BACKEND_SERVICE_ROOT: SERVICE_ROOT } } },
  ];
}

describe('DocumentReportViewComponent', () => {
  let context: ReturnType<typeof fakeContext>;
  let fixture: ComponentFixture<DocumentReportViewComponent>;

  beforeEach(() => {
    context = fakeContext();
    TestBed.configureTestingModule({ providers: baseProviders() });
    TestBed.overrideComponent(DocumentReportViewComponent, { set: { providers: [{ provide: DocumentDataContext, useValue: context }, ReportSigningContext] } });
    fixture = TestBed.createComponent(DocumentReportViewComponent);
    fixture.componentRef.setInput('documentId', 'invoice');
    fixture.componentRef.setInput('locale', 'en');
    fixture.componentRef.setInput('blocks', BLOCKS);
    fixture.componentRef.setInput('inputPorts', [ORDER_PORT, new DocumentInputPort('note', PortType.STRING)]);
    fixture.componentRef.setInput('portValues', { order: 'o-1' });
    fixture.componentRef.setInput('title', 'Invoice');
  });

  it('binds the ports to their values and renders the content', async () => {
    fixture.detectChanges();
    await settle(fixture);

    expect(context.bind).toHaveBeenCalledWith([ORDER_PORT, expect.objectContaining({ name: 'note' })], { order: 'o-1' });
    expect(fixture.nativeElement.querySelector('[data-testid="report-page"]').textContent).toContain('Invoice');
    expect(fixture.nativeElement.querySelector('[data-testid="report-parameter-order"]')).toBeNull();
    expect(context.listObjects).not.toHaveBeenCalled();
  });

  it('offers a picker per ENTITY_REF port and hands a choice back to the host', async () => {
    fixture.componentRef.setInput('showParameters', true);
    const emitted: unknown[] = [];
    fixture.componentInstance.portValuesChange.subscribe((values) => emitted.push(values));
    fixture.detectChanges();
    await settle(fixture);

    expect(context.listObjects).toHaveBeenCalledWith('Order');
    expect(fixture.nativeElement.querySelector('[data-testid="report-parameter-order"]')).not.toBeNull();
    (fixture.componentInstance as unknown as { onParameterChange(port: string, id: string): void }).onParameterChange('order', 'o-2');
    expect(emitted).toEqual([{ order: 'o-2' }]);
  });

  it('keeps an empty picker when the objects cannot be listed', async () => {
    context.listObjects.mockRejectedValue(new Error('down'));
    fixture.componentRef.setInput('showParameters', true);
    fixture.detectChanges();
    await settle(fixture);

    expect((fixture.componentInstance as unknown as { options(): object }).options()).toEqual({});
  });

  it('names the missing parameters and any error', async () => {
    context.missingRequiredPorts.set(['order']);
    context.error.set('Server error');
    fixture.detectChanges();
    await settle(fixture);

    expect(fixture.nativeElement.querySelector('[data-testid="report-missing-parameters"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('Server error');
  });

  it('prints a copy of the rendered page under the report title', async () => {
    fixture.detectChanges();
    await settle(fixture);

    vi.useFakeTimers();
    fixture.debugElement.query(By.css('[data-testid="report-print"]')).nativeElement.click();

    const frame = window.document.querySelector('iframe') as HTMLIFrameElement;
    const print = vi.fn();
    Object.defineProperty(frame.contentWindow as Window, 'print', { value: print });
    vi.runAllTimers();
    vi.useRealTimers();
    expect(print).toHaveBeenCalled();
    expect(frame.contentDocument?.title).toBe('Invoice');
    expect(frame.contentDocument?.body.textContent).toContain('Invoice');
    frame.remove();
  });
});

describe('DocumentPreviewTabComponent', () => {
  const queryParams = new BehaviorSubject(convertToParamMap({ order: 'o-1' }));
  let documentService: { getTranslation: ReturnType<typeof vi.fn> };
  let router: { navigate: ReturnType<typeof vi.fn> };
  const document = new Document('invoice', 'org', 'order-invoice', 'Order Invoice', undefined, undefined, undefined, 'en');
  document.inputPorts = [ORDER_PORT];
  document.kind = DocumentKind.REPORT_TEMPLATE;

  function create(getTranslation: ReturnType<typeof vi.fn>) {
    documentService = { getTranslation };
    router = { navigate: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        ...baseProviders(),
        { provide: BaseDocumentService, useValue: documentService },
        { provide: BaseDocumentStore, useValue: { currentEntity: signal(document), setCurrentEntity: vi.fn() } },
        { provide: ActivatedRoute, useValue: { queryParamMap: queryParams } },
        { provide: Router, useValue: router },
      ],
    });
    TestBed.overrideComponent(DocumentReportViewComponent, { set: { providers: [{ provide: DocumentDataContext, useValue: fakeContext() }, ReportSigningContext] } });
    const fixture = TestBed.createComponent(DocumentPreviewTabComponent);
    fixture.componentRef.setInput('entityId', 'invoice');
    return fixture;
  }

  it('renders the draft against the query parameters, and writes picks back to the URL', async () => {
    const fixture = create(vi.fn().mockResolvedValue({ locale: 'en', blocks: BLOCKS }));
    fixture.detectChanges();
    await settle(fixture);

    expect(documentService.getTranslation).toHaveBeenCalledWith('invoice', 'en');
    const view = fixture.debugElement.query(By.directive(DocumentReportViewComponent)).componentInstance as DocumentReportViewComponent;
    expect(view.portValues()).toEqual({ order: 'o-1' });
    expect(view.inputPorts()).toEqual([ORDER_PORT]);

    view.portValuesChange.emit({ order: 'o-2' });
    expect(router.navigate).toHaveBeenCalledWith([], expect.objectContaining({ queryParams: { order: 'o-2' }, queryParamsHandling: 'merge' }));
  });

  it('shows a failed load as an alert', async () => {
    const fixture = create(vi.fn().mockRejectedValue(new Error('forbidden')));
    fixture.detectChanges();
    await settle(fixture);

    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('forbidden');
  });

  it('falls back to the source locale when the UI language has no draft', async () => {
    document.sourceLocale = 'hu';
    const getTranslation = vi
      .fn()
      .mockRejectedValueOnce(new HttpErrorResponse({ status: 404 }))
      .mockResolvedValue({ locale: 'hu', blocks: BLOCKS });
    const fixture = create(getTranslation);
    fixture.detectChanges();
    await settle(fixture);

    expect(getTranslation).toHaveBeenLastCalledWith('invoice', 'hu');
    document.sourceLocale = 'en';
  });
});

describe('EntityReportsTabComponent', () => {
  const invoice = Object.assign(new Document('invoice', 'org', 'order-invoice', 'Order Invoice', undefined, undefined, undefined, 'hu'), { inputPorts: [ORDER_PORT] });
  const packingSlip = Object.assign(new Document('slip', 'org', 'packing-slip', 'Packing slip', undefined, undefined, undefined, 'en'), { inputPorts: [ORDER_PORT] });

  function create(templates: EntityReportTemplate[], getTranslation: ReturnType<typeof vi.fn>) {
    TestBed.configureTestingModule({
      providers: [
        ...baseProviders(),
        { provide: BaseDocumentService, useValue: { getTranslation } },
        { provide: EntityReportRegistry, useValue: { templatesFor: vi.fn().mockResolvedValue(templates) } },
        { provide: ROUTER_OUTLET_DATA, useValue: signal({ entityName: 'Order' }) },
      ],
    });
    TestBed.overrideComponent(DocumentReportViewComponent, { set: { providers: [{ provide: DocumentDataContext, useValue: fakeContext() }, ReportSigningContext] } });
    const fixture = TestBed.createComponent(EntityReportsTabComponent);
    fixture.componentRef.setInput('entityId', 'o-7');
    return fixture;
  }

  it('runs the first template for this object, from its published content', async () => {
    const getTranslation = vi
      .fn()
      .mockRejectedValueOnce(new HttpErrorResponse({ status: 404 }))
      .mockResolvedValue({ locale: 'hu', blocks: BLOCKS });
    const fixture = create(
      [
        { document: invoice, port: ORDER_PORT },
        { document: packingSlip, port: ORDER_PORT },
      ],
      getTranslation,
    );
    fixture.detectChanges();
    await settle(fixture);

    expect(getTranslation).toHaveBeenLastCalledWith('invoice', 'hu', false);
    const view = fixture.debugElement.query(By.directive(DocumentReportViewComponent)).componentInstance as DocumentReportViewComponent;
    expect(view.portValues()).toEqual({ order: 'o-7' });
    expect(fixture.nativeElement.querySelector('[data-testid="entity-reports-template"]')).not.toBeNull();

    (fixture.componentInstance as unknown as { onSelect(id: string): void }).onSelect('slip');
    (fixture.componentInstance as unknown as { onSelect(id: string): void }).onSelect('unknown');
    await settle(fixture);
    expect(getTranslation).toHaveBeenLastCalledWith('slip', 'en', false);
  });

  it('says so when the template is not published', async () => {
    const fixture = create([{ document: packingSlip, port: ORDER_PORT }], vi.fn().mockRejectedValue(new HttpErrorResponse({ status: 404 })));
    fixture.detectChanges();
    await settle(fixture);

    expect(fixture.nativeElement.querySelector('[data-testid="entity-reports-not-published"]')).not.toBeNull();
  });

  it('shows any other failure as an alert, and nothing without templates', async () => {
    const failing = create([{ document: packingSlip, port: ORDER_PORT }], vi.fn().mockRejectedValue(new Error('down')));
    failing.detectChanges();
    await settle(failing);
    expect(failing.nativeElement.querySelector('[role="alert"]').textContent).toContain('down');

    TestBed.resetTestingModule();
    const empty = create([], vi.fn());
    empty.detectChanges();
    await settle(empty);
    expect(empty.debugElement.query(By.directive(DocumentReportViewComponent))).toBeNull();
  });
});

describe('EntityReportRegistry', () => {
  const published = [{ locale: 'en', status: DocumentStatus.PUBLISHED, publishedRevision: 1 }];
  const template = (id: string, kind: DocumentKind, entityType = 'Order', translations: DocumentTranslationSummary[] = published) =>
    Object.assign(new Document(id), { kind, translations, inputPorts: [Object.assign(new DocumentInputPort('subject', PortType.ENTITY_REF), { entityType })] });

  function create(findAll: () => unknown) {
    TestBed.configureTestingModule({ providers: [{ provide: BaseDocumentService, useValue: { findAll: vi.fn(findAll) } }] });
    return TestBed.inject(EntityReportRegistry);
  }

  it('offers the report templates with an ENTITY_REF port of the entity, fetching once', async () => {
    const registry = create(() => of([template('invoice', DocumentKind.REPORT_TEMPLATE), template('page', DocumentKind.DOCUMENT), template('boat', DocumentKind.REPORT_TEMPLATE, 'Boat')]));

    expect((await registry.templatesFor('Order')).map((entry) => [entry.document.id, entry.port.name])).toEqual([['invoice', 'subject']]);
    expect((await registry.templatesFor('Boat')).map((entry) => entry.document.id)).toEqual(['boat']);
    expect(await registry.templatesFor(undefined)).toEqual([]);
    expect(TestBed.inject(BaseDocumentService).findAll).toHaveBeenCalledTimes(1);

    registry.reset();
    await registry.templatesFor('Order');
    expect(TestBed.inject(BaseDocumentService).findAll).toHaveBeenCalledTimes(2);
  });

  it('does not offer a draft: a template nobody has published belongs to its author alone', async () => {
    const draft = [{ locale: 'en', status: DocumentStatus.DRAFT, publishedRevision: null }];
    const editedSincePublished = [{ locale: 'en', status: DocumentStatus.PUBLISHED_WITH_DRAFT_CHANGES, publishedRevision: 1 }];
    const registry = create(() =>
      of([
        template('draft', DocumentKind.REPORT_TEMPLATE, 'Order', draft),
        template('untouched', DocumentKind.REPORT_TEMPLATE, 'Order', []),
        template('edited', DocumentKind.REPORT_TEMPLATE, 'Order', editedSincePublished),
        template('invoice', DocumentKind.REPORT_TEMPLATE),
      ]),
    );

    // Readers get the published revision, so a published template that has been edited since is still offered.
    expect((await registry.templatesFor('Order')).map((entry) => entry.document.id)).toEqual(['edited', 'invoice']);
  });

  it('reads a paged answer, and a failure as no templates', async () => {
    expect(await create(() => of({ content: [template('invoice', DocumentKind.REPORT_TEMPLATE)] })).templatesFor('Order')).toHaveLength(1);
    TestBed.resetTestingModule();
    expect(await create(() => of(undefined)).templatesFor('Order')).toEqual([]);
    TestBed.resetTestingModule();
    expect(
      await create(() => {
        throw new Error('down');
      }).templatesFor('Order'),
    ).toEqual([]);
  });
});

describe('EntityReportsTabContributor', () => {
  it('offers the Reports tab only to an entity some template reports on', async () => {
    TestBed.configureTestingModule({
      providers: [...provideEntityReportsTab(), { provide: EntityReportRegistry, useValue: { templatesFor: async (name: string) => (name === 'Order' ? [{}] : []) } }],
    });
    const contributor = TestBed.inject(EntityReportsTabContributor);

    expect(TestBed.inject(ENTITY_TAB_CONTRIBUTORS)).toContain(contributor);
    expect(await contributor.tabsFor({ entityName: 'Order' } as BaseEntityDescriptor)).toEqual([ENTITY_REPORTS_TAB]);
    expect(await contributor.tabsFor({ entityName: 'Boat' } as BaseEntityDescriptor)).toEqual([]);
  });
});
