import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';

export type Lang = 'fr' | 'en' | 'ar';

@Injectable({ providedIn: 'root' })
export class I18nService {
  private translate = inject(TranslateService);
  private http = inject(HttpClient);
  readonly lang = signal<Lang>('fr');
  readonly languages: { code: Lang; label: string }[] = [
    { code: 'fr', label: 'Français' },
    { code: 'en', label: 'English' },
    { code: 'ar', label: 'العربية' },
  ];

  init(): void {
    const stored = localStorage.getItem('sf.lang') as Lang | null;
    const browser = navigator.language?.slice(0, 2) as Lang;
    this.use(stored ?? (['fr', 'en', 'ar'].includes(browser) ? browser : 'fr'), false);
  }

  /** Switches language at runtime, including right-to-left layout for Arabic. */
  use(lang: Lang, persistOnServer = true): void {
    this.lang.set(lang);
    this.translate.use(lang);
    localStorage.setItem('sf.lang', lang);
    document.documentElement.lang = lang;
    document.documentElement.dir = lang === 'ar' ? 'rtl' : 'ltr';
    if (persistOnServer && localStorage.getItem('sf.access')) {
      this.http.put(`/api/v1/auth/me/language/${lang}`, {}).subscribe({ error: () => undefined });
    }
  }

  t(key: string, params?: Record<string, unknown>): string {
    return this.translate.instant(key, params);
  }

  get locale(): string {
    return { fr: 'fr-FR', en: 'en-GB', ar: 'ar-DZ' }[this.lang()];
  }
}
