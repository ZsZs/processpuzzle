import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MarkdownPageComponent } from './markdown-page.component';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MarkdownComponent } from 'ngx-markdown';
import { Component, input, output } from '@angular/core';
import { of, throwError } from 'rxjs';
import { MODULE_ASSET_CONTEXT } from '../module-asset/module-asset-context';

@Component({
  selector: 'markdown',
  template: '',
  standalone: true,
})
class MockMarkdownComponent {
  src = input<string>();
  data = input<string>();
  ready = output<void>();
}

describe('MarkdownPageComponent', () => {
  let component: MarkdownPageComponent;
  let fixture: ComponentFixture<MarkdownPageComponent>;
  let snackBarSpy: { open: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    snackBarSpy = {
      open: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [MarkdownPageComponent],
      providers: [{ provide: MatSnackBar, useValue: snackBarSpy }],
    })
      .overrideComponent(MarkdownPageComponent, {
        remove: { imports: [MarkdownComponent] },
        add: { imports: [MockMarkdownComponent] },
      })
      .compileComponents();

    fixture = TestBed.createComponent(MarkdownPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('markdownSrc input', () => {
    it('should accept markdownSrc input', () => {
      const testSrc = '/assets/test.md';
      fixture.componentRef.setInput('markdownSrc', testSrc);
      fixture.detectChanges();

      expect(component.src()).toBe(testSrc);
    });

    it('should render markdown component with src attribute', () => {
      const testSrc = '/assets/documentation.md';
      fixture.componentRef.setInput('markdownSrc', testSrc);
      fixture.detectChanges();

      const markdownElement = fixture.nativeElement.querySelector('markdown');
      expect(markdownElement).toBeTruthy();
    });
  });

  describe('onLoad', () => {
    it('should handle load event', () => {
      expect(() => component.onLoad()).not.toThrow();
    });
  });

  describe('onError', () => {
    it('should display snackbar with error message when error is a string', () => {
      const errorMessage = 'Failed to load markdown file';

      component.onError(errorMessage);

      expect(snackBarSpy.open).toHaveBeenCalledWith(`Error loading markdown: ${errorMessage}`, 'Close', {
        duration: 5000,
        horizontalPosition: 'center',
        verticalPosition: 'bottom',
      });
    });

    it('should display snackbar with error message when error is an Error object', () => {
      const error = new Error('Network error');

      component.onError(error);

      expect(snackBarSpy.open).toHaveBeenCalledWith('Error loading markdown: Network error', 'Close', {
        duration: 5000,
        horizontalPosition: 'center',
        verticalPosition: 'bottom',
      });
    });

    it('should configure snackbar with correct duration', () => {
      component.onError('test error');

      const callArgs = snackBarSpy.open.mock.calls[0];
      expect(callArgs[2].duration).toBe(5000);
    });

    it('should configure snackbar with correct position', () => {
      component.onError('test error');

      const callArgs = snackBarSpy.open.mock.calls[0];
      expect(callArgs[2].horizontalPosition).toBe('center');
      expect(callArgs[2].verticalPosition).toBe('bottom');
    });
  });

  describe('template', () => {
    it('should bind load event to onLoad handler', () => {
      const onLoadSpy = vi.spyOn(component, 'onLoad');

      const markdownComponent = fixture.debugElement.children[0];
      markdownComponent.triggerEventHandler('load', 'test content');

      expect(onLoadSpy).toHaveBeenCalled();
    });

    it('should bind error event to onError handler', () => {
      const onErrorSpy = vi.spyOn(component, 'onError');
      const testError = 'test error';

      const markdownComponent = fixture.debugElement.children[0];
      markdownComponent.triggerEventHandler('error', testError);

      expect(onErrorSpy).toHaveBeenCalledWith(testError);
    });
  });

  describe('a markdown file of the module', () => {
    const blob = vi.fn();

    async function render(src: string, text = '# Hello', context: object | null = { blob }) {
      TestBed.resetTestingModule();
      blob.mockReset();
      blob.mockImplementation((path: string) => of(new Blob([path === src.slice(7) ? text : 'image-bytes'])));
      await TestBed.configureTestingModule({
        imports: [MarkdownPageComponent],
        providers: [{ provide: MatSnackBar, useValue: snackBarSpy }, ...(context ? [{ provide: MODULE_ASSET_CONTEXT, useValue: context }] : [])],
      })
        .overrideComponent(MarkdownPageComponent, { remove: { imports: [MarkdownComponent] }, add: { imports: [MockMarkdownComponent] } })
        .compileComponents();
      const module = TestBed.createComponent(MarkdownPageComponent);
      module.componentRef.setInput('markdownSrc', src);
      module.detectChanges();
      await module.whenStable();
      module.detectChanges();
      return module;
    }

    it('is fetched through the asset context, as text, instead of by URL', async () => {
      const module = await render('module:docs/welcome.md');

      expect(blob).toHaveBeenCalledWith('docs/welcome.md');
      expect(module.nativeElement.querySelector('markdown')).toBeTruthy();
    });

    it('shows an error when the page is not part of a module', async () => {
      await render('module:docs/welcome.md', 'x', null);

      expect(snackBarSpy.open).toHaveBeenCalledWith(expect.stringContaining('not part of a module'), 'Close', expect.anything());
    });

    it('shows an error when the file cannot be had', async () => {
      TestBed.resetTestingModule();
      await TestBed.configureTestingModule({
        imports: [MarkdownPageComponent],
        providers: [
          { provide: MatSnackBar, useValue: snackBarSpy },
          { provide: MODULE_ASSET_CONTEXT, useValue: { blob: () => throwError(() => new Error('404')) } },
        ],
      })
        .overrideComponent(MarkdownPageComponent, { remove: { imports: [MarkdownComponent] }, add: { imports: [MockMarkdownComponent] } })
        .compileComponents();
      const module = TestBed.createComponent(MarkdownPageComponent);
      module.componentRef.setInput('markdownSrc', 'module:gone.md');

      module.detectChanges();

      expect(snackBarSpy.open).toHaveBeenCalledWith('Error loading markdown: 404', 'Close', expect.anything());
    });

    it('shows the images it links to from the module, resolved against its own directory', async () => {
      Object.assign(URL, { createObjectURL: vi.fn(() => 'blob:img'), revokeObjectURL: vi.fn() });
      const module = await render('module:docs/welcome.md');
      const host = module.nativeElement as HTMLElement;
      host.innerHTML = '<img src="img/flow.png"><img src="https://example.org/x.png">';

      (module.componentInstance as unknown as { onAssetRendered(): void }).onAssetRendered();

      expect(blob).toHaveBeenCalledWith('docs/img/flow.png');
      expect(blob).not.toHaveBeenCalledWith(expect.stringContaining('example'));
      const [local, external] = Array.from(host.querySelectorAll('img'));
      expect(local.getAttribute('src')).toBe('blob:img');
      expect(external.getAttribute('src')).toBe('https://example.org/x.png');
    });

    it('opens a link to another file of the module from an object URL, and leaves other links to the browser', async () => {
      Object.assign(URL, { createObjectURL: vi.fn(() => 'blob:doc'), revokeObjectURL: vi.fn() });
      const open = vi.spyOn(window, 'open').mockReturnValue(null);
      const module = await render('module:docs/welcome.md');
      const host = module.nativeElement as HTMLElement;
      host.innerHTML = '<a id="in" href="guide.pdf">guide</a><a id="out" href="https://example.org">out</a>';
      const click = (id: string) => {
        const event = new MouseEvent('click', { bubbles: true, cancelable: true });
        host.querySelector(id)?.dispatchEvent(event);
        (module.componentInstance as unknown as { onAssetClick(e: Event): void }).onAssetClick(Object.defineProperty(event, 'target', { value: host.querySelector(id) }));
        return event;
      };

      expect(click('#in').defaultPrevented).toBe(true);
      expect(open).toHaveBeenCalledWith('blob:doc', '_blank', 'noopener');
      expect(click('#out').defaultPrevented).toBe(false);
      expect(open).toHaveBeenCalledOnce();
    });
  });
});
