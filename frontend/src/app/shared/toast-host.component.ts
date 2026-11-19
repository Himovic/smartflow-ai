import { Component, inject } from '@angular/core';
import { ToastService } from '../core/toast.service';

@Component({
  selector: 'sf-toast-host',
  template: `
    <div class="toasts" aria-live="polite">
      @for (t of toast.toasts(); track t.id) {
        <div class="toast fade-in" [class]="'toast fade-in ' + t.kind" (click)="toast.dismiss(t.id)">
          <span class="icon">{{ icons[t.kind] }}</span><span>{{ t.text }}</span>
        </div>
      }
    </div>`,
  styles: [`
    .toasts { position: fixed; inset-block-end: 20px; inset-inline-end: 20px; display: flex; flex-direction: column; gap: 10px; z-index: 2000; max-width: 420px; }
    .toast { display: flex; gap: 10px; align-items: flex-start; padding: 12px 14px; border-radius: 10px; background: var(--navy-900); color: #fff;
      box-shadow: var(--shadow-lg); cursor: pointer; font-size: 13.5px; line-height: 1.4; border-inline-start: 4px solid var(--cyan-500); }
    .toast.success { border-color: var(--green); } .toast.error { border-color: var(--red); } .toast.warning { border-color: var(--amber); }
  `],
})
export class ToastHostComponent {
  readonly toast = inject(ToastService);
  readonly icons: Record<string, string> = { success: 'check_circle', error: 'error', info: 'info', warning: 'warning' };
}
