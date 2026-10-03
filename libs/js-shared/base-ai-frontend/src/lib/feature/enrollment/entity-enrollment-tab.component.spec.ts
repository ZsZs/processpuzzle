import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ROUTER_OUTLET_DATA } from '@angular/router';
import { BaseEntityDescriptor } from '@processpuzzle/base-entity';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Enrollment } from '../../domain/enrollment/enrollment';
import { EnrollmentService } from '../../domain/enrollment/enrollment.service';
import { RecognitionProfile } from '../../domain/profile/recognition-profile';
import { ProfiledEntityRegistry } from '../../domain/profile/profiled-entity.registry';
import { ENROLLMENT_POLL_MS, EntityEnrollmentTabComponent } from './entity-enrollment-tab.component';

const PROFILE = new RecognitionProfile({ entityName: 'boat', detectorClass: 'boat', identifierAttributeKey: 'sailNumber' });

function enrollment(status: Enrollment['status'], photos: Enrollment['photos']): Enrollment {
  return { entityName: 'boat', objectId: 'o-1', status, identifierText: 'CAN 603', photos };
}

describe('EntityEnrollmentTabComponent', () => {
  const find = vi.fn();
  const profileFor = vi.fn();
  let fixture: ComponentFixture<EntityEnrollmentTabComponent>;

  async function render(): Promise<HTMLElement> {
    TestBed.configureTestingModule({
      imports: [EntityEnrollmentTabComponent],
      providers: [
        provideTranslocoTesting({ translations: { en: {} } }),
        { provide: ProfiledEntityRegistry, useValue: { profileFor } },
        { provide: EnrollmentService, useValue: { find, addPhotos: vi.fn(), deletePhoto: vi.fn(), deleteAll: vi.fn() } },
        { provide: ROUTER_OUTLET_DATA, useValue: signal(new BaseEntityDescriptor({ entityName: 'Boat', attrDescriptors: [] })) },
      ],
    });
    fixture = TestBed.createComponent(EntityEnrollmentTabComponent);
    fixture.componentRef.setInput('entityId', 'o-1');
    fixture.detectChanges();
    await vi.waitFor(() => {
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).not.toContain('loading');
    });
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(() => {
    find.mockReset();
    profileFor.mockReset();
  });

  afterEach(() => vi.useRealTimers());

  it('says so when the entity type has no recognition profile', async () => {
    profileFor.mockResolvedValue(undefined);
    const element = await render();
    expect(element.textContent).toContain('noProfile');
    expect(find).not.toHaveBeenCalled();
  });

  it('shows each photo with what was read on it, and flags a mismatch', async () => {
    profileFor.mockResolvedValue(PROFILE);
    find.mockReturnValue(
      of(
        enrollment('READY', [
          { photoId: 'p-1', status: 'ENROLLED', observedIdentifierText: 'CAN603', identifierMismatch: false, addedAt: '' },
          { photoId: 'p-2', status: 'ENROLLED', observedIdentifierText: 'USA 7', identifierMismatch: true, addedAt: '' },
        ]),
      ),
    );
    const element = await render();

    expect(element.querySelector('[data-testid="registered-identifier"]')?.textContent).toContain('CAN 603');
    expect(element.querySelectorAll('[data-testid="enrollment-photo"]')).toHaveLength(2);
    expect(Array.from(element.querySelectorAll('[data-testid="observed-identifier"]')).map((e) => e.textContent)).toEqual(['CAN603', 'USA 7']);
    expect(element.querySelectorAll('[role="alert"]')).toHaveLength(1);
  });

  it('re-reads the gallery while photos are being processed', async () => {
    profileFor.mockResolvedValue(PROFILE);
    find.mockReturnValueOnce(of(enrollment('PROCESSING', [{ photoId: 'p-1', status: 'PENDING', identifierMismatch: false, addedAt: '' }])));
    find.mockReturnValue(of(enrollment('READY', [{ photoId: 'p-1', status: 'ENROLLED', identifierMismatch: false, addedAt: '' }])));
    await render();

    await vi.waitFor(
      () => {
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="enrollment-status"]')?.getAttribute('data-status')).toBe('READY');
      },
      { timeout: ENROLLMENT_POLL_MS + 2000 },
    );
    expect(find).toHaveBeenCalledTimes(2);
  });
});
