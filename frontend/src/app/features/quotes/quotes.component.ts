import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService, QuoteResult } from '../../core/services/api.service';
import { TranslationService } from '../../core/services/translation.service';
import { HttpStatusCode } from '@angular/common/http';

@Component({
  selector: 'app-quotes',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './quotes.component.html',
  styleUrl: './quotes.component.scss'
})
export class QuotesComponent {
  protected readonly tr = inject(TranslationService);
  private readonly api = inject(ApiService);

  quoteInput = signal('');
  result = signal<QuoteResult | null>(null);
  loading = signal(false);
  searched = signal(false);
  notFound = signal(false);

  get sourceLabel(): string {
    const source = this.result()?.source;
    if (!source) return '';
    const key = `result.source.${source}`;
    return this.tr.t(key);
  }

  get confidencePct(): string {
    const c = this.result()?.confidence ?? 0;
    return Math.round(c * 100) + '%';
  }

  onSearch(): void {
    const q = this.quoteInput().trim();
    if (!q || this.loading()) return;

    this.loading.set(true);
    this.searched.set(false);
    this.notFound.set(false);
    this.result.set(null);

    this.api.findQuoteSource(q).subscribe({
      next: res => {
        this.result.set(res);
        this.searched.set(true);
        this.notFound.set(!res);
        this.loading.set(false);
      },
      error: (err) => {
        if (err.status === 204) {
          this.notFound.set(true);
        } else {
          this.notFound.set(true);
        }
        this.searched.set(true);
        this.loading.set(false);
      }
    });
  }

  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
      this.onSearch();
    }
  }
}
