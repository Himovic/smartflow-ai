import { Component, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ToastService } from '../../core/toast.service';
import { TrPipe, EnumPipe } from '../../shared/tr.pipe';

interface Q { id: string; text: string; type: string; required?: boolean; critical?: boolean; createNc?: boolean; weight?: number; min?: number | null; max?: number | null; unit?: string;
  options?: string[]; failOptions?: string[]; expected?: string; condition?: { questionId: string; equals: string } | null; }

/** Checklist designer: question types, scoring weights, critical criteria, tolerances and conditional questions. */
@Component({
  selector: 'sf-checklist-editor',
  imports: [FormsModule, TrPipe, EnumPipe],
  template: `
  <div class="page">@if (tpl(); as t) {
    <div class="page-header"><div><div class="muted small">{{ 'nav.checklists' | tr }} · {{ t['code'] }}</div><h1>{{ t['name'] }}</h1>
      <div class="subtitle">{{ t['domain'] | enum }} · v{{ t['templateVersion'] }} · {{ 'inspection.passing' | tr }} {{ t['passingScore'] }}%</div></div>
      @if (canEdit()) { <div class="row"><button class="btn" (click)="add()"><span class="icon">add</span>{{ 'checklist.addQuestion' | tr }}</button>
        <button class="btn primary" (click)="save()"><span class="icon">save</span>{{ 'common.save' | tr }}</button></div> }</div>
    <div class="card"><div class="card-body qs">
      @for (q of questions; track q.id; let i = $index) {
        <div class="q">
          <div class="row"><span class="qn">{{ i + 1 }}</span><input class="input" [(ngModel)]="q.text" [disabled]="!canEdit()" />
            <select class="input type" [(ngModel)]="q.type" [disabled]="!canEdit()">@for (tp of types; track tp) {<option [value]="tp">{{ tp | enum }}</option>}</select>
            @if (canEdit()) { <button class="btn ghost icon-only" (click)="move(i, -1)"><span class="icon">arrow_upward</span></button>
              <button class="btn ghost icon-only" (click)="remove(i)"><span class="icon">delete</span></button> }</div>
          <div class="row wrap opts">
            <label class="checkbox"><input type="checkbox" [(ngModel)]="q.required" [disabled]="!canEdit()" />{{ 'fields.required' | tr }}</label>
            <label class="checkbox"><input type="checkbox" [(ngModel)]="q.critical" [disabled]="!canEdit()" />{{ 'inspection.critical' | tr }}</label>
            <label class="checkbox"><input type="checkbox" [(ngModel)]="q.createNc" [disabled]="!canEdit()" />{{ 'checklist.createNc' | tr }}</label>
            <label>{{ 'checklist.weight' | tr }} <input class="input tiny" type="number" [(ngModel)]="q.weight" [disabled]="!canEdit()" /></label>
            @if (q.type === 'NUMERIC') { <label>min <input class="input tiny" type="number" [(ngModel)]="q.min" [disabled]="!canEdit()" /></label>
              <label>max <input class="input tiny" type="number" [(ngModel)]="q.max" [disabled]="!canEdit()" /></label>
              <label>{{ 'fields.unit' | tr }} <input class="input tiny" [(ngModel)]="q.unit" [disabled]="!canEdit()" /></label> }
            @if (q.type === 'YES_NO') { <label>{{ 'checklist.expected' | tr }} <select class="input tiny" [(ngModel)]="q.expected" [disabled]="!canEdit()"><option value="YES">{{ 'YES' | enum }}</option><option value="NO">{{ 'NO' | enum }}</option></select></label> }
            @if (q.type === 'MULTI_SELECT') { <label>{{ 'checklist.options' | tr }} <input class="input" [ngModel]="(q.options ?? []).join(', ')" (ngModelChange)="q.options = split($event)" [disabled]="!canEdit()" /></label> }
            <label>{{ 'checklist.showIf' | tr }} <select class="input tiny2" [ngModel]="q.condition?.questionId ?? ''" (ngModelChange)="setCond(q, $event)" [disabled]="!canEdit()">
              <option value="">—</option>@for (o of questions; track o.id) { @if (o.id !== q.id) {<option [value]="o.id">Q{{ $index + 1 }}</option>} }</select>
              @if (q.condition) { = <input class="input tiny" [(ngModel)]="q.condition.equals" [disabled]="!canEdit()" /> }</label>
          </div>
        </div>
      }
    </div></div>
  }</div>`,
  styles: [`.qs { display: flex; flex-direction: column; gap: 10px; } .q { border: 1px solid var(--border); border-radius: 12px; padding: 12px; display: flex; flex-direction: column; gap: 10px; }
    .qn { width: 26px; height: 26px; border-radius: 50%; background: var(--navy-800); color: #fff; display: grid; place-items: center; font-size: 12px; flex-shrink: 0; }
    .type { width: 170px; } .opts { gap: 8px 16px; font-size: 13px; color: var(--text-2); } .opts label { display: inline-flex; gap: 6px; align-items: center; }
    .tiny { width: 76px; height: 30px; } .tiny2 { width: 90px; height: 30px; }`],
})
export class ChecklistEditorComponent {
  readonly id = input.required<string>();
  private api = inject(ApiService);
  private auth = inject(AuthService);
  private toast = inject(ToastService);
  readonly tpl = signal<Record<string, any> | null>(null);
  readonly types = ['YES_NO', 'PASS_FAIL', 'NUMERIC', 'TEXT', 'MULTI_SELECT', 'PHOTO', 'SIGNATURE'];
  questions: Q[] = [];

  constructor() {
    effect(() => this.api.get<Record<string, any>>(`/inspection-templates/${this.id()}`).subscribe((t) => {
      this.tpl.set(t);
      this.questions = structuredClone(t['questions'] ?? []);
    }));
  }

  canEdit(): boolean {
    return this.auth.can('inspections', 'write');
  }

  add(): void {
    this.questions.push({ id: 'q' + Date.now().toString(36), text: '', type: 'YES_NO', required: true, weight: 1 });
  }

  remove(i: number): void {
    this.questions.splice(i, 1);
  }

  move(i: number, d: number): void {
    if (i + d < 0) return;
    [this.questions[i], this.questions[i + d]] = [this.questions[i + d], this.questions[i]];
  }

  split(v: string): string[] {
    return v.split(',').map((s) => s.trim()).filter(Boolean);
  }

  setCond(q: Q, qid: string): void {
    q.condition = qid ? { questionId: qid, equals: q.condition?.equals ?? 'YES' } : null;
  }

  save(): void {
    const t = { ...this.tpl()!, questions: this.questions, templateVersion: (this.tpl()!['templateVersion'] ?? 1) + 1 };
    this.api.put<Record<string, any>>(`/inspection-templates/${this.id()}`, t).subscribe((r) => {
      this.tpl.set(r);
      this.toast.success('v' + r['templateVersion']);
    });
  }
}
