import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { FormControl, FormGroup } from '@angular/forms';
import { provideRouter } from '@angular/router';
import { BaseEntityDescriptorRegistry, FormControlType } from '@processpuzzle/base-entity';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { provideLogger } from 'ngx-logging-kit';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ModuleTranslationCoverage, ModuleTranslationUploadReport } from './module-translation';
import { ModuleTranslationService } from './module-translation.service';
import { ModuleTranslationsAttrDescriptor, ModuleTranslationsControlComponent } from './module-translations-control.component';

const COVERAGE: ModuleTranslationCoverage[] = [
  { locale: 'en', defaultLocale: true, keyCount: 3, translatedCount: 3, missingKeys: [] },
  { locale: 'de', defaultLocale: false, keyCount: 3, translatedCount: 1, missingKeys: ['route.list', 'route.claim/:id'] },
];

describe('ModuleTranslationsControlComponent', () => {
  const service = { coverage: vi.fn(), download: vi.fn(), upload: vi.fn() };

  beforeEach(() => {
    TestBed.resetTestingModule();
    vi.resetAllMocks();
    service.coverage.mockReturnValue(of(COVERAGE));
  });

  function render(id: string | undefined, version: number | undefined) {
    TestBed.configureTestingModule({
      imports: [ModuleTranslationsControlComponent],
      providers: [
        provideRouter([]),
        provideLogger({ level: 7 }),
        provideTranslocoTesting({ translations: {} }),
        { provide: BaseEntityDescriptorRegistry, useValue: { getStore: () => undefined, getDescriptor: () => undefined } },
        { provide: ModuleTranslationService, useValue: service },
      ],
    });
    const formGroup = new FormGroup({ id: new FormControl(id), version: new FormControl(version) });
    const fixture = TestBed.createComponent(ModuleTranslationsControlComponent);
    fixture.componentRef.setInput('config', new ModuleTranslationsAttrDescriptor());
    fixture.componentRef.setInput('entity', { id });
    fixture.componentRef.setInput('entityName', 'Module Definition');
    fixture.componentRef.setInput('value', undefined);
    (fixture.componentInstance as unknown as { formGroup: FormGroup }).formGroup = formGroup;
    fixture.detectChanges();
    const element = (testId: string) => fixture.nativeElement.querySelector(`[data-testid="${testId}"]`) as HTMLElement | null;
    return { fixture, formGroup, element };
  }

  it('is a disabled custom control, so the form never saves it', () => {
    const descriptor = new ModuleTranslationsAttrDescriptor();

    expect(descriptor.formControlType).toBe(FormControlType.CUSTOM);
    expect(descriptor.component).toBe(ModuleTranslationsControlComponent);
    expect(descriptor.disabled).toBe(true);
    expect(descriptor.hideInTable).toBe(true);
  });

  it('asks the user to save first, there being no stored module to prepare the files from', () => {
    const { element } = render('claims', undefined);

    expect(element('module-translations-save-first')).not.toBeNull();
    expect(element('module-translations-download')).toBeNull();
    expect(service.coverage).not.toHaveBeenCalled();
  });

  it('shows how far each language is covered once the module is saved', () => {
    const { element } = render('claims', 3);

    expect(service.coverage).toHaveBeenCalledWith('claims');
    expect(element('module-translations-en')?.textContent).toContain('3 / 3');
    expect(element('module-translations-de')?.textContent).toContain('1 / 3');
    expect(element('module-translations-de')?.querySelector('td[title]')?.getAttribute('title')).toBe('route.list, route.claim/:id');
  });

  it('turns up once the first save gives the module a version', () => {
    const { fixture, formGroup, element } = render('claims', undefined);

    formGroup.get('version')?.setValue(1);
    fixture.detectChanges();

    expect(element('module-translations-download')).not.toBeNull();
    expect(service.coverage).toHaveBeenCalledWith('claims');
  });

  it('downloads the zip as a file named after the module', () => {
    const zip = new Blob(['zip']);
    service.download.mockReturnValue(of(zip));
    const createObjectURL = vi.fn().mockReturnValue('blob:zip');
    const revokeObjectURL = vi.fn();
    Object.assign(URL, { createObjectURL, revokeObjectURL });
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
    const { element } = render('claims', 3);

    element('module-translations-download')?.click();

    expect(service.download).toHaveBeenCalledWith('claims');
    expect(createObjectURL).toHaveBeenCalledWith(zip);
    expect(click).toHaveBeenCalledOnce();
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:zip');
  });

  it('uploads the chosen file, reports what was stored and refreshes the coverage', () => {
    const report: ModuleTranslationUploadReport = {
      languages: [{ locale: 'de', storedKeys: 2, unknownKeys: ['route.gone'], missingKeys: ['route.list'], untranslatedKeys: [] }],
    };
    service.upload.mockReturnValue(of(report));
    const { fixture, element } = render('claims', 3);
    const file = new File(['{}'], 'de.json');
    const input = element('module-translations-file') as HTMLInputElement;
    Object.defineProperty(input, 'files', { value: { item: () => file } });

    input.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    expect(service.upload).toHaveBeenCalledWith('claims', file);
    expect(service.coverage).toHaveBeenCalledTimes(2);
    expect(element('module-translations-report')?.textContent).toContain('de');
  });

  it('shows the reason the backend refused an upload', () => {
    service.upload.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400, error: { errorId: 'module.translation.rejected', errorText: "The language 'it' is not supported." } })));
    const { fixture, element } = render('claims', 3);
    const input = element('module-translations-file') as HTMLInputElement;
    Object.defineProperty(input, 'files', { value: { item: () => new File(['{}'], 'it.json') } });

    input.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    expect(element('module-translations-error')?.textContent).toContain("'it' is not supported");
  });

  it('does nothing when the file dialog is dismissed', () => {
    const { element } = render('claims', 3);
    const input = element('module-translations-file') as HTMLInputElement;
    Object.defineProperty(input, 'files', { value: { item: () => null } });

    input.dispatchEvent(new Event('change'));

    expect(service.upload).not.toHaveBeenCalled();
  });
});
