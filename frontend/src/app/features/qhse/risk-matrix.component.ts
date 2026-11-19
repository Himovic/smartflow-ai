import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { ApiService } from '../../core/api.service';
import { ContextService } from '../../core/context.service';
import { TrPipe } from '../../shared/tr.pipe';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import { ResourceListComponent } from '../resources/resource-list.component';

interface MatrixData { cells: { probability: number; severity: number; count: number; level: string }[]; top: { id: string; reference: string; title: string; score: number; level: string; residualScore: number }[]; }

/** Risk assessment: interactive 5x5 matrix (initial vs residual) and the risk register. */
@Component({
  selector: 'sf-risk-matrix',
  imports: [TrPipe, StatusBadgeComponent, ResourceListComponent],
  template: `
  <div class="page">
    <div class="page-header"><div><h1>{{ 'nav.risks' | tr }}</h1><div class="subtitle">{{ 'risk.subtitle' | tr }}</div></div>
      <div class="seg-toggle"><button [class.on]="!residual()" (click)="residual.set(false)">{{ 'risk.initial' | tr }}</button>
        <button [class.on]="residual()" (click)="residual.set(true)">{{ 'risk.residual' | tr }}</button></div></div>
    <div class="grid grid-3">
      <div class="card span-2">
        <div class="card-header"><span class="icon" style="color:var(--amber)">grid_on</span><h3>{{ 'risk.matrix' | tr }} 5×5</h3><span class="muted small">{{ 'risk.formula' | tr }}</span></div>
        <div class="card-body">
          <div class="matrix">
            <div class="ylabel">{{ 'fields.probability' | tr }} →</div>
            <div class="cells">
              @for (p of [5,4,3,2,1]; track p) {
                <div class="axis">{{ p }}</div>
                @for (s of [1,2,3,4,5]; track s) {
                  <div class="cell" [class]="'cell ' + level(p * s)"><strong>{{ count(p, s) || '' }}</strong><small>{{ p * s }}</small></div>
                }
              }
              <div></div>@for (s of [1,2,3,4,5]; track s) { <div class="axis">{{ s }}</div> }
            </div>
            <div class="xlabel">{{ 'fields.severity' | tr }} →</div>
          </div>
          <div class="legend row wrap"><span class="lg LOW"></span>{{ 'enum.LOW' | tr }} (1–4)<span class="lg MEDIUM"></span>{{ 'enum.MEDIUM' | tr }} (5–9)
            <span class="lg HIGH"></span>{{ 'enum.HIGH' | tr }} (10–14)<span class="lg CRITICAL"></span>{{ 'enum.CRITICAL' | tr }} (15–25)</div>
        </div>
      </div>
      <div class="card"><div class="card-header"><h3>{{ 'risk.top' | tr }}</h3></div>
        <div>@for (r of data()?.top ?? []; track r.id) {
          <div class="top-row"><div style="flex:1"><strong>{{ r.title }}</strong><div class="muted small">{{ r.reference }}</div></div>
            <div class="scores"><span class="sc">{{ r.score }}</span><span class="icon">arrow_forward</span><span class="sc res">{{ r.residualScore }}</span></div><sf-badge [value]="r.level" /></div>
        }</div></div>
    </div>
    <div style="margin-top:16px"><sf-resource-list resource="risk-assessments" [embedded]="true" /></div>
  </div>`,
  styles: [`
    .seg-toggle { display: inline-flex; border: 1px solid var(--border-strong); border-radius: 10px; overflow: hidden; }
    .seg-toggle button { border: 0; background: #fff; padding: 8px 14px; font: inherit; cursor: pointer; } .seg-toggle button.on { background: var(--navy-800); color: #fff; }
    .matrix { display: grid; grid-template-columns: 24px 1fr; grid-template-rows: 1fr auto; gap: 8px; }
    .ylabel { writing-mode: vertical-rl; transform: rotate(180deg); text-align: center; color: var(--muted); font-size: 12px; }
    .xlabel { grid-column: 2; text-align: center; color: var(--muted); font-size: 12px; }
    .cells { display: grid; grid-template-columns: 24px repeat(5, 1fr); gap: 6px; }
    .axis { display: grid; place-items: center; color: var(--muted); font-weight: 600; font-size: 12px; }
    .cell { aspect-ratio: 1.6; border-radius: 10px; display: flex; flex-direction: column; align-items: center; justify-content: center; color: #fff; position: relative; }
    .cell strong { font-size: 22px; } .cell small { position: absolute; bottom: 4px; inset-inline-end: 8px; opacity: .7; font-size: 10px; }
    .LOW { background: #22c55e; } .MEDIUM { background: #f59e0b; } .HIGH { background: #f97316; } .CRITICAL { background: #e5484d; }
    .legend { gap: 6px 10px; margin-top: 14px; font-size: 12px; color: var(--muted); } .lg { width: 12px; height: 12px; border-radius: 3px; display: inline-block; margin-inline-start: 8px; }
    .top-row { display: flex; gap: 10px; align-items: center; padding: 10px 16px; border-bottom: 1px solid var(--border); }
    .scores { display: flex; align-items: center; gap: 4px; } .sc { font-weight: 700; } .sc.res { color: var(--teal-500); } .scores .icon { font-size: 14px; color: var(--muted); }
  `],
})
export class RiskMatrixComponent {
  private api = inject(ApiService);
  private ctx = inject(ContextService);
  readonly residual = signal(false);
  readonly data = signal<MatrixData | null>(null);
  private grid = computed(() => new Map((this.data()?.cells ?? []).map((c) => [`${c.probability}-${c.severity}`, c.count])));

  constructor() {
    effect(() => {
      this.residual(); this.ctx.siteId();
      untracked(() => this.api.get<MatrixData>('/risk-assessments/matrix', { residual: this.residual(), siteId: this.ctx.siteId() }).subscribe((d) => this.data.set(d)));
    });
  }

  count(p: number, s: number): number {
    return this.grid().get(`${p}-${s}`) ?? 0;
  }

  level(score: number): string {
    return score <= 4 ? 'LOW' : score <= 9 ? 'MEDIUM' : score <= 14 ? 'HIGH' : 'CRITICAL';
  }
}
