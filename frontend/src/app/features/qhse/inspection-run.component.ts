import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Dialog } from '@angular/cdk/dialog';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ToastService } from '../../core/toast.service';
import { TrPipe, EnumPipe } from '../../shared/tr.pipe';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import { SignaturePadComponent } from '../../shared/signature-pad.component';
import { CollabPanelComponent } from '../../shared/collab-panel.component';
import { QrDialogComponent } from '../../shared/qr-dialog.component';

interface Question { id: string; text: string; type: string; required?: boolean; critical?: boolean; createNc?: boolean; options?: string[]; min?: number; max?: number; unit?: string;
  expected?: string; condition?: { questionId: string; equals: string }; }

/** Digital checklist execution: works on tablets and phones, with conditional questions, signature and photo evidence. */
@Component({
  selector: 'sf-inspection-run',
  imports: [FormsModule, DatePipe, DecimalPipe, RouterLink, TrPipe, EnumPipe, StatusBadgeComponent, SignaturePadComponent, CollabPanelComponent],
  template: `
  <div class="page">
    @if (ins(); as i) {
      <div class="page-header">
        <div><div class="muted small">{{ 'nav.inspections' | tr }} · {{ i['reference'] }}</div><h1>{{ i['title'] }}</h1>
          <div class="row wrap" style="margin-top:8px"><sf-badge [value]="i['status']" /><sf-badge [value]="i['domain']" />@if (i['result']) {<sf-badge [value]="i['result']" />}
            <span class="muted small">{{ i['scheduledDate'] | date: 'dd/MM/yyyy' }} · {{ i['inspectorName'] }}</span></div></div>
        <div class="row">
          <button class="btn" (click)="qr()"><span class="icon">qr_code</span>QR</button>
          @if (i['status'] === 'COMPLETED') { <button class="btn" (click)="pdf()"><span class="icon">picture_as_pdf</span>{{ 'case.report' | tr }}</button> }
          @if (i['status'] === 'SCHEDULED' && auth.can('inspections', 'write')) { <button class="btn primary" (click)="start()"><span class="icon">play_arrow</span>{{ 'inspection.start' | tr }}</button> }
        </div>
      </div>
      <div class="grid grid-3">
        <div class="span-2">
          @if (i['status'] === 'COMPLETED') {
            <div class="card result" [class.fail]="i['result'] === 'FAIL'">
              <div class="score">{{ i['score'] | number: '1.0-1' }}<small>%</small></div>
              <div><h2>{{ i['result'] | enum }}</h2><p class="muted">{{ 'inspection.completedAt' | tr }} {{ i['completedAt'] | date: 'dd/MM/yyyy HH:mm' }} ·
                {{ i['generatedNcCount'] ?? 0 }} {{ 'inspection.ncRaised' | tr }}</p>
                @for (n of raised(); track n.id) { <a class="badge red" [routerLink]="['/non-conformities', n.id]">{{ n.reference }}</a> }</div>
            </div>
          }
          <div class="card" style="margin-top:16px">
            <div class="card-header"><span class="icon" style="color:var(--cyan-500)">checklist</span><h3>{{ tpl()?.['name'] }}</h3>
              <span class="muted small">{{ 'inspection.passing' | tr }} {{ tpl()?.['passingScore'] }}%</span></div>
            <div class="card-body questions">
              @for (q of visible(); track q.id; let n = $index) {
                <div class="q" [class.crit]="q.critical">
                  <div class="qtext"><span class="qn">{{ n + 1 }}</span>{{ q.text }}@if (q.required) {<span class="req">*</span>}
                    @if (q.critical) { <span class="badge red">{{ 'inspection.critical' | tr }}</span> }</div>
                  @if (readonly()) {
                    <div class="ans">@if (answerOf(q.id); as a) {
                      <strong>{{ display(a.value) }}</strong> @if (a.passed === true) {<span class="icon ok">check_circle</span>} @if (a.passed === false) {<span class="icon ko">cancel</span>}
                      @if (a.comment) {<span class="muted small"> — {{ a.comment }}</span>}
                    } @else { <span class="muted">—</span> }</div>
                  } @else {
                    @switch (q.type) {
                      @case ('YES_NO') { <div class="seg">@for (o of ['YES','NO','N/A']; track o) { <button type="button" [class.on]="answers[q.id] === o" (click)="set(q.id, o)">{{ o | enum }}</button> }</div> }
                      @case ('PASS_FAIL') { <div class="seg">@for (o of ['PASS','FAIL','N/A']; track o) { <button type="button" [class.on]="answers[q.id] === o" (click)="set(q.id, o)">{{ o | enum }}</button> }</div> }
                      @case ('NUMERIC') { <div class="row"><input class="input num" type="number" step="0.1" [(ngModel)]="answers[q.id]" /><span class="muted small">{{ q.unit }}
                        @if (q.min !== undefined || q.max !== undefined) { ({{ q.min ?? '…' }} – {{ q.max ?? '…' }}) }</span></div> }
                      @case ('MULTI_SELECT') { <div class="seg wrap">@for (o of q.options ?? []; track o) { <button type="button" [class.on]="isChecked(q.id, o)" (click)="toggle(q.id, o)">{{ o }}</button> }</div> }
                      @case ('PHOTO') { <p class="muted small"><span class="icon" style="font-size:16px">photo_camera</span> {{ 'inspection.photoHint' | tr }}</p> }
                      @case ('SIGNATURE') { <sf-signature (signed)="signature = $event; answers[q.id] = $event ? 'signed' : null" /> }
                      @default { <input class="input" [(ngModel)]="answers[q.id]" /> }
                    }
                    <input class="input comment" [placeholder]="'inspection.comment' | tr" [(ngModel)]="comments[q.id]" />
                  }
                </div>
              }
              @if (!readonly()) {
                <div class="form-grid" style="margin-top:12px">
                  <div class="field"><label>{{ 'fields.temperature' | tr }} (°C)</label><input class="input" type="number" step="0.1" [(ngModel)]="temperature" /></div>
                  <div class="field"><label>{{ 'fields.humidity' | tr }} (%)</label><input class="input" type="number" step="0.1" [(ngModel)]="humidity" /></div>
                  <div class="field full"><label>{{ 'fields.notes' | tr }}</label><textarea class="input" [(ngModel)]="notes"></textarea></div>
                </div>
                <div class="row" style="justify-content:flex-end;margin-top:14px">
                  <span class="muted small">{{ answered() }} / {{ visible().length }} {{ 'inspection.answered' | tr }}</span>
                  <button class="btn primary" [disabled]="busy()" (click)="submit()"><span class="icon">send</span>{{ 'inspection.submit' | tr }}</button>
                </div>
              }
            </div>
          </div>
        </div>
        <div><sf-collab entityType="Inspection" [entityId]="id()" /></div>
      </div>
    }
  </div>`,
  styles: [`
    .questions { display: flex; flex-direction: column; gap: 12px; }
    .q { border: 1px solid var(--border); border-radius: 12px; padding: 12px 14px; display: flex; flex-direction: column; gap: 10px; }
    .q.crit { border-inline-start: 4px solid var(--red); }
    .qtext { font-weight: 500; display: flex; gap: 8px; align-items: flex-start; flex-wrap: wrap; }
    .qn { width: 22px; height: 22px; border-radius: 50%; background: var(--surface-2); display: grid; place-items: center; font-size: 11px; font-weight: 700; flex-shrink: 0; }
    .req { color: var(--red); }
    .seg { display: inline-flex; gap: 6px; flex-wrap: wrap; }
    .seg button { min-width: 74px; height: 40px; border-radius: 10px; border: 1px solid var(--border-strong); background: #fff; font: inherit; font-weight: 600; cursor: pointer; }
    .seg button.on { background: var(--navy-800); color: #fff; border-color: var(--navy-800); }
    .num { width: 140px; }
    .comment { height: 34px; font-size: 13px; }
    .ans .ok { color: var(--green); } .ans .ko { color: var(--red); }
    .result { display: flex; gap: 20px; align-items: center; padding: 18px 20px; border-inline-start: 6px solid var(--green); }
    .result.fail { border-color: var(--red); }
    .score { font-size: 46px; font-weight: 800; color: var(--navy-900); } .score small { font-size: 18px; color: var(--muted); }
  `],
})
export class InspectionRunComponent {
  readonly id = input.required<string>();
  private api = inject(ApiService);
  readonly auth = inject(AuthService);
  private toast = inject(ToastService);
  private dialog = inject(Dialog);
  readonly ins = signal<Record<string, any> | null>(null);
  readonly tpl = signal<Record<string, any> | null>(null);
  readonly raised = signal<{ id: string; reference: string }[]>([]);
  readonly busy = signal(false);
  answers: Record<string, any> = {};
  comments: Record<string, string> = {};
  signature: string | null = null;
  temperature: number | null = null;
  humidity: number | null = null;
  notes = '';
  readonly readonly = computed(() => ['COMPLETED', 'CANCELLED'].includes(this.ins()?.['status']));
  private tick = signal(0);

