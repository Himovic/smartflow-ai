import { Component, computed, inject, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { Kpi } from '../core/models';
import { TrPipe } from './tr.pipe';
import { I18nService } from '../core/i18n.service';

@Component({
  selector: 'sf-kpi',
  imports: [DecimalPipe, TrPipe],
  template: `
    <div class="kpi card fade-in" [class.alert]="k().onTarget === false">
      <div class="top">
        <span class="label">{{ ('kpi.' + k().code) | tr }}</span>
        <span class="nature" [class]="'nature ' + k().nature" [title]="('nature.' + k().nature) | tr">{{ ('nature.short.' + k().nature) | tr }}</span>
      </div>
      <div class="value">{{ k().value | number: fmt() : i18n.locale }}<small>{{ k().unit }}</small></div>
      <div class="foot">
        @if (delta() !== null) {
          <span class="delta" [class.good]="good()" [class.bad]="!good()">
            <span class="icon">{{ (delta() ?? 0) >= 0 ? 'trending_up' : 'trending_down' }}</span>{{ delta() | number: '1.0-1' }}%
          </span>
          <span class="muted">{{ 'kpi.vsPrevious' | tr }}</span>
        }
        @if (k().target !== undefined && k().target !== null) {
          <span class="target" [class.ok]="k().onTarget">{{ 'kpi.target' | tr }} {{ k().target }}{{ k().unit }}</span>
        }
      </div>
    </div>`,
  styles: [`
    .kpi { padding: 14px 16px; display: flex; flex-direction: column; gap: 6px; min-height: 112px; position: relative; overflow: hidden; }
    .kpi::before { content: ''; position: absolute; inset-inline-start: 0; top: 0; bottom: 0; width: 3px; background: linear-gradient(var(--cyan-500), var(--teal-500)); }
    .kpi.alert::before { background: var(--amber); }
    .top { display: flex; justify-content: space-between; gap: 8px; align-items: flex-start; }
    .label { font-size: 12.5px; color: var(--muted); font-weight: 500; }
    .value { font-size: 26px; font-weight: 700; letter-spacing: -0.02em; color: var(--navy-900); }
    .value small { font-size: 13px; font-weight: 500; color: var(--muted); margin-inline-start: 4px; }
    .foot { display: flex; gap: 8px; align-items: center; font-size: 12px; flex-wrap: wrap; }
    .delta { display: inline-flex; align-items: center; gap: 2px; font-weight: 600; }
    .delta .icon { font-size: 16px; }
    .delta.good { color: var(--green); } .delta.bad { color: var(--red); }
    .target { margin-inline-start: auto; color: var(--muted); }
    .target.ok { color: var(--green); }
  `],
})
export class KpiCardComponent {
  readonly i18n = inject(I18nService);
  readonly k = input.required<Kpi>();
  readonly lowerIsBetter = new Set(['NC_OPEN', 'CRITICAL_INCIDENTS', 'CAPA_OVERDUE', 'LTIFR', 'SAFETY_INCIDENTS', 'CO2_TOTAL', 'CO2_TRANSPORT',
    'CO2_PER_DELIVERY', 'DISTRIBUTION_COST', 'CRITICAL_ALERTS', 'AVG_DELAY', 'FUEL_LITERS']);
  readonly fmt = computed(() => (Math.abs(this.k().value) >= 100 ? '1.0-0' : '1.0-1'));
  readonly delta = computed(() => {
    const p = this.k().previous;
    if (p === null || p === undefined || p === 0) return null;
    return ((this.k().value - p) / Math.abs(p)) * 100;
  });
  readonly good = computed(() => {
    const d = this.delta() ?? 0;
    return this.lowerIsBetter.has(this.k().code) ? d <= 0 : d >= 0;
  });
}
