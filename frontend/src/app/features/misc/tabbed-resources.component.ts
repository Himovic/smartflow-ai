import { Component, input, signal } from '@angular/core';
import { TrPipe } from '../../shared/tr.pipe';
import { ResourceListComponent } from '../resources/resource-list.component';

export interface TabDef { resource: string; label: string; preset?: Record<string, string>; }

/** Module page grouping several registers in tabs (inventory, fleet, training, hygiene…). */
@Component({
  selector: 'sf-tabbed',
  imports: [TrPipe, ResourceListComponent],
  template: `
  <div class="page">
    <div class="page-header"><div class="row"><div class="title-icon"><span class="icon">{{ icon() }}</span></div>
      <div><h1>{{ title() | tr }}</h1><div class="subtitle">{{ (title() + 'Subtitle') | tr }}</div></div></div></div>
    <div class="tabs">@for (t of tabs(); track t.resource + t.label; let i = $index) {
      <button class="tab" [class.active]="i === active()" (click)="active.set(i)">{{ t.label | tr }}</button> }</div>
    @for (t of tabs(); track t.resource + t.label; let i = $index) {
      @if (i === active()) { <sf-resource-list [resource]="t.resource" [preset]="t.preset ?? {}" [embedded]="true" /> }
    }
  </div>`,
  styles: [`.title-icon { width: 42px; height: 42px; border-radius: 12px; display: grid; place-items: center; background: linear-gradient(135deg, var(--navy-800), var(--cyan-500)); color: #fff; }`],
})
export class TabbedResourcesComponent {
  readonly title = input.required<string>();
  readonly icon = input('apps');
  readonly tabs = input.required<TabDef[]>();
  readonly active = signal(0);
}
