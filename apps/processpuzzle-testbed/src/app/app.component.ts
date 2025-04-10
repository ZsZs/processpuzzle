import { Component } from '@angular/core';
import { RouterModule } from '@angular/router';
import { HeaderComponent } from './navigation/header/header.component';
import { SidenavComponent } from './navigation/sidenav/sidenav.component';
import { MatSidenav, MatSidenavContainer, MatSidenavContent } from '@angular/material/sidenav';
import { FooterComponent } from './navigation/footer/footer.component';

@Component({
  imports: [RouterModule, HeaderComponent, SidenavComponent, MatSidenav, MatSidenavContent, MatSidenavContainer, FooterComponent],
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
})
export class AppComponent {
  title = 'processpuzzle-testbed';
}
