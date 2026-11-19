import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/api.service';
import { LookupService } from '../core/lookup.service';
import { TrPipe, EnumPipe } from './tr.pipe';
import { ToastService } from '../core/toast.service';
import { LookupItem } from '../core/models';

interface WfState { code: string; label: string; color?: string; terminal?: boolean; }
interface WfTransition { code: string; label: string; to: string; fields?: string[]; requiredFields?: string[]; requiresComment?: boolean; requiresEvidence?: boolean; }
interface WfEvent { id: string; fromStatus?: string; toStatus: string; transition: string; actorName?: string; occurredAt: string; comment?: string; }
interface WfView { workflowKey: string; name: string; states: WfState[]; currentState: string; availableTransitions: WfTransition[]; history: WfEvent[]; }

const FIELD_TYPES: Record<string, string> = {
  dueDate: 'date', severity: 'select:MINOR,MAJOR,CRITICAL', rootCauseMethod: 'select:5WHY,ISHIKAWA,FAULT_TREE,8D', effectivenessResult: 'select:EFFECTIVE,PARTIALLY_EFFECTIVE',
  probability: 'number', envCategory: 'select:WASTE,CHEMICAL,WATER,ENERGY,AIR', assigneeUserId: 'user', investigatorUserId: 'user',
  rootCause: 'textarea', investigationFindings: 'textarea', impactAssessment: 'textarea', immediateAction: 'textarea', immediateActions: 'textarea',
  evidenceNote: 'textarea', effectivenessComment: 'textarea', fiveWhys: 'whys',
};

/** Configurable workflow: visual stepper, allowed transitions (role-aware), field capture and full history. */
@Component({
  selector: 'sf-workflow',
  imports: [FormsModule, DatePipe, TrPipe, EnumPipe],
  template: `
    @if (wf(); as w) {
      <div class="card">
        <div class="card-header"><span class="icon" style="color:var(--cyan-500)">account_tree</span><h3>{{ w.name }}</h3><span class="muted small">{{ w.workflowKey }}</span></div>
        <div class="card-body">
          <div class="stepper">
            @for (s of mainStates(); track s.code; let i = $index) {
              <div class="step" [class.done]="i < currentIndex()" [class.current]="s.code === w.currentState">
                <div class="dot-wrap"><span class="num">@if (i < currentIndex()) {<span class="icon">check</span>} @else {{{ i + 1 }}}</span></div>
                <span class="lbl">{{ s.code | enum }}</span>
              </div>
            }
          </div>
          @if (w.availableTransitions.length) {
            <div class="divider"></div>
            @if (!active()) {
              <div class="row wrap">
                <span class="muted small">{{ 'workflow.nextSteps' | tr }}</span>
                @for (t of w.availableTransitions; track t.code) {
                  <button class="btn sm" [class.primary]="!isNegative(t)" (click)="start(t)"><span class="icon">{{ isNegative(t) ? 'undo' : 'arrow_forward' }}</span>{{ t.label }}</button>
                }
              </div>
            } @else {
              <div class="transition fade-in">
                <h3>{{ active()!.label }}</h3>
                <div class="form-grid" style="margin-top:12px">
                  @for (fk of active()!.fields ?? []; track fk) {
                    <div class="field" [class.full]="type(fk) === 'textarea' || type(fk) === 'whys'">
                      <label>{{ ('fields.' + fk) | tr }}{{ active()!.requiredFields?.includes(fk) ? ' *' : '' }}</label>
                      @switch (kind(fk)) {
                        @case ('textarea') { <textarea class="input" rows="3" [(ngModel)]="values[fk]"></textarea> }
                        @case ('date') { <input class="input" type="date" [(ngModel)]="values[fk]" /> }
                        @case ('number') { <input class="input" type="number" min="1" max="5" [(ngModel)]="values[fk]" /> }
                        @case ('select') { <select class="input" [(ngModel)]="values[fk]"><option [ngValue]="null">—</option>@for (o of options(fk); track o) {<option [ngValue]="o">{{ o | enum }}</option>}</select> }
                        @case ('user') { <select class="input" [(ngModel)]="values[fk]" (ngModelChange)="pickUser(fk, $event)"><option [ngValue]="null">—</option>@for (u of users(); track u.id) {<option [ngValue]="u.id">{{ u.label }}</option>}</select> }
                        @case ('whys') {
                          <div class="whys">@for (i of [0,1,2,3,4]; track i) { <input class="input" [placeholder]="('workflow.why' | tr) + ' ' + (i + 1)" [(ngModel)]="whys[i]" /> }</div>
                        }
                        @default { <input class="input" [(ngModel)]="values[fk]" /> }
                      }
                    </div>
                  }
                  <div class="field full">
                    <label>{{ 'workflow.comment' | tr }}{{ active()!.requiresComment ? ' *' : '' }}</label>
                    <textarea class="input" rows="2" [(ngModel)]="comment"></textarea>
                  </div>
                </div>
                @if (active()!.requiresEvidence) { <p class="small muted"><span class="icon" style="font-size:16px">attach_file</span> {{ 'workflow.evidenceHint' | tr }}</p> }
                <div class="row actions" style="justify-content:flex-end;margin-top:12px">
                  <button class="btn" (click)="active.set(null)">{{ 'common.cancel' | tr }}</button>
                  <button class="btn primary" [disabled]="busy()" (click)="submit()"><span class="icon">check</span>{{ 'common.confirm' | tr }}</button>
                </div>
              </div>
            }
          }
        </div>
      </div>
      <div class="card" style="margin-top:16px">
        <div class="card-header"><span class="icon" style="color:var(--cyan-500)">history</span><h3>{{ 'workflow.history' | tr }}</h3></div>
        <div class="card-body">
          <ol class="timeline">
            @for (e of w.history.slice().reverse(); track e.id) {
              <li><span class="tdot"></span>
                <div><strong>{{ e.toStatus | enum }}</strong> <span class="muted small">· {{ e.transition }} · {{ e.actorName }}</span></div>
                <div class="muted small">{{ e.occurredAt | date: 'dd/MM/yyyy HH:mm' }}</div>
                @if (e.comment) { <div class="cmt">{{ e.comment }}</div> }
              </li>
            }
          </ol>
        </div>
      </div>
    }`,
  styles: [`
    .stepper { display: flex; gap: 4px; overflow-x: auto; padding-bottom: 4px; }
    .step { flex: 1; min-width: 110px; display: flex; flex-direction: column; align-items: center; gap: 6px; position: relative; text-align: center; }
    .step::after { content: ''; position: absolute; top: 15px; inset-inline-start: calc(50% + 18px); width: calc(100% - 36px); height: 2px; background: var(--border); }
    .step:last-child::after { display: none; }
    .step.done::after { background: var(--teal-500); }
    .num { width: 30px; height: 30px; border-radius: 50%; display: grid; place-items: center; background: var(--surface-2); border: 2px solid var(--border-strong); font-weight: 700; font-size: 13px; color: var(--muted); }
    .num .icon { font-size: 18px; }
    .step.done .num { background: var(--teal-500); border-color: var(--teal-500); color: #fff; }
    .step.current .num { background: var(--navy-800); border-color: var(--cyan-500); color: #fff; box-shadow: 0 0 0 4px rgba(0,168,204,.18); }
    .lbl { font-size: 11.5px; color: var(--muted); font-weight: 500; }
    .step.current .lbl { color: var(--navy-800); font-weight: 700; }
    .transition { background: var(--surface-2); border: 1px solid var(--border); border-radius: 12px; padding: 14px 16px; }
    .whys { display: grid; gap: 6px; }
    .timeline { list-style: none; margin: 0; padding: 0; position: relative; }
    .timeline li { position: relative; padding-inline-start: 22px; padding-bottom: 14px; border-inline-start: 2px solid var(--border); margin-inline-start: 6px; }
    .timeline li:last-child { border-color: transparent; }
    .tdot { position: absolute; inset-inline-start: -7px; top: 2px; width: 12px; height: 12px; border-radius: 50%; background: var(--cyan-500); border: 2px solid #fff; box-shadow: 0 0 0 1px var(--cyan-500); }
    .cmt { margin-top: 4px; background: var(--surface-2); border-radius: 8px; padding: 6px 10px; font-size: 13px; }
  `],
})
export class WorkflowPanelComponent {
  readonly entityType = input.required<string>();
  readonly entityId = input.required<string>();
  readonly changed = output<Record<string, unknown>>();
  private api = inject(ApiService);
  private toast = inject(ToastService);
  private lookups = inject(LookupService);
  readonly wf = signal<WfView | null>(null);
  readonly active = signal<WfTransition | null>(null);
  readonly busy = signal(false);
  readonly users = signal<LookupItem[]>([]);
  values: Record<string, any> = {};
  whys: string[] = [];
  comment = '';
  readonly mainStates = computed(() => (this.wf()?.states ?? []).filter((s) => s.code !== 'CANCELLED' || this.wf()?.currentState === 'CANCELLED'));
  readonly currentIndex = computed(() => this.mainStates().findIndex((s) => s.code === this.wf()?.currentState));

