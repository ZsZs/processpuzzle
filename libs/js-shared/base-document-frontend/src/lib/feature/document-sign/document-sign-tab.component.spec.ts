import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { DocumentReaderComponent } from './document-reader.component';
import { DocumentSignTabComponent } from './document-sign-tab.component';

const SERVICE_ROOT = 'http://localhost:3000/organizations/processpuzzle-testbed';
const SIGNATURES = `${SERVICE_ROOT}/documents/contract/signatures`;

const signature = (id: string, signedBy = 'Ada') => ({
  id,
  documentId: 'contract',
  locale: 'en',
  revision: 2,
  contentHash: 'h',
  subjectType: 'employee',
  subjectId: 'e-1',
  signedBy,
  signedAt: '2026-10-17T10:00:00Z',
});

describe('DocumentSignTabComponent', () => {
  let fixture: ComponentFixture<DocumentSignTabComponent>;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTranslocoTesting({ translations: { en: { 'base_document.document.sign.button': 'Sign' } } }),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { BACKEND_SERVICE_ROOT: SERVICE_ROOT } } },
      ],
    });
    fixture = TestBed.createComponent(DocumentSignTabComponent);
    controller = TestBed.inject(HttpTestingController);
  });

  async function render(subject?: { subjectType: string; subjectId: string }) {
    fixture.componentRef.setInput('entityId', 'contract');
    if (subject) {
      fixture.componentRef.setInput('subjectType', subject.subjectType);
      fixture.componentRef.setInput('subjectId', subject.subjectId);
    }
    fixture.detectChanges();
    await fixture.whenStable();
  }

  async function settle() {
    for (let turn = 0; turn < 3; turn++) await new Promise<void>((resolve) => setTimeout(resolve, 0));
    fixture.detectChanges();
  }

  function expectPublished(locale = 'en') {
    return controller.expectOne((candidate) => candidate.url === `${SERVICE_ROOT}/documents/contract/translations/${locale}`);
  }

  function expectSignatures() {
    return controller.expectOne((candidate) => candidate.url === SIGNATURES && candidate.method === 'GET');
  }

  function byTestId(id: string): HTMLElement | null {
    return fixture.nativeElement.querySelector(`[data-testid="${id}"]`);
  }

  async function publishedWith(signatures: unknown[], subject?: { subjectType: string; subjectId: string }) {
    await render(subject);
    const content = expectPublished();
    expect(content.request.params.get('draft')).toBe('false');
    content.flush({ locale: 'en', status: 'PUBLISHED', blocks: [{ id: 'intro', kind: 'TEXT', content: { type: 'doc', content: [] } }] });
    await settle();
    expectSignatures().flush(signatures);
    await settle();
  }

  /** What is signed is what readers are served, so the tab reads the published snapshot, never the draft. */
  it('shows the published content read-only', async () => {
    await publishedWith([]);

    const reader = fixture.debugElement.query(By.directive(DocumentReaderComponent))?.componentInstance as DocumentReaderComponent;
    expect(reader.blocks()).toHaveLength(1);
    expect(byTestId('document-sign-none')).not.toBeNull();
  });

  /** A signature for nobody in particular proves nothing, so without a subject there is nothing to press. */
  it('offers no sign button without a subject in the link', async () => {
    await publishedWith([signature('s-1')]);

    expect(byTestId('document-sign-button')).toBeNull();
    expect(byTestId('document-signature-s-1')).not.toBeNull();
  });

  it('narrows the list to the subject and signs only after the reader accepts', async () => {
    await render({ subjectType: 'employee', subjectId: 'e-1' });
    expectPublished().flush({ locale: 'en', status: 'PUBLISHED', blocks: [] });
    await settle();
    const list = expectSignatures();
    expect(list.request.params.get('subjectId')).toBe('e-1');
    list.flush([]);
    await settle();

    const button = byTestId('document-sign-button') as HTMLButtonElement;
    expect(button.disabled).toBe(true);

    (byTestId('document-sign-accept')?.querySelector('input') as HTMLInputElement).click();
    fixture.detectChanges();
    expect(button.disabled).toBe(false);

    button.click();
    const post = controller.expectOne((candidate) => candidate.url === SIGNATURES && candidate.method === 'POST');
    expect(post.request.body).toEqual({ subjectType: 'employee', subjectId: 'e-1', locale: 'en' });
    post.flush(signature('s-9', 'Grace'));
    await settle();

    expect(byTestId('document-signed')?.textContent).toContain('base_document.document.sign.signed');
    expect(byTestId('document-sign-button')).toBeNull();
    expect(byTestId('document-signature-s-9')).not.toBeNull();
  });

  it('shows a refused signature beside the button', async () => {
    await publishedWith([], { subjectType: 'employee', subjectId: 'e-1' });
    (byTestId('document-sign-accept')?.querySelector('input') as HTMLInputElement).click();
    fixture.detectChanges();

    (byTestId('document-sign-button') as HTMLButtonElement).click();
    controller
      .expectOne((candidate) => candidate.url === SIGNATURES && candidate.method === 'POST')
      .flush({ errorId: 'document.sign.not-published', errorText: 'Nothing to sign' }, { status: 409, statusText: 'Conflict' });
    await settle();

    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain('Nothing to sign');
    expect(byTestId('document-sign-button')).not.toBeNull();
  });

  it('says so when nothing is published', async () => {
    await render({ subjectType: 'employee', subjectId: 'e-1' });
    expectPublished().flush({ message: 'none' }, { status: 404, statusText: 'Not Found' });
    await settle();

    expect(byTestId('document-sign-not-published')).not.toBeNull();
    controller.expectNone((candidate) => candidate.url === SIGNATURES);
  });

  it('reports any other failure', async () => {
    await render();
    expectPublished().flush({ message: 'boom' }, { status: 500, statusText: 'Server Error' });
    await settle();

    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
  });
});
