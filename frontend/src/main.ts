import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import localeArDz from '@angular/common/locales/ar-DZ';
import localeEnGb from '@angular/common/locales/en-GB';
import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';

registerLocaleData(localeFr, 'fr-FR');
registerLocaleData(localeArDz, 'ar-DZ');
registerLocaleData(localeEnGb, 'en-GB');

bootstrapApplication(App, appConfig).catch((err) => console.error(err));
