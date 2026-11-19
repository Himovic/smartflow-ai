import { Component, OnInit, inject, input, signal } from '@angular/core';
import { KeyValuePipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { TrPipe } from '../../shared/tr.pipe';

interface QrView { type: string; id: string; title?: string; fields?: Record<string, unknown>; organization?: string; requiresLogin: boolean; route?: string; }

/** Landing page of a scanned QR code: limited public summary, full record after sign-in. */
@Component({
  selector: 'sf-public-qr',
  imports: [RouterLink, KeyValuePipe, TrPipe],
  template: `
    <div class="qr-page">
      <div class="card qr-card fade-in">
        <div class="row"><img src="favicon.svg" width="32" alt="" /><strong>SmartFlow AI + QHSE</strong></div>
        @if (view(); as v) {
          <span class="badge blue">{{ ('qr.type.' + v.type) | tr }}</span>
          <h1>{{ v.title ?? v.id }}</h1>
          @if (v.organization) { <p class="muted">{{ v.organization }}</p> }
          @if (v.fields) {
            <dl class="kv">@for (f of v.fields | keyvalue; track f.key) { <dt>{{ f.key }}</dt><dd>{{ f.value }}</dd> }</dl>
          }
          <button class="btn primary" (click)="openFull()"><span class="icon">open_in_new</span>{{ 'qr.openRecord' | tr }}</button>
        } @else if (error()) { <p class="muted">{{ 'qr.notFound' | tr }}</p> } @else { <div class="skeleton" style="height:120px"></div> }
        <a routerLink="/demo" class="small">{{ 'login.demoAccess' | tr }}</a>
      </div>
    </div>`,
  styles: [`.qr-page { min-height: 100vh; display: grid; place-items: center; padding: 20px; background: linear-gradient(160deg, var(--navy-950), var(--navy-800)); }
    .qr-card { width: min(480px, 100%); padding: 24px; display: flex; flex-direction: column; gap: 14px; }`],
})
export class PublicQrComponent implements OnInit {
  readonly type = input.required<string>();
  readonly id = input.required<string>();
  readonly t = input<string>();
  private api = inject(ApiService);
  private auth = inject(AuthService);
  private router = inject(Router);
  readonly view = signal<QrView | null>(null);
  readonly error = signal(false);

  ngOnInit(): void {
    this.api.get<QrView>(`/public/qr/${this.type()}/${this.id()}`, { t: this.t() }).subscribe({ next: (v) => this.view.set(v), error: () => this.error.set(true) });
  }

  openFull(): void {
    const routes: Record<string, string> = { batch: `/traceability/${this.id()}`, inspection: `/inspections/${this.id()}`, equipment: '/equipment', vehicle: '/fleet',
      warehouse: '/warehouses', product: '/products' };
    const target = routes[this.type()] ?? '/dashboard';
    this.router.navigateByUrl(this.auth.accessToken ? target : '/login');
  }
}
