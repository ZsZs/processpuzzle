import { Component, EventEmitter, inject, Output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ObjectStoreService } from '../../object-store/object-store.service';
import { ArtifactAttr } from './artifact-attr';

@Component({
  selector: 'app-artifact-selector',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="artifact-selector">
      @if (!isSelectorVisible()) {
        <button type="button" class="base-entity-form-focus-action" (click)="showSelector()">Upload file</button>
      } @else {
        <div class="artifact-selector__inputs">
          <input type="file" (change)="onFileSelected($event)" [disabled]="isUploading()" />
          <input type="text" [(ngModel)]="artifactName" placeholder="Artifact name" [disabled]="isUploading()" />
          <input type="text" [(ngModel)]="mimeType" placeholder="MIME type" [disabled]="isUploading()" />
          <button type="button" (click)="uploadSelectedFile()" [disabled]="!canUpload()">Upload</button>
          <button type="button" (click)="cancel()" [disabled]="isUploading()">Cancel</button>
        </div>
      }

      @if (isUploading()) {
        <p>Uploading...</p>
      }
    </div>
  `,
  styleUrls: ['../base-entity-form.css'],
  styles: [
    `
      :host-context(.base-entity-form-field:focus-within) .base-entity-form-focus-action {
        display: revert;
      }
    `,
  ],
})
export class ArtifactSelectorComponent {
  private readonly objectStoreService = inject(ObjectStoreService);

  @Output() artifactUploaded = new EventEmitter<ArtifactAttr>();

  readonly isSelectorVisible = signal(false);
  readonly isUploading = signal(false);
  artifactName = '';
  mimeType = '';

  private selectedFile: File | null = null;

  showSelector(): void {
    this.isSelectorVisible.set(true);
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.item(0);

    if (!file) {
      return;
    }

    this.selectedFile = file;
    this.artifactName = file.name;
    this.mimeType = file.type || this.deriveMimeType(file.name);
  }

  canUpload(): boolean {
    return this.selectedFile !== null && this.artifactName.length > 0 && this.mimeType.length > 0 && !this.isUploading();
  }

  uploadSelectedFile(): void {
    if (!this.selectedFile || !this.canUpload()) {
      return;
    }

    this.isUploading.set(true);

    this.objectStoreService.uploadObject(this.selectedFile, this.artifactName, this.mimeType).subscribe({
      next: (response) => {
        const artifact: ArtifactAttr = {
          bucket: response.bucketName ?? '',
          objectId: response.objectID,
          name: response.fileName || this.artifactName,
          mimeType: response.mimeType || this.mimeType,
        };

        this.artifactUploaded.emit(artifact);
        this.resetToDisplayMode();
      },
      error: () => {
        this.resetToDisplayMode();
      },
    });
  }

  cancel(): void {
    if (this.isUploading()) {
      return;
    }

    this.resetToDisplayMode();
  }

  private resetToDisplayMode(): void {
    this.selectedFile = null;
    this.artifactName = '';
    this.mimeType = '';
    this.isUploading.set(false);
    this.isSelectorVisible.set(false);
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
