import { bootstrapApplication } from '@angular/platform-browser';
import { AppComponent } from './app/app.component';
import { appConfig } from './app/app.config';

bootstrapApplication(AppComponent, appConfig).catch((err: unknown) => {
  // Surface bootstrap failures so the SPA does not silently fail to mount.
  // eslint-disable-next-line no-console
  console.error('Failed to bootstrap application', err);
});
