import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { TrPipe } from '../../shared/tr.pipe';
import { RESOURCES } from '../resources/resources';

interface Dataset { resource: string; dateField?: string; icon: string; }

/** Report center: filtered Excel / PDF exports of every register and printable case reports. */
@Component({
  selector: 'sf-reports',
  imports: [FormsModule, RouterLink, TrPipe],
  template: `
  <div class="page">
    <div class="page-header"><div><h1>{{ 'nav.reports' | tr }}</h1><div class="subtitle">{{ 'reports.subtitle' | tr }}</div></div>
      <div class="row"><input class="input" type="date" [(ngModel)]="from" /><input class="input" type="date" [(ngModel)]="to" /></div></div>
    <div class="sets">
      @for (d of datasets; track d.resource) { @if (auth.can(def(d.resource).module)) {
        <div class="card set"><span class="icon">{{ d.icon }}</span><div style="flex:1"><strong>{{ ('res.' + d.resource) | tr }}</strong>
          <div class="muted small">@if (d.dateField) { {{ ('fields.' + d.dateField) | tr }} @if (from || to) {· {{ from || '…' }} → {{ to || '…' }}} } @else { {{ 'reports.full' | tr }} }</div></div>
          <button class="btn sm" (click)="exp(d, 'xlsx')"><span class="icon">table_view</span>Excel</button>
          <button class="btn sm" (click)="exp(d, 'pdf')"><span class="icon">picture_as_pdf</span>PDF</button></div>
      } }
    </div>
    <div class="card" style="margin-top:16px"><div class="card-header"><h3>{{ 'reports.caseReports' | tr }}</h3></div><div class="card-body">
      <p class="muted">{{ 'reports.caseHint' | tr }}</p>
      <div class="row wrap" style="margin-top:10px"><a class="btn" routerLink="/non-conformities">{{ 'nav.nonConformities' | tr }}</a><a class="btn" routerLink="/incidents">{{ 'nav.incidents' | tr }}</a>
        <a class="btn" routerLink="/inspections">{{ 'nav.inspections' | tr }}</a><a class="btn" routerLink="/traceability">{{ 'trace.impactReport' | tr }}</a></div></div></div>
  </div>`,
  styles: [`.sets { display: grid; grid-template-columns: repeat(auto-fill, minmax(380px, 1fr)); gap: 12px; } .set { display: flex; gap: 12px; align-items: center; padding: 14px 16px; }
    .set > .icon { color: var(--cyan-500); font-size: 26px; }`],
})
export class ReportsComponent {
  private api = inject(ApiService);
  readonly auth = inject(AuthService);
  from = '';
  to = '';
  readonly datasets: Dataset[] = [
    { resource: 'non-conformities', dateField: 'detectedAt', icon: 'report' }, { resource: 'capa', dateField: 'dueDate', icon: 'task_alt' },
    { resource: 'incidents', dateField: 'occurredAt', icon: 'emergency' }, { resource: 'inspections', dateField: 'scheduledDate', icon: 'fact_check' },
    { resource: 'audits', dateField: 'plannedDate', icon: 'policy' }, { resource: 'risk-assessments', icon: 'grid_on' },
    { resource: 'orders', dateField: 'orderDate', icon: 'receipt_long' }, { resource: 'deliveries', dateField: 'plannedDate', icon: 'conveyor_belt' },
    { resource: 'stock-levels', icon: 'inventory_2' }, { resource: 'batches', dateField: 'productionDate', icon: 'qr_code_2' },
    { resource: 'waste-records', dateField: 'recordedDate', icon: 'delete_sweep' }, { resource: 'environmental-readings', dateField: 'recordedAt', icon: 'sensors' },
    { resource: 'training-records', dateField: 'sessionDate', icon: 'school' }, { resource: 'certifications', icon: 'verified' },
    { resource: 'equipment', icon: 'precision_manufacturing' }, { resource: 'maintenance-work-orders', dateField: 'plannedDate', icon: 'build' },
    { resource: 'fuel-logs', dateField: 'logDate', icon: 'local_gas_station' }, { resource: 'suppliers', icon: 'handshake' },
  ];

  def(r: string) {
    return RESOURCES[r];
  }

  exp(d: Dataset, format: string): void {
    const p: Record<string, string> = { format };
    if (d.dateField && this.from) p[d.dateField + '_from'] = this.from;
    if (d.dateField && this.to) p[d.dateField + '_to'] = d.dateField.endsWith('At') ? this.to + 'T23:59:59Z' : this.to;
    this.api.download(`/${RESOURCES[d.resource].endpoint}/export`, p, `${d.resource}.${format}`);
  }
}
