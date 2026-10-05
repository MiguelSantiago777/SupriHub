import { inject, Injectable } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterStateSnapshot, TitleStrategy } from '@angular/router';

/** Título da aba: "<página> — SupriHub" (ajuda leitores de tela a saber onde estão). */
@Injectable()
export class PageTitleStrategy extends TitleStrategy {
  private readonly title = inject(Title);

  override updateTitle(snapshot: RouterStateSnapshot): void {
    const page = this.buildTitle(snapshot);
    this.title.setTitle(page ? `${page} — SupriHub` : 'SupriHub — Contrata + NATA');
  }
}
