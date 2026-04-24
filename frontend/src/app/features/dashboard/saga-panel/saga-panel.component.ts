import { DatePipe, NgClass } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  AnnouncementSagaResult,
  FailAtTarget,
} from '../../../core/models/announcement.model';
import { AnnouncementService } from '../../../core/services/announcement.service';

/**
 * View-state of the saga panel.
 *
 * The SPA tracks only two things: is a command in flight, and what did the BFF
 * return. Everything else the user sees in the log comes from the server — there
 * is no client-side workflow state machine here on purpose. That is the whole
 * didactic point of the With-BFF variant the reference implements: compensation
 * logic lives in the orchestrator, not in the browser.
 */
type SagaView =
  | { readonly kind: 'idle' }
  | { readonly kind: 'dispatching' }
  | { readonly kind: 'result'; readonly result: AnnouncementSagaResult }
  | { readonly kind: 'error'; readonly message: string };

/**
 * Dashboard panel that dispatches a distributed-write saga and renders the
 * execution log returned by the BFF.
 *
 * The panel deliberately contains no step sequencing, no compensation calls
 * and no workflow state machine — those live in the
 * DistributedWriteSagaOrchestrator on the server. The architectural contrast
 * against a client-side orchestrator (see the slide deck's No-BFF variant) is
 * the whole point of showing this saga in the reference.
 */
