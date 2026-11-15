import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { describe, expect, it, vi } from 'vitest';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { WorkingContextStatusbarComponent } from './working-context-statusbar.component';
import { WorkingContextService } from './working-context.service';

const raceContext = {
  name: 'race',
  id: 'r1',
  definition: { name: 'race', entityName: 'Race', display: { label: '{{name}}' } },
  descriptor: new BaseEntityDescriptor({ attrDescriptors: [], entityName: 'Race' }),
  entity: { id: 'r1', name: 'Spring Cup' },
  url: '/ctx/race/r1',
};

function setup(active: unknown[]) {
  const service = { active: signal(active), statusbarHostedByShell: signal(false), clear: vi.fn(), stateAttributeOf: () => undefined, stateOf: () => undefined, firing: signal(false) };
  TestBed.configureTestingModule({
    providers: [provideRouter([]), provideTranslocoTesting({ translations: {} }), { provide: WorkingContextService, useValue: service }, { provide: MatDialog, useValue: { open: vi.fn() } }],
  });
  const fixture = TestBed.createComponent(WorkingContextStatusbarComponent);
  fixture.detectChanges();
  return { fixture, service, host: fixture.nativeElement as HTMLElement };
}

describe('WorkingContextStatusbarComponent', () => {
  it('shows nothing while no context is active', () => {
    const { host } = setup([]);

    expect(host.querySelector('[data-testid="working-context-statusbar"]')).toBeNull();
  });

  it('shows the active contexts, and tells the entity status bar it hosts them for as long as it lives', () => {
    const { fixture, service, host } = setup([raceContext]);

    expect(host.querySelector('[data-testid="working-context-statusbar"]')?.textContent).toContain('Spring Cup');
    expect(service.statusbarHostedByShell()).toBe(true);

    fixture.destroy();

    expect(service.statusbarHostedByShell()).toBe(false);
  });
});
