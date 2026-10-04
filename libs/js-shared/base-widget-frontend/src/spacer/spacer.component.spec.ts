import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { SpacerComponent } from './spacer.component';

describe('SpacerComponent', () => {
  it('renders nothing a screen reader would announce', () => {
    const fixture = TestBed.createComponent(SpacerComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.childElementCount).toBe(0);
    expect(fixture.nativeElement.getAttribute('aria-hidden')).toBe('true');
  });
});
