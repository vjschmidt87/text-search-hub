import { Component, Input, inject } from '@angular/core';
import { SearchResult } from '../../../core/services/api.service';
import { TranslationService } from '../../../core/services/translation.service';
import { DownloadButtonsComponent } from '../download-buttons/download-buttons.component';

const SOURCE_LABELS: Record<string, string> = {
  gutenberg: 'Gutenberg',
  openlibrary: 'Open Library',
  archive: 'Archive.org',
  standardebooks: 'Standard Ebooks',
  arxiv: 'arXiv',
  semanticscholar: 'Semantic Scholar'
};

@Component({
  selector: 'app-result-card',
  standalone: true,
  imports: [DownloadButtonsComponent],
  templateUrl: './result-card.component.html',
  styleUrl: './result-card.component.scss'
})
export class ResultCardComponent {
  @Input() result!: SearchResult;
  protected readonly tr = inject(TranslationService);

  get sourceLabel(): string {
    return SOURCE_LABELS[this.result.source] ?? this.result.source;
  }

  get sourceCssClass(): string {
    return 'source-badge--' + this.result.source;
  }
}
