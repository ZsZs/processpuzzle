import { TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mock, type MockProxy } from 'vitest-mock-extended';
import { ObjectStoreService, type UploadObjectResponse } from '../../object-store/object-store.service';
import { ArtifactSelectorComponent } from './artifact-selector.component';

interface InitialState {
  isSelectorVisible?: boolean;
  isUploading?: boolean;
}

function createFile(name: string, type: string): File {
  return new File(['payload'], name, { type });
}

function fileSelectEvent(file: File | null): Event {
  const files = { length: file ? 1 : 0, item: (i: number) => (i === 0 ? file : null) };
  return { target: { files } } as unknown as Event;
}

function emptyFileEvent(): Event {
  return { target: { files: { item: () => null } } } as unknown as Event;
}

async function setupSelector(objectStore: MockProxy<ObjectStoreService>, initialState?: InitialState) {
  await TestBed.configureTestingModule({
    imports: [ArtifactSelectorComponent],
    providers: [{ provide: ObjectStoreService, useValue: objectStore }],
  }).compileComponents();

  const fixture = TestBed.createComponent(ArtifactSelectorComponent);
  const component = fixture.componentInstance;
  if (initialState?.isSelectorVisible !== undefined) component.isSelectorVisible.set(initialState.isSelectorVisible);
  if (initialState?.isUploading !== undefined) component.isUploading.set(initialState.isUploading);
  fixture.detectChanges();
  return { fixture, component };
}

