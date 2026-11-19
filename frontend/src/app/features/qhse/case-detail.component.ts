import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { Dialog } from '@angular/cdk/dialog';
import { forkJoin } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { LookupService } from '../../core/lookup.service';
import { RESOURCES } from '../resources/resources';
import { TrPipe } from '../../shared/tr.pipe';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import { WorkflowPanelComponent } from '../../shared/workflow-panel.component';
import { CollabPanelComponent } from '../../shared/collab-panel.component';
import { EntityFormComponent } from '../../shared/entity-form.component';

interface CaseConfig {
  entityType: string;
  icon: string;
  badges: string[];
  fields: string[];
  textBlocks: string[];
  lookups: Record<string, string>;
  capa?: boolean;
  report?: boolean;
  newVersion?: boolean;
}

const CASES: Record<string, CaseConfig> = {
  'non-conformities': { entityType: 'NonConformity', icon: 'report', badges: ['severity', 'type', 'category'], capa: true, report: true,
    fields: ['siteId', 'departmentId', 'productionLineId', 'productId', 'batchId', 'supplierId', 'source', 'detectedAt', 'reportedByName', 'assigneeName', 'dueDate', 'quantityAffected', 'closedAt'],
    textBlocks: ['description', 'immediateAction', 'rootCause'],
    lookups: { siteId: 'sites', departmentId: 'departments', productionLineId: 'production-lines', productId: 'products', batchId: 'batches', supplierId: 'suppliers' } },
  capa: { entityType: 'CapaAction', icon: 'task_alt', badges: ['type', 'priority', 'sourceType'],
    fields: ['siteId', 'ownerName', 'dueDate', 'completedAt', 'verifiedAt', 'verifiedBy', 'effectivenessResult'],
    textBlocks: ['description', 'evidenceNote', 'effectivenessComment'], lookups: { siteId: 'sites' } },
  incidents: { entityType: 'Incident', icon: 'emergency', badges: ['domain', 'type', 'envCategory'], capa: true, report: true,
    fields: ['siteId', 'departmentId', 'locationDetail', 'equipmentId', 'occurredAt', 'reportedByName', 'severity', 'probability', 'riskScore', 'injuredPersons', 'lostTimeDays', 'investigatorName', 'dueDate', 'closedAt'],
    textBlocks: ['description', 'immediateActions', 'impactAssessment', 'investigationFindings', 'rootCause'],
    lookups: { siteId: 'sites', departmentId: 'departments', equipmentId: 'equipment' } },
  documents: { entityType: 'QhseDocument', icon: 'folder_open', badges: ['docType', 'domain', 'accessLevel'], newVersion: true,
    fields: ['docVersion', 'ownerName', 'approverName', 'approvedAt', 'effectiveDate', 'reviewDate', 'expiryDate', 'standardClause', 'siteId'],
    textBlocks: ['description'], lookups: { siteId: 'sites' } },
};

