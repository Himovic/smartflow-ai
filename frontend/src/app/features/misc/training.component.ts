import { Component, inject, signal } from '@angular/core';
import { ApiService } from '../../core/api.service';
import { TrPipe } from '../../shared/tr.pipe';
import { ResourceListComponent } from '../resources/resource-list.component';

type R = Record<string, any>;

/** Training & certifications: qualification matrix, records, habilitations and catalogue. */
@Component({
  selector: 'sf-training',
  imports: [TrPipe, ResourceListComponent],
  template: `
  <div class="page">
    <div class="page-header"><div><h1>{{ 'nav.training' | tr }}</h1><div class="subtitle">{{ 'training.subtitle' | tr }}</div></div>
      @if (matrix(); as m) { <div class="rate"><span class="muted small">{{ 'training.compliance' | tr }}</span><strong>{{ m['complianceRate'] }} %</strong></div> }</div>
    <div class="tabs">@for (t of tabs; track t; let i = $index) { <button class="tab" [class.active]="tab() === i" (click)="tab.set(i)">{{ t | tr }}</button> }</div>
    @switch (tab()) {
      @case (0) {
        @if (matrix(); as m) {
          <div class="card"><div class="table-wrap matrix"><table class="table"><thead><tr><th>{{ 'nav.employees' | tr }}</th>
            @for (t of m['trainings']; track t['id']) { <th class="vh" [title]="t['title']">{{ t['code'] }}</th> }</tr></thead>
            <tbody>@for (r of m['rows']; track r['employeeId']) { <tr><td><strong>{{ r['name'] }}</strong><div class="muted small">{{ r['jobTitle'] }}</div></td>
              @for (t of m['trainings']; track t['id']) { <td class="c"><span class="cell" [class]="'cell ' + r['cells'][t['id']]['status']" [title]="r['cells'][t['id']]['expiryDate']"></span></td> }</tr> }</tbody></table></div>
            <div class="legend row wrap"><span class="cell VALID"></span>{{ 'enum.VALID' | tr }}<span class="cell EXPIRING"></span>{{ 'enum.EXPIRING' | tr }}
              <span class="cell EXPIRED"></span>{{ 'enum.EXPIRED' | tr }}<span class="cell MISSING"></span>{{ 'enum.MISSING' | tr }}</div></div>
        } @else { <div class="card skeleton" style="height:400px"></div> }
      }
      @case (1) { <sf-resource-list resource="training-records" [embedded]="true" /> }
      @case (2) { <sf-resource-list resource="certifications" [embedded]="true" /> }
      @case (3) { <sf-resource-list resource="trainings" [embedded]="true" /> }
    }
  </div>`,
  styles: [`.rate { display: flex; flex-direction: column; align-items: flex-end; } .rate strong { font-size: 26px; color: var(--teal-500); }
    .matrix { max-height: 620px; } .vh { writing-mode: vertical-rl; transform: rotate(180deg); height: 110px; text-align: start; font-size: 10.5px; }
    .c { text-align: center; padding: 6px; } .cell { display: inline-block; width: 18px; height: 18px; border-radius: 5px; }
    .VALID { background: #22c55e; } .EXPIRING { background: #f59e0b; } .EXPIRED { background: #e5484d; } .MISSING { background: #e2e8f1; border: 1px dashed #94a3b8; }
    .legend { gap: 6px 10px; padding: 12px 16px; font-size: 12px; color: var(--muted); } .legend .cell { margin-inline-start: 8px; width: 12px; height: 12px; }`],
})
export class TrainingComponent {
  private api = inject(ApiService);
  readonly tab = signal(0);
  readonly matrix = signal<R | null>(null);
  readonly tabs = ['training.matrix', 'res.training-records', 'res.certifications', 'res.trainings'];

  constructor() {
    this.api.get<R>('/training-matrix').subscribe((m) => this.matrix.set(m));
  }
}
