import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Dialog } from '@angular/cdk/dialog';
import { forkJoin, of, Subject, debounceTime } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { LookupService } from '../../core/lookup.service';
import { ContextService } from '../../core/context.service';
import { I18nService } from '../../core/i18n.service';
import { LookupItem, Page } from '../../core/models';
import { RESOURCES } from './resources';
import { ColumnDef, ResourceDef } from './resource.model';
import { TrPipe, EnumPipe } from '../../shared/tr.pipe';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import { EntityFormComponent } from '../../shared/entity-form.component';
import { ConfirmDialogComponent } from '../../shared/dialogs';
import { QrDialogComponent } from '../../shared/qr-dialog.component';

type Row = Record<string, any>;

/** Generic, metadata-driven list screen: search, filters, sorting, pagination, CRUD, export and QR codes. */
@Component({
  selector: 'sf-resource-list',
  imports: [FormsModule, DatePipe, DecimalPipe, TrPipe, EnumPipe, StatusBadgeComponent],
  templateUrl: './resource-list.component.html',
  styles: [`
    .toolbar { display: flex; gap: 10px; align-items: center; padding: 12px 14px; border-bottom: 1px solid var(--border); flex-wrap: wrap; }
    .toolbar .search { width: 280px; }
    .toolbar select { width: auto; min-width: 150px; height: 34px; }
    .score { display: inline-flex; align-items: center; gap: 8px; }
    .bar { width: 52px; height: 6px; background: var(--border); border-radius: 4px; overflow: hidden; }
    .bar i { display: block; height: 100%; background: linear-gradient(90deg, var(--cyan-500), var(--teal-500)); }
    .bar.low i { background: var(--red); } .bar.mid i { background: var(--amber); }
    .actions-cell { white-space: nowrap; text-align: end; }
    .skel-row td { padding: 14px; }
    .title-icon { width: 42px; height: 42px; border-radius: 12px; display: grid; place-items: center; background: linear-gradient(135deg, var(--navy-800), var(--cyan-500)); color: #fff; }
  `],
})
export class ResourceListComponent {
  readonly resource = input.required<string>();
  readonly preset = input<Record<string, string>>({});
  readonly embedded = input(false);
  readonly title = input<string | null>(null);

