import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { FormControl, FormGroup } from '@angular/forms';
import { provideRouter } from '@angular/router';
import { BaseEntityDescriptorRegistry, FormControlType } from '@processpuzzle/base-entity';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { provideLogger } from 'ngx-logging-kit';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AppAsset } from './app-asset';
import { AppAssetService } from './app-asset.service';
import { AppAssetsAttrDescriptor, AppAssetsControlComponent } from './app-assets-control.component';

const ASSETS: AppAsset[] = [
  { path: 'docs/welcome.md', mimeType: 'text/markdown', size: 120, sha256: 'a' },
  { path: 'docs/img/flow.png', mimeType: 'image/png', size: 20480, sha256: 'b' },
];

describe('AppAssetsControlComponent', () => {
  const service = { list: vi.fn(), upload: vi.fn(), remove: vi.fn() };

  beforeEach(() => {
    TestBed.resetTestingModule();
    vi.resetAllMocks();
    service.list.mockReturnValue(of(ASSETS));
  });

  function render(id: string | undefined, version: number | undefined) {
    TestBed.configureTestingModule({
      imports: [AppAssetsControlComponent],
      providers: [
        provideRouter([]),
        provideLogger({ level: 7 }),
        provideTranslocoTesting({ translations: {} }),
        { provide: BaseEntityDescriptorRegistry, useValue: { getStore: () => undefined, getDescriptor: () => undefined } },
        { provide: AppAssetService, useValue: service },
      ],
    });
    const formGroup = new FormGroup({ id: new FormControl(id), version: new FormControl(version) });
    const fixture = TestBed.createComponent(AppAssetsControlComponent);
    fixture.componentRef.setInput('config', new AppAssetsAttrDescriptor());
    fixture.componentRef.setInput('entity', { id });
    fixture.componentRef.setInput('entityName', 'App Definition');
    fixture.componentRef.setInput('value', undefined);
    (fixture.componentInstance as unknown as { formGroup: FormGroup }).formGroup = formGroup;
    fixture.detectChanges();
    const element = (testId: string) => fixture.nativeElement.querySelector(`[data-testid="${testId}"]`) as HTMLElement | null;
    const choose = (...files: File[]) => {
      const input = element('app-assets-file') as HTMLInputElement;
      Object.defineProperty(input, 'files', { value: files, configurable: true });
      input.dispatchEvent(new Event('change'));
      fixture.detectChanges();
    };
    return { fixture, formGroup, element, choose };
  }

  it('is a disabled custom control, so the form never saves it', () => {
    const descriptor = new AppAssetsAttrDescriptor();

    expect(descriptor.formControlType).toBe(FormControlType.CUSTOM);
    expect(descriptor.component).toBe(AppAssetsControlComponent);
    expect(descriptor.disabled).toBe(true);
    expect(descriptor.hideInTable).toBe(true);
  });

  it('asks the user to save first, an asset belonging to the stored application', () => {
    const { element } = render('demo', undefined);

    expect(element('app-assets-save-first')).not.toBeNull();
    expect(element('app-assets-upload')).toBeNull();
    expect(service.list).not.toHaveBeenCalled();
  });

  it('lists the assets by the reference a widget would write', () => {
    const { element } = render('demo', 2);

    expect(service.list).toHaveBeenCalledWith('demo');
    expect(element('app-asset-docs/welcome.md')?.textContent).toContain('app:docs/welcome.md');
    expect(element('app-asset-docs/welcome.md')?.textContent).toContain('120 B');
    expect(element('app-asset-docs/img/flow.png')?.textContent).toContain('20.0 kB');
  });

  it('says there is nothing yet for an application without assets', () => {
    service.list.mockReturnValue(of([]));

    expect(render('demo', 1).element('app-assets-none')).not.toBeNull();
  });

  it('turns up once the first save gives the application a version', () => {
    const { fixture, formGroup, element } = render('demo', undefined);

    formGroup.get('version')?.setValue(1);
    fixture.detectChanges();

    expect(element('app-assets-upload')).not.toBeNull();
    expect(service.list).toHaveBeenCalledWith('demo');
  });

  it('uploads every chosen file into the typed folder, then reloads the list', () => {
    service.upload.mockReturnValue(of({}));
    const { element, choose } = render('demo', 1);
    (element('app-assets-folder') as HTMLInputElement).value = ' /docs/img/ ';
    const first = new File(['x'], 'a.png');
    const second = new File(['y'], 'b.png');

    choose(first, second);

    expect(service.upload).toHaveBeenCalledWith('demo', first, 'docs/img/a.png');
    expect(service.upload).toHaveBeenCalledWith('demo', second, 'docs/img/b.png');
    expect(service.list).toHaveBeenCalledTimes(2);
  });

  it('leaves the path to the backend when no folder is typed', () => {
    service.upload.mockReturnValue(of({}));
    const { choose } = render('demo', 1);
    const file = new File(['x'], 'a.png');

    choose(file);

    expect(service.upload).toHaveBeenCalledWith('demo', file, undefined);
  });

  it('shows why an upload was refused, and still reloads what did get stored', () => {
    service.upload.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400, error: { errorText: "'a b.png' is not a valid asset path" } })));
    const { element, choose } = render('demo', 1);

    choose(new File(['x'], 'a b.png'));

    expect(element('app-assets-error')?.textContent).toContain('not a valid asset path');
    expect(service.list).toHaveBeenCalledTimes(2);
  });

  it('removes an asset and reloads the list', () => {
    service.remove.mockReturnValue(of(undefined));
    const { element } = render('demo', 1);

    element('app-asset-remove-docs/welcome.md')?.click();

    expect(service.remove).toHaveBeenCalledWith('demo', 'docs/welcome.md');
    expect(service.list).toHaveBeenCalledTimes(2);
  });

  it('does nothing when the file dialog is dismissed', () => {
    const { choose } = render('demo', 1);

    choose();

    expect(service.upload).not.toHaveBeenCalled();
  });
});
