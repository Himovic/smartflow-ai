import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { Page } from './models';

export type Params = Record<string, string | number | boolean | null | undefined>;

@Injectable({ providedIn: 'root' })
export class ApiService {
  private http = inject(HttpClient);
  readonly base = '/api/v1';

  private params(p?: Params): HttpParams {
    let hp = new HttpParams();
    Object.entries(p ?? {}).forEach(([k, v]) => {
      if (v !== null && v !== undefined && v !== '') hp = hp.set(k, String(v));
    });
    return hp;
  }

  get<T>(path: string, params?: Params): Observable<T> {
    return this.http.get<T>(this.base + path, { params: this.params(params) });
  }

  page<T>(path: string, params?: Params): Observable<Page<T>> {
    return this.get<Page<T>>(path, params);
  }

  post<T>(path: string, body: unknown = {}, params?: Params): Observable<T> {
    return this.http.post<T>(this.base + path, body, { params: this.params(params) });
  }

  put<T>(path: string, body: unknown): Observable<T> {
    return this.http.put<T>(this.base + path, body);
  }

  delete<T>(path: string): Observable<T> {
    return this.http.delete<T>(this.base + path);
  }

  upload<T>(path: string, file: File, extra: Record<string, string> = {}): Observable<T> {
    const fd = new FormData();
    fd.append('file', file);
    Object.entries(extra).forEach(([k, v]) => fd.append(k, v));
    return this.http.post<T>(this.base + path, fd);
  }

  blob(path: string, params?: Params): Observable<HttpResponse<Blob>> {
    return this.http.get(this.base + path, { params: this.params(params), responseType: 'blob', observe: 'response' });
  }

  /** Downloads a file produced by the API (Excel, PDF…) using the server-provided file name. */
  download(path: string, params?: Params, fallbackName = 'export'): void {
    this.blob(path, params).subscribe((res) => {
      const cd = res.headers.get('Content-Disposition') ?? '';
      const m = /filename="?([^";]+)"?/.exec(cd);
      const url = URL.createObjectURL(res.body as Blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = m?.[1] ?? fallbackName;
      a.click();
      setTimeout(() => URL.revokeObjectURL(url), 2000);
    });
  }

  /** Object URL for authenticated images (QR codes, photos). */
  objectUrl(path: string, params?: Params): Observable<string> {
    return this.blob(path, params).pipe(map((r) => URL.createObjectURL(r.body as Blob)));
  }
}
