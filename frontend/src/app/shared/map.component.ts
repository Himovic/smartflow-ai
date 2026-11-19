import { AfterViewInit, Component, ElementRef, OnDestroy, effect, input, viewChild } from '@angular/core';
import * as L from 'leaflet';

export interface MapMarker { lat: number; lng: number; label?: string; color?: string; popup?: string; size?: number; }
export interface MapLine { points: [number, number][]; color?: string; label?: string; dashed?: boolean; }

/** Lightweight Leaflet wrapper (OpenStreetMap tiles) used for sites, fleet, routes and recall impact. */
@Component({
  selector: 'sf-map',
  template: `<div #host class="map" [style.height.px]="height()"></div>`,
  styles: [`.map { width: 100%; border-radius: 12px; z-index: 0; }`],
})
export class MapComponent implements AfterViewInit, OnDestroy {
  readonly markers = input<MapMarker[]>([]);
  readonly lines = input<MapLine[]>([]);
  readonly height = input(360);
  private host = viewChild.required<ElementRef<HTMLDivElement>>('host');
  private map?: L.Map;
  private layer = L.layerGroup();

  constructor() {
    effect(() => this.render(this.markers(), this.lines()));
  }

  ngAfterViewInit(): void {
    this.map = L.map(this.host().nativeElement, { zoomControl: true, attributionControl: true }).setView([36.2, 4.2], 6);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 18, attribution: '© OpenStreetMap' }).addTo(this.map);
    this.layer.addTo(this.map);
    this.render(this.markers(), this.lines());
    setTimeout(() => this.map?.invalidateSize(), 200);
  }

  private render(markers: MapMarker[], lines: MapLine[]): void {
    if (!this.map) return;
    this.layer.clearLayers();
    const bounds: L.LatLngExpression[] = [];
    for (const l of lines) {
      if (l.points.length < 2) continue;
      L.polyline(l.points, { color: l.color ?? '#00a8cc', weight: 4, opacity: .85, dashArray: l.dashed ? '6 8' : undefined })
        .bindTooltip(l.label ?? '').addTo(this.layer);
      bounds.push(...l.points);
    }
    for (const m of markers) {
      if (m.lat == null || m.lng == null) continue;
      const c = L.circleMarker([m.lat, m.lng], { radius: m.size ?? 7, color: '#fff', weight: 2, fillColor: m.color ?? '#1a3a68', fillOpacity: .95 });
      if (m.label) c.bindTooltip(m.label);
      if (m.popup) c.bindPopup(m.popup);
      c.addTo(this.layer);
      bounds.push([m.lat, m.lng]);
    }
    if (bounds.length) this.map.fitBounds(L.latLngBounds(bounds as L.LatLngTuple[]).pad(0.15), { maxZoom: 11 });
  }

  ngOnDestroy(): void {
    this.map?.remove();
  }
}
