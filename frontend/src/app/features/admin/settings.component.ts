import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ToastService } from '../../core/toast.service';
import { TrPipe } from '../../shared/tr.pipe';
import { ResourceListComponent } from '../resources/resource-list.component';

type R = Record<string, any>;

/** Tenant configuration: industry profile, enabled modules, terminology, reference lists, emission factors and LLM provider. */
@Component({
  selector: 'sf-settings',
  imports: [FormsModule, TrPipe, ResourceListComponent],
  template: `
  <div class="page">
    <div class="page-header"><div><h1>{{ 'nav.settings' | tr }}</h1><div class="subtitle">{{ cfg()?.['name'] }} · {{ cfg()?.['industryProfile'] }}</div></div>
      @if (admin) { <button class="btn primary" (click)="save()"><span class="icon">save</span>{{ 'common.save' | tr }}</button> }</div>
    <div class="tabs">@for (t of ['profile','modules','terminology','lists','ai','fields','kpis','rules']; track t) {
      <button class="tab" [class.active]="tab() === t" (click)="tab.set(t)">{{ ('settings.' + t) | tr }}</button> }</div>
    @if (cfg(); as c) {
      @switch (tab()) {
        @case ('profile') {
          <div class="card"><div class="card-body">
            <div class="form-grid"><div class="field"><label>{{ 'fields.name' | tr }}</label><input class="input" [(ngModel)]="c['name']" /></div>
              <div class="field"><label>{{ 'settings.defaultLanguage' | tr }}</label><select class="input" [(ngModel)]="c['defaultLanguage']"><option value="fr">Français</option><option value="en">English</option><option value="ar">العربية</option></select></div></div>
            <div class="divider"></div><h3>{{ 'settings.industryProfiles' | tr }}</h3><p class="muted small">{{ 'settings.profileHint' | tr }}</p>
            <div class="profiles">@for (p of profiles(); track p['code']) {
              <div class="card prof" [class.on]="p['code'] === c['industryProfile']"><strong>{{ p['label'] }}</strong><p class="muted small">{{ p['description'] }}</p>
                <span class="small muted">{{ p['settings']['enabledModules'].length }} modules · {{ p['inspectionTemplates'].length }} checklists</span>
                @if (admin && p['code'] !== c['industryProfile']) { <button class="btn sm" (click)="apply(p['code'])">{{ 'settings.apply' | tr }}</button> }
                @if (p['code'] === c['industryProfile']) { <span class="badge green">{{ 'settings.active' | tr }}</span> }</div> }</div>
          </div></div>
        }
        @case ('modules') {
          <div class="card"><div class="card-body"><p class="muted small" style="margin-bottom:12px">{{ 'settings.modulesHint' | tr }}</p>
            <div class="mods">@for (m of optional(); track m) { <label class="mod" [class.on]="c['settings']['enabledModules'].includes(m)">
              <input type="checkbox" [checked]="c['settings']['enabledModules'].includes(m)" (change)="toggleModule(m)" [disabled]="!admin" />
              <strong>{{ ('module.' + m) | tr }}</strong><span class="muted small">{{ m }}</span></label> }</div></div></div>
        }
        @case ('terminology') {
          <div class="card"><div class="card-body"><p class="muted small">{{ 'settings.terminologyHint' | tr }}</p>
            @for (k of termKeys(c); track k) { <div class="row term"><code>{{ k }}</code><input class="input" [(ngModel)]="c['settings']['terminology'][k]" /></div> }
            <div class="row term"><input class="input" [(ngModel)]="newTerm" placeholder="key (e.g. batch)" /><button class="btn sm" (click)="addTerm(c)"><span class="icon">add</span></button></div></div></div>
        }
        @case ('lists') {
          <div class="grid grid-2">@for (l of ['riskCategories','hazardCategories','qualityCriteria','ncTypes','incidentTypes','environmentalIndicators','referenceStandards','operationalUnits']; track l) {
            <div class="card"><div class="card-header"><h3>{{ ('settings.' + l) | tr }}</h3></div><div class="card-body">
              <textarea class="input" rows="4" [ngModel]="(c['settings'][l] ?? []).join('\\n')" (ngModelChange)="c['settings'][l] = lines($event)"></textarea></div></div> }
            <div class="card"><div class="card-header"><h3>{{ 'settings.emissionFactors' | tr }}</h3></div><div class="card-body">
              @for (k of keys(c['settings']['emissionFactors']); track k) { <div class="row term"><code>{{ k }}</code><input class="input" type="number" step="0.01" [(ngModel)]="c['settings']['emissionFactors'][k]" /></div> }</div></div>
            <div class="card"><div class="card-header"><h3>QR</h3></div><div class="card-body"><label class="checkbox"><input type="checkbox" [(ngModel)]="c['settings']['publicQrAccess']" />{{ 'settings.publicQr' | tr }}</label></div></div>
          </div>
        }
        @case ('ai') {
          <div class="card"><div class="card-body"><p class="muted small">{{ 'settings.llmHint' | tr }}</p>
            <div class="form-grid" style="margin-top:12px">
              <div class="field"><label>{{ 'settings.llmProvider' | tr }}</label><select class="input" [(ngModel)]="c['settings']['llm']['provider']">
                @for (p of ['NONE','OPENAI','OPENAI_COMPATIBLE','OLLAMA']; track p) {<option [value]="p">{{ p }}</option>}</select></div>
              <div class="field"><label>{{ 'settings.llmModel' | tr }}</label><input class="input" [(ngModel)]="c['settings']['llm']['model']" placeholder="gpt-4o-mini / llama3.1" /></div>
              <div class="field full"><label>Base URL</label><input class="input" [(ngModel)]="c['settings']['llm']['baseUrl']" placeholder="https://api.openai.com/v1 · http://ollama:11434" /></div>
              <div class="field full"><label>API key @if (c['settings']['llm']['apiKeyConfigured']) {<span class="badge green">{{ 'settings.configured' | tr }}</span>}</label>
                <input class="input" type="password" [(ngModel)]="llmKey" autocomplete="new-password" [placeholder]="'settings.keyHint' | tr" /></div>
            </div></div></div>
        }
        @case ('fields') { <sf-resource-list resource="custom-fields" [embedded]="true" /> }
        @case ('kpis') { <sf-resource-list resource="kpi-definitions" [embedded]="true" /> }
        @case ('rules') { <sf-resource-list resource="notification-rules" [embedded]="true" /> }
      }
    }
  </div>`,
  styles: [`.profiles { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 12px; margin-top: 12px; }
    .prof { padding: 14px; display: flex; flex-direction: column; gap: 6px; } .prof.on { border-color: var(--teal-500); box-shadow: 0 0 0 3px rgba(20,184,166,.15); }
    .mods { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 10px; }
    .mod { display: grid; grid-template-columns: auto 1fr; grid-template-rows: auto auto; gap: 0 10px; padding: 12px; border: 1px solid var(--border); border-radius: 10px; cursor: pointer; }
    .mod input { grid-row: span 2; } .mod.on { border-color: var(--cyan-500); background: #f2fbfd; }
    .term { margin: 8px 0; } .term code { min-width: 160px; }`],
})
export class SettingsComponent {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  readonly admin = inject(AuthService).can('tenant', 'admin');
  readonly tab = signal('profile');
  readonly cfg = signal<R | null>(null);
  readonly profiles = signal<R[]>([]);
  readonly optional = signal<string[]>([]);
  llmKey = '';
  newTerm = '';

