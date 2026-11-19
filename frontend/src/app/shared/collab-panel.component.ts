import { Component, effect, inject, input, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/api.service';
import { TrPipe } from './tr.pipe';

interface CommentRow { id: string; body: string; authorName: string; createdAt: string; }
interface AttachmentRow { id: string; fileName: string; contentType: string; sizeBytes: number; kind: string; uploadedBy: string; createdAt: string; }

/** Comments and evidence attachments (photos, documents) attached to any record. */
@Component({
  selector: 'sf-collab',
  imports: [FormsModule, DatePipe, DecimalPipe, TrPipe],
  template: `
    <div class="card">
      <div class="card-header"><span class="icon" style="color:var(--cyan-500)">attach_file</span><h3>{{ 'collab.evidence' | tr }}</h3>
        <label class="btn sm"><span class="icon">upload</span>{{ 'collab.upload' | tr }}<input type="file" hidden accept="image/*,application/pdf,.xlsx,.docx,.csv,.txt" capture="environment" (change)="upload($event)" /></label>
      </div>
      <div class="card-body">
        @if (!attachments().length) { <p class="muted small">{{ 'collab.noFiles' | tr }}</p> }
        <div class="files">
          @for (a of attachments(); track a.id) {
            <button class="file" (click)="openFile(a)">
              @if (thumbs()[a.id]) { <img [src]="thumbs()[a.id]" alt="" /> } @else { <span class="icon big">{{ a.contentType.includes('pdf') ? 'picture_as_pdf' : 'description' }}</span> }
              <span class="fname">{{ a.fileName }}</span><span class="muted small">{{ a.sizeBytes / 1024 | number: '1.0-0' }} kB · {{ a.uploadedBy }}</span>
            </button>
          }
        </div>
      </div>
    </div>
    <div class="card" style="margin-top:16px">
      <div class="card-header"><span class="icon" style="color:var(--cyan-500)">forum</span><h3>{{ 'collab.comments' | tr }}</h3></div>
      <div class="card-body">
        @for (c of comments(); track c.id) {
          <div class="comment"><strong>{{ c.authorName }}</strong> <span class="muted small">{{ c.createdAt | date: 'dd/MM/yyyy HH:mm' }}</span><p>{{ c.body }}</p></div>
        }
        <div class="row" style="margin-top:10px">
          <input class="input" [(ngModel)]="text" [placeholder]="'collab.addComment' | tr" (keyup.enter)="send()" />
          <button class="btn primary icon-only" (click)="send()" [disabled]="!text.trim()"><span class="icon">send</span></button>
        </div>
      </div>
    </div>`,
  styles: [`
    .files { display: grid; grid-template-columns: repeat(auto-fill, minmax(140px, 1fr)); gap: 10px; }
    .file { display: flex; flex-direction: column; gap: 4px; align-items: flex-start; padding: 8px; border: 1px solid var(--border); border-radius: 10px; background: var(--surface-2); cursor: pointer; font: inherit; text-align: start; }
    .file img { width: 100%; height: 90px; object-fit: cover; border-radius: 6px; }
    .file .big { font-size: 40px; color: var(--muted); height: 90px; display: grid; place-items: center; width: 100%; }
    .fname { font-size: 12px; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; width: 100%; }
    .comment { padding: 8px 0; border-bottom: 1px solid var(--border); }
    .comment p { margin-top: 4px; white-space: pre-wrap; }
  `],
})
export class CollabPanelComponent {
  readonly entityType = input.required<string>();
  readonly entityId = input.required<string>();
  private api = inject(ApiService);
  readonly comments = signal<CommentRow[]>([]);
  readonly attachments = signal<AttachmentRow[]>([]);
  readonly thumbs = signal<Record<string, string>>({});
  text = '';

  constructor() {
    effect(() => this.load(this.entityType(), this.entityId()));
  }

  load(type: string, id: string): void {
    this.api.get<CommentRow[]>(`/collab/${type}/${id}/comments`).subscribe((c) => this.comments.set(c));
    this.api.get<AttachmentRow[]>(`/collab/${type}/${id}/attachments`).subscribe((a) => {
      this.attachments.set(a);
      a.filter((x) => x.contentType.startsWith('image/')).forEach((x) =>
        this.api.objectUrl(`/collab/attachments/${x.id}/content`).subscribe((u) => this.thumbs.update((t) => ({ ...t, [x.id]: u }))));
    });
  }

  upload(ev: Event): void {
    const file = (ev.target as HTMLInputElement).files?.[0];
    if (!file) return;
    this.api.upload(`/collab/${this.entityType()}/${this.entityId()}/attachments`, file, { kind: file.type.startsWith('image/') ? 'PHOTO' : 'DOCUMENT' })
      .subscribe(() => this.load(this.entityType(), this.entityId()));
  }

  openFile(a: AttachmentRow): void {
    this.api.objectUrl(`/collab/attachments/${a.id}/content`).subscribe((u) => window.open(u, '_blank'));
  }

  send(): void {
    if (!this.text.trim()) return;
    this.api.post(`/collab/${this.entityType()}/${this.entityId()}/comments`, { body: this.text }).subscribe(() => {
      this.text = '';
      this.load(this.entityType(), this.entityId());
    });
  }
}
