import { Component, OnInit, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { I18nService, Lang } from '../../core/i18n.service';
import { TrPipe, EnumPipe } from '../../shared/tr.pipe';
import { ToastService } from '../../core/toast.service';

interface DemoInfo {
  enabled: boolean; ready: boolean; seeding: boolean; product: string; tagline: string; developer: string; organization: string; url: string;
  password?: string; accounts?: { role: string; email: string; name: string; scope: string }[]; notice: string;
}

@Component({
  selector: 'sf-demo-landing',
  imports: [RouterLink, TrPipe, EnumPipe],
  templateUrl: './demo-landing.component.html',
  styleUrl: './demo-landing.component.scss',
})
export class DemoLandingComponent implements OnInit {
  private api = inject(ApiService);
  private auth = inject(AuthService);
  private router = inject(Router);
  private toast = inject(ToastService);
  readonly i18n = inject(I18nService);
  readonly info = signal<DemoInfo | null>(null);
  readonly qr = signal<string>('');
  readonly busy = signal<string | null>(null);
  readonly modules = [
    { icon: 'space_dashboard', key: 'controlTower' }, { icon: 'local_shipping', key: 'distribution' }, { icon: 'route', key: 'routing' },
    { icon: 'account_tree', key: 'traceability' }, { icon: 'verified', key: 'quality' }, { icon: 'sanitizer', key: 'hygiene' },
    { icon: 'health_and_safety', key: 'safety' }, { icon: 'eco', key: 'environment' }, { icon: 'neurology', key: 'ai' }, { icon: 'hub', key: 'integrations' },
  ];

  ngOnInit(): void {
    this.api.get<DemoInfo>('/public/demo').subscribe((i) => {
      this.info.set(i);
      const url = location.origin + '/demo';
      this.qr.set(`/api/v1/public/qr-image?size=360&url=${encodeURIComponent(url)}`);
    });
  }

  setLang(l: Lang): void {
    this.i18n.use(l, false);
  }

  enter(email: string): void {
    const pwd = this.info()?.password;
    if (!pwd) return;
    this.busy.set(email);
    this.auth.login(email, pwd).subscribe({
      next: () => this.router.navigateByUrl('/dashboard'),
      error: () => {
        this.busy.set(null);
        this.toast.error('Login failed');
      },
    });
  }

  copy(text: string): void {
    navigator.clipboard?.writeText(text);
    this.toast.info(text);
  }
}
