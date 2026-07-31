import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TranslocoService } from '@jsverse/transloco';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { beforeEach, describe, expect, it } from 'vitest';
import { EntityLabelPipe } from './entity-label.pipe';

@Component({
  selector: 'host-component',
  standalone: true,
  imports: [EntityLabelPipe],
  template: `<span>{{ key() | ppLabel: fallback() }}</span>`,
})
class HostComponent {
  key = signal<string | undefined>(undefined);
  fallback = signal('Fallback');
}

async function renderHost() {
  await TestBed.configureTestingModule({
    imports: [HostComponent],
    providers: [provideTranslocoTesting({ translations: {} })],
  }).compileComponents();

  const transloco = TestBed.inject(TranslocoService);
  transloco.setTranslation({ 'orders.Order.orderNumber': 'Order #' }, 'en');
  const fixture = TestBed.createComponent(HostComponent);
  const text = () => fixture.nativeElement.querySelector('span').textContent.trim();
  return { fixture, component: fixture.componentInstance, text };
}

describe('EntityLabelPipe', () => {
  beforeEach(() => TestBed.resetTestingModule());

  it('returns the fallback when no key is provided', async () => {
    const { fixture, text } = await renderHost();
    fixture.detectChanges();

    expect(text()).toBe('Fallback');
  });

  it('returns the translation when the key resolves', async () => {
    const { fixture, component, text } = await renderHost();
    component.key.set('orders.Order.orderNumber');
    fixture.detectChanges();

    expect(text()).toBe('Order #');
  });

  it('returns the fallback when the key is missing from the translations', async () => {
    const { fixture, component, text } = await renderHost();
    component.key.set('orders.Order.unknown');
    fixture.detectChanges();

    expect(text()).toBe('Fallback');
  });
});
