import { Component, input, output } from '@angular/core';
import { NgxEchartsDirective } from 'ngx-echarts';
import type { EChartsCoreOption } from 'echarts/core';

export const PALETTE = ['#1a3a68', '#00a8cc', '#14b8a6', '#f59e0b', '#7c3aed', '#e5484d', '#64748b', '#2563eb', '#16a34a'];

export const baseOption = (): EChartsCoreOption => ({
  color: PALETTE,
  textStyle: { fontFamily: 'Inter, Noto Sans Arabic, sans-serif' },
  grid: { left: 8, right: 16, top: 36, bottom: 8, containLabel: true },
  tooltip: { trigger: 'axis', backgroundColor: '#0b1f3a', borderWidth: 0, textStyle: { color: '#fff' } },
  legend: { top: 0, icon: 'roundRect', itemWidth: 10, itemHeight: 10, textStyle: { color: '#3b4a5f' } },
});

@Component({
  selector: 'sf-chart',
  imports: [NgxEchartsDirective],
  template: `<div echarts [options]="options()" [autoResize]="true" class="chart" [class.tall]="tall()" [style.height.px]="height()" (chartClick)="pick.emit($event)"></div>`,
})
export class ChartComponent {
  readonly options = input.required<EChartsCoreOption>();
  readonly tall = input(false);
  readonly height = input<number | null>(null);
  readonly pick = output<{ name?: string; seriesName?: string; data?: unknown }>();
}
