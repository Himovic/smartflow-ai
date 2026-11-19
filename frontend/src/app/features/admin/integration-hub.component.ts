import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe, JsonPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ToastService } from '../../core/toast.service';
import { TrPipe, EnumPipe } from '../../shared/tr.pipe';
import { StatusBadgeComponent } from '../../shared/status-badge.component';

type R = Record<string, any>;

/** Integration hub: ERP (SAP, Odoo), CMMS/GMAO, GPS and IoT connectors with sync monitoring and retries. */
@Component({
  selector: 'sf-integration-hub',
  imports: [DatePipe, JsonPipe, FormsModule, TrPipe, EnumPipe, StatusBadgeComponent],
  templateUrl: './integration-hub.component.html',
  styles: [`.conns { display: grid; grid-template-columns: repeat(auto-fill, minmax(330px, 1fr)); gap: 14px; }
    .conn { padding: 16px; display: flex; flex-direction: column; gap: 10px; } .conn.sel { border-color: var(--cyan-500); box-shadow: 0 0 0 3px rgba(0,168,204,.12); }
    .conn-head { display: flex; gap: 10px; align-items: flex-start; } .logo { width: 40px; height: 40px; border-radius: 10px; display: grid; place-items: center; background: var(--surface-2); color: var(--navy-800); }
    .meta { display: flex; flex-wrap: wrap; gap: 6px; font-size: 12px; } .err { color: var(--red); font-size: 12px; }
    .wizard { padding: 18px; } pre { background: var(--surface-2); padding: 8px; border-radius: 8px; font-size: 11.5px; max-height: 160px; overflow: auto; margin: 0; }
    .entities { display: flex; gap: 10px; flex-wrap: wrap; }`],
})
export class IntegrationHubComponent {
  private api = inject(ApiService);
  readonly auth = inject(AuthService);
  private toast = inject(ToastService);
  readonly providers = signal<R[]>([]);
  readonly connectors = signal<R[]>([]);
  readonly selected = signal<R | null>(null);
  readonly jobs = signal<R[]>([]);
  readonly records = signal<R[]>([]);
  readonly showWizard = signal(false);
  readonly busy = signal<string | null>(null);
  readonly admin = computed(() => this.auth.can('integrations', 'admin'));
  form: R = this.blank();
  creds = { username: '', password: '', apiKey: '', token: '', clientId: '', clientSecret: '' };
  configJson = '{}';

  constructor() {
    this.api.get<R[]>('/integrations/providers').subscribe((p) => this.providers.set(p));
    this.load();
  }

  blank(): R {
    return { name: '', provider: 'SIM_ERP', baseUrl: '', authType: 'NONE', entityTypes: [], enabled: true, syncFrequencyMinutes: 60, conflictPolicy: 'REMOTE_WINS' };
  }

  load(): void {
    this.api.get<R[]>('/integrations/connectors').subscribe((c) => {
      this.connectors.set(c);
      const sel = this.selected();
      if (sel) this.open(c.find((x) => x['id'] === sel['id']) ?? c[0]);
    });
  }

  provider(code: string): R | undefined {
    return this.providers().find((p) => p['code'] === code);
  }

  icon(cat: string): string {
    return ({ ERP: 'account_balance', CMMS: 'engineering', GPS: 'satellite_alt', IOT: 'sensors', CUSTOM: 'api' } as Record<string, string>)[cat] ?? 'hub';
  }

  open(c: R): void {
    this.selected.set(c);
    this.api.page<R>(`/integrations/connectors/${c['id']}/jobs`).subscribe((p) => this.jobs.set(p.content));
    this.api.page<R>('/integrations/records', { connectorId: c['id'], status: 'FAILED,CONFLICT' }).subscribe((p) => this.records.set(p.content));
  }

  test(c: R): void {
    this.busy.set('test' + c['id']);
    this.api.post<R>(`/integrations/connectors/${c['id']}/test`).subscribe({
      next: (r) => { this.busy.set(null); r['ok'] ? this.toast.success(`${r['message']} (${r['latencyMs']} ms)`) : this.toast.error(r['message']); },
      error: () => this.busy.set(null),
    });
  }

  run(c: R): void {
    this.busy.set('run' + c['id']);
    this.api.post<R>(`/integrations/connectors/${c['id']}/run`).subscribe({
      next: (j) => { this.busy.set(null); this.toast.info(`${j['status']} · ${j['recordsRead']} / +${j['recordsCreated']} / ~${j['recordsUpdated']} / ✗${j['recordsFailed']}`); this.load(); this.open(c); },
      error: () => this.busy.set(null),
    });
  }

  toggle(c: R): void {
    this.api.post(`/integrations/connectors/${c['id']}/toggle`).subscribe(() => this.load());
  }

  retry(r: R): void {
    this.api.post<R>(`/integrations/records/${r['id']}/retry`).subscribe((x) => {
      x['status'] === 'RETRIED' ? this.toast.success('OK') : this.toast.warning(x['error']);
      this.open(this.selected()!);
    });
  }

  toggleEntity(e: string): void {
    const s = new Set<string>(this.form['entityTypes']);
    if (s.has(e)) s.delete(e);
    else s.add(e);
    this.form['entityTypes'] = [...s];
  }

  create(): void {
    let config: R = {};
    try {
      config = JSON.parse(this.configJson || '{}');
    } catch {
      this.toast.error('Invalid JSON configuration');
      return;
    }
    const credentials = Object.fromEntries(Object.entries(this.creds).filter(([, v]) => v));
    this.api.post<R>('/integrations/connectors', { ...this.form, config, credentials }).subscribe((r) => {
      this.toast.success(`${r['name']} — webhook secret: ${r['webhookSecret']}`);
      this.showWizard.set(false);
      this.form = this.blank();
      this.load();
    });
  }
}
