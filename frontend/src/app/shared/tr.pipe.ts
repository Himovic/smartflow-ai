import { Pipe, PipeTransform, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';

export function humanize(v: string): string {
  const last = v.includes('.') ? v.substring(v.lastIndexOf('.') + 1) : v;
  const s = last.replace(/_/g, ' ').replace(/([a-z])([A-Z])/g, '$1 $2').toLowerCase();
  return s.charAt(0).toUpperCase() + s.slice(1);
}

/** Translation with graceful fallback: unknown keys are humanized instead of shown raw. */
@Pipe({ name: 'tr', pure: false })
export class TrPipe implements PipeTransform {
  private t = inject(TranslateService);

  transform(key: string | null | undefined, params?: Record<string, unknown>): string {
    if (key === null || key === undefined || key === '') return '';
    const v = this.t.instant(key, params);
    return v === key ? humanize(key) : v;
  }
}

/** Translates an enumerated value (status, type…) using the `enum.` namespace. */
@Pipe({ name: 'enum', pure: false })
export class EnumPipe implements PipeTransform {
  private t = inject(TranslateService);

  transform(value: unknown): string {
    if (value === null || value === undefined || value === '') return '—';
    const key = 'enum.' + String(value);
    const v = this.t.instant(key);
    return v === key ? humanize(String(value)) : v;
  }
}