  constructor() {
    effect(() => this.load(this.entityType(), this.entityId()));
    this.api.get<{ content: { id: string; firstName: string; lastName: string; role: string }[] }>('/users', { size: 200 }).subscribe({
      next: (p) => this.users.set(p.content.map((u) => ({ id: u.id, label: `${u.firstName} ${u.lastName} (${u.role})` }))),
      error: () => this.lookups.list('employees').subscribe((l) => this.users.set(l)),
    });
  }

  load(type: string, id: string): void {
    this.api.get<WfView>(`/workflow/${type}/${id}`).subscribe((w) => this.wf.set(w));
  }

  isNegative(t: WfTransition): boolean {
    return /cancel|reject|ineffective|request_changes|obsolete/.test(t.code);
  }

  start(t: WfTransition): void {
    this.values = {};
    this.whys = [];
    this.comment = '';
    this.active.set(t);
  }

  type(fk: string): string { return FIELD_TYPES[fk] ?? 'text'; }
  kind(fk: string): string { return this.type(fk).split(':')[0]; }
  options(fk: string): string[] { return (this.type(fk).split(':')[1] ?? '').split(',').filter(Boolean); }

  pickUser(fk: string, id: string): void {
    const label = this.users().find((u) => u.id === id)?.label?.replace(/ \(.*\)$/, '');
    if (fk === 'assigneeUserId') this.values['assigneeName'] = label;
    if (fk === 'investigatorUserId') this.values['investigatorName'] = label;
  }

  submit(): void {
    const t = this.active()!;
    const fields = { ...this.values };
    if (t.fields?.includes('fiveWhys')) fields['fiveWhys'] = this.whys.filter((w) => w?.trim());
    this.busy.set(true);
    this.api.post<Record<string, unknown>>(`/workflow/${this.entityType()}/${this.entityId()}/transitions/${t.code}`, { comment: this.comment, fields }).subscribe({
      next: (r) => {
        this.busy.set(false);
        this.active.set(null);
        this.toast.success(t.label);
        this.load(this.entityType(), this.entityId());
        this.changed.emit(r);
      },
      error: () => this.busy.set(false),
    });
  }
}
