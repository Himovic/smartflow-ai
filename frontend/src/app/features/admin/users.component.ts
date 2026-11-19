import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { LookupService } from '../../core/lookup.service';
import { ToastService } from '../../core/toast.service';
import { LookupItem } from '../../core/models';
import { TrPipe, EnumPipe } from '../../shared/tr.pipe';

type R = Record<string, any>;

@Component({
  selector: 'sf-users',
  imports: [DatePipe, FormsModule, TrPipe, EnumPipe],
  template: `
  <div class="page">
    <div class="page-header"><div><h1>{{ 'nav.users' | tr }}</h1><div class="subtitle">{{ 'users.subtitle' | tr }}</div></div>
      @if (admin) { <button class="btn primary" (click)="edit(null)"><span class="icon">person_add</span>{{ 'common.new' | tr }}</button> }</div>
    <div class="grid grid-3">
      <div class="card span-2"><div class="toolbar row" style="padding:12px 14px"><div class="search" style="width:280px"><span class="icon">search</span>
        <input class="input" [(ngModel)]="q" (ngModelChange)="load()" [placeholder]="'common.search' | tr" /></div></div>
        <div class="table-wrap"><table class="table"><thead><tr><th>{{ 'fields.name' | tr }}</th><th>Email</th><th>{{ 'users.role' | tr }}</th><th>{{ 'users.scope' | tr }}</th>
          <th>{{ 'users.lastLogin' | tr }}</th><th>{{ 'fields.active' | tr }}</th></tr></thead>
          <tbody>@for (u of users(); track u['id']) { <tr class="clickable" (click)="edit(u)"><td><strong>{{ u['firstName'] }} {{ u['lastName'] }}</strong><div class="muted small">{{ u['jobTitle'] }}</div></td>
            <td>{{ u['email'] }}</td><td><span class="badge navy">{{ u['role'] | enum }}</span></td><td class="small">{{ u['allSites'] ? ('shell.allSites' | tr) : siteNames(u['siteIds']) }}</td>
            <td class="small">{{ u['lastLoginAt'] ? (u['lastLoginAt'] | date: 'dd/MM/yyyy HH:mm') : '—' }}</td>
            <td>@if (u['active']) {<span class="badge green">{{ 'common.yes' | tr }}</span>} @else {<span class="badge red">{{ 'common.no' | tr }}</span>}</td></tr> }</tbody></table></div></div>
      <div class="card"><div class="card-header"><h3>{{ 'users.roles' | tr }}</h3></div>
        <div class="card-body roles">@for (r of roles(); track r['role']) { <div class="role"><strong>{{ r['role'] | enum }}</strong><span class="muted small">{{ r['grants'].join(', ') }}</span></div> }</div></div>
    </div>
    @if (current(); as u) {
      <div class="overlay" (click)="current.set(null)"><div class="dlg" (click)="$event.stopPropagation()">
        <h2>{{ u['id'] ? u['firstName'] + ' ' + u['lastName'] : ('common.new' | tr) }}</h2><div class="divider"></div>
        <div class="form-grid">
          <div class="field"><label>{{ 'fields.firstName' | tr }} *</label><input class="input" [(ngModel)]="u['firstName']" [disabled]="!admin" /></div>
          <div class="field"><label>{{ 'fields.lastName' | tr }} *</label><input class="input" [(ngModel)]="u['lastName']" [disabled]="!admin" /></div>
          <div class="field"><label>Email *</label><input class="input" type="email" [(ngModel)]="u['email']" [disabled]="!admin" /></div>
          <div class="field"><label>{{ 'users.role' | tr }} *</label><select class="input" [(ngModel)]="u['role']" [disabled]="!admin">@for (r of roles(); track r['role']) {<option [value]="r['role']">{{ r['role'] | enum }}</option>}</select></div>
          <div class="field"><label>{{ 'fields.jobTitle' | tr }}</label><input class="input" [(ngModel)]="u['jobTitle']" [disabled]="!admin" /></div>
          <div class="field"><label>{{ 'fields.phone' | tr }}</label><input class="input" [(ngModel)]="u['phone']" [disabled]="!admin" /></div>
          <div class="field full"><label class="checkbox"><input type="checkbox" [(ngModel)]="u['allSites']" [disabled]="!admin" />{{ 'users.allSites' | tr }}</label>
            @if (!u['allSites']) { <div class="row wrap" style="margin-top:6px">@for (s of sites(); track s.id) {
              <label class="checkbox small"><input type="checkbox" [checked]="u['siteIds']?.includes(s.id)" (change)="toggleSite(u, s.id)" [disabled]="!admin" />{{ s.label }}</label> }</div> }</div>
          <div class="field"><label>{{ 'login.password' | tr }} {{ u['id'] ? '' : '*' }}</label><input class="input" type="password" [(ngModel)]="u['password']" autocomplete="new-password" [disabled]="!admin" />
            <span class="hint">{{ 'login.passwordPolicy' | tr }}</span></div>
        </div>
        @if (history().length) { <div class="divider"></div><h3>{{ 'users.loginHistory' | tr }}</h3>
          <table class="table"><tbody>@for (h of history().slice(0, 6); track h['id']) { <tr><td class="small">{{ h['occurredAt'] | date: 'dd/MM/yyyy HH:mm' }}</td>
            <td>@if (h['success']) {<span class="badge green">OK</span>} @else {<span class="badge red">{{ h['reason'] }}</span>}</td><td class="small muted">{{ h['ipAddress'] }}</td></tr> }</tbody></table> }
        <div class="row actions">
          @if (u['id'] && admin) { <button class="btn" (click)="activate(u)"><span class="icon">{{ u['active'] ? 'person_off' : 'person' }}</span>{{ (u['active'] ? 'users.deactivate' : 'users.activate') | tr }}</button> }
          <span class="spacer"></span><button class="btn" (click)="current.set(null)">{{ 'common.close' | tr }}</button>
          @if (admin) { <button class="btn primary" (click)="save(u)"><span class="icon">save</span>{{ 'common.save' | tr }}</button> }
        </div>
      </div></div>
    }
  </div>`,
  styles: [`.roles { display: flex; flex-direction: column; gap: 10px; max-height: 620px; overflow: auto; } .role { display: flex; flex-direction: column; gap: 2px; }
    .overlay { position: fixed; inset: 0; background: rgba(7,20,40,.5); display: grid; place-items: center; z-index: 1000; }`],
})
export class UsersComponent {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  readonly admin = inject(AuthService).can('users', 'admin');
  readonly users = signal<R[]>([]);
  readonly roles = signal<R[]>([]);
  readonly sites = signal<LookupItem[]>([]);
  readonly current = signal<R | null>(null);
  readonly history = signal<R[]>([]);
  q = '';

