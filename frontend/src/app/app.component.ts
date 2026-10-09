import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { NavComponent } from './shared/components/nav/nav.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, NavComponent],
  template: `
    <app-nav />
    <div class="app-content">
      <router-outlet />
    </div>
  `,
  styles: [`
    .app-content {
      padding-top: 60px;
      min-height: 100vh;
    }
  `]
})
export class AppComponent {}