  private api = inject(ApiService);
  readonly auth = inject(AuthService);
  private lookups = inject(LookupService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private dialog = inject(Dialog);
  private ctx = inject(ContextService);
  readonly i18n = inject(I18nService);

  readonly def = computed<ResourceDef>(() => RESOURCES[this.resource()]);
  readonly rows = signal<Row[]>([]);
  readonly total = signal(0);
  readonly loading = signal(true);
  readonly page = signal(0);
  readonly size = signal(20);
  readonly sort = signal<string | null>(null);
  readonly q = signal('');
  readonly filters = signal<Record<string, string>>({});
  readonly maps = signal<Record<string, Map<string, string>>>({});
  readonly filterOptions = signal<Record<string, LookupItem[]>>({});
  readonly canWrite = computed(() => this.auth.can(this.def().module, 'write') && !this.def().readOnly);
  readonly canDelete = computed(() => this.auth.can(this.def().module, 'delete') && !this.def().noDelete && !this.def().readOnly);
  readonly pages = computed(() => Math.max(1, Math.ceil(this.total() / this.size())));
  private search$ = new Subject<string>();

  constructor() {
    effect(() => {
      const d = this.def();
      untracked(() => {
        const qp = this.embedded() ? {} : this.route.snapshot.queryParams;
        this.filters.set(Object.fromEntries(Object.entries(qp).filter(([k]) => k !== 'q')) as Record<string, string>);
        if (qp['q']) this.q.set(qp['q']);
        this.page.set(0);
        this.sort.set(d.sort ?? null);
        this.loadLookups(d);
      });
    });
    effect(() => {
      this.def(); this.page(); this.sort(); this.filters(); this.ctx.siteId(); this.preset();
      untracked(() => this.load());
    });
    this.search$.pipe(debounceTime(300), takeUntilDestroyed()).subscribe(() => {
      this.page.set(0);
      this.load();
    });
  }

  private hasSite(d: ResourceDef): boolean {
    return d.columns.some((c) => c.key === 'siteId') || d.fields.some((f) => f.key === 'siteId');
  }

  params(): Record<string, string | number> {
    const p: Record<string, string | number> = { page: this.page(), size: this.size(), ...this.filters(), ...this.preset() };
    if (this.q()) p['q'] = this.q();
    if (this.sort()) p['sort'] = this.sort()!;
    if (this.ctx.siteId() && this.hasSite(this.def()) && !p['siteId']) p['siteId'] = this.ctx.siteId()!;
    return p;
  }

  load(): void {
    this.loading.set(true);
    this.api.page<Row>('/' + this.def().endpoint, this.params()).subscribe({
      next: (p: Page<Row>) => {
        this.rows.set(p.content);
        this.total.set(p.totalElements);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  private loadLookups(d: ResourceDef): void {
    const needed = new Set<string>();
    d.columns.filter((c) => c.lookup).forEach((c) => needed.add(c.lookup!));
    d.filters?.filter((f) => f.lookup).forEach((f) => needed.add(f.lookup!));
    if (!needed.size) return;
    forkJoin(Object.fromEntries([...needed].map((l) => [l, this.lookups.list(l)]))).subscribe((res) => {
      const maps: Record<string, Map<string, string>> = {};
      Object.entries(res).forEach(([k, v]) => (maps[k] = new Map((v as LookupItem[]).map((i) => [i.id, i.label]))));
      this.maps.set(maps);
      this.filterOptions.set(res as Record<string, LookupItem[]>);
    });
  }

  onSearch(v: string): void {
    this.q.set(v);
    this.search$.next(v);
  }

  setFilter(key: string, value: string): void {
    const f = { ...this.filters() };
    if (value) f[key] = value;
    else delete f[key];
    this.filters.set(f);
    this.page.set(0);
  }

  toggleSort(col: ColumnDef): void {
    const cur = this.sort();
    this.sort.set(cur === `${col.key},asc` ? `${col.key},desc` : `${col.key},asc`);
  }

  sortIcon(col: ColumnDef): string {
    const s = this.sort();
    if (s === `${col.key},asc`) return 'arrow_upward';
    if (s === `${col.key},desc`) return 'arrow_downward';
    return '';
  }

  label(col: ColumnDef, row: Row): string {
    const v = row[col.key];
    if (v === null || v === undefined) return '—';
    return this.maps()[col.lookup!]?.get(v) ?? '…';
  }

  open(row: Row): void {
    const d = this.def();
    if (d.detail) this.router.navigateByUrl(d.detail + row['id']);
    else if (this.canWrite()) this.edit(row);
  }

  create(): void {
    this.dialog.open(EntityFormComponent, { data: { resource: this.def(), preset: this.preset() } }).closed.subscribe((r) => {
      if (!r) return;
      const d = this.def();
      if (d.detail && (r as Row)['id']) this.router.navigateByUrl(d.detail + (r as Row)['id']);
      else this.load();
    });
  }

  edit(row: Row, ev?: Event): void {
    ev?.stopPropagation();
    this.dialog.open(EntityFormComponent, { data: { resource: this.def(), entity: row } }).closed.subscribe((r) => r && this.load());
  }

  remove(row: Row, ev: Event): void {
    ev.stopPropagation();
    this.dialog.open(ConfirmDialogComponent, { data: { title: 'common.deleteConfirmTitle', message: 'common.deleteConfirm', danger: true, confirmLabel: 'common.delete' } })
      .closed.subscribe((r) => {
        if ((r as { confirmed?: boolean })?.confirmed) this.api.delete(`/${this.def().endpoint}/${row['id']}`).subscribe(() => this.load());
      });
  }

  qr(row: Row, ev: Event): void {
    ev.stopPropagation();
    const d = this.def();
    const label = row['batchNumber'] ?? row['reference'] ?? row['code'] ?? row['sku'] ?? row['plateNumber'] ?? row['name'];
    this.dialog.open(QrDialogComponent, { data: { type: d.qr, id: row['id'], label } });
  }

  export(format: 'xlsx' | 'pdf'): void {
    const p: Record<string, string | number> = { ...this.params(), format };
    delete p['page'];
    delete p['size'];
    this.api.download(`/${this.def().endpoint}/export`, p, `${this.def().key}.${format}`);
  }

  scoreClass(v: number): string {
    return v < 70 ? 'low' : v < 85 ? 'mid' : '';
  }
}
