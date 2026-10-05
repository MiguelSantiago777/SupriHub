import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { AuthService } from './core/auth/auth.service';
import { ROLE_LABELS } from './core/models';

@Component({
  selector: 'app-root',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatButtonModule,
    MatMenuModule,
    MatIconModule,
  ],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly auth = inject(AuthService);
  protected readonly menuOpen = signal(false);
  protected readonly roleLabels = computed(() =>
    (this.auth.user()?.roles ?? []).map((r) => ROLE_LABELS[r]).join(', '),
  );
  protected readonly initials = computed(() => {
    const parts = (this.auth.user()?.fullName ?? '').trim().split(/\s+/);
    return (
      (parts[0]?.[0] ?? '') + (parts.length > 1 ? parts[parts.length - 1][0] : '')
    ).toUpperCase();
  });
  protected readonly year = new Date().getFullYear();

  constructor() {
    inject(Router)
      .events.pipe(filter((e) => e instanceof NavigationEnd))
      .subscribe(() => {
        this.menuOpen.set(false);
        // Leva o foco ao conteúdo principal a cada navegação (leitores de tela).
        queueMicrotask(() => document.getElementById('conteudo')?.focus({ preventScroll: true }));
      });
  }

  protected toggleMenu(): void {
    this.menuOpen.update((v) => !v);
  }
}
