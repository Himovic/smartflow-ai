import type { EChartsCoreOption } from 'echarts/core';
import { baseOption, PALETTE } from '../../shared/chart.component';

export interface Series { months: string[]; values: number[]; }

export function lineChart(series: Record<string, Series>, opts: { area?: boolean; bar?: string[]; colors?: string[] } = {}): EChartsCoreOption {
  const names = Object.keys(series);
  const months = series[names[0]]?.months ?? [];
  return {
    ...baseOption(),
    color: opts.colors ?? PALETTE,
    xAxis: { type: 'category', data: months, axisLine: { lineStyle: { color: '#cfd8e5' } }, axisLabel: { color: '#6b7a90' } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#eef2f7' } }, axisLabel: { color: '#6b7a90' } },
    series: names.map((n) => opts.bar?.includes(n)
      ? { name: n, type: 'bar', data: series[n].values, barMaxWidth: 18, itemStyle: { borderRadius: [4, 4, 0, 0] } }
      : { name: n, type: 'line', smooth: true, symbolSize: 5, data: series[n].values, areaStyle: opts.area ? { opacity: 0.12 } : undefined }),
  };
}

export function stackedBars(series: Record<string, Series>, colors?: string[]): EChartsCoreOption {
  const names = Object.keys(series);
  return {
    ...baseOption(),
    color: colors ?? PALETTE,
    xAxis: { type: 'category', data: series[names[0]]?.months ?? [], axisLabel: { color: '#6b7a90' } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#eef2f7' } } },
    series: names.map((n) => ({ name: n, type: 'bar', stack: 'total', data: series[n].values, barMaxWidth: 22 })),
  };
}

export function donut(data: { name: string; value: number }[], colors?: string[]): EChartsCoreOption {
  return {
    ...baseOption(),
    color: colors ?? PALETTE,
    tooltip: { trigger: 'item', backgroundColor: '#0b1f3a', textStyle: { color: '#fff' } },
    legend: { bottom: 0, type: 'scroll', textStyle: { color: '#3b4a5f' } },
    series: [{ type: 'pie', radius: ['48%', '72%'], center: ['50%', '45%'], itemStyle: { borderColor: '#fff', borderWidth: 2, borderRadius: 4 },
      label: { show: false }, data }],
  };
}

export function hbar(data: { name: string; value: number }[], color = '#00a8cc'): EChartsCoreOption {
  const d = [...data].reverse();
  return {
    ...baseOption(),
    legend: { show: false },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, backgroundColor: '#0b1f3a', textStyle: { color: '#fff' } },
    xAxis: { type: 'value', splitLine: { lineStyle: { color: '#eef2f7' } } },
    yAxis: { type: 'category', data: d.map((x) => x.name), axisLabel: { color: '#3b4a5f', width: 160, overflow: 'truncate' } },
    series: [{ type: 'bar', data: d.map((x) => x.value), itemStyle: { color, borderRadius: [0, 4, 4, 0] }, barMaxWidth: 16 }],
  };
}

export function gauge(value: number, label: string): EChartsCoreOption {
  return {
    series: [{
      type: 'gauge', min: 0, max: 100, startAngle: 210, endAngle: -30, radius: '95%', center: ['50%', '58%'],
      axisLine: { lineStyle: { width: 16, color: [[0.3, '#16a34a'], [0.6, '#f59e0b'], [1, '#e5484d']] } },
      pointer: { length: '55%', width: 5, offsetCenter: [0, '-8%'], itemStyle: { color: '#0b1f3a' } }, axisTick: { show: false }, splitLine: { show: false },
      axisLabel: { show: false }, title: { offsetCenter: [0, '72%'], fontSize: 12, color: '#6b7a90' },
      detail: { valueAnimation: true, fontSize: 30, fontWeight: 700, offsetCenter: [0, '42%'], color: '#0b1f3a' }, data: [{ value, name: label }],
    }],
  };
}
