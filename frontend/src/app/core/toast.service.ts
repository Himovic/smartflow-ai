import { Injectable, signal } from '@angular/core';

export interface Toast {
  id: number;
  kind: 'success' | 'error' | 'info' | 'warning';
  text: string;
}

@Injectable({ providedIn: 'root' })
export class ToastService {
  readonly toasts = signal<Toast[]>([]);
  private seq = 0;

  show(kind: Toast['kind'], text: string, ms = 4500): void {
    const t = { id: ++this.seq, kind, text };
    this.toasts.update((l) => [...l.slice(-4), t]);
    setTimeout(() => this.dismiss(t.id), ms);
  }

  success(text: string): void { this.show('success', text); }
  error(text: string): void { this.show('error', text, 7000); }
  info(text: string): void { this.show('info', text); }
  warning(text: string): void { this.show('warning', text, 6000); }

  dismiss(id: number): void {
    this.toasts.update((l) => l.filter((t) => t.id !== id));
  }
}