@Component({
  selector: 'app-saga-panel',
  standalone: true,
  imports: [DatePipe, FormsModule, NgClass],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <article class="panel" data-test-id="saga-panel">
      <h2>Verteilter Schreibvorgang (Saga-Demo)</h2>
      <p class="panel__intro">
        Sende ein einzelnes Kommando an den BFF. Der BFF führt die Saga aus,
        kümmert sich um die Kompensation und liefert das vollständige
        Ausführungsprotokoll zurück. Die SPA rendert es nur.
      </p>

      <div class="controls">
        <label class="field">
          <span>Fehler simulieren bei</span>
          <select
            [(ngModel)]="failAt"
            data-test-id="saga-fail-at"
            [disabled]="inFlight()"
          >
            <option value="none">kein Fehler (Happy Path)</option>
            <option value="user">user-service</option>
            <option value="notification">notification-service</option>
            <option value="activity">activity-service</option>
          </select>
        </label>
        <button
          type="button"
          data-test-id="saga-run"
          (click)="run()"
          [disabled]="inFlight()"
        >
          @if (inFlight()) {
            Läuft …
          } @else {
            Verteilten Schreibvorgang ausführen
          }
        </button>
      </div>

      <!--
        role="status" + aria-live="polite" ensures screen-reader users hear the
        saga outcome without focus being stolen (cf. BITV 2.0 / WCAG 2.1 AA
        4.1.3). The region is permanently rendered (empty when idle) so the
        live-region announcement also fires on the first result.
      -->
      <div class="result-region" role="status" aria-live="polite">
        @if (outcome(); as o) {
          <p class="outcome" [ngClass]="'outcome--' + o">
            Ergebnis:
            <strong data-test-id="saga-outcome">{{ outcomeLabel(o) }}</strong>
            <span class="outcome__id" data-test-id="saga-announcement-id">
              announcementId: {{ announcementId() }}
            </span>
          </p>
        }
        @if (errorMessage(); as msg) {
          <p class="outcome outcome--failed" data-test-id="saga-error">
            Request fehlgeschlagen: {{ msg }}
          </p>
        }
      </div>

      <h3>Ausführungsprotokoll (vom BFF)</h3>
      @if (entries().length === 0) {
        <p class="empty">Noch keine Saga ausgeführt.</p>
      } @else {
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
                data-test-id="saga-log-row"
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
      }
    </article>
  `,
  styles: [
    `
      .panel {
        background: var(--color-surface);
        border: 1px solid var(--color-border);
        border-radius: var(--radius);
        box-shadow: var(--shadow);
        padding: var(--space-4);
      }
      .panel h2 {
        margin: 0 0 var(--space-2);
        font-size: 1.25rem;
      }
      .panel h3 {
        margin: var(--space-4) 0 var(--space-2);
        font-size: 0.95rem;
        color: var(--color-text-muted);
        text-transform: uppercase;
        letter-spacing: 0.05em;
      }
      .panel__intro {
        margin: 0 0 var(--space-3);
        color: var(--color-text-muted);
        font-size: 0.9rem;
      }
      .controls {
        display: flex;
        align-items: flex-end;
        gap: var(--space-3);
        flex-wrap: wrap;
      }
      .field {
        display: flex;
        flex-direction: column;
        gap: var(--space-1);
        font-size: 0.85rem;
        color: var(--color-text-muted);
      }
      .field select {
        padding: var(--space-1) var(--space-2);
        border: 1px solid var(--color-border);
        border-radius: var(--radius);
        background: var(--color-bg);
        color: var(--color-text);
      }
      .outcome {
        margin: var(--space-3) 0 0;
        padding: var(--space-2) var(--space-3);
        border-radius: var(--radius);
        font-size: 0.9rem;
      }
      .outcome--succeeded {
        background: #e6f7ed;
        color: #14532d;
        border: 1px solid #86efac;
      }
      .outcome--compensated {
        background: #fff7ed;
        color: #7c2d12;
        border: 1px solid #fdba74;
      }
      .outcome--failed {
        background: #fef2f2;
        color: #7f1d1d;
        border: 1px solid #fca5a5;
      }
      /*
       * Dark-mode variants. The light-mode pastel triple (green/amber/red)
       * gets replaced with desaturated surfaces and bright text so WCAG AA
       * contrast holds against the dark app background (see styles.css
       * prefers-color-scheme block). Matches the rest of the dashboard,
       * which the README promises does not regress in dark mode.
       */
      @media (prefers-color-scheme: dark) {
        .outcome--succeeded {
          background: #14532d;
          color: #86efac;
          border-color: #166534;
        }
        .outcome--compensated {
          background: #78350f;
          color: #fdba74;
          border-color: #9a3412;
        }
        .outcome--failed {
          background: #7f1d1d;
          color: #fca5a5;
          border-color: #991b1b;
        }
      }
      .outcome__id {
        display: block;
        font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
        font-size: 0.75rem;
        margin-top: var(--space-1);
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
      .row--compensation {
        background: #fff7ed;
      }
      .row--coordinator {
        background: var(--color-bg);
        font-style: italic;
      }
      .status--failed td:nth-child(4) {
        color: #7f1d1d;
        font-weight: 600;
      }
      .status--succeeded td:nth-child(4),
      .status--compensated td:nth-child(4) {
        color: #14532d;
        font-weight: 600;
      }
      .empty {
        color: var(--color-text-muted);
      }
      @media (prefers-color-scheme: dark) {
        .row--compensation {
          background: rgba(234, 88, 12, 0.12);
        }
        .status--failed td:nth-child(4) {
          color: #fca5a5;
        }
        .status--succeeded td:nth-child(4),
        .status--compensated td:nth-child(4) {
          color: #86efac;
        }
      }
    `,
  ],
})
export class SagaPanelComponent {
  private readonly announcements = inject(AnnouncementService);

  /** Selected failure-injection target, bound into the <select>. */
  failAt: FailAtTarget = 'none';

  /** Opaque view state. */
  private readonly view = signal<SagaView>({ kind: 'idle' });

  /** True while the BFF call is in flight. */
  readonly inFlight = computed(() => this.view().kind === 'dispatching');

  /** Saga outcome string if a result has been received, otherwise null. */
  readonly outcome = computed(() => {
    const v = this.view();
    return v.kind === 'result' ? v.result.outcome : null;
  });

  /** Server-assigned announcement id, if known. */
  readonly announcementId = computed(() => {
    const v = this.view();
    return v.kind === 'result' ? v.result.announcementId : null;
  });

  /** Transport-level error message, if the BFF call itself failed. */
  readonly errorMessage = computed(() => {
    const v = this.view();
    return v.kind === 'error' ? v.message : null;
  });

  /** Log entries, rendered verbatim from the BFF response. */
  readonly entries = computed(() => {
    const v = this.view();
    return v.kind === 'result' ? v.result.log : [];
  });

  run(): void {
    this.view.set({ kind: 'dispatching' });
    this.announcements
      .execute({ message: 'Unternehmensweite Ankündigung', failAt: this.failAt })
      .subscribe({
        next: (result) => this.view.set({ kind: 'result', result }),
        error: (err: unknown) => {
          const message = err instanceof Error ? err.message : 'Unbekannter Fehler';
          this.view.set({ kind: 'error', message });
        },
      });
  }

  /**
   * Übersetzt die vom BFF gelieferten englischen Ergebnis-Strings in deutsche
   * Labels. Die technischen Werte (succeeded/compensated/failed) bleiben in
   * den CSS-Klassen, damit das Styling unverändert bleibt; nur die Anzeige
   * wird lokalisiert.
   */
  outcomeLabel(outcome: string): string {
    switch (outcome) {
      case 'succeeded':
        return 'erfolgreich';
      case 'compensated':
        return 'kompensiert';
      case 'failed':
        return 'fehlgeschlagen';
      default:
        return outcome;
    }
  }

  phaseLabel(phase: string): string {
    switch (phase) {
      case 'forward':
        return 'Forward';
      case 'compensation':
        return 'Kompensation';
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
      case 'compensated':
        return 'kompensiert';
      default:
        return status;
    }
  }
}
