import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Router } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { RealtimeService } from '../../core/realtime.service';
import { Alert } from '../../core/models';
import { TrPipe, EnumPipe } from '../../shared/tr.pipe';

type R = Record<string, any>;
const ROUTES: Record<string, string> = { NonConformity: '/non-conformities/', Incident: '/incidents/', CapaAction: '/capa/', Batch: '/traceability/', Inspection: '/inspections/',
  QhseDocument: '/documents/' };

@Component({
  selector: 'sf-notifications',
  imports: [DatePipe, TrPipe, EnumPipe],
  template: `
  <div class="page">
    <div class="page-header"><div><h1>{{ 'nav.notifications' | tr }}</h1><div class="subtitle">{{ 'notif.subtitle' | tr }}</div></div>
      <button class="btn" (click)="readAll()"><span class="icon">done_all</span>{{ 'notif.readAll' | tr }}</button></div>
    <div class="grid grid-2">
      <div class="card"><div class="card-header"><span class="icon">notifications</span><h3>{{ 'notif.inbox' | tr }}</h3></div>
        @for (n of items(); track n['id']) { <button class="n" [class.unread]="!n['readAt']" (click)="open(n)"><span class="icon" [class]="'icon sev-' + n['severity']">{{ icon(n['severity']) }}</span>
          <div style="flex:1"><strong>{{ n['title'] }}</strong><div class="muted small">{{ n['message'] }}</div></div><span class="small muted">{{ n['createdAt'] | date: 'dd/MM HH:mm' }}</span></button>
        } @empty { <div class="empty"><span class="icon">notifications_off</span>{{ 'common.empty' | tr }}</div> }</div>
      <div class="card"><div class="card-header"><span class="icon">crisis_alert</span><h3>{{ 'notif.liveAlerts' | tr }}</h3><span class="badge">{{ alerts().length }}</span></div>
        <div style="max-height:720px;overflow:auto">@for (a of alerts(); track $index) { <button class="n" (click)="go(a.route)"><span class="icon" [class]="'icon sev-' + a.severity">{{ icon(a.severity) }}</span>
          <div style="flex:1"><strong>{{ a.title }}</strong><div class="muted small">{{ a.detail }}</div></div><span class="badge">{{ a.category | enum }}</span></button> }</div></div>
    </div>
  </div>`,
  styles: [`.n { display: flex; gap: 10px; width: 100%; padding: 12px 16px; border: 0; border-bottom: 1px solid var(--border); background: none; text-align: start; font: inherit; cursor: pointer; align-items: flex-start; }
    .n:hover { background: var(--surface-2); } .n.unread { background: #f2fbfd; } .n.unread strong::before { content: '● '; color: var(--cyan-500); }
    .sev-CRITICAL { color: var(--red); } .sev-WARNING { color: var(--amber); } .sev-INFO, .sev-SUCCESS { color: var(--blue-500); }`],
})
export class NotificationsComponent {
  private api = inject(ApiService);
  private router = inject(Router);
  private rt = inject(RealtimeService);
  readonly items = signal<R[]>([]);
  readonly alerts = signal<Alert[]>([]);

  constructor() {
    this.load();
    this.api.get<Alert[]>('/analytics/alerts').subscribe((a) => this.alerts.set(a));
  }

  load(): void {
    this.api.page<R>('/notifications', { size: 50 }).subscribe((p) => this.items.set(p.content));
    this.rt.refreshUnread();
  }

  icon(s: string): string {
    return s === 'CRITICAL' ? 'error' : s === 'WARNING' ? 'warning' : 'info';
  }

  open(n: R): void {
    this.api.post(`/notifications/${n['id']}/read`).subscribe(() => this.load());
    if (n['entityType'] && ROUTES[n['entityType']]) this.router.navigateByUrl(ROUTES[n['entityType']] + n['entityId']);
  }

  readAll(): void {
    this.api.post('/notifications/read-all').subscribe(() => this.load());
  }

  go(route?: string): void {
    if (route) this.router.navigateByUrl(route);
  }
}
