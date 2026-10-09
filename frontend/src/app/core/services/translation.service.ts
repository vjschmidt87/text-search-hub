import { Injectable, signal, computed, inject, effect } from '@angular/core';
import { HttpClient } from '@angular/common/http';

export type Language = 'en' | 'pt-br';

@Injectable({ providedIn: 'root' })
export class TranslationService {
  private readonly http = inject(HttpClient);

  language = signal<Language>((localStorage.getItem('lang') as Language) ?? 'en');
  private readonly _translations = signal<Record<string, string>>({});

  constructor() {
    this.loadLanguage(this.language());
  }

  t(key: string): string {
    return this._translations()[key] ?? key;
  }

  setLanguage(lang: Language): void {
    this.language.set(lang);
    localStorage.setItem('lang', lang);
    this.loadLanguage(lang);
  }

  private loadLanguage(lang: Language): void {
    this.http.get<Record<string, string>>(`assets/i18n/${lang}.json`).subscribe({
      next: data => this._translations.set(data),
      error: () => console.warn(`Failed to load language: ${lang}`)
    });
  }
}
