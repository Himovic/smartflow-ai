import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ContextService } from '../../core/context.service';
import { Alert, Kpi } from '../../core/models';
import { KpiCardComponent } from '../../shared/kpi-card.component';
import { ChartComponent } from '../../shared/chart.component';
import { MapComponent, MapMarker } from '../../shared/map.component';
import { TrPipe, EnumPipe } from '../../shared/tr.pipe';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import { gauge, lineChart, Series } from './charts';
import { I18nService } from '../../core/i18n.service';

interface ExecData {
  kpis: Kpi[];
  operationalRisk: { score: number; level: string; formula: string; components: Record<string, number> };
  productionTrend: Series; revenueTrend: Series; ncTrend: Series; incidentTrend: Series;
  alerts: Alert[];
  sites: { id: string; name: string; type: string; city: string; latitude: number; longitude: number; openNc: number; openIncidents: number; employees: number }[];
}

@Component({
  selector: 'sf-executive-dashboard',
  imports: [RouterLink, FormsModule, KpiCardComponent, ChartComponent, MapComponent, TrPipe, EnumPipe, StatusBadgeComponent],
  templateUrl: './executive-dashboard.component.html',
  styles: [`
    .risk { display: flex; flex-direction: column; } .risk .formula { font-size: 11.5px; color: var(--muted); padding: 0 16px 14px; }
    .alert-row { display: flex; gap: 10px; padding: 10px 16px; border-bottom: 1px solid var(--border); align-items: flex-start; text-decoration: none !important; color: inherit; }
    .alert-row:hover { background: var(--surface-2); }
    .alert-row .icon { margin-top: 1px; }
    .sev-CRITICAL { color: var(--red); } .sev-WARNING { color: var(--amber); } .sev-INFO { color: var(--blue-500); }
    .site-row td { font-size: 13px; }
    .filters { display: flex; gap: 8px; align-items: center; }
    .filters .input { width: 150px; height: 34px; }
  `],
})
export class ExecutiveDashboardComponent {
  private api = inject(ApiService);
  readonly auth = inject(AuthService);
  readonly ctx = inject(ContextService);
  readonly i18n = inject(I18nService);
  readonly data = signal<ExecData | null>(null);
  readonly from = signal<string>('');
  readonly to = signal<string>('');

  readonly riskGauge = computed(() => gauge(this.data()?.operationalRisk.score ?? 0, this.i18n.t('dash.operationalRisk')));
  readonly productionChart = computed(() => this.data() ? lineChart({ [this.i18n.t('dash.unitsProduced')]: this.data()!.productionTrend }, { area: true }) : null);
  readonly revenueChart = computed(() => this.data() ? lineChart({ [this.i18n.t('dash.revenue')]: this.data()!.revenueTrend }, { bar: [this.i18n.t('dash.revenue')], colors: ['#1a3a68'] }) : null);
  readonly qhseChart = computed(() => this.data() ? lineChart({ [this.i18n.t('nav.nonConformities')]: this.data()!.ncTrend, [this.i18n.t('nav.incidents')]: this.data()!.incidentTrend },
    { bar: [this.i18n.t('nav.nonConformities')], colors: ['#00a8cc', '#e5484d'] }) : null);
  readonly siteMarkers = computed<MapMarker[]>(() => (this.data()?.sites ?? []).map((s) => ({
    lat: s.latitude, lng: s.longitude, size: 11, color: s.openIncidents + s.openNc > 20 ? '#e5484d' : s.type === 'PRODUCTION' ? '#1a3a68' : '#14b8a6',
    label: s.name, popup: `<b>${s.name}</b><br>${s.city}<br>NC: ${s.openNc} · Incidents: ${s.openIncidents} · ${s.employees} emp.` })));

  constructor() {
    effect(() => {
      this.ctx.siteId(); this.from(); this.to();
      untracked(() => this.load());
    });
  }

  load(): void {
    this.api.get<ExecData>('/analytics/dashboard/executive', { siteId: this.ctx.siteId(), from: this.from(), to: this.to() }).subscribe((d) => this.data.set(d));
  }

  greeting(): string {
    const h = new Date().getHours();
    return h < 12 ? 'dash.goodMorning' : h < 18 ? 'dash.goodAfternoon' : 'dash.goodEvening';
  }
}