  readonly visible = computed<Question[]>(() => {
    this.tick();
    const qs = (this.tpl()?.['questions'] ?? []) as Question[];
    const src = this.readonly() ? Object.fromEntries(((this.ins()?.['answers'] ?? []) as { questionId: string; value: unknown }[]).map((a) => [a.questionId, a.value])) : this.answers;
    return qs.filter((q) => !q.condition || String(src[q.condition.questionId] ?? '').toUpperCase() === String(q.condition.equals).toUpperCase());
  });

  constructor() {
    effect(() => this.load(this.id()));
  }

  load(id: string): void {
    this.api.get<Record<string, any>>(`/inspections/${id}`).subscribe((i) => {
      this.ins.set(i);
      this.temperature = i['temperature'];
      this.humidity = i['humidity'];
      if (i['templateId']) this.api.get<Record<string, any>>(`/inspection-templates/${i['templateId']}`).subscribe((t) => this.tpl.set(t));
      if (i['status'] === 'COMPLETED') this.api.page<{ id: string; reference: string }>('/non-conformities', { inspectionId: id, size: 20 }).subscribe((p) => this.raised.set(p.content));
    });
  }

  answerOf(qid: string): { value: unknown; passed?: boolean; comment?: string } | undefined {
    return (this.ins()?.['answers'] ?? []).find((a: { questionId: string }) => a.questionId === qid);
  }