describe('ArtifactSelectorComponent', () => {
  let objectStore: MockProxy<ObjectStoreService>;

  beforeEach(() => {
    TestBed.resetTestingModule();
    objectStore = mock<ObjectStoreService>();
  });

  it('renders the upload trigger while the selector is hidden', async () => {
    const { fixture } = await setupSelector(objectStore);
    const host = fixture.nativeElement as HTMLElement;

    expect(host.querySelector('button[type="button"]')?.textContent?.trim()).toBe('Upload file');
    expect(host.querySelector('input[type="file"]')).toBeNull();
  });

  it('flips isSelectorVisible to true on showSelector()', async () => {
    const { component } = await setupSelector(objectStore);

    component.showSelector();

    expect(component.isSelectorVisible()).toBe(true);
  });

  it('renders the file/name/mime inputs and upload/cancel buttons when the selector is visible', async () => {
    const { fixture } = await setupSelector(objectStore, { isSelectorVisible: true });
    const host = fixture.nativeElement as HTMLElement;

    expect(host.querySelector('input[type="file"]')).not.toBeNull();
    expect(host.querySelectorAll('input[type="text"]')).toHaveLength(2);
    const buttonLabels = Array.from(host.querySelectorAll('button')).map((btn) => btn.textContent?.trim());
    expect(buttonLabels).toEqual(expect.arrayContaining(['Upload', 'Cancel']));
  });

  it('renders the uploading indicator when isUploading is set', async () => {
    const { fixture } = await setupSelector(objectStore, { isUploading: true });

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Uploading...');
  });

  it('does nothing when onFileSelected fires without a file', async () => {
    const { component } = await setupSelector(objectStore, { isSelectorVisible: true });

    component.onFileSelected(emptyFileEvent());

    expect(component.artifactName).toBe('');
    expect(component.mimeType).toBe('');
    expect(component.canUpload()).toBe(false);
    expect(objectStore.uploadObject).not.toHaveBeenCalled();
  });

  it('populates artifactName and mimeType on file selection without uploading', async () => {
    const { component } = await setupSelector(objectStore, { isSelectorVisible: true });

    component.onFileSelected(fileSelectEvent(createFile('local.txt', 'text/plain')));

    expect(component.artifactName).toBe('local.txt');
    expect(component.mimeType).toBe('text/plain');
    expect(component.canUpload()).toBe(true);
    expect(objectStore.uploadObject).not.toHaveBeenCalled();
  });

  it('uploads via uploadSelectedFile() and emits the artifact using the server response', async () => {
    const response: UploadObjectResponse = { objectID: 'oid-1', fileName: 'server-name.txt', mimeType: 'text/x-custom', bucketName: 'bucket-a' };
    objectStore.uploadObject.mockReturnValue(of(response));
    const { component } = await setupSelector(objectStore, { isSelectorVisible: true });
    const emit = vi.fn();
    component.artifactUploaded.subscribe(emit);

    const file = createFile('local.txt', 'text/plain');
    component.onFileSelected(fileSelectEvent(file));
    component.artifactName = 'edited-name.txt';
    component.mimeType = 'text/edited';
    component.uploadSelectedFile();

    expect(objectStore.uploadObject).toHaveBeenCalledWith(file, 'edited-name.txt', 'text/edited');
    expect(emit).toHaveBeenCalledWith({
      bucket: 'bucket-a',
      objectId: 'oid-1',
      name: 'server-name.txt',
      mimeType: 'text/x-custom',
    });
    expect(component.isSelectorVisible()).toBe(false);
    expect(component.isUploading()).toBe(false);
  });

  it('falls back to local artifactName and mimeType when the response omits them', async () => {
    objectStore.uploadObject.mockReturnValue(of({ objectID: 'oid-2', fileName: '', mimeType: '' } as UploadObjectResponse));
    const { component } = await setupSelector(objectStore, { isSelectorVisible: true });
    const emit = vi.fn();
    component.artifactUploaded.subscribe(emit);

    component.onFileSelected(fileSelectEvent(createFile('local.csv', 'text/plain')));
    component.uploadSelectedFile();

    expect(emit).toHaveBeenCalledWith({
      bucket: '',
      objectId: 'oid-2',
      name: 'local.csv',
      mimeType: 'text/plain',
    });
  });

  it.each([
    { scenario: 'derives the mime type from the file extension', fileName: 'report.csv', expected: 'text/csv' },
    { scenario: 'falls back to application/octet-stream for unknown extensions', fileName: 'binary.dat', expected: 'application/octet-stream' },
    { scenario: 'falls back to application/octet-stream when the file has no extension', fileName: 'Makefile', expected: 'application/octet-stream' },
  ])('$scenario when the browser leaves file.type empty', async ({ fileName, expected }) => {
    const { component } = await setupSelector(objectStore, { isSelectorVisible: true });

    component.onFileSelected(fileSelectEvent(createFile(fileName, '')));

    expect(component.mimeType).toBe(expected);
  });

  it('does not upload when uploadSelectedFile() is called without a selected file', async () => {
    const { component } = await setupSelector(objectStore, { isSelectorVisible: true });

    component.uploadSelectedFile();

    expect(objectStore.uploadObject).not.toHaveBeenCalled();
  });

  it('keeps isUploading true while the upload is pending', async () => {
    const inflight = new Subject<UploadObjectResponse>();
    objectStore.uploadObject.mockReturnValue(inflight.asObservable());
    const { component } = await setupSelector(objectStore, { isSelectorVisible: true });

    component.onFileSelected(fileSelectEvent(createFile('first.txt', 'text/plain')));
    component.uploadSelectedFile();

    expect(component.isUploading()).toBe(true);
    expect(component.canUpload()).toBe(false);

    inflight.next({ objectID: 'oid', fileName: 'first.txt', mimeType: 'text/plain', bucketName: 'b' });
    inflight.complete();

    expect(component.isUploading()).toBe(false);
  });

  it('returns to display mode without emitting when the upload fails', async () => {
    objectStore.uploadObject.mockReturnValue(throwError(() => new Error('boom')));
    const { component } = await setupSelector(objectStore, { isSelectorVisible: true });
    const emit = vi.fn();
    component.artifactUploaded.subscribe(emit);

    component.onFileSelected(fileSelectEvent(createFile('a.txt', 'text/plain')));
    component.uploadSelectedFile();

    expect(component.isUploading()).toBe(false);
    expect(component.isSelectorVisible()).toBe(false);
    expect(component.artifactName).toBe('');
    expect(component.mimeType).toBe('');
    expect(emit).not.toHaveBeenCalled();
  });

  it('resets to display mode when cancel() is called', async () => {
    const { component } = await setupSelector(objectStore, { isSelectorVisible: true });

    component.onFileSelected(fileSelectEvent(createFile('a.txt', 'text/plain')));
    component.cancel();

    expect(component.isSelectorVisible()).toBe(false);
    expect(component.artifactName).toBe('');
    expect(component.mimeType).toBe('');
    expect(objectStore.uploadObject).not.toHaveBeenCalled();
  });
});
