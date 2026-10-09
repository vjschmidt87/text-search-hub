import { Component, Input, inject } from '@angular/core';
import { DownloadLinks } from '../../../core/services/api.service';
import { TranslationService } from '../../../core/services/translation.service';

@Component({
  selector: 'app-download-buttons',
  standalone: true,
  templateUrl: './download-buttons.component.html',
  styleUrl: './download-buttons.component.scss'
})
export class DownloadButtonsComponent {
  @Input() downloads!: DownloadLinks;
  protected readonly tr = inject(TranslationService);
}