  constructor() {
    this.load();
    this.api.get<R[]>('/tenant/industry-profiles').subscribe((p) => this.profiles.set(p));
    this.api.get<R>('/tenant/modules').subscribe((m) => this.optional.set(m['optional']));
  }

  load(): void {
    this.api.get<R>('/tenant/config').subscribe((c) => this.cfg.set(c));
  }

  keys(o: R): string[] { return Object.keys(o ?? {}); }
  termKeys(c: R): string[] { return Object.keys(c['settings']['terminology'] ?? {}); }
  lines(v: string): string[] { return v.split('\n').map((s) => s.trim()).filter(Boolean); }

  addTerm(c: R): void {
    if (!this.newTerm.trim()) return;
    c['settings']['terminology'][this.newTerm.trim()] = '';
    this.newTerm = '';
  }

  toggleModule(m: string): void {
    const c = this.cfg()!;
    const s = new Set<string>(c['settings']['enabledModules']);
    if (s.has(m)) s.delete(m);
    else s.add(m);
    c['settings']['enabledModules'] = [...s];
  }

  save(): void {
    const c = this.cfg()!;
    this.api.put<R>('/tenant/config', { name: c['name'], defaultLanguage: c['defaultLanguage'], settings: c['settings'], llmApiKey: this.llmKey || null }).subscribe((r) => {
      this.cfg.set(r);
      this.llmKey = '';
      this.toast.success('OK — reload to apply navigation changes');
    });
  }

  apply(code: string): void {
    this.api.post<R>(`/tenant/config/apply-profile/${code}`).subscribe((r) => { this.cfg.set(r); this.toast.success(code); });
  }
}
