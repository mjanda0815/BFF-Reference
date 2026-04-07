import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivityEvent } from '../../../core/models/dashboard.model';

@Component({
  selector: 'app-activity-widget',
  standalone: true,
  imports: [DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <article class="card" aria-labelledby="activity-heading">
      <h2 id="activity-heading">Recent activity</h2>
      @if (events()?.length) {
        <table>
          <caption class="visually-hidden">Recent activity events</caption>
          <thead>
            <tr>
              <th scope="col">Action</th>
              <th scope="col">Resource</th>
              <th scope="col">When</th>
            </tr>
          </thead>
          <tbody>
            @for (e of events(); track e.id) {
              <tr>
                <td>{{ e.action }}</td>
                <td>{{ e.resource }}</td>
                <td>
                  <time [attr.datetime]="e.timestamp">
                    {{ e.timestamp | date: 'short' }}
                  </time>
                </td>
              </tr>
            }
          </tbody>
        </table>
      } @else {
        <p class="empty">No recent activity.</p>
      }
    </article>
  `,
  styles: [
    `
      .card {
        background: var(--color-surface);
        border: 1px solid var(--color-border);
        border-radius: var(--radius);
        padding: var(--space-4);
        box-shadow: var(--shadow);
      }
      h2 {
        margin: 0 0 var(--space-3) 0;
        font-size: 1.25rem;
      }
      table {
        width: 100%;
        border-collapse: collapse;
      }
      th,
      td {
        text-align: left;
        padding: var(--space-2) var(--space-3);
        border-bottom: 1px solid var(--color-border);
      }
      th {
        color: var(--color-text-muted);
        font-weight: 600;
        font-size: 0.85rem;
        text-transform: uppercase;
        letter-spacing: 0.04em;
      }
      tbody tr:last-child td {
        border-bottom: none;
      }
      .empty {
        color: var(--color-text-muted);
      }
    `,
  ],
})
export class ActivityWidgetComponent {
  readonly events = input<ActivityEvent[] | null>(null);
}
