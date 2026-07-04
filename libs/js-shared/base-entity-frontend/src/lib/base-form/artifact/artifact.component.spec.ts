import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { Subject, of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { mock, type MockProxy } from 'vitest-mock-extended';
import { FormControlType } from '../../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../../base-entity/base-entity-attr.descriptor';
import { ObjectStoreService } from '../../object-store/object-store.service';
import { TestEntity } from '../../test-entity';
import { setupFormControlTest } from '../../../test-setup';
import { ArtifactComponent } from './artifact.component';
import type { ArtifactAttr } from './artifact-attr';

function makeArtifact(overrides: Partial<ArtifactAttr> = {}): ArtifactAttr {
  return { bucket: 'bucket-1', objectId: 'oid-1', name: 'report.pdf', mimeType: 'application/pdf', ...overrides };
}

function makeConfig(): BaseEntityAttrDescriptor {
  return new BaseEntityAttrDescriptor('artifact', FormControlType.ARTIFACT, 'Attachment');
}

async function setupArtifactComponent(
  config: BaseEntityAttrDescriptor,
  entityArtifact: ArtifactAttr | null,
  dialog: MockProxy<MatDialog>,
  objectStore: MockProxy<ObjectStoreService>,
) {
  const entity = new TestEntity('1', 'name');
  Reflect.set(entity, 'artifact', entityArtifact);

  const harness = await setupFormControlTest(ArtifactComponent, config, entity, [
    { provide: MatDialog, useValue: dialog },
    { provide: ObjectStoreService, useValue: objectStore },
  ]);

  const component = harness.component as ArtifactComponent<TestEntity>;
  return { ...harness, component, entity };
}

describe('ArtifactComponent', () => {
  let dialog: MockProxy<MatDialog>;
  let objectStore: MockProxy<ObjectStoreService>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [NoopAnimationsModule] }).compileComponents();
    dialog = mock<MatDialog>();
    objectStore = mock<ObjectStoreService>();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('artifact()', () => {
    it('returns the current form control value', async () => {
      const artifact = makeArtifact();
      const { component } = await setupArtifactComponent(makeConfig(), artifact, dialog, objectStore);

      expect(component.artifact()).toEqual(artifact);
    });

    it('returns null when the form control is empty', async () => {
      const { component } = await setupArtifactComponent(makeConfig(), null, dialog, objectStore);

      expect(component.artifact()).toBeNull();
    });
  });

  describe('openArtifact()', () => {
    it('opens the resolved uri in a new window', async () => {
      const artifact = makeArtifact();
      objectStore.getObjectUriByID.mockReturnValue(of({ uri: 'https://cdn.example/report.pdf' }));
      const windowOpen = vi.spyOn(window, 'open').mockReturnValue(null);
      const preventDefault = vi.fn();
      const { component } = await setupArtifactComponent(makeConfig(), artifact, dialog, objectStore);

      component.openArtifact({ preventDefault } as unknown as Event, artifact);

      expect(preventDefault).toHaveBeenCalled();
      expect(objectStore.getObjectUriByID).toHaveBeenCalledWith('bucket-1', 'oid-1');
      expect(windowOpen).toHaveBeenCalledWith('https://cdn.example/report.pdf', '_blank', 'noopener,noreferrer');
    });

    it('does not open a window when the resolved uri is empty', async () => {
      const artifact = makeArtifact();
      objectStore.getObjectUriByID.mockReturnValue(of({ uri: '' }));
      const windowOpen = vi.spyOn(window, 'open').mockReturnValue(null);
      const { component } = await setupArtifactComponent(makeConfig(), artifact, dialog, objectStore);

      component.openArtifact({ preventDefault: vi.fn() } as unknown as Event, artifact);

      expect(windowOpen).not.toHaveBeenCalled();
    });
  });

  describe('onArtifactUploaded()', () => {
    it('writes the uploaded artifact onto the form control and marks it dirty + touched', async () => {
      const { component } = await setupArtifactComponent(makeConfig(), null, dialog, objectStore);
      const uploaded = makeArtifact({ objectId: 'new-oid' });

      component.onArtifactUploaded(uploaded);

      const control = component.formGroup.get('artifact');
      expect(control?.value).toEqual(uploaded);
      expect(control?.dirty).toBe(true);
      expect(control?.touched).toBe(true);
    });

    it('does nothing when the descriptor is disabled', async () => {
      const config = makeConfig();
      config.disabled = true;
      const { component } = await setupArtifactComponent(config, null, dialog, objectStore);
      const before = component.formGroup.get('artifact')?.value;

      component.onArtifactUploaded(makeArtifact({ objectId: 'new-oid' }));

      expect(component.formGroup.get('artifact')?.value).toEqual(before);
      expect(component.formGroup.get('artifact')?.dirty).toBe(false);
    });
  });

  describe('deleteArtifact()', () => {
    it('does nothing when the descriptor is disabled', async () => {
      const config = makeConfig();
      config.disabled = true;
      const { component } = await setupArtifactComponent(config, makeArtifact(), dialog, objectStore);

      component.deleteArtifact();

      expect(dialog.open).not.toHaveBeenCalled();
    });

    it('does nothing when there is no artifact to delete', async () => {
      const { component } = await setupArtifactComponent(makeConfig(), null, dialog, objectStore);

      component.deleteArtifact();

      expect(dialog.open).not.toHaveBeenCalled();
    });

    it('opens the confirmation dialog but leaves the artifact intact when the user cancels', async () => {
      const artifact = makeArtifact();
      dialog.open.mockReturnValue({ afterClosed: () => of(false) } as never);
      const { component } = await setupArtifactComponent(makeConfig(), artifact, dialog, objectStore);

      component.deleteArtifact();

      expect(dialog.open).toHaveBeenCalled();
      const data = dialog.open.mock.calls[0][1]?.data as { contentParams?: { artifactName?: string } } | undefined;
      expect(data?.contentParams?.artifactName).toBe(artifact.name);
      expect(objectStore.deleteObjectByID).not.toHaveBeenCalled();
      expect(component.formGroup.get('artifact')?.value).toEqual(artifact);
    });

    it('deletes the artifact and clears the form control when the user confirms', async () => {
      const artifact = makeArtifact();
      dialog.open.mockReturnValue({ afterClosed: () => of(true) } as never);
      const deleteResult = new Subject<void>();
      objectStore.deleteObjectByID.mockReturnValue(deleteResult.asObservable());
      const { component } = await setupArtifactComponent(makeConfig(), artifact, dialog, objectStore);

      component.deleteArtifact();

      expect(objectStore.deleteObjectByID).toHaveBeenCalledWith('bucket-1', 'oid-1');

      deleteResult.next();
      deleteResult.complete();

      const control = component.formGroup.get('artifact');
      expect(control?.value).toBeNull();
      expect(control?.dirty).toBe(true);
      expect(control?.touched).toBe(true);
    });
  });

  describe('template', () => {
    it('renders the artifact name and a delete button when an artifact is present and the control is enabled', async () => {
      const artifact = makeArtifact({ name: 'manual.pdf' });
      const { fixture } = await setupArtifactComponent(makeConfig(), artifact, dialog, objectStore);
      const host = fixture.nativeElement as HTMLElement;

      expect(host.querySelector('a')?.textContent?.trim()).toBe('manual.pdf');
      expect(host.querySelector('button[aria-label="Delete artifact reference"]')).not.toBeNull();
      expect(host.querySelector('app-artifact-selector')).not.toBeNull();
    });

    it('hides the delete button and the upload selector when the descriptor is disabled', async () => {
      const config = makeConfig();
      config.disabled = true;
      const { fixture } = await setupArtifactComponent(config, makeArtifact(), dialog, objectStore);
      const host = fixture.nativeElement as HTMLElement;

      expect(host.querySelector('button[aria-label="Delete artifact reference"]')).toBeNull();
      expect(host.querySelector('app-artifact-selector')).toBeNull();
    });

    it('renders the value as a heading when isHeading is set', async () => {
      const config = makeConfig();
      config.isHeading = true;
      const entity = new TestEntity('1', 'name');
      Reflect.set(entity, 'artifact', 'Section Title');

      const harness = await setupFormControlTest(ArtifactComponent, config, entity, [
        { provide: MatDialog, useValue: dialog },
        { provide: ObjectStoreService, useValue: objectStore },
      ]);

      expect((harness.fixture.nativeElement as HTMLElement).querySelector('h3')?.textContent).toBe('Section Title');
    });

    it('renders nothing when the descriptor is not visible', async () => {
      const config = makeConfig();
      config.visible = false;
      const { fixture } = await setupArtifactComponent(config, makeArtifact(), dialog, objectStore);
      const host = fixture.nativeElement as HTMLElement;

      expect(host.querySelector('fieldset')).toBeNull();
      expect(host.querySelector('h3')).toBeNull();
    });
  });
});
