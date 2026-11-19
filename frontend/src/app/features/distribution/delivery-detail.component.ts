import { Component, effect, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { LookupService } from '../../core/lookup.service';
import { TrPipe } from '../../shared/tr.pipe';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import { CollabPanelComponent } from '../../shared/collab-panel.component';
import { MapComponent, MapMarker, MapLine } from '../../shared/map.component';

@Component({
  selector: 'sf-delivery-detail',
  imports: [DatePipe, FormsModule, RouterLink, TrPipe, StatusBadgeComponent, CollabPanelComponent, MapComponent],
  template: `
  <div class="page">@if (d(); as d) {
    <div class="page-header"><div><div class="muted small">{{ 'nav.deliveries' | tr }}</div><h1>{{ d['deliveryNumber'] }}</h1>
      <div class="row wrap" style="margin-top:8px"><sf-badge [value]="d['status']" /><span class="muted small">{{ names()['customers']?.get(d['customerId']) }}</span></div></div>
      <div class="row">@if (canWrite) {
        @if (['PLANNED','DELAYED'].includes(d['status'])) { <button class="btn primary" (click)="act('depart')"><span class="icon">departure_board</span>{{ 'delivery.depart' | tr }}</button> }
        @if (['IN_TRANSIT','DELAYED'].includes(d['status'])) {
          <input class="input" style="width:110px" type="number" [(ngModel)]="distance" [placeholder]="'km'" />
          <button class="btn accent" (click)="act('deliver')"><span class="icon">where_to_vote</span>{{ 'delivery.deliver' | tr }}</button> }
        @if (['PLANNED','IN_TRANSIT','DELAYED'].includes(d['status'])) { <button class="btn" (click)="act('fail')"><span class="icon">report</span>{{ 'delivery.fail' | tr }}</button> }
      }</div></div>
    <div class="grid grid-3">
      <div class="span-2" style="display:flex;flex-direction:column;gap:16px">
        <div class="card"><div class="card-body"><dl class="kv">
          <dt>{{ 'fields.orderId' | tr }}</dt><dd>@if (d['orderId']) {<a [routerLink]="['/orders', d['orderId']]">{{ 'common.open' | tr }}</a>}</dd>
          <dt>{{ 'fields.warehouseId' | tr }}</dt><dd>{{ names()['warehouses']?.get(d['warehouseId']) }}</dd>
          <dt>{{ 'fields.vehicleId' | tr }}</dt><dd>{{ names()['vehicles']?.get(d['vehicleId']) ?? '—' }}</dd>
          <dt>{{ 'fields.driverId' | tr }}</dt><dd>{{ names()['drivers']?.get(d['driverId']) ?? '—' }}</dd>
          <dt>{{ 'fields.plannedDate' | tr }}</dt><dd>{{ d['plannedDate'] | date: 'dd/MM/yyyy' }} · {{ d['timeWindowStart'] }}–{{ d['timeWindowEnd'] }}</dd>
          <dt>{{ 'fields.deliveredAt' | tr }}</dt><dd>{{ d['deliveredAt'] ? (d['deliveredAt'] | date: 'dd/MM/yyyy HH:mm') : '—' }}</dd>
          <dt>{{ 'fields.delayMinutes' | tr }}</dt><dd>{{ d['delayMinutes'] ?? '—' }}</dd>
          <dt>{{ 'fields.distanceKm' | tr }}</dt><dd>{{ d['distanceKm'] ?? '—' }}</dd>
          <dt>{{ 'fields.fuelLiters' | tr }} / CO2</dt><dd>{{ d['fuelLiters'] ?? '—' }} L · {{ d['co2Kg'] ?? '—' }} kg <span class="nature PREDICTION">{{ 'nature.short.ESTIMATE' | tr }}</span></dd>
          <dt>{{ 'fields.proofNote' | tr }}</dt><dd>{{ d['proofNote'] ?? '—' }}</dd></dl></div></div>
        <div class="card"><div class="card-body"><sf-map [markers]="markers()" [lines]="lines()" [height]="300" /></div></div>
      </div>
      <div><sf-collab entityType="Delivery" [entityId]="id()" /></div>
    </div>
  }</div>`,
})
export class DeliveryDetailComponent {
  readonly id = input.required<string>();
  private api = inject(ApiService);
  private auth = inject(AuthService);
  private lookups = inject(LookupService);
  readonly d = signal<Record<string, any> | null>(null);
  readonly names = signal<Record<string, Map<string, string>>>({});
  readonly markers = signal<MapMarker[]>([]);
  readonly lines = signal<MapLine[]>([]);
  readonly canWrite = this.auth.can('distribution', 'write');
  distance: number | null = null;

  constructor() {
    forkJoin({ customers: this.lookups.map('customers'), warehouses: this.lookups.map('warehouses'), vehicles: this.lookups.map('vehicles'), drivers: this.lookups.map('drivers') })
      .subscribe((m) => this.names.set(m));
    effect(() => this.load(this.id()));
  }

  load(id: string): void {
    this.api.get<Record<string, any>>(`/deliveries/${id}`).subscribe((d) => {
      this.d.set(d);
      this.distance = d['distanceKm'];
      forkJoin({ c: this.api.get<Record<string, any>>(`/customers/${d['customerId']}`), w: this.api.get<Record<string, any>>(`/warehouses/${d['warehouseId']}`) }).subscribe(({ c, w }) => {
        this.markers.set([{ lat: w['latitude'], lng: w['longitude'], color: '#1a3a68', size: 10, label: w['name'] }, { lat: c['latitude'], lng: c['longitude'], color: '#14b8a6', size: 10, label: c['name'] }]);
        this.lines.set([{ points: [[w['latitude'], w['longitude']], [c['latitude'], c['longitude']]], dashed: true, color: '#00a8cc' }]);
      });
    });
  }

  act(a: string): void {
    const body = a === 'deliver' ? { distanceKm: this.distance, proofNote: 'Confirmed in SmartFlow' } : a === 'fail' ? { reason: 'Customer absent' } : {};
    this.api.post(`/deliveries/${this.id()}/${a}`, body).subscribe(() => this.load(this.id()));
  }
}
