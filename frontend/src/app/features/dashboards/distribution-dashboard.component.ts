import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DecimalPipe } from '@angular/common';
import { ApiService } from '../../core/api.service';
import { ContextService } from '../../core/context.service';
import { I18nService } from '../../core/i18n.service';
import { Kpi } from '../../core/models';
import { KpiCardComponent } from '../../shared/kpi-card.component';
import { ChartComponent, baseOption } from '../../shared/chart.component';
import { MapComponent, MapMarker } from '../../shared/map.component';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import { TrPipe } from '../../shared/tr.pipe';
import { donut, hbar, lineChart, Series } from './charts';

interface DistData {
  kpis: Kpi[];
  ordersByStatus: { name: string; value: number }[];
  deliveriesDaily: { day: string; planned: number; delivered: number; late: number }[];
  fleetStatus: { name: string; value: number }[];
  stockByWarehouse: { name: string; value: number }[];
  stockoutRisks: { productId: string; sku: string; name: string; freeStock: number; forecastWeekly: number; daysOfCover: number; stockoutRisk: string; recommendedReplenishment: number }[];
  routeOptimization: { plans: number; km_saved: number; original_km: number; optimized_km: number; co2: number };
  fuelTrend: Series; co2Trend: Series;
  vehicles: { id: string; plate_number: string; type: string; status: string; current_latitude: number; current_longitude: number }[];
}

@Component({
  selector: 'sf-distribution-dashboard',
  imports: [RouterLink, DecimalPipe, KpiCardComponent, ChartComponent, MapComponent, StatusBadgeComponent, TrPipe],
  template: `
  <div class="page">
    <div class="page-header">
      <div><h1>{{ 'nav.distributionDashboard' | tr }}</h1><div class="subtitle">{{ 'dash.distSubtitle' | tr }}</div></div>
      <div class="row"><a class="btn" routerLink="/orders"><span class="icon">receipt_long</span>{{ 'nav.orders' | tr }}</a>
        <a class="btn accent" routerLink="/route-planning"><span class="icon">route</span>{{ 'dash.optimizeRoutes' | tr }}</a></div>
    </div>
    @if (d(); as d) {
      <div class="kpi-grid">@for (k of d.kpis; track k.code) { <sf-kpi [k]="k" /> }</div>
      <div class="grid grid-3" style="margin-top:16px">
        <div class="card span-2"><div class="card-header"><h3>{{ 'dash.deliveries30' | tr }}</h3><span class="nature MEASURED">{{ 'nature.short.MEASURED' | tr }}</span></div>
          <div class="card-body"><sf-chart [options]="deliveriesChart()!" /></div></div>
        <div class="card"><div class="card-header"><h3>{{ 'dash.routeSavings' | tr }}</h3><span class="nature PREDICTION">{{ 'nature.short.PREDICTION' | tr }}</span></div>
          <div class="card-body savings">
            <div class="big">{{ d.routeOptimization.km_saved | number: '1.0-0' }} <small>km</small></div>
            <div class="muted">{{ 'dash.kmSaved90' | tr }} ({{ d.routeOptimization.plans }} {{ 'dash.plans' | tr }})</div>
            <div class="bar2"><i [style.width.%]="d.routeOptimization.original_km ? (d.routeOptimization.optimized_km / d.routeOptimization.original_km * 100) : 0"></i></div>
            <div class="row small muted"><span>{{ 'dash.optimized' | tr }} {{ d.routeOptimization.optimized_km | number: '1.0-0' }} km</span><span class="spacer"></span>
              <span>{{ 'dash.manual' | tr }} {{ d.routeOptimization.original_km | number: '1.0-0' }} km</span></div>
          </div></div>
        <div class="card span-2"><div class="card-header"><h3>{{ 'dash.fleetLive' | tr }}</h3><span class="badge sim-flag">GPS {{ 'demo.simulated' | tr }}</span></div>
          <div class="card-body"><sf-map [markers]="fleetMarkers()" [height]="320" /></div></div>
        <div class="card"><div class="card-header"><h3>{{ 'dash.fleetStatus' | tr }}</h3></div><div class="card-body"><sf-chart [options]="fleetDonut()!" /></div></div>
        <div class="card span-2"><div class="card-header"><h3>{{ 'dash.stockoutRisks' | tr }}</h3><span class="nature PREDICTION">{{ 'nature.short.PREDICTION' | tr }}</span>
          <a class="btn sm ghost" routerLink="/ai">{{ 'nav.aiCenter' | tr }}</a></div>
          <div class="table-wrap"><table class="table"><thead><tr><th>SKU</th><th>{{ 'fields.name' | tr }}</th><th class="num">{{ 'dash.freeStock' | tr }}</th>
            <th class="num">{{ 'dash.weeklyForecast' | tr }}</th><th class="num">{{ 'dash.daysOfCover' | tr }}</th><th>{{ 'dash.risk' | tr }}</th><th class="num">{{ 'dash.replenish' | tr }}</th></tr></thead>
            <tbody>@for (r of d.stockoutRisks; track r.productId) {
              <tr><td><strong>{{ r.sku }}</strong></td><td>{{ r.name }}</td><td class="num">{{ r.freeStock }}</td><td class="num">{{ r.forecastWeekly }}</td>
                <td class="num">{{ r.daysOfCover }}</td><td><sf-badge [value]="r.stockoutRisk" /></td><td class="num">{{ r.recommendedReplenishment }}</td></tr>
            } @empty { <tr><td colspan="7"><div class="empty">{{ 'dash.noStockout' | tr }}</div></td></tr> }</tbody></table></div></div>
        <div class="card"><div class="card-header"><h3>{{ 'dash.stockByWarehouse' | tr }}</h3></div><div class="card-body"><sf-chart [options]="stockBars()!" /></div></div>
        <div class="card span-2"><div class="card-header"><h3>{{ 'dash.fuelCo2' | tr }}</h3></div><div class="card-body"><sf-chart [options]="fuelChart()!" /></div></div>
        <div class="card"><div class="card-header"><h3>{{ 'dash.ordersByStatus' | tr }}</h3></div><div class="card-body"><sf-chart [options]="ordersDonut()!" /></div></div>
      </div>
    } @else { <div class="kpi-grid">@for (i of [1,2,3,4,5,6,7,8]; track i) { <div class="card skeleton" style="height:112px"></div> }</div> }
  </div>`,
  styles: [`.savings { display: flex; flex-direction: column; gap: 10px; } .big { font-size: 38px; font-weight: 700; color: var(--teal-500); } .big small { font-size: 16px; color: var(--muted); }
    .bar2 { height: 10px; border-radius: 6px; background: var(--border); overflow: hidden; } .bar2 i { display: block; height: 100%; background: linear-gradient(90deg, var(--cyan-500), var(--teal-500)); }`],
})
export class DistributionDashboardComponent {
  private api = inject(ApiService);
  private ctx = inject(ContextService);
  private i18n = inject(I18nService);
  readonly d = signal<DistData | null>(null);
  private e = (v: string) => { const t = this.i18n.t('enum.' + v); return t === 'enum.' + v ? v : t; };

