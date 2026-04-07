import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { HttpClient } from '@angular/common/http';
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
  private readonly http = inject(HttpClient);

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
   * Logs the user out via the BFF. The HttpClient automatically attaches the
   * X-XSRF-TOKEN header read from the XSRF-TOKEN cookie. After Spring Security
   * processes the logout it redirects to Keycloak; we follow up by sending the
   * browser back to the SPA root which will trigger a fresh login flow.
   */
  logout(): void {
    this.http
      .post('/logout', null, { withCredentials: true, observe: 'response' })
      .subscribe({
        next: () => {
          window.location.href = '/';
        },
        error: () => {
          window.location.href = '/';
        },
      });
  }
}
