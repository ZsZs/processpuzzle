import { TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mock, type MockProxy } from 'vitest-mock-extended';
import { ObjectStoreService, type UploadObjectResponse } from '../../object-store/object-store.service';
import { ArtifactSelectorComponent } from './artifact-selector.component';

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

async function setupSelector(objectStore: MockProxy<ObjectStoreService>, initialState?: Partial<ArtifactSelectorComponent>) {
  await TestBed.configureTestingModule({
    imports: [ArtifactSelectorComponent],
    providers: [{ provide: ObjectStoreService, useValue: objectStore }],
  }).compileComponents();

  const fixture = TestBed.createComponent(ArtifactSelectorComponent);
  if (initialState) Object.assign(fixture.componentInstance, initialState);
  fixture.detectChanges();
  return { fixture, component: fixture.componentInstance };
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

    expect(component.isSelectorVisible).toBe(true);
  });

  it('renders the file/name/mime inputs when the selector is visible', async () => {
    const { fixture } = await setupSelector(objectStore, { isSelectorVisible: true });
    const host = fixture.nativeElement as HTMLElement;

    expect(host.querySelector('input[type="file"]')).not.toBeNull();
    const textInputs = host.querySelectorAll('input[type="text"]');
    expect(textInputs).toHaveLength(2);
  });

  it('renders the uploading indicator when isUploading is set', async () => {
    const { fixture } = await setupSelector(objectStore, { isUploading: true });

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Uploading...');
  });

  it('renders the error message paragraph when errorMessage is set', async () => {
    const { fixture } = await setupSelector(objectStore, { errorMessage: 'Artifact upload failed.' });

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Artifact upload failed.');
  });

  it('does nothing when onFileSelected fires without a file', async () => {
    const { component } = await setupSelector(objectStore);

    component.onFileSelected(emptyFileEvent());

    expect(component.artifactName).toBe('');
    expect(component.mimeType).toBe('');
    expect(objectStore.uploadObject).not.toHaveBeenCalled();
  });

  it('uploads the selected file and emits artifactUploaded with the full server response', async () => {
    const response: UploadObjectResponse = { objectID: 'oid-1', fileName: 'server-name.txt', mimeType: 'text/x-custom', bucketName: 'bucket-a' };
    objectStore.uploadObject.mockReturnValue(of(response));
    const { component } = await setupSelector(objectStore);
    const emit = vi.fn();
    component.artifactUploaded.subscribe(emit);

    const file = createFile('local.txt', 'text/plain');
    component.onFileSelected(fileSelectEvent(file));

    expect(objectStore.uploadObject).toHaveBeenCalledWith(file, 'local.txt', 'text/plain');
    expect(emit).toHaveBeenCalledWith({
      bucket: 'bucket-a',
      objectId: 'oid-1',
      name: 'server-name.txt',
      mimeType: 'text/x-custom',
    });
    expect(component.isUploading).toBe(false);
    expect(component.errorMessage).toBe('');
  });

  it('falls back to local artifactName and mimeType when the response omits them', async () => {
    objectStore.uploadObject.mockReturnValue(of({ objectID: 'oid-2', fileName: '', mimeType: '' } as UploadObjectResponse));
    const { component } = await setupSelector(objectStore);
    const emit = vi.fn();
    component.artifactUploaded.subscribe(emit);

    component.onFileSelected(fileSelectEvent(createFile('local.csv', 'text/plain')));

    expect(emit).toHaveBeenCalledWith({
      bucket: '',
      objectId: 'oid-2',
      name: 'local.csv',
      mimeType: 'text/plain',
    });
  });

  it('derives the mime type from the file extension when the browser leaves file.type empty', async () => {
    objectStore.uploadObject.mockReturnValue(of({ objectID: 'oid', fileName: 'a.csv', mimeType: 'text/csv', bucketName: 'b' }));
    const { component } = await setupSelector(objectStore);

    component.onFileSelected(fileSelectEvent(createFile('report.csv', '')));

    expect(component.mimeType).toBe('text/csv');
    expect(objectStore.uploadObject).toHaveBeenCalledWith(expect.any(File), 'report.csv', 'text/csv');
  });

  it('falls back to application/octet-stream for unknown extensions', async () => {
    objectStore.uploadObject.mockReturnValue(of({ objectID: 'oid', fileName: 'binary.dat', mimeType: 'application/octet-stream' }));
    const { component } = await setupSelector(objectStore);

    component.onFileSelected(fileSelectEvent(createFile('binary.dat', '')));

    expect(component.mimeType).toBe('application/octet-stream');
  });

  it('falls back to application/octet-stream when the file has no extension at all', async () => {
    objectStore.uploadObject.mockReturnValue(of({ objectID: 'oid', fileName: '', mimeType: '' }));
    const { component } = await setupSelector(objectStore);

    component.onFileSelected(fileSelectEvent(createFile('Makefile', '')));

    expect(component.mimeType).toBe('application/octet-stream');
  });

  it('keeps isUploading true while the upload is pending and ignores concurrent file selections', async () => {
    const inflight = new Subject<UploadObjectResponse>();
    objectStore.uploadObject.mockReturnValue(inflight.asObservable());
    const { component } = await setupSelector(objectStore);

    component.onFileSelected(fileSelectEvent(createFile('first.txt', 'text/plain')));

    expect(component.isUploading).toBe(true);

    component.onFileSelected(fileSelectEvent(createFile('second.txt', 'text/plain')));

    expect(objectStore.uploadObject).toHaveBeenCalledTimes(1);

    inflight.next({ objectID: 'oid', fileName: 'first.txt', mimeType: 'text/plain', bucketName: 'b' });
    inflight.complete();

    expect(component.isUploading).toBe(false);
  });

  it('sets errorMessage and clears the uploading flag when the upload fails', async () => {
    objectStore.uploadObject.mockReturnValue(throwError(() => new Error('boom')));
    const { component } = await setupSelector(objectStore);
    const emit = vi.fn();
    component.artifactUploaded.subscribe(emit);

    component.onFileSelected(fileSelectEvent(createFile('a.txt', 'text/plain')));

    expect(component.isUploading).toBe(false);
    expect(component.errorMessage).toBe('Artifact upload failed.');
    expect(emit).not.toHaveBeenCalled();
  });
});
