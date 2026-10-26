import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { FormControl, FormGroup } from '@angular/forms';
import { provideRouter } from '@angular/router';
import { BaseEntityDescriptorRegistry, FormControlType } from '@processpuzzle/base-entity';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { provideLogger } from 'ngx-logging-kit';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ModuleAsset } from './module-asset';
import { ModuleAssetService } from './module-asset.service';
import { ModuleAssetsAttrDescriptor, ModuleAssetsControlComponent } from './module-assets-control.component';

const ASSETS: ModuleAsset[] = [
  { path: 'docs/welcome.md', mimeType: 'text/markdown', size: 120, sha256: 'a' },
  { path: 'docs/img/flow.png', mimeType: 'image/png', size: 20480, sha256: 'b' },
];

describe('ModuleAssetsControlComponent', () => {
  const service = { list: vi.fn(), upload: vi.fn(), remove: vi.fn() };

  beforeEach(() => {
    TestBed.resetTestingModule();
    vi.resetAllMocks();
    service.list.mockReturnValue(of(ASSETS));
  });

  function render(id: string | undefined, version: number | undefined) {
    TestBed.configureTestingModule({
      imports: [ModuleAssetsControlComponent],
      providers: [
        provideRouter([]),
        provideLogger({ level: 7 }),
        provideTranslocoTesting({ translations: {} }),
        { provide: BaseEntityDescriptorRegistry, useValue: { getStore: () => undefined, getDescriptor: () => undefined } },
        { provide: ModuleAssetService, useValue: service },
      ],
    });
    const formGroup = new FormGroup({ id: new FormControl(id), version: new FormControl(version) });
    const fixture = TestBed.createComponent(ModuleAssetsControlComponent);
    fixture.componentRef.setInput('config', new ModuleAssetsAttrDescriptor());
    fixture.componentRef.setInput('entity', { id });
    fixture.componentRef.setInput('entityName', 'Module Definition');
    fixture.componentRef.setInput('value', undefined);
    (fixture.componentInstance as unknown as { formGroup: FormGroup }).formGroup = formGroup;
    fixture.detectChanges();
    const element = (testId: string) => fixture.nativeElement.querySelector(`[data-testid="${testId}"]`) as HTMLElement | null;
    const choose = (...files: File[]) => {
      const input = element('module-assets-file') as HTMLInputElement;
      Object.defineProperty(input, 'files', { value: files, configurable: true });
      input.dispatchEvent(new Event('change'));
      fixture.detectChanges();
    };
    return { fixture, formGroup, element, choose };
  }

  it('is a disabled custom control, so the form never saves it', () => {
    const descriptor = new ModuleAssetsAttrDescriptor();

    expect(descriptor.formControlType).toBe(FormControlType.CUSTOM);
    expect(descriptor.component).toBe(ModuleAssetsControlComponent);
    expect(descriptor.disabled).toBe(true);
    expect(descriptor.hideInTable).toBe(true);
  });

  it('asks the user to save first, an asset belonging to the stored module', () => {
    const { element } = render('claims', undefined);

    expect(element('module-assets-save-first')).not.toBeNull();
    expect(element('module-assets-upload')).toBeNull();
    expect(service.list).not.toHaveBeenCalled();
  });

  it('lists the assets by the reference a widget would write', () => {
    const { element } = render('claims', 2);

    expect(service.list).toHaveBeenCalledWith('claims');
    expect(element('module-asset-docs/welcome.md')?.textContent).toContain('module:docs/welcome.md');
    expect(element('module-asset-docs/welcome.md')?.textContent).toContain('120 B');
    expect(element('module-asset-docs/img/flow.png')?.textContent).toContain('20.0 kB');
  });

  it('says there is nothing yet for a module without assets', () => {
    service.list.mockReturnValue(of([]));

    expect(render('claims', 1).element('module-assets-none')).not.toBeNull();
  });

  it('turns up once the first save gives the module a version', () => {
    const { fixture, formGroup, element } = render('claims', undefined);

    formGroup.get('version')?.setValue(1);
    fixture.detectChanges();

    expect(element('module-assets-upload')).not.toBeNull();
    expect(service.list).toHaveBeenCalledWith('claims');
  });

  it('uploads every chosen file into the typed folder, then reloads the list', () => {
    service.upload.mockReturnValue(of({}));
    const { element, choose } = render('claims', 1);
    (element('module-assets-folder') as HTMLInputElement).value = ' /docs/img/ ';
    const first = new File(['x'], 'a.png');
    const second = new File(['y'], 'b.png');

    choose(first, second);

    expect(service.upload).toHaveBeenCalledWith('claims', first, 'docs/img/a.png');
    expect(service.upload).toHaveBeenCalledWith('claims', second, 'docs/img/b.png');
    expect(service.list).toHaveBeenCalledTimes(2);
  });

  it('leaves the path to the backend when no folder is typed', () => {
    service.upload.mockReturnValue(of({}));
    const { choose } = render('claims', 1);
    const file = new File(['x'], 'a.png');

    choose(file);

    expect(service.upload).toHaveBeenCalledWith('claims', file, undefined);
  });

  it('shows why an upload was refused, and still reloads what did get stored', () => {
    service.upload.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400, error: { errorText: "'a b.png' is not a valid asset path" } })));
    const { element, choose } = render('claims', 1);

    choose(new File(['x'], 'a b.png'));

    expect(element('module-assets-error')?.textContent).toContain('not a valid asset path');
    expect(service.list).toHaveBeenCalledTimes(2);
  });

  it('removes an asset and reloads the list', () => {
    service.remove.mockReturnValue(of(undefined));
    const { element } = render('claims', 1);

    element('module-asset-remove-docs/welcome.md')?.click();

    expect(service.remove).toHaveBeenCalledWith('claims', 'docs/welcome.md');
    expect(service.list).toHaveBeenCalledTimes(2);
  });

  it('does nothing when the file dialog is dismissed', () => {
    const { choose } = render('claims', 1);

    choose();

    expect(service.upload).not.toHaveBeenCalled();
  });
});
