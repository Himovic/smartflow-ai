import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';
import { ApiService } from '../../core/api.service';
import { ContextService } from '../../core/context.service';
import { I18nService } from '../../core/i18n.service';
import { Kpi } from '../../core/models';
import { KpiCardComponent } from '../../shared/kpi-card.component';
import { ChartComponent, baseOption } from '../../shared/chart.component';
import { TrPipe, EnumPipe } from '../../shared/tr.pipe';
import { donut, lineChart, Series, stackedBars, hbar } from './charts';

interface QhseData {
  kpis: Kpi[];
  riskDistribution: { name: string; value: number }[];
  ncBySeverityTrend: Record<string, Series>;
  ncByType: { name: string; value: number }[];
  ncByStatus: { name: string; value: number }[];
  incidentsByType: { name: string; value: number }[];
  safetyTrend: Record<string, Series>;
  inspectionsByDomain: { domain: string; total: number; passRate: number }[];
  environment: { months: string[]; electricityKwh: number[]; gasM3: number[]; waterM3: number[]; wasteKg: number[]; recycledKg: number[] };
  upcomingInspections: { id: string; reference: string; title: string; domain: string; scheduled_date: string; inspector_name: string }[];
}

@Component({
  selector: 'sf-qhse-dashboard',
  imports: [RouterLink, DatePipe, KpiCardComponent, ChartComponent, TrPipe, EnumPipe],
  template: `
  <div class="page">
    <div class="page-header">
      <div><h1>{{ 'nav.qhseDashboard' | tr }}</h1><div class="subtitle">{{ 'dash.qhseSubtitle' | tr }}</div></div>
      <div class="row"><a class="btn" routerLink="/non-conformities"><span class="icon">report</span>{{ 'nav.nonConformities' | tr }}</a>
        <a class="btn primary" routerLink="/incidents"><span class="icon">add_alert</span>{{ 'dash.reportIncident' | tr }}</a></div>
    </div>
    @if (d(); as d) {
      <div class="kpi-grid">@for (k of d.kpis; track k.code) { <sf-kpi [k]="k" /> }</div>
      <div class="grid grid-3" style="margin-top:16px">
        <div class="card span-2"><div class="card-header"><h3>{{ 'dash.ncTrendBySeverity' | tr }}</h3><span class="nature MEASURED">{{ 'nature.short.MEASURED' | tr }}</span></div>
          <div class="card-body"><sf-chart [options]="ncTrend()!" /></div></div>
        <div class="card"><div class="card-header"><h3>{{ 'dash.riskDistribution' | tr }}</h3><a class="btn sm ghost" routerLink="/risk-assessments">{{ 'common.details' | tr }}</a></div>
          <div class="card-body"><sf-chart [options]="riskDonut()!" /></div></div>
        <div class="card span-2"><div class="card-header"><h3>{{ 'dash.safetyTrend' | tr }}</h3></div>
          <div class="card-body"><sf-chart [options]="safetyChart()!" /></div></div>
        <div class="card"><div class="card-header"><h3>{{ 'dash.ncByType' | tr }}</h3></div>
          <div class="card-body"><sf-chart [options]="ncTypeDonut()!" /></div></div>
        <div class="card"><div class="card-header"><h3>{{ 'dash.inspectionCompliance' | tr }}</h3></div>
          <div class="card-body"><sf-chart [options]="inspectionBars()!" /></div></div>
        <div class="card"><div class="card-header"><h3>{{ 'dash.ncPipeline' | tr }}</h3></div>
          <div class="card-body"><sf-chart [options]="ncPipeline()!" /></div></div>
        <div class="card"><div class="card-header"><h3>{{ 'dash.upcomingInspections' | tr }}</h3><a class="btn sm ghost" routerLink="/inspections">{{ 'common.viewAll' | tr }}</a></div>
          <div>@for (i of d.upcomingInspections; track i.id) {
            <a class="up-row" [routerLink]="['/inspections', i.id]"><span class="badge">{{ i.domain | enum }}</span><div style="flex:1"><strong>{{ i.title }}</strong>
              <div class="muted small">{{ i.reference }} · {{ i.inspector_name }}</div></div><span class="small">{{ i.scheduled_date | date: 'dd/MM' }}</span></a>
          }</div></div>
        <div class="card span-3"><div class="card-header"><h3>{{ 'dash.environmentalIndicators' | tr }}</h3><span class="nature MEASURED">{{ 'nature.short.MEASURED' | tr }}</span>
          <a class="btn sm ghost" routerLink="/environment">{{ 'common.details' | tr }}</a></div>
          <div class="card-body"><sf-chart [options]="envChart()!" /></div></div>
      </div>
    } @else { <div class="kpi-grid">@for (i of [1,2,3,4,5,6,7,8]; track i) { <div class="card skeleton" style="height:112px"></div> }</div> }
  </div>`,
  styles: [`.up-row { display: flex; gap: 10px; padding: 10px 16px; border-bottom: 1px solid var(--border); align-items: center; color: inherit; text-decoration: none !important; }
    .up-row:hover { background: var(--surface-2); }`],
})
export class QhseDashboardComponent {
  private api = inject(ApiService);
  private ctx = inject(ContextService);
  private i18n = inject(I18nService);
  readonly d = signal<QhseData | null>(null);

