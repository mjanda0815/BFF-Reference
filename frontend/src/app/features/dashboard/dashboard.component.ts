import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { DashboardService } from '../../core/services/dashboard.service';
import { DashboardData } from '../../core/models/dashboard.model';
import { UserProfileWidgetComponent } from './user-profile-widget/user-profile-widget.component';
import { NotificationsWidgetComponent } from './notifications-widget/notifications-widget.component';
import { ActivityWidgetComponent } from './activity-widget/activity-widget.component';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    UserProfileWidgetComponent,
    NotificationsWidgetComponent,
    ActivityWidgetComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="page-header">
      <div class="container">
        <h1>BFF Reference Dashboard</h1>
        <button
          type="button"
          class="secondary"
          (click)="logout()"
          aria-label="Sign out and end session"
        >
          Sign out
        </button>
      </div>
    </header>

    <main id="main-content" class="container" tabindex="-1">
      <h2 class="visually-hidden">Dashboard widgets</h2>

      <p
        class="status"
        role="status"
        aria-live="polite"
        [class.error]="errorMessage()"
      >
        @if (loading()) {
          Loading dashboard data…
        } @else if (errorMessage(); as msg) {
          {{ msg }}
        } @else {
          Dashboard up to date.
        }
      </p>

      @if (data(); as d) {
        <section class="grid" aria-label="Dashboard overview">
          <app-user-profile-widget [profile]="d.profile" />
          <app-notifications-widget [overview]="d.notifications" />
          <app-activity-widget [events]="d.activity" />
        </section>
      }

      @if (errorMessage()) {
        <button type="button" (click)="reload()">Try again</button>
      }
    </main>
  `,
  styles: [
    `
      .page-header {
        background: var(--color-surface);
        border-bottom: 1px solid var(--color-border);
        box-shadow: var(--shadow);
      }
      .page-header .container {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: var(--space-3) var(--space-4);
      }
      .container {
        max-width: 1200px;
        margin: 0 auto;
        padding: var(--space-4);
      }
      h1 {
        margin: 0;
        font-size: 1.5rem;
      }
      main:focus {
        outline: none;
      }
      .status {
        background: var(--color-surface);
        border: 1px solid var(--color-border);
        border-radius: var(--radius);
        padding: var(--space-2) var(--space-3);
        margin: 0 0 var(--space-4) 0;
        color: var(--color-text-muted);
      }
      .status.error {
        border-color: var(--color-error);
        color: var(--color-error);
      }
      .grid {
        display: grid;
        gap: var(--space-4);
        grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
      }
    `,
  ],
})
export class DashboardComponent implements OnInit {
  private readonly dashboardService = inject(DashboardService);

  readonly data = signal<DashboardData | null>(null);
  readonly loading = signal<boolean>(true);
  readonly errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.dashboardService.loadDashboard().subscribe({
      next: (payload) => {
        this.data.set(payload);
        this.loading.set(false);
      },
      error: () => {
        this.errorMessage.set(
          'Could not load dashboard data. Please try again.',
        );
        this.loading.set(false);
      },
    });
  }

  /**
   * Logs the user out via the BFF.
   *
   * Spring Security responds to POST /logout with a 302 to Keycloak's end_session_endpoint
   * so the SSO session is terminated too. An XHR POST would swallow that redirect (fetch
   * cannot trigger a cross-origin top-level navigation), so we instead submit a real HTML
   * form: the browser then follows the 302 naturally, lands on Keycloak's logout page, and
   * finally comes back to the SPA root.
   *
   * The CSRF token is read from the XSRF-TOKEN cookie and attached as a hidden _csrf input.
   */
  logout(): void {
    const form = document.createElement('form');
    form.method = 'POST';
    form.action = '/logout';

    const csrf = this.readCookie('XSRF-TOKEN');
    if (csrf) {
      const input = document.createElement('input');
      input.type = 'hidden';
      input.name = '_csrf';
      input.value = csrf;
      form.appendChild(input);
    }

    document.body.appendChild(form);
    form.submit();
  }

  private readCookie(name: string): string | null {
    const match = document.cookie.match(
      new RegExp('(?:^|; )' + name.replace(/([.$?*|{}()[\]\\/+^])/g, '\\$1') + '=([^;]*)'),
    );
    return match ? decodeURIComponent(match[1]) : null;
  }
}