/** Case file page shared by non-conformities, CAPA, incidents and controlled documents. */
@Component({
  selector: 'sf-case-detail',
  imports: [RouterLink, DatePipe, TrPipe, StatusBadgeComponent, WorkflowPanelComponent, CollabPanelComponent],
  template: `
  <div class="page">
    @if (row(); as r) {
      <div class="page-header">
        <div class="row" style="align-items:flex-start">
          <div class="title-icon"><span class="icon">{{ cfg().icon }}</span></div>
          <div>
            <div class="muted small">{{ ('res.' + resource()) | tr }} · {{ r['reference'] }}</div>
            <h1>{{ r['title'] }}</h1>
            <div class="row wrap" style="margin-top:8px">
              <sf-badge [value]="r['status']" />
              @for (b of cfg().badges; track b) { @if (r[b]) { <sf-badge [value]="r[b]" /> } }
              @if (r['sourceType'] && r['sourceId']) { <a class="small" [routerLink]="sourceLink(r)">{{ 'case.viewSource' | tr }}</a> }
            </div>
          </div>
        </div>
        <div class="row">
          @if (cfg().report) { <button class="btn" (click)="pdf()"><span class="icon">picture_as_pdf</span>{{ 'case.report' | tr }}</button> }
          @if (cfg().newVersion && auth.can('documents', 'write')) { <button class="btn" (click)="newVersion()"><span class="icon">file_copy</span>{{ 'case.newVersion' | tr }}</button> }
          @if (canEdit()) { <button class="btn" (click)="edit()"><span class="icon">edit</span>{{ 'common.edit' | tr }}</button> }
        </div>
      </div>
      <div class="grid grid-3">
        <div class="span-2" style="display:flex;flex-direction:column;gap:16px">
          <sf-workflow [entityType]="cfg().entityType" [entityId]="id()" (changed)="load()" />
          <div class="card"><div class="card-header"><h3>{{ 'case.details' | tr }}</h3></div>
            <div class="card-body">
              <dl class="kv">
                @for (f of cfg().fields; track f) {
                  <dt>{{ ('fields.' + f) | tr }}</dt>
                  <dd>
                    @if (cfg().lookups[f]) { {{ names()[f] ?? '—' }} }
                    @else if (isDate(r[f])) { {{ r[f] | date: (String(r[f]).length > 10 ? 'dd/MM/yyyy HH:mm' : 'dd/MM/yyyy') }} }
                    @else { {{ r[f] ?? '—' }} }
                  </dd>
                }
              </dl>
              @for (b of cfg().textBlocks; track b) {
                @if (r[b]) { <div class="divider"></div><h3 class="blk">{{ ('fields.' + b) | tr }}</h3><p class="txt">{{ r[b] }}</p> }
              }
              @if (r['fiveWhys']?.length) {
                <div class="divider"></div><h3 class="blk">5 {{ 'workflow.why' | tr }}</h3>
                <ol class="whys">@for (w of r['fiveWhys']; track $index) { <li>{{ w }}</li> }</ol>
              }
            </div>
          </div>
          @if (cfg().capa) {
            <div class="card"><div class="card-header"><span class="icon" style="color:var(--cyan-500)">task_alt</span><h3>{{ 'nav.capa' | tr }}</h3>
              @if (auth.can('capa', 'write')) { <button class="btn sm primary" (click)="addCapa()"><span class="icon">add</span>{{ 'case.addCapa' | tr }}</button> }</div>
              <div class="table-wrap"><table class="table"><thead><tr><th>{{ 'fields.reference' | tr }}</th><th>{{ 'fields.title' | tr }}</th><th>{{ 'fields.type' | tr }}</th>
                <th>{{ 'fields.ownerName' | tr }}</th><th>{{ 'fields.dueDate' | tr }}</th><th>{{ 'fields.status' | tr }}</th></tr></thead>
                <tbody>@for (c of capas(); track c['id']) {
                  <tr class="clickable" [routerLink]="['/capa', c['id']]"><td><strong>{{ c['reference'] }}</strong></td><td>{{ c['title'] }}</td><td><sf-badge [value]="c['type']" /></td>
                    <td>{{ c['ownerName'] }}</td><td>{{ c['dueDate'] | date: 'dd/MM/yyyy' }}</td><td><sf-badge [value]="c['status']" /></td></tr>
                } @empty { <tr><td colspan="6"><div class="empty">{{ 'case.noCapa' | tr }}</div></td></tr> }</tbody></table></div>
            </div>
          }
        </div>
        <div><sf-collab [entityType]="cfg().entityType" [entityId]="id()" /></div>
      </div>
    } @else { <div class="page"><div class="card skeleton" style="height:300px"></div></div> }
  </div>`,
  styles: [`.title-icon { width: 46px; height: 46px; border-radius: 12px; display: grid; place-items: center; background: linear-gradient(135deg, var(--navy-800), var(--cyan-500)); color: #fff; flex-shrink: 0; }
    .blk { font-size: 13px; color: var(--text-2); margin-bottom: 6px; } .txt { white-space: pre-wrap; line-height: 1.55; } .whys { margin: 0; padding-inline-start: 20px; line-height: 1.7; }`],
})
export class CaseDetailComponent {
  readonly resource = input.required<string>();
  readonly id = input.required<string>();
  private api = inject(ApiService);
  readonly auth = inject(AuthService);
  private lookups = inject(LookupService);
  private dialog = inject(Dialog);
  private router = inject(Router);
  readonly row = signal<Record<string, any> | null>(null);
  readonly capas = signal<Record<string, any>[]>([]);
  readonly names = signal<Record<string, string>>({});
  readonly cfg = computed(() => CASES[this.resource()]);
  readonly canEdit = computed(() => this.auth.can(RESOURCES[this.resource()].module, 'write') && !['CLOSED', 'OBSOLETE'].includes(this.row()?.['status']));
  readonly String = String;

  constructor() {
    effect(() => {
      this.resource();
      this.id();
      this.load();
    });
  }

  load(): void {
    this.api.get<Record<string, any>>(`/${RESOURCES[this.resource()].endpoint}/${this.id()}`).subscribe((r) => {
      this.row.set(r);
      const entries = Object.entries(this.cfg().lookups).filter(([k]) => r[k]);
      if (entries.length) {
        forkJoin(Object.fromEntries(entries.map(([k, l]) => [k, this.lookups.map(l)]))).subscribe((maps) => {
          const n: Record<string, string> = {};
          Object.entries(maps).forEach(([k, m]) => (n[k] = (m as Map<string, string>).get(r[k]) ?? '—'));
          this.names.set(n);
        });
      }
    });
    if (this.cfg().capa) this.api.get<Record<string, any>[]>(`/${this.resource()}/${this.id()}/capa`).subscribe((c) => this.capas.set(c));
  }

  isDate(v: unknown): boolean {
    return typeof v === 'string' && /^\d{4}-\d{2}-\d{2}/.test(v);
  }

  sourceLink(r: Record<string, any>): string {
    return r['sourceType'] === 'INCIDENT' ? `/incidents/${r['sourceId']}` : `/non-conformities/${r['sourceId']}`;
  }

  edit(): void {
    this.dialog.open(EntityFormComponent, { data: { resource: RESOURCES[this.resource()], entity: this.row() } }).closed.subscribe((r) => r && this.load());
  }

  addCapa(): void {
    this.dialog.open(EntityFormComponent, { data: { resource: RESOURCES['capa'], endpoint: `${this.resource()}/${this.id()}/capa`, preset: { siteId: this.row()?.['siteId'] } } })
      .closed.subscribe((r) => r && this.load());
  }

  pdf(): void {
    this.api.download(`/${this.resource()}/${this.id()}/report`, undefined, `${this.row()?.['reference']}.pdf`);
  }

  newVersion(): void {
    this.api.post<{ id: string }>(`/documents/${this.id()}/new-version`).subscribe((d) => this.router.navigateByUrl(`/documents/${d.id}`));
  }
}