  readonly deliveriesChart = computed(() => this.d() && {
    ...baseOption(), color: ['#cfd8e5', '#14b8a6', '#e5484d'],
    xAxis: { type: 'category', data: this.d()!.deliveriesDaily.map((x) => x.day.substring(5)) }, yAxis: { type: 'value', splitLine: { lineStyle: { color: '#eef2f7' } } },
    series: [
      { name: this.i18n.t('dash.planned'), type: 'bar', data: this.d()!.deliveriesDaily.map((x) => x.planned), barGap: '-100%', barMaxWidth: 14 },
      { name: this.i18n.t('dash.delivered'), type: 'bar', data: this.d()!.deliveriesDaily.map((x) => x.delivered), barMaxWidth: 14 },
      { name: this.i18n.t('dash.late'), type: 'line', smooth: true, data: this.d()!.deliveriesDaily.map((x) => x.late) },
    ],
  });
  readonly fleetDonut = computed(() => this.d() && donut(this.d()!.fleetStatus.map((x) => ({ ...x, name: this.e(x.name) })), ['#16a34a', '#00a8cc', '#f59e0b', '#e5484d']));
  readonly ordersDonut = computed(() => this.d() && donut(this.d()!.ordersByStatus.map((x) => ({ ...x, name: this.e(x.name) }))));
  readonly stockBars = computed(() => this.d() && hbar(this.d()!.stockByWarehouse, '#1a3a68'));
  readonly fuelChart = computed(() => this.d() && lineChart({ [this.i18n.t('dash.fuelL')]: this.d()!.fuelTrend, [this.i18n.t('dash.co2Kg')]: this.d()!.co2Trend },
    { bar: [this.i18n.t('dash.fuelL')], colors: ['#1a3a68', '#14b8a6'] }));
  readonly fleetMarkers = computed<MapMarker[]>(() => (this.d()?.vehicles ?? []).map((v) => ({ lat: v.current_latitude, lng: v.current_longitude, size: 8,
    color: v.status === 'ON_ROUTE' ? '#00a8cc' : v.status === 'AVAILABLE' ? '#16a34a' : '#f59e0b', label: `${v.plate_number} · ${this.e(v.status)}` })));

  constructor() {
    effect(() => {
      this.ctx.siteId();
      untracked(() => this.api.get<DistData>('/analytics/dashboard/distribution', { siteId: this.ctx.siteId() }).subscribe((d) => this.d.set(d)));
    });
  }
}
