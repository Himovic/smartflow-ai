import { Component, OnInit, inject, signal } from '@angular/core';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ApiService } from '../core/api.service';
import { TrPipe } from './tr.pipe';

@Component({
  selector: 'sf-qr-dialog',
  imports: [TrPipe],
  template: `
    <div class="dlg qr">
      <h2>{{ 'qr.title' | tr }}</h2>
      <p class="muted">{{ data.label }}</p>
      @if (src()) { <img [src]="src()" alt="QR" width="280" height="280" /> } @else { <div class="skeleton" style="width:280px;height:280px"></div> }
      <p class="muted small">{{ 'qr.hint' | tr }}</p>
      <div class="row actions">
        @if (src()) { <a class="btn" [href]="src()" [download]="data.label + '.png'"><span class="icon">download</span>PNG</a> }
        <button class="btn" (click)="print()"><span class="icon">print</span>{{ 'common.print' | tr }}</button>
        <button class="btn primary" (click)="ref.close()">{{ 'common.close' | tr }}</button>
      </div>
    </div>`,
  styles: [`.qr { text-align: center; width: 380px; } .qr img { margin: 12px auto; display: block; border: 1px solid var(--border); border-radius: 12px; padding: 8px; }`],
})
export class QrDialogComponent implements OnInit {
  readonly data = inject<{ type: string; id: string; label: string }>(DIALOG_DATA);
  readonly ref = inject(DialogRef);
  private api = inject(ApiService);
  readonly src = signal<string | null>(null);

  ngOnInit(): void {
    this.api.objectUrl(`/qr/${this.data.type}/${this.data.id}.png`, { size: 560 }).subscribe((u) => this.src.set(u));
  }

  print(): void {
    const w = window.open('', '_blank');
    w?.document.write(`<html><body style="text-align:center;font-family:sans-serif"><img src="${this.src()}" width="320"/><h3>${this.data.label}</h3>
      <small>SmartFlow AI + QHSE</small><script>setTimeout(()=>print(),300)</script></body></html>`);
  }
}
