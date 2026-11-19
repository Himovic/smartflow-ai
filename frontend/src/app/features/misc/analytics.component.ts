import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { ContextService } from '../../core/context.service';
import { I18nService } from '../../core/i18n.service';
import { TrPipe } from '../../shared/tr.pipe';
import { ChartComponent } from '../../shared/chart.component';
import { donut, hbar, stackedBars } from '../dashboards/charts';

type R = Record<string, any>;

/** Interactive analytics with period / site filters; clicking a chart element drills down to the filtered records. */
@Component({
  selector: 'sf-analytics',
  imports: [FormsModule, TrPipe, ChartComponent],
  template: `
  <div class="page">
    <div class="page-header"><div><h1>{{ 'nav.analytics' | tr }}</h1><div class="subtitle">{{ 'analytics.subtitle' | tr }}</div></div>
      <div class="row"><input class="input" type="date" [ngModel]="from()" (ngModelChange)="from.set($event)" /><input class="input" type="date" [ngModel]="to()" (ngModelChange)="to.set($event)" />
        <button class="btn" (click)="exportAll()"><span class="icon">table_view</span>Excel</button></div></div>
    @if (q(); as q) {
      <div class="grid grid-2">
        <div class="card"><div class="card-header"><h3>{{ 'dash.ncByType' | tr }}</h3><span class="muted small">{{ 'analytics.clickHint' | tr }}</span></div>
          <div class="card-body"><sf-chart [options]="ncType()!" (pick)="drill('/non-conformities', 'type', $event)" /></div></div>
        <div class="card"><div class="card-header"><h3>{{ 'dash.ncPipeline' | tr }}</h3></div>
          <div class="card-body"><sf-chart [options]="ncStatus()!" (pick)="drill('/non-conformities', 'status', $event)" /></div></div>
        <div class="card"><div class="card-header"><h3>{{ 'dash.incidentsByType' | tr }}</h3></div>
          <div class="card-body"><sf-chart [options]="incType()!" (pick)="drill('/incidents', 'type', $event)" /></div></div>
        <div class="card"><div class="card-header"><h3>{{ 'dash.riskDistribution' | tr }}</h3></div>
          <div class="card-body"><sf-chart [options]="risk()!" (pick)="drill('/risk-assessments', 'riskLevel', $event)" /></div></div>
        <div class="card span-2"><div class="card-header"><h3>{{ 'dash.ncTrendBySeverity' | tr }}</h3></div>
          <div class="card-body"><sf-chart [options]="ncTrend()!" (pick)="drill('/non-conformities', 'severity', $event, true)" /></div></div>
        <div class="card"><div class="card-header"><h3>{{ 'dash.ordersByStatus' | tr }}</h3></div>
          <div class="card-body"><sf-chart [options]="orders()!" (pick)="drill('/orders', 'status', $event)" /></div></div>
        <div class="card"><div class="card-header"><h3>{{ 'dash.fleetStatus' | tr }}</h3></div>
          <div class="card-body"><sf-chart [options]="fleet()!" (pick)="drill('/fleet', 'status', $event)" /></div></div>
      </div>
    }
  </div>`,
})
export class AnalyticsComponent {
  private api = inject(ApiService);
  private ctx = inject(ContextService);
  private router = inject(Router);
  private i18n = inject(I18nService);
  readonly from = signal('');
  readonly to = signal('');
  readonly q = signal<R | null>(null);
  readonly d = signal<R | null>(null);
  private codes = new Map<string, string>();

  private tr(list: { name: string; value: number }[]): { name: string; value: number }[] {
    return list.map((x) => {
      const t = this.i18n.t('enum.' + x.name);
      const label = t === 'enum.' + x.name ? x.name : t;
      this.codes.set(label, x.name);
      return { ...x, name: label };
    });
  }

  readonly ncType = computed(() => this.q() && donut(this.tr(this.q()!['ncByType'])));
  readonly ncStatus = computed(() => this.q() && hbar(this.tr(this.q()!['ncByStatus']), '#1a3a68'));
  readonly incType = computed(() => this.q() && hbar(this.tr(this.q()!['incidentsByType']), '#e5484d'));
  readonly risk = computed(() => this.q() && donut(this.tr(this.q()!['riskDistribution']), ['#f59e0b', '#e5484d', '#16a34a', '#00a8cc']));
  readonly ncTrend = computed(() => {
    const t = this.q()?.['ncBySeverityTrend'];
    if (!t) return null;
    ['CRITICAL', 'MAJOR', 'MINOR'].forEach((s) => this.codes.set(this.i18n.t('enum.' + s), s));
    return stackedBars({ [this.i18n.t('enum.CRITICAL')]: t['CRITICAL'], [this.i18n.t('enum.MAJOR')]: t['MAJOR'], [this.i18n.t('enum.MINOR')]: t['MINOR'] }, ['#e5484d', '#f59e0b', '#00a8cc']);
  });
  readonly orders = computed(() => this.d() && donut(this.tr(this.d()!['ordersByStatus'])));
  readonly fleet = computed(() => this.d() && donut(this.tr(this.d()!['fleetStatus']), ['#16a34a', '#00a8cc', '#f59e0b', '#e5484d']));

  constructor() {
    effect(() => {
      const p = { siteId: this.ctx.siteId(), from: this.from(), to: this.to() };
      untracked(() => forkJoin({ q: this.api.get<R>('/analytics/dashboard/qhse', p), d: this.api.get<R>('/analytics/dashboard/distribution', p) })
        .subscribe(({ q, d }) => { this.q.set(q); this.d.set(d); }));
    });
  }

  drill(path: string, field: string, e: { name?: string; seriesName?: string }, bySeries = false): void {
    const label = (bySeries ? e.seriesName : e.name) ?? '';
    const code = this.codes.get(label) ?? label;
    this.router.navigate([path], { queryParams: { [field]: code } });
  }

  exportAll(): void {
    this.api.download('/non-conformities/export', { format: 'xlsx', detectedAt_from: this.from() || undefined, detectedAt_to: this.to() ? this.to() + 'T23:59:59Z' : undefined });
  }
}
