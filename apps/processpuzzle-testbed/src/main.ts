import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { AppComponent } from './app/app.component';

console.log('Stage: ', import.meta.env.NG_APP_PIPELINE_STAGE);

bootstrapApplication(AppComponent, appConfig).catch((err) => console.error(err));
