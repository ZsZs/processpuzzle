import { Component, EventEmitter, inject, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ObjectStoreService } from '../../object-store/object-store.service';
import { ArtifactAttr } from './artifact-attr';

@Component({
  selector: 'app-artifact-selector',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="artifact-selector">
      @if (!isSelectorVisible) {
        <button type="button" (click)="showSelector()">Upload file</button>
      } @else {
        <div class="artifact-selector__inputs">
          <input type="file" (change)="onFileSelected($event)" />
          <input
            type="text"
            [(ngModel)]="artifactName"
            placeholder="Artifact name"
            [disabled]="isUploading"
          />
          <input
            type="text"
            [(ngModel)]="mimeType"
            placeholder="MIME type"
            [disabled]="isUploading"
          />
        </div>
      }

      @if (isUploading) {
        <p>Uploading...</p>
      }

      @if (errorMessage) {
        <p>{{ errorMessage }}</p>
      }
    </div>
  `,
  styles: ``,
})
export class ArtifactSelectorComponent {
  private readonly objectStoreService = inject(ObjectStoreService);

  @Output() artifactUploaded = new EventEmitter<ArtifactAttr>();

  isSelectorVisible = false;
  isUploading = false;
  artifactName = '';
  mimeType = '';
  errorMessage = '';

  private selectedFile: File | null = null;

  showSelector(): void {
    this.isSelectorVisible = true;
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.item(0);

    if (!file) {
      return;
    }

    this.selectedFile = file;
    this.errorMessage = '';
    this.artifactName = file.name;
    this.mimeType = file.type || this.deriveMimeType(file.name);

    this.uploadSelectedFile();
  }

  private uploadSelectedFile(): void {
    if (!this.selectedFile || !this.artifactName || !this.mimeType || this.isUploading) {
      return;
    }

    this.isUploading = true;

    this.objectStoreService.uploadObject(this.selectedFile, this.artifactName, this.mimeType).subscribe({
      next: (response) => {
        const artifact: ArtifactAttr = {
          bucket: response.bucketName ?? '',
          objectId: response.objectID,
          name: response.fileName || this.artifactName,
          mimeType: response.mimeType || this.mimeType,
        };

        this.artifactUploaded.emit(artifact);
        this.selectedFile = null;
        this.isUploading = false;
      },
      error: () => {
        this.isUploading = false;
        this.errorMessage = 'Artifact upload failed.';
      },
    });
  }

  private deriveMimeType(fileName: string): string {
    const extension = fileName.split('.').pop()?.toLowerCase() ?? '';
    const mimeTypeByExtension: Record<string, string> = {
      csv: 'text/csv',
      gif: 'image/gif',
      jpeg: 'image/jpeg',
      jpg: 'image/jpeg',
      json: 'application/json',
      pdf: 'application/pdf',
      png: 'image/png',
      svg: 'image/svg+xml',
      txt: 'text/plain',
      xml: 'application/xml',
      zip: 'application/zip',
    };

    return mimeTypeByExtension[extension] ?? 'application/octet-stream';
  }
}
