import { Component, input } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Observable, of, Subject, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { APP_ASSET_CONTEXT, appAssetPathOf, MODULE_ASSET_CONTEXT, moduleAssetPathOf, moduleAssetUrl, resolveAssetLink } from './module-asset-context';

describe('moduleAssetPathOf', () => {
  it('names the module path of a module: value', () => {
    expect(moduleAssetPathOf('module:docs/welcome.md')).toBe('docs/welcome.md');
    expect(moduleAssetPathOf('module:/docs/welcome.md')).toBe('docs/welcome.md');
  });

  // `assets/demo/welcome.md` is a file of the hosting application and must keep meaning that.
  it('leaves every other value alone, relative URLs included', () => {
    expect(moduleAssetPathOf('assets/demo/welcome.md')).toBeUndefined();
    expect(moduleAssetPathOf('/assets/a.md')).toBeUndefined();
    expect(moduleAssetPathOf('https://example.org/a.md')).toBeUndefined();
    expect(moduleAssetPathOf(undefined)).toBeUndefined();
    expect(moduleAssetPathOf(null)).toBeUndefined();
  });
});

describe('appAssetPathOf', () => {
  it('names the application path of an app: value', () => {
    expect(appAssetPathOf('app:cards/plan.svg')).toBe('cards/plan.svg');
    expect(appAssetPathOf('app:/cards/plan.svg')).toBe('cards/plan.svg');
  });

  it('leaves module values and every other value alone', () => {
    expect(appAssetPathOf('module:cards/plan.svg')).toBeUndefined();
    expect(appAssetPathOf('assets/plan.svg')).toBeUndefined();
    expect(appAssetPathOf(undefined)).toBeUndefined();
  });
});

describe('resolveAssetLink', () => {
  it('resolves a relative link against the directory of the file that holds it', () => {
    expect(resolveAssetLink('docs/welcome.md', 'img/flow.png')).toBe('docs/img/flow.png');
    expect(resolveAssetLink('docs/welcome.md', './img/flow.png')).toBe('docs/img/flow.png');
    expect(resolveAssetLink('docs/guide/start.md', '../img/flow.png')).toBe('docs/img/flow.png');
    expect(resolveAssetLink('welcome.md', 'flow.png')).toBe('flow.png');
  });

  it('drops a query or an anchor and decodes what the author encoded', () => {
    expect(resolveAssetLink('docs/a.md', 'b.md#section')).toBe('docs/b.md');
    expect(resolveAssetLink('docs/a.md', 'b.png?raw=1')).toBe('docs/b.png');
    expect(resolveAssetLink('docs/a.md', 'my%20pic.png')).toBe('docs/my pic.png');
  });

  it('is not a link into the module when it carries a scheme, is absolute or only an anchor', () => {
    for (const link of ['https://example.org/a.png', 'mailto:me@example.org', 'data:image/png;base64,AAAA', '//cdn.example.org/a.png', '/assets/a.png', '#top', '', null, undefined]) {
      expect(resolveAssetLink('docs/a.md', link), String(link)).toBeUndefined();
    }
  });

  it('refuses a link that climbs out above the module root', () => {
    expect(resolveAssetLink('docs/a.md', '../../secret.png')).toBeUndefined();
    expect(resolveAssetLink('a.md', '../b.png')).toBeUndefined();
  });

  it('refuses a malformed escape rather than throwing', () => {
    expect(resolveAssetLink('docs/a.md', '%E0%A4%A.png')).toBeUndefined();
  });
});

@Component({ selector: 'pp-asset-probe', template: '', standalone: true })
class AssetProbeComponent {
  readonly src = input<string | undefined>();
  readonly url = moduleAssetUrl(() => this.src());
}

