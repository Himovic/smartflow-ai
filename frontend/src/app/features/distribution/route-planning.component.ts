import { Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { LookupService } from '../../core/lookup.service';
import { ToastService } from '../../core/toast.service';
import { LookupItem } from '../../core/models';
import { TrPipe } from '../../shared/tr.pipe';
import { MapComponent, MapLine, MapMarker } from '../../shared/map.component';
import { ResourceListComponent } from '../resources/resource-list.component';
import { PALETTE } from '../../shared/chart.component';

interface RouteStop { stopId: string; label: string; sequence: number; latitude: number; longitude: number; distanceFromPreviousKm: number; arrivalMinute: number; timeWindowRespected: boolean; }
interface VehicleRoute { vehicleId: string; label: string; stops: RouteStop[]; distanceKm: number; durationMinutes: number; loadKg: number; capacityKg: number; fuelLiters: number; co2Kg: number; }
interface Result { planId?: string; planCode?: string; depot: { name: string; latitude: number; longitude: number }; dataNature: string;
  solution: { engine: string; routes: VehicleRoute[]; unassignedStopIds: string[]; originalDistanceKm: number; optimizedDistanceKm: number; estimatedDurationMinutes: number;
    estimatedFuelLiters: number; estimatedCo2Kg: number; originalCo2Kg: number; timeWindowViolations: number; notes: string[] }; }

/** AI route optimization: capacity, priorities and time windows; before/after distance, fuel and CO2. */
@Component({
  selector: 'sf-route-planning',
  imports: [DecimalPipe, FormsModule, TrPipe, MapComponent, ResourceListComponent],
  template: `
  <div class="page">
    <div class="page-header"><div><h1>{{ 'nav.routes' | tr }}</h1><div class="subtitle">{{ 'routes.subtitle' | tr }}</div></div></div>
    <div class="card controls"><div class="row wrap">
      <div class="field"><label>{{ 'routes.depot' | tr }}</label><select class="input" [(ngModel)]="depot">@for (w of warehouses(); track w.id) {<option [ngValue]="w.id">{{ w.label }}</option>}</select></div>
      <div class="field"><label>{{ 'routes.date' | tr }}</label><input class="input" type="date" [(ngModel)]="date" /></div>
      <span class="spacer"></span>
      <button class="btn" [disabled]="busy() || !depot" (click)="optimize(false)"><span class="icon">science</span>{{ 'routes.simulate' | tr }}</button>
      @if (auth.can('routes', 'write')) { <button class="btn accent" [disabled]="busy() || !depot" (click)="optimize(true)"><span class="icon">auto_awesome</span>{{ 'routes.optimizeSave' | tr }}</button> }
    </div></div>

    @if (busy()) { <div class="card skeleton" style="height:120px;margin-top:16px"></div> }
    @if (res(); as r) {
      <div class="kpi-grid" style="margin-top:16px">
        <div class="card k"><span>{{ 'routes.manual' | tr }}</span><strong>{{ r.solution.originalDistanceKm | number: '1.0-0' }} km</strong></div>
        <div class="card k good"><span>{{ 'routes.optimized' | tr }}</span><strong>{{ r.solution.optimizedDistanceKm | number: '1.0-0' }} km</strong>
          <em>-{{ saving() | number: '1.0-1' }} %</em></div>
        <div class="card k"><span>{{ 'routes.vehicles' | tr }}</span><strong>{{ r.solution.routes.length }}</strong></div>
        <div class="card k"><span>{{ 'routes.duration' | tr }}</span><strong>{{ r.solution.estimatedDurationMinutes / 60 | number: '1.1-1' }} h</strong></div>
        <div class="card k"><span>{{ 'routes.fuel' | tr }}</span><strong>{{ r.solution.estimatedFuelLiters | number: '1.0-0' }} L</strong></div>
        <div class="card k good"><span>CO2</span><strong>{{ r.solution.estimatedCo2Kg | number: '1.0-0' }} kg</strong><em>-{{ (r.solution.originalCo2Kg - r.solution.estimatedCo2Kg) | number: '1.0-0' }} kg</em></div>
        <div class="card k" [class.warn]="r.solution.timeWindowViolations > 0"><span>{{ 'routes.twViolations' | tr }}</span><strong>{{ r.solution.timeWindowViolations }}</strong></div>
      </div>
      <p class="muted small engine"><span class="icon">memory</span>{{ r.solution.engine }} · {{ r.dataNature }}</p>
      <div class="grid grid-3" style="margin-top:8px">
        <div class="card span-2"><div class="card-body"><sf-map [markers]="markers()" [lines]="lines()" [height]="480" /></div></div>
        <div class="card routes">
          <div class="card-header"><h3>{{ 'routes.plan' | tr }} @if (r.planCode) {· {{ r.planCode }}}</h3>
            @if (r.planId && auth.can('routes', 'write')) { <button class="btn sm primary" (click)="dispatch()"><span class="icon">send</span>{{ 'routes.dispatch' | tr }}</button> }</div>
          @for (v of r.solution.routes; track v.vehicleId; let i = $index) {
            <div class="vroute"><div class="vhead"><span class="sw" [style.background]="color(i)"></span><strong>{{ v.label }}</strong><span class="spacer"></span>
              <span class="small muted">{{ v.distanceKm | number: '1.0-0' }} km · {{ v.loadKg | number: '1.0-0' }}/{{ v.capacityKg | number: '1.0-0' }} kg</span></div>
              <ol>@for (s of v.stops; track s.stopId) { <li [class.late]="!s.timeWindowRespected">{{ s.label }} <span class="muted small">{{ eta(s.arrivalMinute) }}</span></li> }</ol></div>
          }
          @if (r.solution.unassignedStopIds.length) { <p class="warnp">{{ r.solution.unassignedStopIds.length }} {{ 'routes.unassigned' | tr }}</p> }
        </div>
      </div>
    }
    <h2 style="margin:24px 0 12px">{{ 'routes.history' | tr }}</h2>
    <sf-resource-list resource="route-plans" [embedded]="true" />
  </div>`,
  styles: [`.controls { padding: 14px 16px; } .controls .field { min-width: 240px; }
    .k { padding: 12px 14px; display: flex; flex-direction: column; gap: 4px; } .k span { font-size: 12px; color: var(--muted); } .k strong { font-size: 22px; color: var(--navy-900); }
    .k em { font-style: normal; color: var(--green); font-weight: 700; font-size: 13px; } .k.good { border-color: #bfeee4; } .k.warn strong { color: var(--amber); }
    .engine { display: flex; gap: 6px; align-items: center; margin-top: 10px; } .engine .icon { font-size: 16px; }
    .routes { max-height: 560px; overflow: auto; } .vroute { padding: 10px 16px; border-bottom: 1px solid var(--border); }
    .vhead { display: flex; gap: 8px; align-items: center; } .sw { width: 12px; height: 12px; border-radius: 3px; }
    ol { margin: 8px 0 0; padding-inline-start: 22px; font-size: 13px; line-height: 1.7; } li.late { color: var(--red); } .warnp { padding: 10px 16px; color: var(--amber); }`],
})
export class RoutePlanningComponent {
  private api = inject(ApiService);
  readonly auth = inject(AuthService);
  private toast = inject(ToastService);
  readonly warehouses = signal<LookupItem[]>([]);
  readonly res = signal<Result | null>(null);
  readonly busy = signal(false);
  depot: string | null = null;
  date = new Date().toISOString().substring(0, 10);
  readonly saving = computed(() => {
    const s = this.res()?.solution;
    return s && s.originalDistanceKm ? (1 - s.optimizedDistanceKm / s.originalDistanceKm) * 100 : 0;
  });
  readonly markers = computed<MapMarker[]>(() => {
    const r = this.res();
    if (!r) return [];
    const m: MapMarker[] = [{ lat: r.depot.latitude, lng: r.depot.longitude, color: '#0b1f3a', size: 13, label: r.depot.name }];
    r.solution.routes.forEach((v, i) => v.stops.forEach((s) => m.push({ lat: s.latitude, lng: s.longitude, color: this.color(i), size: 8, label: `${s.sequence}. ${s.label}` })));
    return m;
  });
  readonly lines = computed<MapLine[]>(() => {
    const r = this.res();
    if (!r) return [];
    return r.solution.routes.map((v, i) => ({ color: this.color(i), label: v.label,
      points: [[r.depot.latitude, r.depot.longitude], ...v.stops.map((s) => [s.latitude, s.longitude] as [number, number]), [r.depot.latitude, r.depot.longitude]] as [number, number][] }));
  });

  constructor() {
    inject(LookupService).list('warehouses').subscribe((w) => {
      this.warehouses.set(w);
      this.depot = w.find((x) => x.label.startsWith('WH-ALG'))?.id ?? w[0]?.id ?? null;
    });
  }

  color(i: number): string {
    return PALETTE[(i + 1) % PALETTE.length];
  }

  eta(min: number): string {
    const t = 7 * 60 + min;
    return `${String(Math.floor(t / 60) % 24).padStart(2, '0')}:${String(Math.round(t % 60)).padStart(2, '0')}`;
  }

  optimize(save: boolean): void {
    this.busy.set(true);
    this.api.post<Result>('/routing/optimize', { depotWarehouseId: this.depot, planDate: this.date, save }).subscribe({
      next: (r) => { this.res.set(r); this.busy.set(false); },
      error: () => this.busy.set(false),
    });
  }

  dispatch(): void {
    this.api.post(`/routing/plans/${this.res()!.planId}/dispatch`).subscribe(() => this.toast.success(this.res()!.planCode + ' ✔'));
  }
}