  constructor() {
    this.load();
    this.api.get<R[]>('/users/roles').subscribe((r) => this.roles.set(r));
    inject(LookupService).list('sites').subscribe((s) => this.sites.set(s));
  }

  load(): void {
    this.api.page<R>('/users', { q: this.q, size: 200 }).subscribe((p) => this.users.set(p.content));
  }

  siteNames(ids: string[]): string {
    return (ids ?? []).map((id) => this.sites().find((s) => s.id === id)?.label ?? '?').join(', ') || '—';
  }

  edit(u: R | null): void {
    this.current.set(u ? { ...u, siteIds: [...(u['siteIds'] ?? [])] } : { role: 'EMPLOYEE', allSites: false, siteIds: [], language: 'fr' });
    this.history.set([]);
    if (u) this.api.get<R[]>(`/users/${u['id']}/login-history`).subscribe((h) => this.history.set(h));
  }

  toggleSite(u: R, id: string): void {
    const s = new Set<string>(u['siteIds']);
    if (s.has(id)) s.delete(id);
    else s.add(id);
    u['siteIds'] = [...s];
  }

  save(u: R): void {
    const call = u['id'] ? this.api.put(`/users/${u['id']}`, u) : this.api.post('/users', u);
    call.subscribe(() => { this.toast.success('OK'); this.current.set(null); this.load(); });
  }

  activate(u: R): void {
    this.api.post(`/users/${u['id']}/activation`, {}, { active: !u['active'] }).subscribe(() => { this.current.set(null); this.load(); });
  }
}
