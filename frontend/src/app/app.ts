import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ToastHostComponent } from './shared/toast-host.component';
import { I18nService } from './core/i18n.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, ToastHostComponent],
  template: `<router-outlet /><sf-toast-host />`,
})
export class App {
  constructor() {
    inject(I18nService).init();
  }
}
