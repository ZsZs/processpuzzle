import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { ColorSwatchComponent, swatchColor } from './color-swatch.component';

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

describe('ColorSwatchComponent', () => {
  function setup(value = '') {
    const fixture = TestBed.createComponent(ColorSwatchComponent);
    fixture.componentRef.setInput('value', value);
    fixture.detectChanges();
    const swatch = (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>('[data-testid="color-swatch"]') as HTMLInputElement;
    return { fixture, swatch };
  }

  it('shows the value as the swatch color', () => {
    const { swatch } = setup('#336699');
    expect(swatch.value).toBe('#336699');
  });

  it('writes a picked color into the value', () => {
    const { fixture, swatch } = setup('');
    swatch.value = '#ff0000';
    swatch.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(fixture.componentInstance.value()).toBe('#ff0000');
  });

  it('shows black while the value is text a swatch cannot show', () => {
    const { swatch } = setup('rebeccapurple');
    expect(swatch.value).toBe('#000000');
  });
});
