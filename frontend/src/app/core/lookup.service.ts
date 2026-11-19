import { Injectable, inject } from '@angular/core';
import { Observable, map, shareReplay, of, catchError } from 'rxjs';
import { ApiService } from './api.service';
import { LookupItem } from './models';

/** Cached id → label dictionaries used to display references (site, product, batch…) in tables and forms. */
@Injectable({ providedIn: 'root' })
export class LookupService {
  private api = inject(ApiService);
  private cache = new Map<string, Observable<LookupItem[]>>();

  list(endpoint: string): Observable<LookupItem[]> {
    if (!this.cache.has(endpoint)) {
      this.cache.set(endpoint, this.api.get<LookupItem[]>(`/${endpoint}/lookup`).pipe(catchError(() => of([])), shareReplay(1)));
    }
    return this.cache.get(endpoint)!;
  }

  map(endpoint: string): Observable<Map<string, string>> {
    return this.list(endpoint).pipe(map((l) => new Map(l.map((i) => [i.id, i.label]))));
  }

  invalidate(endpoint?: string): void {
    if (endpoint) this.cache.delete(endpoint);
    else this.cache.clear();
  }
}
