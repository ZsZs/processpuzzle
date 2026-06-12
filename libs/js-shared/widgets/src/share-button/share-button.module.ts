import { NgModule } from '@angular/core';
import { provideShareButtonsOptions } from 'ngx-sharebuttons';
import { shareIcons } from 'ngx-sharebuttons/icons';

@NgModule({
  imports: [],
  declarations: [],
  providers: [provideShareButtonsOptions(shareIcons())],
})
export class ShareButtonModule {}
