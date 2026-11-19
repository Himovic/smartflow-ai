import { Injectable, signal } from '@angular/core';

/** Global working context chosen in the top bar (site filter applied to dashboards and lists). */
@Injectable({ providedIn: 'root' })
export class ContextService {
  readonly siteId = signal<string | null>(localStorage.getItem('sf.site'));

  setSite(id: string | null): void {
    this.siteId.set(id);
    if (id) localStorage.setItem('sf.site', id);
    else localStorage.removeItem('sf.site');
  }
}
