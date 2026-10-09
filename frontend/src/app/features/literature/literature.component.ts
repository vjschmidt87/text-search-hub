import { Component, inject, signal } from '@angular/core';
import { ApiService, SearchResult } from '../../core/services/api.service';
import { TranslationService } from '../../core/services/translation.service';
import { SearchBarComponent } from '../../shared/components/search-bar/search-bar.component';
import { ResultCardComponent } from '../../shared/components/result-card/result-card.component';

@Component({
  selector: 'app-literature',
  standalone: true,
  imports: [SearchBarComponent, ResultCardComponent],
  templateUrl: './literature.component.html',
  styleUrl: './literature.component.scss'
})
export class LiteratureComponent {
  protected readonly tr = inject(TranslationService);
  private readonly api = inject(ApiService);

  searchType = signal<'book' | 'author'>('book');
  results = signal<SearchResult[]>([]);
  loading = signal(false);
  searched = signal(false);

  onSearch(query: string): void {
    this.loading.set(true);
    this.searched.set(false);
    this.api.searchLiterature(query, this.searchType()).subscribe({
      next: res => {
        this.results.set(res);
        this.searched.set(true);
        this.loading.set(false);
      },
      error: () => {
        this.results.set([]);
        this.searched.set(true);
        this.loading.set(false);
      }
    });
  }

  setSearchType(type: 'book' | 'author'): void {
    this.searchType.set(type);
  }
}
