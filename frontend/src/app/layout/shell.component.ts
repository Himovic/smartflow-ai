import { Component, ElementRef, HostListener, computed, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { filter, forkJoin, of, catchError, debounceTime, Subject, switchMap } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AuthService } from '../core/auth.service';
import { I18nService, Lang } from '../core/i18n.service';
import { NAV } from '../core/nav';
import { TrPipe, EnumPipe } from '../shared/tr.pipe';
import { ApiService } from '../core/api.service';
import { RealtimeService } from '../core/realtime.service';
import { ContextService } from '../core/context.service';
import { LookupService } from '../core/lookup.service';
import { LookupItem } from '../core/models';

interface SearchHit { label: string; sub: string; route: string; icon: string; }

@Component({
  selector: 'sf-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, FormsModule, TrPipe, EnumPipe],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss',
})
export class ShellComponent {
  readonly auth = inject(AuthService);
  readonly i18n = inject(I18nService);
  readonly rt = inject(RealtimeService);
  readonly ctx = inject(ContextService);
  private api = inject(ApiService);
  private router = inject(Router);
  private lookups = inject(LookupService);
  private host = inject(ElementRef);

  readonly collapsed = signal(localStorage.getItem('sf.sidebar') === 'collapsed');
  readonly mobileOpen = signal(false);
  readonly menu = signal<'user' | 'lang' | 'org' | null>(null);
  readonly sites = signal<LookupItem[]>([]);
  readonly tenants = signal<{ id: string; code: string; name: string; demo: boolean }[]>([]);
  readonly crumbs = signal<string[]>([]);
  readonly query = signal('');
  readonly hits = signal<SearchHit[]>([]);
  private search$ = new Subject<string>();

  readonly nav = computed(() =>
    NAV.map((g) => ({ ...g, items: g.items.filter((i) => this.auth.can(i.module, i.action ?? 'read')) })).filter((g) => g.items.length));
  readonly initials = computed(() => {
    const m = this.auth.me();
    return m ? (m.firstName[0] + m.lastName[0]).toUpperCase() : '';
  });
  readonly currentTenant = computed(() => {
    const id = this.auth.tenantOverride() ?? this.auth.me()?.homeTenantId;
    return this.tenants().find((t) => t.id === id)?.name ?? this.auth.me()?.tenant.name;
  });

  constructor() {
    this.lookups.list('sites').subscribe((s) => this.sites.set(s));
    if (this.auth.me()?.role === 'SUPER_ADMIN') this.api.get<{ id: string; code: string; name: string; demo: boolean }[]>('/platform/tenants').subscribe((t) => this.tenants.set(t));
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd), takeUntilDestroyed()).subscribe(() => {
      this.mobileOpen.set(false);
      this.crumbs.set(this.router.url.split('?')[0].split('/').filter((s) => s && !/^[0-9a-f-]{36}$/.test(s)));
    });
    this.crumbs.set(this.router.url.split('?')[0].split('/').filter((s) => s && !/^[0-9a-f-]{36}$/.test(s)));
    this.search$.pipe(debounceTime(250), switchMap((q) => this.runSearch(q)), takeUntilDestroyed()).subscribe((h) => this.hits.set(h));
  }

  toggleSidebar(): void {
    this.collapsed.update((c) => !c);
    localStorage.setItem('sf.sidebar', this.collapsed() ? 'collapsed' : 'open');
  }

  setLang(l: Lang): void {
    this.i18n.use(l);
    this.menu.set(null);
  }

  onSearch(q: string): void {
    this.query.set(q);
    if (q.trim().length < 2) {
      this.hits.set([]);
      return;
    }
    this.search$.next(q.trim());
  }

  go(h: SearchHit): void {
    this.query.set('');
    this.hits.set([]);
    this.router.navigateByUrl(h.route);
  }

  private runSearch(q: string) {
    type Rec = Record<string, string>;
    const all: { path: string; module: string; icon: string; route: (r: Rec) => string; label: (r: Rec) => string; sub: string }[] = [
      { path: '/non-conformities', module: 'nc', icon: 'report', route: (r) => `/non-conformities/${r['id']}`, label: (r) => `${r['reference']} — ${r['title']}`, sub: 'nav.nonConformities' },
      { path: '/incidents', module: 'incidents', icon: 'emergency', route: (r) => `/incidents/${r['id']}`, label: (r) => `${r['reference']} — ${r['title']}`, sub: 'nav.incidents' },
      { path: '/batches', module: 'inventory', icon: 'qr_code_2', route: (r) => `/traceability/${r['id']}`, label: (r) => r['batchNumber'], sub: 'nav.batches' },
      { path: '/products', module: 'catalog', icon: 'category', route: () => `/products`, label: (r) => `${r['sku']} — ${r['name']}`, sub: 'nav.products' },
      { path: '/customers', module: 'catalog', icon: 'storefront', route: () => `/customers`, label: (r) => r['name'], sub: 'nav.customers' },
      { path: '/equipment', module: 'assets', icon: 'precision_manufacturing', route: () => `/equipment`, label: (r) => `${r['code']} — ${r['name']}`, sub: 'nav.equipment' },
      { path: '/capa', module: 'capa', icon: 'task_alt', route: (r) => `/capa/${r['id']}`, label: (r) => `${r['reference']} — ${r['title']}`, sub: 'nav.capa' },
    ];
    const sources = all.filter((s) => this.auth.can(s.module));
    return forkJoin(sources.map((s) => this.api.page<Record<string, string>>(s.path, { q, size: 4 }).pipe(catchError(() => of({ content: [] as Record<string, string>[] })))))
      .pipe(switchMap((pages) => of(pages.flatMap((p, i) => p.content.map((r) => ({
        label: sources[i].label(r), sub: sources[i].sub, route: sources[i].route(r), icon: sources[i].icon }))))));
  }

  @HostListener('document:click', ['$event'])
  onDocClick(e: MouseEvent): void {
    if (!this.host.nativeElement.querySelector('.topbar')?.contains(e.target as Node)) {
      this.menu.set(null);
      this.hits.set([]);
    }
  }
}
