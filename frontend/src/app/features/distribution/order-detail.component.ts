import { Component, effect, inject, input, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { LookupService } from '../../core/lookup.service';
import { ToastService } from '../../core/toast.service';
import { LookupItem } from '../../core/models';
import { TrPipe } from '../../shared/tr.pipe';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import { I18nService } from '../../core/i18n.service';

/** Sales order: lines, FEFO allocation (confirm), shipment (stock movements + delivery) and cancellation. */
@Component({
  selector: 'sf-order-detail',
  imports: [DatePipe, DecimalPipe, FormsModule, RouterLink, TrPipe, StatusBadgeComponent],
  template: `
  <div class="page">@if (order(); as o) {
    <div class="page-header"><div><div class="muted small">{{ 'nav.orders' | tr }}</div><h1>{{ o['orderNumber'] }}</h1>
      <div class="row wrap" style="margin-top:8px"><sf-badge [value]="o['status']" /><sf-badge [value]="o['priority']" />
        <span class="muted small">{{ names()['customers']?.get(o['customerId']) }} · {{ names()['warehouses']?.get(o['warehouseId']) }}</span></div></div>
      <div class="row">
        @if (o['status'] === 'DRAFT' && canWrite) { <button class="btn primary" (click)="act('confirm')"><span class="icon">inventory</span>{{ 'order.confirm' | tr }}</button> }
        @if (['CONFIRMED','PREPARING'].includes(o['status']) && canWrite) { <button class="btn accent" (click)="act('ship')"><span class="icon">local_shipping</span>{{ 'order.ship' | tr }}</button> }
        @if (['DRAFT','CONFIRMED','PREPARING'].includes(o['status']) && canWrite) { <button class="btn" (click)="act('cancel')"><span class="icon">block</span>{{ 'common.cancel' | tr }}</button> }
      </div></div>
    <div class="grid grid-3">
      <div class="card span-2"><div class="card-header"><h3>{{ 'order.lines' | tr }}</h3></div>
        <div class="table-wrap"><table class="table"><thead><tr><th>{{ 'fields.productId' | tr }}</th><th>{{ 'fields.batchId' | tr }}</th><th class="num">{{ 'fields.quantity' | tr }}</th>
          <th class="num">{{ 'fields.unitPrice' | tr }}</th><th class="num">{{ 'fields.lineTotal' | tr }}</th><th></th></tr></thead>
          <tbody>@for (l of lines(); track l['id']) { <tr><td>{{ names()['products']?.get(l['productId']) }}</td>
            <td>@if (l['batchId']) {<a [routerLink]="['/traceability', l['batchId']]">{{ names()['batches']?.get(l['batchId']) ?? '…' }}</a>} @else {<span class="muted">{{ 'order.fefo' | tr }}</span>}</td>
            <td class="num">{{ l['quantity'] }}</td><td class="num">{{ l['unitPrice'] | number: '1.0-0' : i18n.locale }}</td><td class="num">{{ l['lineTotal'] | number: '1.0-0' : i18n.locale }}</td>
            <td>@if (o['status'] === 'DRAFT' && canWrite) {<button class="btn ghost sm icon-only" (click)="removeLine(l['id'])"><span class="icon">delete</span></button>}</td></tr> }</tbody>
          <tfoot><tr><td colspan="4" class="num"><strong>{{ 'order.total' | tr }}</strong></td><td class="num"><strong>{{ o['totalAmount'] | number: '1.0-0' : i18n.locale }} DZD</strong></td><td></td></tr></tfoot>
        </table></div>
        @if (o['status'] === 'DRAFT' && canWrite) {
          <div class="row add-line">
            <select class="input" [(ngModel)]="newProduct"><option [ngValue]="null">{{ 'fields.productId' | tr }}…</option>@for (p of products(); track p.id) {<option [ngValue]="p.id">{{ p.label }}</option>}</select>
            <input class="input qty" type="number" min="1" [(ngModel)]="newQty" />
            <button class="btn primary" [disabled]="!newProduct || !newQty" (click)="addLine()"><span class="icon">add</span>{{ 'order.addLine' | tr }}</button>
          </div>
        }
      </div>
      <div class="card"><div class="card-header"><h3>{{ 'case.details' | tr }}</h3></div><div class="card-body"><dl class="kv">
        <dt>{{ 'fields.orderDate' | tr }}</dt><dd>{{ o['orderDate'] | date: 'dd/MM/yyyy' }}</dd><dt>{{ 'fields.requestedDate' | tr }}</dt><dd>{{ o['requestedDate'] | date: 'dd/MM/yyyy' }}</dd>
        <dt>{{ 'fields.totalWeightKg' | tr }}</dt><dd>{{ o['totalWeightKg'] }} kg</dd><dt>{{ 'fields.notes' | tr }}</dt><dd>{{ o['notes'] ?? '—' }}</dd></dl>
        @if (delivery(); as d) { <div class="divider"></div><a class="btn" [routerLink]="['/deliveries', d['id']]"><span class="icon">local_shipping</span>{{ d['deliveryNumber'] }} · <sf-badge [value]="d['status']" /></a> }
      </div></div>
    </div>
  }</div>`,
  styles: [`.add-line { padding: 12px 14px; border-top: 1px solid var(--border); } .add-line select { flex: 1; } .qty { width: 110px; }`],
})
export class OrderDetailComponent {
  readonly id = input.required<string>();
  private api = inject(ApiService);
  private auth = inject(AuthService);
  private lookups = inject(LookupService);
  private toast = inject(ToastService);
  readonly i18n = inject(I18nService);
  readonly order = signal<Record<string, any> | null>(null);
  readonly lines = signal<Record<string, any>[]>([]);
  readonly delivery = signal<Record<string, any> | null>(null);
  readonly names = signal<Record<string, Map<string, string>>>({});
  readonly products = signal<LookupItem[]>([]);
  readonly canWrite = this.auth.can('distribution', 'write');
  newProduct: string | null = null;
  newQty = 10;

  constructor() {
    forkJoin({ customers: this.lookups.map('customers'), warehouses: this.lookups.map('warehouses'), products: this.lookups.map('products'), batches: this.lookups.map('batches') })
      .subscribe((m) => this.names.set(m));
    this.lookups.list('products').subscribe((p) => this.products.set(p));
    effect(() => this.load(this.id()));
  }

  load(id: string): void {
    this.api.get<Record<string, any>>(`/orders/${id}`).subscribe((o) => this.order.set(o));
    this.api.get<Record<string, any>[]>(`/orders/${id}/lines`).subscribe((l) => this.lines.set(l));
    this.api.page<Record<string, any>>('/deliveries', { orderId: id, size: 1 }).subscribe((p) => this.delivery.set(p.content[0] ?? null));
  }

  addLine(): void {
    this.api.post('/order-lines', { orderId: this.id(), productId: this.newProduct, quantity: this.newQty }).subscribe(() => { this.newProduct = null; this.load(this.id()); });
  }

  removeLine(id: string): void {
    this.api.delete(`/order-lines/${id}`).subscribe(() => this.load(this.id()));
  }

  act(action: 'confirm' | 'ship' | 'cancel'): void {
    this.api.post(`/orders/${this.id()}/${action}`).subscribe(() => {
      this.toast.success(this.i18n.t('order.' + action + 'Done'));
      this.lookups.invalidate('batches');
      this.load(this.id());
    });
  }
}
