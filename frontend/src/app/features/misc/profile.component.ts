import { Component, inject, signal } from '@angular/core';
import { DatePipe, SlicePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { I18nService, Lang } from '../../core/i18n.service';
import { ToastService } from '../../core/toast.service';
import { TrPipe, EnumPipe } from '../../shared/tr.pipe';

type R = Record<string, any>;

@Component({
  selector: 'sf-profile',
  imports: [DatePipe, SlicePipe, FormsModule, TrPipe, EnumPipe],
  template: `
  <div class="page">
    <div class="page-header"><div><h1>{{ 'shell.profile' | tr }}</h1><div class="subtitle">{{ auth.me()?.email }}</div></div></div>
    <div class="grid grid-2">
      <div class="card"><div class="card-body"><dl class="kv"><dt>{{ 'fields.name' | tr }}</dt><dd>{{ auth.me()?.firstName }} {{ auth.me()?.lastName }}</dd>
        <dt>{{ 'users.role' | tr }}</dt><dd><span class="badge navy">{{ auth.me()?.role | enum }}</span></dd><dt>{{ 'fields.jobTitle' | tr }}</dt><dd>{{ auth.me()?.jobTitle }}</dd>
        <dt>{{ 'shell.organization' | tr }}</dt><dd>{{ auth.me()?.tenant?.name }}</dd>
        <dt>{{ 'shell.language' | tr }}</dt><dd><div class="row">@for (l of i18n.languages; track l.code) { <button class="btn sm" [class.primary]="i18n.lang() === l.code" (click)="setLang(l.code)">{{ l.label }}</button> }</div></dd></dl>
        <div class="divider"></div><h3>{{ 'profile.permissions' | tr }}</h3>
        <div class="perms">@for (m of modules(); track m) { <span class="badge outline">{{ ('module.' + m) | tr }}: {{ auth.me()!.permissions[m].join(', ') }}</span> }</div></div></div>
      <div class="card"><div class="card-header"><h3>{{ 'profile.changePassword' | tr }}</h3></div><div class="card-body">
        @if (auth.me()?.demo) { <p class="muted">{{ 'profile.demoPassword' | tr }}</p> } @else {
          <div class="field"><label>{{ 'profile.current' | tr }}</label><input class="input" type="password" [(ngModel)]="cur" /></div>
          <div class="field" style="margin-top:10px"><label>{{ 'login.newPassword' | tr }}</label><input class="input" type="password" [(ngModel)]="next" /><span class="hint">{{ 'login.passwordPolicy' | tr }}</span></div>
          <button class="btn primary" style="margin-top:12px" (click)="change()">{{ 'common.save' | tr }}</button> }
        <div class="divider"></div><h3>{{ 'profile.sessions' | tr }}</h3>
        @for (s of sessions(); track s['id']) { <div class="row sess"><span class="icon">devices</span><div style="flex:1"><div class="small">{{ s['userAgent'] | slice: 0 : 70 }}</div>
          <div class="muted small">{{ s['ipAddress'] }} · {{ s['createdAt'] | date: 'dd/MM/yyyy HH:mm' }}</div></div><button class="btn sm" (click)="revoke(s['id'])">{{ 'profile.revoke' | tr }}</button></div> }
        <div class="divider"></div><h3>{{ 'users.loginHistory' | tr }}</h3>
        @for (h of history().slice(0, 8); track h['id']) { <div class="row small" style="margin:6px 0"><span>{{ h['occurredAt'] | date: 'dd/MM/yyyy HH:mm' }}</span>
          @if (h['success']) {<span class="badge green">OK</span>} @else {<span class="badge red">{{ h['reason'] }}</span>}<span class="muted">{{ h['ipAddress'] }}</span></div> }
      </div></div>
    </div>
  </div>`,
  styles: [`.perms { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; } .sess { margin: 8px 0; }`],
})
export class ProfileComponent {
  readonly auth = inject(AuthService);
  readonly i18n = inject(I18nService);
  private api = inject(ApiService);
  private toast = inject(ToastService);
  readonly sessions = signal<R[]>([]);
  readonly history = signal<R[]>([]);
  readonly modules = () => Object.keys(this.auth.me()?.permissions ?? {});
  cur = '';
  next = '';

  constructor() {
    this.load();
  }

  load(): void {
    this.api.get<R[]>('/auth/me/sessions').subscribe((s) => this.sessions.set(s));
    this.api.get<R[]>('/auth/me/login-history').subscribe((h) => this.history.set(h));
  }

  setLang(l: Lang): void {
    this.i18n.use(l);
  }

  change(): void {
    this.api.put('/auth/me/password', { currentPassword: this.cur, newPassword: this.next }).subscribe(() => { this.toast.success('OK'); this.cur = this.next = ''; });
  }

  revoke(id: string): void {
    this.api.delete(`/auth/me/sessions/${id}`).subscribe(() => this.load());
  }
}
