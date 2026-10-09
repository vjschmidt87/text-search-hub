import { Component, inject, signal } from '@angular/core';
import { ApiService, SearchResult } from '../../core/services/api.service';
import { TranslationService } from '../../core/services/translation.service';
import { SearchBarComponent } from '../../shared/components/search-bar/search-bar.component';
import { ResultCardComponent } from '../../shared/components/result-card/result-card.component';

@Component({
  selector: 'app-academic',
  standalone: true,
  imports: [SearchBarComponent, ResultCardComponent],
  templateUrl: './academic.component.html',
  styleUrl: './academic.component.scss'
})
export class AcademicComponent {
  protected readonly tr = inject(TranslationService);
  private readonly api = inject(ApiService);

  results = signal<SearchResult[]>([]);
  loading = signal(false);
  searched = signal(false);

  onSearch(query: string): void {
    this.loading.set(true);
    this.searched.set(false);
    this.api.searchAcademic(query).subscribe({
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
}
