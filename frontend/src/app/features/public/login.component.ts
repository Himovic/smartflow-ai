import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../core/auth.service';
import { ApiService } from '../../core/api.service';
import { I18nService, Lang } from '../../core/i18n.service';
import { TrPipe } from '../../shared/tr.pipe';
import { ToastService } from '../../core/toast.service';

@Component({
  selector: 'sf-login',
  imports: [FormsModule, RouterLink, TrPipe],
  template: `
    <div class="auth">
      <div class="panel brand-panel">
        <div class="brand"><img src="favicon.svg" width="44" height="44" alt="" /><div><strong>SmartFlow <span>AI</span></strong><small>+ QHSE</small></div></div>
        <h1>{{ 'landing.tagline' | tr }}</h1>
        <ul>
          <li><span class="icon">check_circle</span>{{ 'login.point1' | tr }}</li>
          <li><span class="icon">check_circle</span>{{ 'login.point2' | tr }}</li>
          <li><span class="icon">check_circle</span>{{ 'login.point3' | tr }}</li>
        </ul>
        <small class="by">D&amp;D | Data&amp;Development</small>
      </div>
      <div class="panel form-panel">
        <div class="langs">@for (l of i18n.languages; track l.code) { <button [class.on]="i18n.lang() === l.code" (click)="setLang(l.code)">{{ l.code.toUpperCase() }}</button> }</div>
        @if (mode() === 'login') {
          <form (ngSubmit)="submit()" class="form">
            <h2>{{ 'login.title' | tr }}</h2>
            <p class="muted">{{ 'login.subtitle' | tr }}</p>
            <div class="field"><label>{{ 'login.email' | tr }}</label><input class="input" name="email" type="email" autocomplete="username" [(ngModel)]="email" required /></div>
            <div class="field"><label>{{ 'login.password' | tr }}</label><input class="input" name="password" type="password" autocomplete="current-password" [(ngModel)]="password" required /></div>
            @if (error()) { <div class="err"><span class="icon">error</span>{{ error() }}</div> }
            <button class="btn primary big" type="submit" [disabled]="busy()">{{ 'login.signIn' | tr }}</button>
            <div class="row" style="justify-content:space-between">
              <a href="javascript:void(0)" (click)="mode.set('forgot')">{{ 'login.forgot' | tr }}</a>
              <a routerLink="/demo">{{ 'login.demoAccess' | tr }}</a>
            </div>
          </form>
        } @else if (mode() === 'forgot') {
          <form (ngSubmit)="forgot()" class="form">
            <h2>{{ 'login.forgotTitle' | tr }}</h2>
            <p class="muted">{{ 'login.forgotHint' | tr }}</p>
            <div class="field"><label>{{ 'login.email' | tr }}</label><input class="input" name="email" type="email" [(ngModel)]="email" required /></div>
            <button class="btn primary big" type="submit" [disabled]="busy()">{{ 'login.sendCode' | tr }}</button>
            <a href="javascript:void(0)" (click)="mode.set('reset')">{{ 'login.haveCode' | tr }}</a>
            <a href="javascript:void(0)" (click)="mode.set('login')">← {{ 'login.back' | tr }}</a>
          </form>
        } @else {
          <form (ngSubmit)="reset()" class="form">
            <h2>{{ 'login.resetTitle' | tr }}</h2>
            <div class="field"><label>{{ 'login.code' | tr }}</label><input class="input" name="token" [(ngModel)]="token" required /></div>
            <div class="field"><label>{{ 'login.newPassword' | tr }}</label><input class="input" name="np" type="password" [(ngModel)]="newPassword" required />
              <span class="hint">{{ 'login.passwordPolicy' | tr }}</span></div>
            <button class="btn primary big" type="submit" [disabled]="busy()">{{ 'login.reset' | tr }}</button>
            <a href="javascript:void(0)" (click)="mode.set('login')">← {{ 'login.back' | tr }}</a>
          </form>
        }
      </div>
    </div>`,
  styles: [`
    .auth { min-height: 100vh; display: grid; grid-template-columns: 1.1fr 1fr; }
    .brand-panel { background: radial-gradient(80% 80% at 100% 0%, rgba(0,168,204,.35), transparent 60%), linear-gradient(160deg, var(--navy-950), var(--navy-800)); color: #dbe8f8;
      padding: 48px 6vw; display: flex; flex-direction: column; gap: 26px; justify-content: center; }
    .brand { display: flex; gap: 12px; align-items: center; color: #fff; }
    .brand strong { font-size: 22px; display: block; } .brand span { color: var(--cyan-400); } .brand small { color: var(--teal-500); font-weight: 700; letter-spacing: .08em; }
    .brand-panel h1 { color: #fff; font-size: clamp(24px, 3vw, 34px); line-height: 1.25; max-width: 560px; }
    ul { list-style: none; padding: 0; margin: 0; display: grid; gap: 12px; }
    li { display: flex; gap: 10px; align-items: center; } li .icon { color: var(--teal-500); }
    .by { color: #8ea3c4; }
    .form-panel { display: flex; flex-direction: column; justify-content: center; align-items: center; padding: 40px 24px; position: relative; background: #fff; }
    .langs { position: absolute; top: 18px; inset-inline-end: 18px; display: flex; gap: 4px; }
    .langs button { border: 1px solid var(--border); background: #fff; border-radius: 6px; padding: 4px 8px; cursor: pointer; font: inherit; font-size: 12px; }
    .langs button.on { background: var(--navy-800); color: #fff; }
    .form { width: min(380px, 100%); display: flex; flex-direction: column; gap: 16px; }
    .big { height: 44px; justify-content: center; font-size: 15px; }
    .err { display: flex; gap: 8px; align-items: center; color: var(--red); background: var(--red-bg); padding: 10px 12px; border-radius: 8px; font-size: 13px; }
    @media (max-width: 860px) { .auth { grid-template-columns: 1fr; } .brand-panel { padding: 32px 24px; } }
  `],
})
export class LoginComponent {
  private auth = inject(AuthService);
  private api = inject(ApiService);
  private router = inject(Router);
  private toast = inject(ToastService);
  readonly i18n = inject(I18nService);
  readonly mode = signal<'login' | 'forgot' | 'reset'>('login');
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  email = '';
  password = '';
  token = '';
  newPassword = '';

  setLang(l: Lang): void {
    this.i18n.use(l, false);
  }

  submit(): void {
    this.busy.set(true);
    this.error.set(null);
    this.auth.login(this.email, this.password).subscribe({
      next: () => this.router.navigateByUrl('/dashboard'),
      error: (e: HttpErrorResponse) => {
        this.busy.set(false);
        this.error.set((e.error as { detail?: string })?.detail ?? this.i18n.t('login.failed'));
      },
    });
  }

  forgot(): void {
    this.busy.set(true);
    this.api.post<{ status: string }>('/auth/forgot-password', { email: this.email }).subscribe({
      next: (r) => { this.busy.set(false); this.toast.info(r.status); this.mode.set('reset'); },
      error: () => this.busy.set(false),
    });
  }

  reset(): void {
    this.busy.set(true);
    this.api.post('/auth/reset-password', { token: this.token, newPassword: this.newPassword }).subscribe({
      next: () => { this.busy.set(false); this.toast.success(this.i18n.t('login.resetDone')); this.mode.set('login'); },
      error: () => this.busy.set(false),
    });
  }
}
