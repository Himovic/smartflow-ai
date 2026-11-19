import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Dialog } from '@angular/cdk/dialog';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ToastService } from '../../core/toast.service';
import { TrPipe } from '../../shared/tr.pipe';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import { MapComponent, MapLine, MapMarker } from '../../shared/map.component';
import { ConfirmDialogComponent } from '../../shared/dialogs';
import { QrDialogComponent } from '../../shared/qr-dialog.component';

type R = Record<string, any>;

/** Backward / forward batch traceability and product recall with impact analysis and report. */
@Component({
  selector: 'sf-traceability',
  imports: [DatePipe, DecimalPipe, FormsModule, RouterLink, TrPipe, StatusBadgeComponent, MapComponent],
  templateUrl: './traceability.component.html',
  styleUrl: './traceability.component.scss',
})
export class TraceabilityComponent {
  readonly id = input<string>();
  private api = inject(ApiService);
  readonly auth = inject(AuthService);
  private router = inject(Router);
  private toast = inject(ToastService);
  private dialog = inject(Dialog);
  readonly t = signal<R | null>(null);
  readonly loading = signal(false);
  readonly recent = signal<R[]>([]);
  query = '';

  readonly markers = computed<MapMarker[]>(() => {
    const t = this.t();
    if (!t) return [];
    const stock = (t['forward']['stock'] as R[]).map((s) => ({ lat: s['latitude'], lng: s['longitude'], color: '#1a3a68', size: 11, label: `${s['warehouse']} · ${s['quantity']}` }));
    const cust = (t['forward']['customers'] as R[]).map((c) => ({ lat: c['latitude'], lng: c['longitude'], color: '#e5484d', size: 7, label: `${c['name']} · ${c['quantity']}` }));
    return [...stock, ...cust];
  });
  readonly lines = computed<MapLine[]>(() => {
    const t = this.t();
    if (!t) return [];
    const site = t['backward']['site'] as R | null;
    if (!site) return [];
    return (t['forward']['stock'] as R[]).map((s) => ({ points: [[site['latitude'], site['longitude']], [s['latitude'], s['longitude']]] as [number, number][], dashed: true, color: '#00a8cc' }));
  });

  constructor() {
    effect(() => {
      const id = this.id();
      if (id) this.load(id);
      else this.api.page<R>('/batches', { size: 8, sort: 'productionDate,desc' }).subscribe((p) => this.recent.set(p.content));
    });
  }

  load(id: string): void {
    this.loading.set(true);
    this.api.get<R>(`/traceability/batches/${id}`).subscribe({ next: (t) => { this.t.set(t); this.loading.set(false); }, error: () => this.loading.set(false) });
  }

  search(): void {
    if (!this.query.trim()) return;
    this.api.get<R>('/traceability/search', { batchNumber: this.query.trim() }).subscribe({
      next: (t) => this.router.navigateByUrl('/traceability/' + t['batch']['id']),
      error: () => this.toast.warning(this.query + ' ?'),
    });
  }

  recall(): void {
    this.dialog.open(ConfirmDialogComponent, { data: { title: 'trace.recallTitle', message: 'trace.recallWarning', danger: true, confirmLabel: 'trace.recall',
      commentLabel: 'trace.recallReason', commentRequired: true } }).closed.subscribe((r) => {
      const res = r as { confirmed?: boolean; comment?: string } | undefined;
      if (!res?.confirmed) return;
      this.api.post<R>(`/traceability/batches/${this.id()}/recall`, { reason: res.comment, recallClass: 'Class II' }).subscribe((x) => {
        this.toast.warning(`${x['nonConformity']['reference']}`);
        this.load(this.id()!);
      });
    });
  }

  report(): void {
    this.api.download(`/traceability/batches/${this.id()}/recall-report`, undefined, 'recall.pdf');
  }

  qr(): void {
    this.dialog.open(QrDialogComponent, { data: { type: 'batch', id: this.id(), label: this.t()?.['batch']['batchNumber'] } });
  }

  release(): void {
    this.api.post(`/batches/${this.id()}/status/RELEASED`, { reason: 'Quality release' }).subscribe(() => this.load(this.id()!));
  }
}
