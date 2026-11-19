import { Component, inject } from '@angular/core';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { FormsModule } from '@angular/forms';
import { TrPipe } from './tr.pipe';

export interface ConfirmData {
  title: string;
  message?: string;
  confirmLabel?: string;
  danger?: boolean;
  commentLabel?: string;
  commentRequired?: boolean;
}

@Component({
  selector: 'sf-confirm',
  imports: [FormsModule, TrPipe],
  template: `
    <div class="dlg">
      <h2>{{ data.title | tr }}</h2>
      @if (data.message) { <p class="muted msg">{{ data.message | tr }}</p> }
      @if (data.commentLabel) {
        <div class="field"><label>{{ data.commentLabel | tr }}{{ data.commentRequired ? ' *' : '' }}</label>
          <textarea class="input" [(ngModel)]="comment" rows="3"></textarea></div>
      }
      <div class="row actions">
        <button class="btn" (click)="ref.close()">{{ 'common.cancel' | tr }}</button>
        <button class="btn" [class.danger]="data.danger" [class.primary]="!data.danger" [disabled]="data.commentRequired && !comment.trim()"
          (click)="ref.close({ confirmed: true, comment })">{{ (data.confirmLabel ?? 'common.confirm') | tr }}</button>
      </div>
    </div>`,
})
export class ConfirmDialogComponent {
  readonly data = inject<ConfirmData>(DIALOG_DATA);
  readonly ref = inject(DialogRef<{ confirmed: boolean; comment: string }>);
  comment = '';
}

/** Shared dialog chrome (styles are global so every dialog looks the same). */
export const DIALOG_STYLES = `
.dlg { background: #fff; border-radius: 16px; padding: 22px 24px; width: min(720px, 94vw); max-height: 90vh; overflow: auto; box-shadow: var(--shadow-lg); }
.dlg h2 { margin-bottom: 6px; }
.dlg .msg { margin-bottom: 14px; }
.dlg .actions { justify-content: flex-end; margin-top: 18px; }
`;
