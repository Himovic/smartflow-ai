import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ToastService } from '../../core/toast.service';
import { TrPipe, EnumPipe } from '../../shared/tr.pipe';

type R = Record<string, any>;

/** Workflow designer: states and transitions (permissions, required fields, comments, evidence, guards) as configuration. */
@Component({
  selector: 'sf-workflows',
  imports: [FormsModule, TrPipe, EnumPipe],
  template: `
  <div class="page">
    <div class="page-header"><div><h1>{{ 'nav.workflows' | tr }}</h1><div class="subtitle">{{ 'workflows.subtitle' | tr }}</div></div></div>
    <div class="grid grid-3">
      <div class="card"><div class="card-header"><h3>{{ 'workflows.definitions' | tr }}</h3></div>
        @for (w of defs(); track w['id']) { <button class="wf" [class.on]="sel()?.['id'] === w['id']" (click)="select(w)"><strong>{{ w['name'] }}</strong>
          <span class="muted small">{{ w['workflowKey'] }} · {{ w['entityType'] }}</span></button> }</div>
      @if (sel(); as w) {
        <div class="card span-2"><div class="card-header"><h3>{{ w['name'] }}</h3>
          @if (canEdit) { <button class="btn sm primary" (click)="save()"><span class="icon">save</span>{{ 'common.save' | tr }}</button> }</div>
          <div class="card-body">
            <p class="muted">{{ w['description'] }}</p>
            <div class="diagram">@for (s of w['states']; track s['code']) { <div class="state" [style.borderColor]="s['color']"><span class="dot" [style.background]="s['color']"></span>{{ s['code'] | enum }}
              @if (s['code'] === w['initialState']) {<span class="badge blue">start</span>} @if (s['terminal']) {<span class="badge">end</span>}</div> }</div>
            <table class="table" style="margin-top:14px"><thead><tr><th>{{ 'workflows.transition' | tr }}</th><th>{{ 'workflows.from' | tr }}</th><th>{{ 'workflows.to' | tr }}</th>
              <th>{{ 'workflows.permission' | tr }}</th><th>{{ 'workflows.rules' | tr }}</th></tr></thead>
              <tbody>@for (t of w['transitions']; track t['code']) { <tr><td><strong>{{ t['label'] }}</strong><div class="mono muted">{{ t['code'] }}</div></td>
                <td>@for (f of t['from']; track f) {<span class="badge outline">{{ f | enum }}</span> }</td><td><span class="badge teal">{{ t['to'] | enum }}</span></td>
                <td class="mono">{{ t['permission'] }}</td>
                <td class="small">@if (t['requiredFields']?.length) {<div>{{ 'workflows.required' | tr }}: {{ t['requiredFields'].join(', ') }}</div>}
                  @if (t['requiresComment']) {<div>✓ {{ 'workflow.comment' | tr }}</div>} @if (t['requiresEvidence']) {<div>✓ {{ 'workflows.evidence' | tr }}</div>}
                  @if (t['guards']?.length) {<div>⛨ {{ t['guards'].join(', ') }}</div>}</td></tr> }</tbody></table>
            @if (canEdit) { <details style="margin-top:14px"><summary>{{ 'workflows.advanced' | tr }} (JSON)</summary>
              <textarea class="input mono" rows="16" [(ngModel)]="json"></textarea></details> }
          </div></div>
      }
    </div>
  </div>`,
  styles: [`.wf { display: flex; flex-direction: column; gap: 2px; width: 100%; text-align: start; padding: 12px 16px; border: 0; border-bottom: 1px solid var(--border); background: none; cursor: pointer; font: inherit; }
    .wf.on { background: #f2fbfd; box-shadow: inset 3px 0 0 var(--cyan-500); }
    .diagram { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 12px; } .state { display: flex; gap: 6px; align-items: center; padding: 6px 10px; border: 2px solid; border-radius: 999px; font-size: 12.5px; font-weight: 600; }
    .dot { width: 8px; height: 8px; border-radius: 50%; }`],
})
export class WorkflowsComponent {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  readonly canEdit = inject(AuthService).can('workflows', 'write');
  readonly defs = signal<R[]>([]);
  readonly sel = signal<R | null>(null);
  json = '';

  constructor() {
    this.api.page<R>('/workflow-definitions', { size: 50 }).subscribe((p) => { this.defs.set(p.content); if (p.content[0]) this.select(p.content[0]); });
  }

  select(w: R): void {
    this.sel.set(w);
    this.json = JSON.stringify({ states: w['states'], transitions: w['transitions'] }, null, 2);
  }

  save(): void {
    try {
      const parsed = JSON.parse(this.json);
      const body = { ...this.sel(), ...parsed };
      this.api.put<R>(`/workflow-definitions/${body['id']}`, body).subscribe((r) => { this.toast.success('OK'); this.sel.set(r); });
    } catch {
      this.toast.error('Invalid JSON');
    }
  }
}
