import { Component } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { RouterLink } from '@angular/router';
import { MatButton } from '@angular/material/button';

@Component({
  selector: 'app-content',
  templateUrl: './content.component.html',
  imports: [MatCardModule, RouterLink, MatButton],
  styleUrl: './content.component.scss',
})
export class ContentComponent {}
