import { Component, ElementRef, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { I18nService } from '../../core/i18n.service';
import { TrPipe } from '../../shared/tr.pipe';

interface Answer { intent: string; language: string; answer: string; dataNature: string; table: Record<string, unknown>[]; references: { entityType: string; id: string; label: string; route: string }[];
  engine: string; suggestions: string[]; }
interface Msg { role: 'user' | 'assistant'; text: string; answer?: Answer; }

/** SmartFlow AI Assistant (FR / EN / AR), grounded on authorized platform data with cited records. */
@Component({
  selector: 'sf-assistant',
  imports: [FormsModule, RouterLink, TrPipe],
  template: `
  <div class="page chat-page">
    <div class="page-header"><div><h1>{{ 'nav.assistant' | tr }}</h1><div class="subtitle">{{ 'assistant.subtitle' | tr }}</div></div></div>
    <div class="card chat">
      <div class="msgs" #msgs>
        @if (!messages().length) {
          <div class="welcome"><span class="icon bot">smart_toy</span><h2>{{ 'assistant.hello' | tr }}</h2><p class="muted">{{ 'assistant.intro' | tr }}</p>
            <div class="sugs">@for (s of suggestions(); track s) { <button class="sug" (click)="ask(s)">{{ s }}</button> }</div></div>
        }
        @for (m of messages(); track $index) {
          <div class="msg" [class.user]="m.role === 'user'">
            @if (m.role === 'assistant') { <span class="icon av">smart_toy</span> }
            <div class="bubble">
              <p class="text">{{ m.text }}</p>
              @if (m.answer; as a) {
                @if (a.table.length) {
                  <div class="table-wrap mini"><table class="table"><thead><tr>@for (k of keys(a.table[0]); track k) {<th>{{ ("fields." + k) | tr }}</th>}</tr></thead>
                    <tbody>@for (r of a.table.slice(0, 10); track $index) { <tr>@for (k of keys(a.table[0]); track k) {<td>{{ fmt(r[k]) }}</td>}</tr> }</tbody></table></div>
                }
                @if (a.references.length) { <div class="refs">@for (r of a.references.slice(0, 8); track r.id) { <a class="badge blue" [routerLink]="r.route">{{ r.label }}</a> }</div> }
                <div class="meta"><span class="nature" [class]="'nature ' + a.dataNature">{{ ('nature.short.' + a.dataNature) | tr }}</span><span class="muted small">{{ a.engine }}</span></div>
              }
            </div>
          </div>
        }
        @if (busy()) { <div class="msg"><span class="icon av">smart_toy</span><div class="bubble typing"><i></i><i></i><i></i></div></div> }
      </div>
      <form class="composer" (ngSubmit)="ask(q)">
        <input class="input" name="q" [(ngModel)]="q" [placeholder]="'assistant.placeholder' | tr" autocomplete="off" />
        <button class="btn primary" type="submit" [disabled]="!q.trim() || busy()"><span class="icon">send</span></button>
      </form>
    </div>
  </div>`,
  styles: [`
    .chat-page { display: flex; flex-direction: column; height: calc(100vh - 100px); }
    .chat { flex: 1; display: flex; flex-direction: column; min-height: 0; }
    .msgs { flex: 1; overflow-y: auto; padding: 18px; display: flex; flex-direction: column; gap: 14px; }
    .welcome { text-align: center; margin: auto; max-width: 760px; display: flex; flex-direction: column; gap: 8px; align-items: center; }
    .bot { font-size: 46px; color: var(--cyan-500); }
    .sugs { display: flex; flex-wrap: wrap; gap: 8px; justify-content: center; margin-top: 12px; }
    .sug { border: 1px solid var(--border); background: var(--surface-2); border-radius: 999px; padding: 8px 14px; cursor: pointer; font: inherit; font-size: 13px; }
    .sug:hover { border-color: var(--cyan-500); }
    .msg { display: flex; gap: 10px; align-items: flex-start; }
    .msg.user { justify-content: flex-end; }
    .av { width: 32px; height: 32px; border-radius: 50%; background: var(--navy-800); color: var(--cyan-400); display: grid; place-items: center; font-size: 18px; flex-shrink: 0; }
    .bubble { max-width: min(820px, 85%); background: var(--surface-2); border: 1px solid var(--border); border-radius: 14px; padding: 12px 14px; display: flex; flex-direction: column; gap: 10px; }
    .msg.user .bubble { background: var(--navy-800); color: #fff; border-color: var(--navy-800); }
    .text { white-space: pre-wrap; line-height: 1.55; }
    .mini td, .mini th { padding: 6px 8px; font-size: 12px; }
    .refs { display: flex; flex-wrap: wrap; gap: 6px; }
    .meta { display: flex; gap: 8px; align-items: center; }
    .composer { display: flex; gap: 8px; padding: 12px; border-top: 1px solid var(--border); }
    .composer .input { height: 44px; }
    .typing { flex-direction: row; gap: 4px; } .typing i { width: 7px; height: 7px; border-radius: 50%; background: var(--muted); animation: blink 1.2s infinite; }
    .typing i:nth-child(2) { animation-delay: .2s; } .typing i:nth-child(3) { animation-delay: .4s; }
    @keyframes blink { 0%, 100% { opacity: .2; } 50% { opacity: 1; } }
  `],
})
export class AssistantComponent {
  private api = inject(ApiService);
  private i18n = inject(I18nService);
  private box = viewChild<ElementRef<HTMLDivElement>>('msgs');
  readonly messages = signal<Msg[]>([]);
  readonly suggestions = signal<string[]>([]);
  readonly busy = signal(false);
  q = '';

  constructor() {
    this.api.get<string[]>('/ai/assistant/suggestions', { lang: this.i18n.lang() }).subscribe((s) => this.suggestions.set(s));
  }

  keys(r: Record<string, unknown>): string[] {
    return Object.keys(r).filter((k) => !/Id$/.test(k) && k !== 'id');
  }

  fmt(v: unknown): string {
    if (Array.isArray(v)) return v.join(', ');
    if (v === null || v === undefined) return '—';
    return String(v);
  }

  ask(text: string): void {
    if (!text.trim()) return;
    this.messages.update((m) => [...m, { role: 'user', text }]);
    this.q = '';
    this.busy.set(true);
    this.scroll();
    this.api.post<Answer>('/ai/assistant', { question: text, language: /[؀-ۿ]/.test(text) ? 'ar' : this.i18n.lang() }).subscribe({
      next: (a) => {
        this.messages.update((m) => [...m, { role: 'assistant', text: a.answer, answer: a }]);
        this.busy.set(false);
        if (a.intent === 'help') this.suggestions.set(a.suggestions);
        this.scroll();
      },
      error: () => this.busy.set(false),
    });
  }

  private scroll(): void {
    setTimeout(() => this.box()?.nativeElement.scrollTo({ top: 1e9, behavior: 'smooth' }), 50);
  }
}
