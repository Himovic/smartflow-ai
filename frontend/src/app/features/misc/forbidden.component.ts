import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TrPipe } from '../../shared/tr.pipe';

@Component({
  selector: 'sf-forbidden',
  imports: [RouterLink, TrPipe],
  template: `<div class="page"><div class="card empty" style="margin-top:40px"><span class="icon">lock</span><h2>{{ 'forbidden.title' | tr }}</h2>
    <p class="muted" style="margin:8px 0 16px">{{ 'forbidden.text' | tr }}</p><a class="btn primary" routerLink="/dashboard">{{ 'nav.executive' | tr }}</a></div></div>`,
})
export class ForbiddenComponent {}
