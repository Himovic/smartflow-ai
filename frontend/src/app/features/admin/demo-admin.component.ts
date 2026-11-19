import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ToastService } from '../../core/toast.service';
import { TrPipe } from '../../shared/tr.pipe';

type R = Record<string, any>;

@Component({
  selector: 'sf-demo-admin',
  imports: [DatePipe, TrPipe],
  template: `
  <div class="page">
    <div class="page-header"><div><h1>{{ 'nav.demoAdmin' | tr }}</h1><div class="subtitle">{{ 'demoAdmin.subtitle' | tr }}</div></div></div>
    <div class="grid grid-2">
      <div class="card"><div class="card-header"><h3>{{ 'demoAdmin.status' | tr }}</h3></div><div class="card-body">
        @if (status(); as s) { <dl class="kv"><dt>{{ 'demoAdmin.enabled' | tr }}</dt><dd>{{ s['enabled'] }}</dd><dt>{{ 'demoAdmin.lastReset' | tr }}</dt><dd>{{ s['lastReset'] ? (s['lastReset'] | date: 'dd/MM/yyyy HH:mm') : '—' }}</dd>
          <dt>{{ 'demoAdmin.tenants' | tr }}</dt><dd>@for (t of s['tenants']; track t['code']) {<span class="badge amber">{{ t['name'] }}</span> }</dd></dl> }
        <div class="divider"></div>
        @if (auth.me()?.role === 'SUPER_ADMIN') { <button class="btn danger" [disabled]="busy()" (click)="reset()"><span class="icon">restart_alt</span>{{ 'demoAdmin.reset' | tr }}</button>
          <p class="muted small" style="margin-top:8px">{{ 'demoAdmin.resetHint' | tr }}</p> }
      </div></div>
      <div class="card"><div class="card-header"><h3>{{ 'demoAdmin.qr' | tr }}</h3></div><div class="card-body qr">
        <img [src]="qr" width="240" height="240" alt="QR" /><code>{{ url }}</code><button class="btn" (click)="print()"><span class="icon">print</span>{{ 'common.print' | tr }}</button></div></div>
    </div>
  </div>`,
  styles: [`.qr { display: flex; flex-direction: column; align-items: center; gap: 10px; }`],
})
export class DemoAdminComponent {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  readonly auth = inject(AuthService);
  readonly status = signal<R | null>(null);
  readonly busy = signal(false);
  readonly url = location.origin + '/demo';
  readonly qr = `/api/v1/public/qr-image?size=480&url=${encodeURIComponent(this.url)}`;

  constructor() {
    this.load();
  }

  load(): void {
    this.api.get<R>('/demo/status').subscribe((s) => this.status.set(s));
  }

  reset(): void {
    if (!confirm('Reset demo data?')) return;
    this.busy.set(true);
    this.api.post<R>('/demo/reset').subscribe({ next: () => { this.busy.set(false); this.toast.success('Reset'); this.auth.logout(); }, error: () => this.busy.set(false) });
  }

  print(): void {
    const w = window.open('', '_blank');
    w?.document.write(`<html><body style="text-align:center;font-family:sans-serif;padding-top:60px"><h1>SmartFlow AI + QHSE</h1><p>One intelligent platform connecting industrial operations, distribution and QHSE.</p>
      <img src="${this.qr}" width="420"/><h3>${this.url}</h3><p>D&amp;D | Data&amp;Development — Smart Care Challenge 2026</p><script>setTimeout(()=>print(),600)</script></body></html>`);
  }
}
