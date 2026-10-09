import { Component, Input, Output, EventEmitter, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-search-bar',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './search-bar.component.html',
  styleUrl: './search-bar.component.scss'
})
export class SearchBarComponent {
  @Input() placeholder = 'Search...';
  @Input() loading = false;
  @Input() submitLabel = 'Search';
  @Output() search = new EventEmitter<string>();

  query = signal('');

  onSubmit(event: Event): void {
    event.preventDefault();
    const q = this.query().trim();
    if (q) this.search.emit(q);
  }

  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter') this.onSubmit(event);
  }
}
