import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { FormControl, FormGroup } from '@angular/forms';
import { provideRouter } from '@angular/router';
import { BaseEntityDescriptorRegistry, FormControlType } from '@processpuzzle/base-entity';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { provideLogger } from 'ngx-logging-kit';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AppTranslationUploadReport } from './app-translation';
import { AppTranslationService } from './app-translation.service';
import { AppTranslationsAttrDescriptor, AppTranslationsControlComponent } from './app-translations-control.component';

describe('AppTranslationsControlComponent', () => {
  const service = { download: vi.fn(), upload: vi.fn(), bundle: vi.fn() };

  beforeEach(() => {
    TestBed.resetTestingModule();
    vi.resetAllMocks();
  });

  function render(id: string | undefined, version: number | undefined) {
    if (!service.bundle.getMockImplementation()) service.bundle.mockReturnValue(of({}));
    TestBed.configureTestingModule({
      imports: [AppTranslationsControlComponent],
      providers: [
        provideRouter([]),
        provideLogger({ level: 7 }),
        provideTranslocoTesting({ translations: {} }),
        { provide: BaseEntityDescriptorRegistry, useValue: { getStore: () => undefined, getDescriptor: () => undefined } },
        { provide: AppTranslationService, useValue: service },
      ],
    });
    const formGroup = new FormGroup({ id: new FormControl(id), version: new FormControl(version) });
    const fixture = TestBed.createComponent(AppTranslationsControlComponent);
    fixture.componentRef.setInput('config', new AppTranslationsAttrDescriptor());
    fixture.componentRef.setInput('entity', { id });
    fixture.componentRef.setInput('entityName', 'App Definition');
    fixture.componentRef.setInput('value', undefined);
    (fixture.componentInstance as unknown as { formGroup: FormGroup }).formGroup = formGroup;
    fixture.detectChanges();
    const element = (testId: string) => fixture.nativeElement.querySelector(`[data-testid="${testId}"]`) as HTMLElement | null;
    return { fixture, formGroup, element };
  }

  function choose(input: HTMLInputElement, file: File | null) {
    Object.defineProperty(input, 'files', { value: { item: () => file } });
    input.dispatchEvent(new Event('change'));
  }

  it('is a disabled custom control, so the form never saves it', () => {
    const descriptor = new AppTranslationsAttrDescriptor();

    expect(descriptor.formControlType).toBe(FormControlType.CUSTOM);
    expect(descriptor.component).toBe(AppTranslationsControlComponent);
    expect(descriptor.disabled).toBe(true);
    expect(descriptor.hideInTable).toBe(true);
  });

  it('asks the user to save first, the texts belonging to the stored application', () => {
    const { element } = render('demo', undefined);

    expect(element('app-translations-save-first')).not.toBeNull();
    expect(element('app-translations-download')).toBeNull();
  });

  it('turns up once the first save gives the application a version', () => {
    const { fixture, formGroup, element } = render('demo', undefined);

    formGroup.get('version')?.setValue(1);
    fixture.detectChanges();

    expect(element('app-translations-download')).not.toBeNull();
    expect(element('app-translations-upload')).not.toBeNull();
  });

  it('downloads the zip as a file named after the application', () => {
    const zip = new Blob(['zip']);
    service.download.mockReturnValue(of(zip));
    const createObjectURL = vi.fn().mockReturnValue('blob:zip');
    const revokeObjectURL = vi.fn();
    Object.assign(URL, { createObjectURL, revokeObjectURL });
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
    const { element } = render('demo', 3);

    element('app-translations-download')?.click();

    expect(service.download).toHaveBeenCalledWith('demo');
    expect(createObjectURL).toHaveBeenCalledWith(zip);
    expect(click).toHaveBeenCalledOnce();
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:zip');
  });

  it('uploads the chosen file and reports what was stored per language', () => {
    const report: AppTranslationUploadReport = { languages: [{ locale: 'de', storedKeys: 4 }] };
    service.upload.mockReturnValue(of(report));
    const { fixture, element } = render('demo', 3);
    const file = new File(['{}'], 'de.json');

    choose(element('app-translations-file') as HTMLInputElement, file);
    fixture.detectChanges();

    expect(service.upload).toHaveBeenCalledWith('demo', file);
    expect(element('app-translations-report')?.textContent).toContain('de');
  });

  it('shows the reason the backend refused an upload', () => {
    service.upload.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400, error: { errorText: "'bad key' is not a valid key" } })));
    const { fixture, element } = render('demo', 3);

    choose(element('app-translations-file') as HTMLInputElement, new File(['{}'], 'de.json'));
    fixture.detectChanges();

    expect(element('app-translations-error')?.textContent).toContain('not a valid key');
  });

  it('does nothing when the file dialog is dismissed', () => {
    const { element } = render('demo', 3);

    choose(element('app-translations-file') as HTMLInputElement, null);

    expect(service.upload).not.toHaveBeenCalled();
  });

  describe('the stored texts', () => {
    const storedIn = (bundles: Record<string, Record<string, string>>) =>
      service.bundle.mockImplementation((_appId: string, locale: string) => of(bundles[locale] ?? {}));

    it('reads nothing before the application is saved', () => {
      render('demo', undefined);

      expect(service.bundle).not.toHaveBeenCalled();
    });

    it('lists each offered language that holds texts, from the draft, with its count', () => {
      storedIn({ en: { 'shop.home.title': 'Home', 'shop.home.content': 'Welcome' } });
      const { element } = render('demo', 3);

      expect(service.bundle).toHaveBeenCalledWith('demo', 'en', 'draft');
      expect(service.bundle).toHaveBeenCalledWith('demo', 'de', 'draft');
      expect(element('app-translations-language-en')?.textContent).toContain('en');
      expect(element('app-translations-language-de')).toBeNull();
    });

    it('says so when no language holds texts', () => {
      const { element } = render('demo', 3);

      expect(element('app-translations-none')).not.toBeNull();
      expect(element('app-translations-languages')).toBeNull();
    });

    it('shows the keys and texts of a language, sorted by key, on a click and hides them on the next', () => {
      storedIn({ en: { 'shop.b': 'Second', 'shop.a': 'First' } });
      const { fixture, element } = render('demo', 3);

      element('app-translations-language-en')?.click();
      fixture.detectChanges();
      const cells = Array.from(element('app-translations-texts')?.querySelectorAll('tbody td') ?? []).map((cell) => cell.textContent?.trim());
      expect(cells).toEqual(['shop.a', 'First', 'shop.b', 'Second']);

      element('app-translations-language-en')?.click();
      fixture.detectChanges();
      expect(element('app-translations-texts')).toBeNull();
    });

    it('leaves out a language whose read fails, rather than failing the list', () => {
      service.bundle.mockImplementation((_appId: string, locale: string) =>
        locale === 'de' ? throwError(() => new HttpErrorResponse({ status: 500 })) : of({ 'shop.title': 'Shop' }),
      );
      const { element } = render('demo', 3);

      expect(element('app-translations-language-en')).not.toBeNull();
      expect(element('app-translations-language-de')).toBeNull();
    });

    it('reads again after an upload, so the new language turns up', () => {
      const { fixture, element } = render('demo', 3);
      expect(element('app-translations-none')).not.toBeNull();
      storedIn({ de: { 'shop.title': 'Laden' } });
      service.upload.mockReturnValue(of({ languages: [{ locale: 'de', storedKeys: 1 }] }));

      choose(element('app-translations-file') as HTMLInputElement, new File(['{}'], 'de.json'));
      fixture.detectChanges();

      expect(element('app-translations-language-de')).not.toBeNull();
    });
  });
});
