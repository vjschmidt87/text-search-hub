import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { TranslationService } from '../../../core/services/translation.service';

@Component({
  selector: 'app-nav',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './nav.component.html',
  styleUrl: './nav.component.scss'
})
export class NavComponent {
  protected readonly tr = inject(TranslationService);

  toggleLanguage(): void {
    this.tr.setLanguage(this.tr.language() === 'en' ? 'pt-br' : 'en');
  }
}
