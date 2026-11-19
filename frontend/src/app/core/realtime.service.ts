import { Injectable, inject, signal } from '@angular/core';
import { Subject } from 'rxjs';
import { AuthService } from './auth.service';
import { ToastService } from './toast.service';
import { ApiService } from './api.service';

export interface RealtimeEvent {
  type: string;
  payload: { title?: string; message?: string; severity?: string; [k: string]: unknown };
}

/** WebSocket channel for live notifications and alerts, with automatic reconnection. */
@Injectable({ providedIn: 'root' })
export class RealtimeService {
  private auth = inject(AuthService);
  private toast = inject(ToastService);
  private api = inject(ApiService);
  private ws?: WebSocket;
  private retry = 0;
  readonly events$ = new Subject<RealtimeEvent>();
  readonly unread = signal(0);
  readonly connected = signal(false);

  connect(): void {
    this.refreshUnread();
    if (this.ws && this.ws.readyState <= 1) return;
    const token = this.auth.accessToken;
    if (!token) return;
    const proto = location.protocol === 'https:' ? 'wss' : 'ws';
    this.ws = new WebSocket(`${proto}://${location.host}/ws/events?access_token=${encodeURIComponent(token)}`);
    this.ws.onopen = () => {
      this.connected.set(true);
      this.retry = 0;
    };
    this.ws.onmessage = (m) => {
      const e = JSON.parse(m.data) as RealtimeEvent;
      this.events$.next(e);
      if (e.type === 'notification') {
        this.unread.update((n) => n + 1);
        const sev = e.payload.severity;
        this.toast.show(sev === 'CRITICAL' ? 'error' : sev === 'WARNING' ? 'warning' : 'info', `${e.payload.title ?? ''}`);
      }
    };
    this.ws.onclose = () => {
      this.connected.set(false);
      if (!this.auth.accessToken) return;
      setTimeout(() => this.connect(), Math.min(30000, 2000 * 2 ** this.retry++));
    };
  }

  refreshUnread(): void {
    this.api.get<{ count: number }>('/notifications/unread-count').subscribe({ next: (r) => this.unread.set(r.count), error: () => undefined });
  }
}