  display(v: unknown): string {
    if (Array.isArray(v)) return v.join(', ');
    if (typeof v === 'string' && v.startsWith('data:')) return '✔';
    return String(v);
  }

  set(qid: string, v: unknown): void {
    this.answers[qid] = v;
    this.tick.update((t) => t + 1);
  }

  answered(): number {
    return this.visible().filter((q) => this.answers[q.id] !== undefined && this.answers[q.id] !== null && this.answers[q.id] !== '').length;
  }

  isChecked(qid: string, o: string): boolean {
    return (this.answers[qid] as string[] | undefined)?.includes(o) ?? false;
  }

  toggle(qid: string, o: string): void {
    const cur = new Set((this.answers[qid] as string[]) ?? []);
    if (cur.has(o)) cur.delete(o);
    else cur.add(o);
    this.set(qid, [...cur]);
  }

  start(): void {
    this.api.post(`/inspections/${this.id()}/start`).subscribe(() => this.load(this.id()));
  }

  submit(): void {
    const answers = this.visible().filter((q) => this.answers[q.id] !== undefined && this.answers[q.id] !== null && this.answers[q.id] !== '')
      .map((q) => ({ questionId: q.id, value: this.answers[q.id], comment: this.comments[q.id] || undefined }));
    this.busy.set(true);
    this.api.post<{ nonConformities: { id: string; reference: string }[] }>(`/inspections/${this.id()}/submit`,
      { answers, temperature: this.temperature, humidity: this.humidity, signature: this.signature, notes: this.notes }).subscribe({
      next: (r) => {
        this.busy.set(false);
        this.toast.success(`${r.nonConformities.length} NC`);
        this.load(this.id());
      },
      error: () => this.busy.set(false),
    });
  }

  pdf(): void {
    this.api.download(`/inspections/${this.id()}/report`, undefined, `${this.ins()?.['reference']}.pdf`);
  }

  qr(): void {
    this.dialog.open(QrDialogComponent, { data: { type: 'inspection', id: this.id(), label: this.ins()?.['reference'] } });
  }
}
