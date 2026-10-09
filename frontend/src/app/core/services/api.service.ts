import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface DownloadLinks {
  en: string | null;
  ptPt: string | null;
  ptBr: string | null;
}

export interface SearchResult {
  title: string;
  author: string;
  year: string | null;
  source: string;
  format: string;
  description: string | null;
  downloads: DownloadLinks;
}

export interface QuoteResult {
  quote: string;
  author: string;
  work: string | null;
  source: string;
  confidence: number;
}

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  searchLiterature(q: string, type: 'book' | 'author'): Observable<SearchResult[]> {
    const params = new HttpParams().set('q', q).set('type', type);
    return this.http.get<SearchResult[]>('/api/literature/search', { params });
  }

  searchAcademic(q: string): Observable<SearchResult[]> {
    const params = new HttpParams().set('q', q);
    return this.http.get<SearchResult[]>('/api/academic/search', { params });
  }

  findQuoteSource(quote: string): Observable<QuoteResult | null> {
    return this.http.post<QuoteResult | null>('/api/quotes/search', { quote });
  }
}
