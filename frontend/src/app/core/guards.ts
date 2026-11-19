import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { I18nService } from './i18n.service';
import { RealtimeService } from './realtime.service';

export const authGuard: CanActivateFn = async () => {
  // resolve dependencies before any await: inject() is only valid synchronously
  const auth = inject(AuthService);
  const router = inject(Router);
  const i18n = inject(I18nService);
  const realtime = inject(RealtimeService);
  if (auth.me()) return true;
  const me = await auth.loadMe();
  if (!me) return router.parseUrl('/login');
  i18n.use((localStorage.getItem('sf.lang') as 'fr' | 'en' | 'ar') ?? (me.language as 'fr' | 'en' | 'ar') ?? 'fr', false);
  realtime.connect();
  return true;
};

/** Route guard reading `data.module` (and optional `data.action`) to enforce RBAC + enabled modules. */
export const permissionGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const module = route.data?.['module'] as string | undefined;
  if (!module) return true;
  return auth.can(module, (route.data?.['action'] as string) ?? 'read') ? true : inject(Router).parseUrl('/forbidden');
};
