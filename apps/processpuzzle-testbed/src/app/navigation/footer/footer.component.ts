import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatToolbar } from '@angular/material/toolbar';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { VersionButtonComponent } from '@processpuzzle/base-widget';

@Component({
  selector: 'app-footer',
  imports: [CommonModule, MatToolbar, VersionButtonComponent],
  templateUrl: './footer.component.html',
  styleUrl: './footer.component.scss',
})
export class FooterComponent {}
