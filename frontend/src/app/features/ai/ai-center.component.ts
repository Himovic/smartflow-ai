import { Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { LookupService } from '../../core/lookup.service';
import { I18nService } from '../../core/i18n.service';
import { LookupItem } from '../../core/models';
import { TrPipe } from '../../shared/tr.pipe';
import { ChartComponent, baseOption } from '../../shared/chart.component';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import { hbar } from '../dashboards/charts';

type R = Record<string, any>;

/** AI Center: demand forecasting, predictive quality, predictive maintenance and QHSE intelligence. */
@Component({
  selector: 'sf-ai-center',
  imports: [DecimalPipe, FormsModule, RouterLink, TrPipe, ChartComponent, StatusBadgeComponent],
  templateUrl: './ai-center.component.html',
  styles: [`.status { display: flex; gap: 10px; align-items: center; padding: 12px 16px; } .status .icon { color: var(--cyan-500); }
    .fc-controls { display: flex; gap: 10px; align-items: flex-end; flex-wrap: wrap; } .fc-controls .field { min-width: 260px; }
    .metric { display: flex; flex-direction: column; gap: 2px; padding: 10px 14px; border: 1px solid var(--border); border-radius: 10px; background: var(--surface-2); }
    .metric span { font-size: 11.5px; color: var(--muted); } .metric strong { font-size: 18px; }
    .reasons { font-size: 12px; color: var(--muted); }
    .flag { display: flex; gap: 10px; padding: 10px 14px; background: var(--amber-bg); border-radius: 10px; margin-bottom: 8px; align-items: center; }`],
})
export class AiCenterComponent {
  private api = inject(ApiService);
  private i18n = inject(I18nService);
  readonly tab = signal<'forecast' | 'quality' | 'maintenance' | 'qhse'>('forecast');
  readonly status = signal<R | null>(null);
  readonly products = signal<LookupItem[]>([]);
  readonly overview = signal<R[]>([]);
  readonly fc = signal<R | null>(null);
  readonly quality = signal<R | null>(null);
  readonly maintenance = signal<R | null>(null);
  readonly qhse = signal<R | null>(null);
  readonly loading = signal(false);
  productId: string | null = null;
  horizon = 12;

  readonly fcChart = computed(() => {
    const f = this.fc();
    if (!f) return null;
    const hist = (f['history'] as R[]).slice(-52);
    const fut = f['forecast'] as R[];
    const labels = [...hist.map((h) => h['period']), ...fut.map((p) => p['period'])];
    const pad = (n: number) => Array(n).fill(null);
    return {
      ...baseOption(), color: ['#1a3a68', '#7c3aed', '#c4b5fd', '#c4b5fd'],
      legend: { top: 0, data: [this.i18n.t('ai.actual'), this.i18n.t('ai.forecast'), this.i18n.t('ai.interval')] },
      xAxis: { type: 'category', data: labels, axisLabel: { color: '#6b7a90' } },
      yAxis: { type: 'value', splitLine: { lineStyle: { color: '#eef2f7' } } },
      series: [
        { name: this.i18n.t('ai.actual'), type: 'line', smooth: true, symbol: 'none', data: [...hist.map((h) => h['value']), ...pad(fut.length)], areaStyle: { opacity: .08 } },
        { name: this.i18n.t('ai.forecast'), type: 'line', smooth: true, lineStyle: { type: 'dashed', width: 2.5 }, data: [...pad(hist.length - 1), hist[hist.length - 1]?.['value'], ...fut.map((p) => p['value'])] },
        { name: this.i18n.t('ai.lower'), type: 'line', stack: 'band', symbol: 'none', lineStyle: { opacity: 0 }, data: [...pad(hist.length), ...fut.map((p) => p['lower'])] },
        { name: this.i18n.t('ai.interval'), type: 'line', stack: 'band', symbol: 'none', lineStyle: { opacity: 0 }, areaStyle: { color: 'rgba(124,58,237,.15)' },
          data: [...pad(hist.length), ...fut.map((p) => p['upper'] - p['lower'])] },
      ],
    };
  });
  readonly supplierChart = computed(() => this.quality() && hbar((this.quality()!['supplierPerformance'] as R[]).map((s) => ({ name: s['name'], value: s['performanceIndex'] })), '#14b8a6'));
  readonly lineChart = computed(() => this.quality() && hbar((this.quality()!['lineDefectRates'] as R[]).map((s) => ({ name: s['code'] + ' ' + s['name'], value: s['ncPer100Batches'] })), '#e5484d'));
  readonly eqChart = computed(() => this.maintenance() && hbar((this.maintenance()!['equipment'] as R[]).slice(0, 12).map((s) => ({ name: s['name'], value: s['riskScore'] })), '#f59e0b'));

  constructor() {
    this.api.get<R>('/ai/status').subscribe((s) => this.status.set(s));
    inject(LookupService).list('products').subscribe((p) => {
      this.products.set(p);
      this.productId = p[0]?.id ?? null;
      this.runForecast();
    });
    this.api.get<R[]>('/ai/forecast/overview').subscribe((o) => this.overview.set(o));
  }

  select(t: 'forecast' | 'quality' | 'maintenance' | 'qhse'): void {
    this.tab.set(t);
    if (t === 'quality' && !this.quality()) this.api.get<R>('/ai/quality').subscribe((q) => this.quality.set(q));
    if (t === 'maintenance' && !this.maintenance()) this.api.get<R>('/ai/maintenance').subscribe((q) => this.maintenance.set(q));
    if (t === 'qhse' && !this.qhse()) this.api.get<R>('/ai/qhse').subscribe((q) => this.qhse.set(q));
  }

  runForecast(): void {
    if (!this.productId) return;
    this.loading.set(true);
    this.api.get<R>(`/ai/forecast/${this.productId}`, { horizon: this.horizon }).subscribe({ next: (f) => { this.fc.set(f); this.loading.set(false); }, error: () => this.loading.set(false) });
  }

  pick(id: string): void {
    this.productId = id;
    this.runForecast();
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }
}