  readonly ncTrend = computed(() => this.d() && stackedBars({ [this.e('CRITICAL')]: this.d()!.ncBySeverityTrend['CRITICAL'], [this.e('MAJOR')]: this.d()!.ncBySeverityTrend['MAJOR'],
    [this.e('MINOR')]: this.d()!.ncBySeverityTrend['MINOR'] }, ['#e5484d', '#f59e0b', '#00a8cc']));
  readonly riskDonut = computed(() => this.d() && donut(this.d()!.riskDistribution.map((x) => ({ ...x, name: this.e(x.name) })), ['#f59e0b', '#e5484d', '#16a34a', '#00a8cc']));
  readonly ncTypeDonut = computed(() => this.d() && donut(this.d()!.ncByType.map((x) => ({ ...x, name: this.e(x.name) }))));
  readonly ncPipeline = computed(() => this.d() && hbar(this.d()!.ncByStatus.map((x) => ({ ...x, name: this.e(x.name) })), '#1a3a68'));
  readonly safetyChart = computed(() => this.d() && lineChart({ [this.i18n.t('dash.accidents')]: this.d()!.safetyTrend['accidents'],
    [this.i18n.t('dash.nearMisses')]: this.d()!.safetyTrend['nearMisses'], [this.i18n.t('dash.environmental')]: this.d()!.safetyTrend['environmental'] },
    { bar: [this.i18n.t('dash.nearMisses')], colors: ['#e5484d', '#14b8a6', '#7c3aed'] }));
  readonly inspectionBars = computed(() => this.d() && {
    ...baseOption(), legend: { show: false },
    xAxis: { type: 'category', data: this.d()!.inspectionsByDomain.map((x) => this.e(x.domain)) },
    yAxis: { type: 'value', max: 100, axisLabel: { formatter: '{value}%' } },
    series: [{ type: 'bar', data: this.d()!.inspectionsByDomain.map((x) => ({ value: x.passRate, itemStyle: { color: x.passRate >= 90 ? '#16a34a' : x.passRate >= 80 ? '#f59e0b' : '#e5484d', borderRadius: [4, 4, 0, 0] } })), barMaxWidth: 34,
      label: { show: true, position: 'top', formatter: '{c}%' } }],
  });
  readonly envChart = computed(() => {
    const e = this.d()?.environment;
    if (!e) return null;
    return {
      ...baseOption(), color: ['#1a3a68', '#00a8cc', '#14b8a6', '#f59e0b'],
      xAxis: { type: 'category', data: e.months },
      yAxis: [{ type: 'value', name: 'kWh', splitLine: { lineStyle: { color: '#eef2f7' } } }, { type: 'value', name: 'kg / m³', splitLine: { show: false } }],
      series: [
        { name: this.i18n.t('dash.electricity'), type: 'bar', data: e.electricityKwh, barMaxWidth: 16 },
        { name: this.i18n.t('dash.water'), type: 'line', yAxisIndex: 1, smooth: true, data: e.waterM3 },
        { name: this.i18n.t('dash.recycled'), type: 'line', yAxisIndex: 1, smooth: true, data: e.recycledKg },
        { name: this.i18n.t('dash.waste'), type: 'line', yAxisIndex: 1, smooth: true, data: e.wasteKg },
      ],
    };
  });

  constructor() {
    effect(() => {
      this.ctx.siteId();
      untracked(() => this.api.get<QhseData>('/analytics/dashboard/qhse', { siteId: this.ctx.siteId() }).subscribe((d) => this.d.set(d)));
    });
  }

  private e(v: string): string {
    const k = 'enum.' + v;
    const t = this.i18n.t(k);
    return t === k ? v : t;
  }
}
