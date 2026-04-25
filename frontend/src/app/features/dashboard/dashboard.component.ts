import { DatePipe, NgClass } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { DashboardService } from '../../core/services/dashboard.service';
import {
  AggregationStepEntry,
  DashboardData,
  DashboardResult,
} from '../../core/models/dashboard.model';
import { UserProfileWidgetComponent } from './user-profile-widget/user-profile-widget.component';
import { NotificationsWidgetComponent } from './notifications-widget/notifications-widget.component';
import { ActivityWidgetComponent } from './activity-widget/activity-widget.component';
import { SagaPanelComponent } from './saga-panel/saga-panel.component';

/**
 * Read-side view-state, shaped exactly like the saga panel's view-state so a
 * reader who has understood one understands the other immediately.
 *
 * `idle` — after authentication, before the user has clicked the read button.
 *   The dashboard renders an empty shell with just the trigger control. This
 *   makes the BFF call a deliberate, observable action instead of an invisible
 *   side-effect of navigation — useful for live demos and for keeping the
 *   read protocol meaningful (the user can correlate "I clicked" with "the
 *   log appeared").
 * `dispatching` — request in flight.
 * `result` — server-authored DashboardResult received; render data + log.
 * `error` — transport-level failure; show message + retry control.
 */
type DashboardView =
  | { readonly kind: 'idle' }
  | { readonly kind: 'dispatching' }
  | { readonly kind: 'result'; readonly result: DashboardResult }
  | { readonly kind: 'error'; readonly message: string };

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    DatePipe,
    NgClass,
    UserProfileWidgetComponent,
    NotificationsWidgetComponent,
    ActivityWidgetComponent,
    SagaPanelComponent,
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

      <section class="read-trigger" aria-label="Lesevorgang">
        <p class="read-trigger__intro">
          Klicke auf <strong>Lesevorgang ausführen</strong>, damit der BFF die
          drei Downstream-Services parallel aufruft, das Dashboard zusammenstellt
          und das Ausführungsprotokoll mitliefert. Bis dahin bleibt die Ansicht
          leer — der Aufruf ist absichtlich eine bewusste Aktion.
        </p>
        <button
          type="button"
          data-test-id="dashboard-run"
          (click)="runRead()"
          [disabled]="inFlight()"
        >
          @if (inFlight()) {
            Lade Dashboard …
          } @else if (hasResult()) {
            Lesevorgang erneut ausführen
          } @else {
            Lesevorgang ausführen
          }
        </button>
      </section>

      <p
        class="status"
        role="status"
        aria-live="polite"
        [class.error]="errorMessage()"
      >
        @if (inFlight()) {
          Loading dashboard data…
        } @else if (errorMessage(); as msg) {
          {{ msg }}
        } @else if (hasResult()) {
          Dashboard up to date.
        } @else {
          Bereit. Noch kein Lesevorgang ausgeführt.
        }
      </p>

      @if (data(); as d) {
        <section class="grid" aria-label="Dashboard overview">
          <app-user-profile-widget [profile]="d.profile" />
          <app-notifications-widget [overview]="d.notifications" />
          <app-activity-widget [events]="d.activity" />
        </section>

        <article class="protocol" aria-label="Ausführungsprotokoll des Lesevorgangs">
          <h2>Ausführungsprotokoll (vom BFF)</h2>
          <p class="protocol__intro">
            Der BFF protokolliert den parallelen Fan-Out auf die drei Services.
            Die Reihenfolge spiegelt die tatsächliche Ausführung wider — Forward-
            Schritte interleaven, weil sie nebenläufig laufen.
          </p>
          <table class="log">
            <thead>
              <tr>
                <th scope="col">Zeit</th>
                <th scope="col">Phase</th>
                <th scope="col">Schritt</th>
                <th scope="col">Status</th>
                <th scope="col">Detail</th>
              </tr>
            </thead>
            <tbody>
              @for (entry of entries(); track $index) {
                <tr
                  data-test-id="dashboard-log-row"
                  [ngClass]="'row--' + entry.phase + ' status--' + entry.status"
                >
                  <td>{{ entry.timestamp | date: 'mediumTime' }}</td>
                  <td>{{ phaseLabel(entry.phase) }}</td>
                  <td>{{ entry.step }}</td>
                  <td>{{ statusLabel(entry.status) }}</td>
                  <td class="log__detail">{{ entry.detail }}</td>
                </tr>
              }
            </tbody>
          </table>
        </article>
      }

      @if (errorMessage()) {
        <button type="button" (click)="runRead()">Erneut versuchen</button>
      }

      <section class="saga-section" aria-label="Distributed write saga demo">
        <app-saga-panel />
      </section>
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
      .read-trigger {
        background: var(--color-surface);
        border: 1px solid var(--color-border);
        border-radius: var(--radius);
        box-shadow: var(--shadow);
        padding: var(--space-4);
        margin: 0 0 var(--space-4) 0;
        display: flex;
        flex-direction: column;
        gap: var(--space-3);
      }
      .read-trigger__intro {
        margin: 0;
        color: var(--color-text-muted);
        font-size: 0.95rem;
      }
      .read-trigger button {
        align-self: flex-start;
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
      .protocol {
        background: var(--color-surface);
        border: 1px solid var(--color-border);
        border-radius: var(--radius);
        box-shadow: var(--shadow);
        padding: var(--space-4);
        margin: var(--space-4) 0 0;
      }
      .protocol h2 {
        margin: 0 0 var(--space-2);
        font-size: 1.15rem;
      }
      .protocol__intro {
        margin: 0 0 var(--space-3);
        color: var(--color-text-muted);
        font-size: 0.9rem;
      }
      .log {
        width: 100%;
        border-collapse: collapse;
        font-size: 0.85rem;
      }
      .log th,
      .log td {
        text-align: left;
        padding: var(--space-1) var(--space-2);
        border-bottom: 1px solid var(--color-border);
        vertical-align: top;
      }
      .log__detail {
        color: var(--color-text-muted);
      }
      .row--coordinator {
        background: var(--color-bg);
        font-style: italic;
      }
      .status--failed td:nth-child(4) {
        color: #7f1d1d;
        font-weight: 600;
      }
      .status--succeeded td:nth-child(4) {
        color: #14532d;
        font-weight: 600;
      }
      @media (prefers-color-scheme: dark) {
        .status--failed td:nth-child(4) {
          color: #fca5a5;
        }
        .status--succeeded td:nth-child(4) {
          color: #86efac;
        }
      }
      .saga-section {
        margin-top: var(--space-4);
      }
    `,
  ],
})
export class DashboardComponent {
  private readonly dashboardService = inject(DashboardService);

  /** Opaque view state. */
  private readonly view = signal<DashboardView>({ kind: 'idle' });

  readonly inFlight = computed(() => this.view().kind === 'dispatching');

  readonly hasResult = computed(() => this.view().kind === 'result');

  /** Dashboard data once received, otherwise null — feeds the existing widgets. */
  readonly data = computed<DashboardData | null>(() => {
    const v = this.view();
    return v.kind === 'result' ? v.result.data : null;
  });

  /** Execution log received from the BFF, otherwise empty. */
  readonly entries = computed<readonly AggregationStepEntry[]>(() => {
    const v = this.view();
    return v.kind === 'result' ? v.result.log : [];
  });

  readonly errorMessage = computed<string | null>(() => {
    const v = this.view();
    return v.kind === 'error' ? v.message : null;
  });

  /**
   * Triggers the read aggregation. Deliberately not called from ngOnInit —
   * the user must click. Re-clicking after a successful run replays the call
   * (same endpoint, fresh server-side log).
   */
  runRead(): void {
    this.view.set({ kind: 'dispatching' });
    this.dashboardService.loadDashboard().subscribe({
      next: (result) => this.view.set({ kind: 'result', result }),
      error: () => {
        this.view.set({
          kind: 'error',
          message: 'Could not load dashboard data. Please try again.',
        });
      },
    });
  }

  phaseLabel(phase: string): string {
    switch (phase) {
      case 'forward':
        return 'Forward';
      case 'coordinator':
        return 'Koordinator';
      default:
        return phase;
    }
  }

  statusLabel(status: string): string {
    switch (status) {
      case 'started':
        return 'gestartet';
      case 'succeeded':
        return 'erfolgreich';
      case 'failed':
        return 'fehlgeschlagen';
      default:
        return status;
    }
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
