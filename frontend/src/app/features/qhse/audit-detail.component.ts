import { Component, effect, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Dialog } from '@angular/cdk/dialog';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { TrPipe } from '../../shared/tr.pipe';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import { CollabPanelComponent } from '../../shared/collab-panel.component';
import { EntityFormComponent } from '../../shared/entity-form.component';
import { RESOURCES } from '../resources/resources';
import { ResourceDef, f } from '../resources/resource.model';

const FINDING: ResourceDef = { key: 'audit-findings', endpoint: 'audit-findings', module: 'audits', icon: 'flag', columns: [],
  fields: [f.sel('findingType', ['MAJOR_NC', 'MINOR_NC', 'OBSERVATION', 'OPPORTUNITY', 'CONFORMITY'], true), f.text('clause'), f.area('description', true), f.area('evidence'),
    f.sel('status', ['OPEN', 'IN_PROGRESS', 'CLOSED'])] };

@Component({
  selector: 'sf-audit-detail',
  imports: [DatePipe, RouterLink, TrPipe, StatusBadgeComponent, CollabPanelComponent],
  template: `
  <div class="page">@if (audit(); as a) {
    <div class="page-header"><div><div class="muted small">{{ 'nav.audits' | tr }} · {{ a['reference'] }}</div><h1>{{ a['title'] }}</h1>
      <div class="row wrap" style="margin-top:8px"><sf-badge [value]="a['status']" /><sf-badge [value]="a['auditType']" /><sf-badge [value]="a['standard']" />
        <span class="muted small">{{ a['plannedDate'] | date: 'dd/MM/yyyy' }} · {{ a['leadAuditor'] }}</span></div></div>
      @if (auth.can('audits', 'write')) { <button class="btn" (click)="edit()"><span class="icon">edit</span>{{ 'common.edit' | tr }}</button> }</div>
    <div class="grid grid-3">
      <div class="span-2" style="display:flex;flex-direction:column;gap:16px">
        <div class="card"><div class="card-body"><dl class="kv"><dt>{{ 'fields.scope' | tr }}</dt><dd>{{ a['scope'] }}</dd><dt>{{ 'fields.auditTeam' | tr }}</dt><dd>{{ a['auditTeam'] ?? '—' }}</dd>
          <dt>{{ 'fields.score' | tr }}</dt><dd>{{ a['score'] ?? '—' }}</dd><dt>{{ 'fields.conclusion' | tr }}</dt><dd>{{ a['conclusion'] ?? '—' }}</dd></dl></div></div>
        <div class="card"><div class="card-header"><span class="icon" style="color:var(--cyan-500)">flag</span><h3>{{ 'audit.findings' | tr }}</h3>
          @if (auth.can('audits', 'write')) { <button class="btn sm primary" (click)="addFinding()"><span class="icon">add</span>{{ 'audit.addFinding' | tr }}</button> }</div>
          <div class="table-wrap"><table class="table"><thead><tr><th>{{ 'fields.reference' | tr }}</th><th>{{ 'fields.findingType' | tr }}</th><th>{{ 'fields.clause' | tr }}</th>
            <th>{{ 'fields.description' | tr }}</th><th>NC</th><th>{{ 'fields.status' | tr }}</th></tr></thead>
            <tbody>@for (x of findings(); track x['id']) { <tr><td><strong>{{ x['reference'] }}</strong></td><td><sf-badge [value]="x['findingType']" /></td><td>{{ x['clause'] }}</td>
              <td>{{ x['description'] }}</td><td>@if (x['nonConformityId']) {<a [routerLink]="['/non-conformities', x['nonConformityId']]"><span class="icon">open_in_new</span></a>}</td>
              <td><sf-badge [value]="x['status']" /></td></tr> } @empty { <tr><td colspan="6"><div class="empty">{{ 'common.empty' | tr }}</div></td></tr> }</tbody></table></div>
          <p class="small muted" style="padding:10px 16px">{{ 'audit.ncHint' | tr }}</p></div>
      </div>
      <div><sf-collab entityType="Audit" [entityId]="id()" /></div>
    </div>
  }</div>`,
})
export class AuditDetailComponent {
  readonly id = input.required<string>();
  private api = inject(ApiService);
  readonly auth = inject(AuthService);
  private dialog = inject(Dialog);
  readonly audit = signal<Record<string, any> | null>(null);
  readonly findings = signal<Record<string, any>[]>([]);

  constructor() {
    effect(() => this.load(this.id()));
  }

  load(id: string): void {
    this.api.get<Record<string, any>>(`/audits/${id}`).subscribe((a) => this.audit.set(a));
    this.api.page<Record<string, any>>('/audit-findings', { auditId: id, size: 100, sort: 'reference,asc' }).subscribe((p) => this.findings.set(p.content));
  }

  edit(): void {
    this.dialog.open(EntityFormComponent, { data: { resource: RESOURCES['audits'], entity: this.audit() } }).closed.subscribe((r) => r && this.load(this.id()));
  }

  addFinding(): void {
    this.dialog.open(EntityFormComponent, { data: { resource: FINDING, preset: { auditId: this.id(), status: 'OPEN' } } }).closed.subscribe((r) => r && this.load(this.id()));
  }
}
