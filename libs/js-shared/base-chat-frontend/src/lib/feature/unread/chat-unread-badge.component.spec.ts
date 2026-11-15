import { computed, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ChatStore } from '../../domain/chat.store';
import { chatTranslations } from '../../test-translations';
import { ChatUnreadBadgeComponent } from './chat-unread-badge.component';

function render(unread: number, inputs: Record<string, unknown> = {}) {
  const total = signal(unread);
  const store = { totalUnread: computed(() => total()), connect: vi.fn(), release: vi.fn() };
  TestBed.configureTestingModule({
    providers: [provideTranslocoTesting({ translations: chatTranslations() }), { provide: ChatStore, useValue: store }],
  });
  const fixture: ComponentFixture<ChatUnreadBadgeComponent> = TestBed.createComponent(ChatUnreadBadgeComponent);
  for (const [name, value] of Object.entries(inputs)) fixture.componentRef.setInput(name, value);
  fixture.detectChanges();
  return { fixture, total, store, root: fixture.nativeElement as HTMLElement };
}

const badge = (root: HTMLElement) => root.querySelector<HTMLElement>('[data-testid="chat-unread-badge"]');

describe('ChatUnreadBadgeComponent', () => {
  beforeEach(() => TestBed.resetTestingModule());

  it('shows the number of unread messages', () => {
    const { root } = render(7);

    expect(badge(root)?.textContent).toBe('7');
  });

  it('shows nothing when there is nothing unread', () => {
    const { root } = render(0);

    expect(badge(root)).toBeNull();
  });

  it('says in words, for a screen reader, how many are unread', () => {
    const { root } = render(3);

    expect(badge(root)?.getAttribute('aria-label')).toBe('3 unread chat messages');
    expect(badge(root)?.getAttribute('role')).toBe('status');
  });

  it('caps the number at the largest it is told to show', () => {
    expect(badge(render(150).root)?.textContent).toBe('99+');
  });

  it('takes the largest number to show from its input', () => {
    expect(badge(render(12, { max: 9 }).root)?.textContent).toBe('9+');
  });

  it('shows the number itself when it is the largest allowed', () => {
    expect(badge(render(9, { max: 9 }).root)?.textContent).toBe('9');
  });

  it('follows the store as messages arrive and are read', () => {
    const { fixture, total, root } = render(1);

    total.set(4);
    fixture.detectChanges();
    expect(badge(root)?.textContent).toBe('4');

    total.set(0);
    fixture.detectChanges();
    expect(badge(root)).toBeNull();
  });

  it('keeps the live stream open while it is there and lets go when it is gone', () => {
    const { fixture, store } = render(1);
    expect(store.connect).toHaveBeenCalledTimes(1);

    fixture.destroy();

    expect(store.release).toHaveBeenCalledTimes(1);
  });
});
