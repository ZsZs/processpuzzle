import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TranslocoService } from '@jsverse/transloco';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { describe, expect, it } from 'vitest';
import { AddressPipe } from './address.pipe';

@Component({
  selector: 'host-component',
  standalone: true,
  imports: [AddressPipe],
  template: `<span>{{ value() | ppAddress }}</span>`,
})
class HostComponent {
  value = signal<unknown>({ country: 'DE', street: 'Hauptstr. 1', postalCode: '10115', city: 'Berlin' });
}

describe('AddressPipe', () => {
  it('writes the address on one line, the country named in the active language', async () => {
    await TestBed.configureTestingModule({ imports: [HostComponent], providers: [provideTranslocoTesting({ translations: {} })] }).compileComponents();
    const fixture = TestBed.createComponent(HostComponent);
    const text = () => (fixture.nativeElement as HTMLElement).querySelector('span')?.textContent?.trim();

    fixture.detectChanges();
    expect(text()).toBe('Hauptstr. 1, 10115 Berlin, Germany');

    TestBed.inject(TranslocoService).setActiveLang('de');
    fixture.detectChanges();
    expect(text()).toBe('Hauptstr. 1, 10115 Berlin, Deutschland');

    fixture.componentInstance.value.set('not an address');
    fixture.detectChanges();
    expect(text()).toBe('');
  });
});
