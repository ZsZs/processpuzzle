import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { ColorPickerComponent, swatchColor } from './color-picker.component';

describe('swatchColor', () => {
  it('keeps a hex color and expands a short one', () => {
    expect(swatchColor('#1F3A5F')).toBe('#1f3a5f');
    expect(swatchColor(' #abc ')).toBe('#aabbcc');
  });

  it('shows black for what a color input cannot hold', () => {
    expect(swatchColor('rebeccapurple')).toBe('#000000');
    expect(swatchColor('var(--pp-color-white)')).toBe('#000000');
    expect(swatchColor('')).toBe('#000000');
  });
});

describe('ColorPickerComponent', () => {
  function setup(value = '') {
    const fixture = TestBed.createComponent(ColorPickerComponent);
    fixture.componentRef.setInput('value', value);
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;
    return {
      fixture,
      swatch: element.querySelector<HTMLInputElement>('[data-testid="color-swatch"]') as HTMLInputElement,
      text: element.querySelector<HTMLInputElement>('[data-testid="color-text"]') as HTMLInputElement,
    };
  }

  it('shows the value in both the swatch and the text field', () => {
    const { swatch, text } = setup('#336699');
    expect(swatch.value).toBe('#336699');
    expect(text.value).toBe('#336699');
  });

  it('writes a picked color into the value', () => {
    const { fixture, swatch, text } = setup('');
    swatch.value = '#ff0000';
    swatch.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(fixture.componentInstance.value()).toBe('#ff0000');
    expect(text.value).toBe('#ff0000');
  });

  it('takes typed text a swatch cannot show', () => {
    const { fixture, text, swatch } = setup('');
    text.value = 'rebeccapurple';
    text.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(fixture.componentInstance.value()).toBe('rebeccapurple');
    expect(swatch.value).toBe('#000000');
  });
});
