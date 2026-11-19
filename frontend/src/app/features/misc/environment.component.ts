import { Component, computed, inject, signal } from '@angular/core';
import { ApiService } from '../../core/api.service';
import { I18nService } from '../../core/i18n.service';
import { TrPipe } from '../../shared/tr.pipe';
import { ChartComponent, baseOption } from '../../shared/chart.component';
import { ResourceListComponent } from '../resources/resource-list.component';
import { Kpi } from '../../core/models';
import { KpiCardComponent } from '../../shared/kpi-card.component';

type R = Record<string, any>;

/** Environmental management: energy, water, waste and recycling, CO2 and chemical products (ISO 14001 / ESG). */
@Component({
  selector: 'sf-environment',
  imports: [TrPipe, ChartComponent, ResourceListComponent, KpiCardComponent],
  template: `
  <div class="page">
    <div class="page-header"><div class="row"><div class="title-icon"><span class="icon">eco</span></div><div><h1>{{ 'nav.environment' | tr }}</h1>
      <div class="subtitle">{{ 'env.subtitle' | tr }}</div></div></div></div>
    <div class="kpi-grid">@for (k of kpis(); track k.code) { <sf-kpi [k]="k" /> }</div>
    <div class="grid grid-2" style="margin-top:16px">
      <div class="card"><div class="card-header"><h3>{{ 'env.energy' | tr }}</h3><span class="nature MEASURED">{{ 'nature.short.MEASURED' | tr }}</span></div><div class="card-body">@if (energy()) {<sf-chart [options]="energy()!" />}</div></div>
      <div class="card"><div class="card-header"><h3>{{ 'env.waste' | tr }}</h3><span class="nature MEASURED">{{ 'nature.short.MEASURED' | tr }}</span></div><div class="card-body">@if (waste()) {<sf-chart [options]="waste()!" />}</div></div>
    </div>
    <div class="tabs" style="margin-top:20px">@for (t of tabs; track t.r; let i = $index) { <button class="tab" [class.active]="tab() === i" (click)="tab.set(i)">{{ t.l | tr }}</button> }</div>
    @for (t of tabs; track t.r; let i = $index) { @if (tab() === i) { <sf-resource-list [resource]="t.r" [preset]="t.p" [embedded]="true" /> } }
  </div>`,
  styles: [`.title-icon { width: 42px; height: 42px; border-radius: 12px; display: grid; place-items: center; background: linear-gradient(135deg, #0f766e, var(--teal-500)); color: #fff; }`],
})
export class EnvironmentComponent {
  private api = inject(ApiService);
  private i18n = inject(I18nService);
  readonly data = signal<R | null>(null);
  readonly kpis = signal<Kpi[]>([]);
  readonly tab = signal(0);
  readonly tabs: { r: string; l: string; p: Record<string, string> }[] = [
    { r: 'environmental-readings', l: 'env.readings', p: {} }, { r: 'waste-records', l: 'env.wasteRegister', p: {} },
    { r: 'chemical-products', l: 'env.chemicals', p: {} }, { r: 'incidents', l: 'env.incidents', p: { domain: 'ENVIRONMENT' } },
    { r: 'inspections', l: 'env.inspections', p: { domain: 'ENVIRONMENT' } },
  ];
  readonly energy = computed(() => {
    const e = this.data()?.['environment'];
    if (!e) return null;
    return { ...baseOption(), color: ['#1a3a68', '#f59e0b', '#00a8cc'], xAxis: { type: 'category', data: e['months'] },
      yAxis: [{ type: 'value', name: 'kWh' }, { type: 'value', name: 'm³', splitLine: { show: false } }],
      series: [{ name: this.i18n.t('dash.electricity'), type: 'bar', data: e['electricityKwh'], barMaxWidth: 16 },
        { name: this.i18n.t('env.gas'), type: 'line', yAxisIndex: 1, smooth: true, data: e['gasM3'] },
        { name: this.i18n.t('dash.water'), type: 'line', yAxisIndex: 1, smooth: true, data: e['waterM3'] }] };
  });
  readonly waste = computed(() => {
    const e = this.data()?.['environment'];
    if (!e) return null;
    const rate = (e['wasteKg'] as number[]).map((w, i) => (w ? Math.round((e['recycledKg'][i] / w) * 1000) / 10 : 0));
    return { ...baseOption(), color: ['#cfd8e5', '#14b8a6', '#0b1f3a'], xAxis: { type: 'category', data: e['months'] },
      yAxis: [{ type: 'value', name: 'kg' }, { type: 'value', name: '%', max: 100, splitLine: { show: false } }],
      series: [{ name: this.i18n.t('dash.waste'), type: 'bar', data: e['wasteKg'], barMaxWidth: 16 }, { name: this.i18n.t('dash.recycled'), type: 'bar', data: e['recycledKg'], barMaxWidth: 16 },
        { name: this.i18n.t('kpi.RECYCLING_RATE'), type: 'line', yAxisIndex: 1, smooth: true, data: rate }] };
  });

  constructor() {
    this.api.get<R>('/analytics/dashboard/qhse').subscribe((d) => this.data.set(d));
    this.api.get<R>('/analytics/dashboard/executive').subscribe((d) => this.kpis.set((d['kpis'] as Kpi[]).filter((k) => ['CO2_TOTAL', 'RECYCLING_RATE'].includes(k.code))));
    this.api.get<R>('/analytics/dashboard/distribution').subscribe((d) => this.kpis.update((k) => [...k, ...(d['kpis'] as Kpi[]).filter((x) => ['CO2_TRANSPORT', 'CO2_PER_DELIVERY', 'FUEL_LITERS'].includes(x.code))]));
  }
}
