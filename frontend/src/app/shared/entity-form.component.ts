import { Component, OnInit, inject, signal } from '@angular/core';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { ApiService } from '../core/api.service';
import { LookupService } from '../core/lookup.service';
import { LookupItem } from '../core/models';
import { FieldDef, ResourceDef } from '../features/resources/resource.model';
import { TrPipe, EnumPipe } from './tr.pipe';
import { ToastService } from '../core/toast.service';

export interface EntityFormData {
  resource: ResourceDef;
  entity?: Record<string, unknown>;
  preset?: Record<string, unknown>;
  endpoint?: string;
}

/** Metadata-driven create/edit form (validation is enforced server side and mirrored here for required fields). */
@Component({
  selector: 'sf-entity-form',
  imports: [FormsModule, TrPipe, EnumPipe],
  template: `
    <form class="dlg" (ngSubmit)="save()" #frm="ngForm">
      <div class="row"><span class="icon head-icon">{{ data.resource.icon }}</span>
        <h2>{{ (isEdit ? 'common.edit' : 'common.create') | tr }} — {{ ('res.' + data.resource.key) | tr }}</h2></div>
      <div class="divider"></div>
      <div class="form-grid">
        @for (fd of data.resource.fields; track fd.key) {
          <div class="field" [class.full]="fd.full || fd.type === 'textarea'">
            <label>{{ ('fields.' + fd.key) | tr }}{{ fd.required ? ' *' : '' }}</label>
            @switch (fd.type) {
              @case ('textarea') { <textarea class="input" [name]="fd.key" [(ngModel)]="model[fd.key]" [required]="!!fd.required" rows="3"></textarea> }
              @case ('number') { <input class="input" type="number" [name]="fd.key" [step]="fd.step ?? 1" [(ngModel)]="model[fd.key]" [required]="!!fd.required" /> }
              @case ('date') { <input class="input" type="date" [name]="fd.key" [(ngModel)]="model[fd.key]" [required]="!!fd.required" /> }
              @case ('datetime') { <input class="input" type="datetime-local" [name]="fd.key" [(ngModel)]="model[fd.key]" [required]="!!fd.required" /> }
              @case ('time') { <input class="input" type="time" [name]="fd.key" [(ngModel)]="model[fd.key]" /> }
              @case ('boolean') { <label class="checkbox"><input type="checkbox" [name]="fd.key" [(ngModel)]="model[fd.key]" /> {{ 'common.yes' | tr }}</label> }
              @case ('select') {
                <select class="input" [name]="fd.key" [(ngModel)]="model[fd.key]" [required]="!!fd.required">
                  <option [ngValue]="null">—</option>
                  @for (o of fd.options; track o) { <option [ngValue]="o">{{ o | enum }}</option> }
                </select>
              }
              @case ('lookup') {
                <select class="input" [name]="fd.key" [(ngModel)]="model[fd.key]" [required]="!!fd.required">
                  <option [ngValue]="null">—</option>
                  @for (o of lookups()[fd.lookup!] ?? []; track o.id) { <option [ngValue]="o.id">{{ o.label }}</option> }
                </select>
              }
              @case ('tags') { <input class="input" [name]="fd.key" [(ngModel)]="tags[fd.key]" [placeholder]="'common.commaSeparated' | tr" /> }
              @default { <input class="input" [name]="fd.key" [(ngModel)]="model[fd.key]" [required]="!!fd.required" /> }
            }
          </div>
        }
      </div>
      <div class="row actions">
        <button type="button" class="btn" (click)="ref.close()">{{ 'common.cancel' | tr }}</button>
        <button type="submit" class="btn primary" [disabled]="saving() || frm.invalid"><span class="icon">save</span>{{ 'common.save' | tr }}</button>
      </div>
    </form>`,
  styles: [`.head-icon { color: var(--cyan-500); font-size: 26px; }`],
})
export class EntityFormComponent implements OnInit {
  readonly data = inject<EntityFormData>(DIALOG_DATA);
  readonly ref = inject(DialogRef<Record<string, unknown>>);
  private api = inject(ApiService);
  private lookupSvc = inject(LookupService);
  private toast = inject(ToastService);
  readonly lookups = signal<Record<string, LookupItem[]>>({});
  readonly saving = signal(false);
  model: Record<string, any> = {};
  tags: Record<string, string> = {};
  isEdit = false;

  ngOnInit(): void {
    this.isEdit = !!this.data.entity?.['id'];
    this.model = { ...(this.data.resource.defaults ?? {}), ...(this.data.entity ?? {}), ...(this.data.preset ?? {}) };
    for (const fd of this.data.resource.fields) {
      if (fd.type === 'datetime' && this.model[fd.key]) this.model[fd.key] = String(this.model[fd.key]).substring(0, 16);
      if (fd.type === 'tags') this.tags[fd.key] = ((this.model[fd.key] as string[]) ?? []).join(', ');
      if (fd.type === 'select' && this.model[fd.key] !== undefined && this.model[fd.key] !== null) this.model[fd.key] = String(this.model[fd.key]);
    }
    const needed = [...new Set(this.data.resource.fields.filter((x) => x.type === 'lookup').map((x) => x.lookup!))];
    if (needed.length) {
      const calls: Record<string, ReturnType<LookupService['list']>> = Object.fromEntries(needed.map((l) => [l, this.lookupSvc.list(l)]));
      forkJoin(calls).subscribe((r) => this.lookups.set(r));
    }
  }

  save(): void {
    const body: Record<string, unknown> = { ...this.model };
    for (const fd of this.data.resource.fields as FieldDef[]) {
      if (fd.type === 'datetime' && body[fd.key]) body[fd.key] = new Date(body[fd.key] as string).toISOString();
      if (fd.type === 'tags') body[fd.key] = (this.tags[fd.key] ?? '').split(',').map((s) => s.trim()).filter(Boolean);
      if (fd.type === 'number' && body[fd.key] === '') body[fd.key] = null;
      if (fd.type === 'select' && ['severity', 'probability'].includes(fd.key) && body[fd.key] !== null && body[fd.key] !== undefined) body[fd.key] = Number(body[fd.key]);
    }
    const endpoint = '/' + (this.data.endpoint ?? this.data.resource.endpoint);
    this.saving.set(true);
    const call = this.isEdit ? this.api.put<Record<string, unknown>>(`${endpoint}/${this.model['id']}`, body) : this.api.post<Record<string, unknown>>(endpoint, body);
    call.subscribe({
      next: (r) => {
        this.toast.success(this.isEdit ? 'Saved' : 'Created');
        this.lookupSvc.invalidate(this.data.resource.endpoint);
        this.ref.close(r);
      },
      error: () => this.saving.set(false),
    });
  }
}