describe('moduleAssetUrl', () => {
  const blob = vi.fn<(path: string) => Observable<Blob>>();
  const created: string[] = [];
  const revoked: string[] = [];

  beforeEach(() => {
    blob.mockReset();
    created.length = 0;
    revoked.length = 0;
    Object.assign(URL, {
      createObjectURL: vi.fn(() => {
        created.push(`blob:${created.length}`);
        return created[created.length - 1];
      }),
      revokeObjectURL: vi.fn((url: string) => revoked.push(url)),
    });
  });

  function probe(src: string | undefined, withContext = true) {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: withContext ? [{ provide: MODULE_ASSET_CONTEXT, useValue: { blob } }] : [] });
    const fixture = TestBed.createComponent(AssetProbeComponent);
    fixture.componentRef.setInput('src', src);
    fixture.detectChanges();
    return fixture;
  }

  it('passes an ordinary URL through untouched and asks the context for nothing', () => {
    const fixture = probe('assets/pic.png');

    expect(fixture.componentInstance.url()).toBe('assets/pic.png');
    expect(blob).not.toHaveBeenCalled();
  });

  it('turns a module asset into an object URL', () => {
    blob.mockReturnValue(of(new Blob(['x'])));

    const fixture = probe('module:img/flow.png');

    expect(blob).toHaveBeenCalledWith('img/flow.png');
    expect(fixture.componentInstance.url()).toBe('blob:0');
  });

  it('has no URL until the asset has arrived, and none when it cannot be had', () => {
    const pending = new Subject<Blob>();
    blob.mockReturnValue(pending);
    const waiting = probe('module:a.png');
    expect(waiting.componentInstance.url()).toBeUndefined();

    blob.mockReturnValue(throwError(() => new Error('404')));
    expect(probe('module:b.png').componentInstance.url()).toBeUndefined();
  });

  it('has none for a module asset outside any module', () => {
    expect(probe('module:a.png', false).componentInstance.url()).toBeUndefined();
  });

  it('revokes the object URL when the value changes and when the widget goes', () => {
    blob.mockReturnValue(of(new Blob(['x'])));
    const fixture = probe('module:a.png');
    expect(fixture.componentInstance.url()).toBe('blob:0');

    fixture.componentRef.setInput('src', 'module:b.png');
    fixture.detectChanges();
    expect(fixture.componentInstance.url()).toBe('blob:1');
    expect(revoked).toEqual(['blob:0']);

    fixture.destroy();
    expect(revoked).toEqual(['blob:0', 'blob:1']);
  });

  describe('an application asset', () => {
    const appBlob = vi.fn<(path: string) => Observable<Blob>>();

    function appProbe(src: string, contexts: { app?: boolean; module?: boolean }) {
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        providers: [
          ...(contexts.app ? [{ provide: APP_ASSET_CONTEXT, useValue: { blob: appBlob } }] : []),
          ...(contexts.module ? [{ provide: MODULE_ASSET_CONTEXT, useValue: { blob } }] : []),
        ],
      });
      const fixture = TestBed.createComponent(AssetProbeComponent);
      fixture.componentRef.setInput('src', src);
      fixture.detectChanges();
      return fixture;
    }

    it('is asked of the application context, never of the module one', () => {
      appBlob.mockReset();
      appBlob.mockReturnValue(of(new Blob(['x'])));

      const fixture = appProbe('app:cards/plan.svg', { app: true, module: true });

      expect(appBlob).toHaveBeenCalledWith('cards/plan.svg');
      expect(blob).not.toHaveBeenCalled();
      expect(fixture.componentInstance.url()).toBe('blob:0');
    });

    it('is not asked of the application context for a module asset', () => {
      appBlob.mockReset();
      blob.mockReturnValue(of(new Blob(['x'])));

      appProbe('module:a.png', { app: true, module: true });

      expect(blob).toHaveBeenCalledWith('a.png');
      expect(appBlob).not.toHaveBeenCalled();
    });

    it('has none outside an application context', () => {
      expect(appProbe('app:a.png', { module: true }).componentInstance.url()).toBeUndefined();
    });
  });
});
