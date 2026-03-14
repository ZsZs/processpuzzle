import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MarkdownPageComponent } from './markdown-page.component';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MarkdownComponent } from 'ngx-markdown';
import { Component, input } from '@angular/core';

@Component({
  selector: 'markdown',
  template: '',
  standalone: true,
})
class MockMarkdownComponent {
  src = input<string>();
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
      const testContent = '# Test Markdown';

      expect(() => component.onLoad(testContent)).not.toThrow();
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

      expect(onLoadSpy).toHaveBeenCalledWith('test content');
    });

    it('should bind error event to onError handler', () => {
      const onErrorSpy = vi.spyOn(component, 'onError');
      const testError = 'test error';

      const markdownComponent = fixture.debugElement.children[0];
      markdownComponent.triggerEventHandler('error', testError);

      expect(onErrorSpy).toHaveBeenCalledWith(testError);
    });
  });
});
