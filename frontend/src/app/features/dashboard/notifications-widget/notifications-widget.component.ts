import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { NotificationOverview } from '../../../core/models/dashboard.model';

@Component({
  selector: 'app-notifications-widget',
  standalone: true,
  imports: [DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <article class="card" aria-labelledby="notifications-heading">
      <header>
        <h2 id="notifications-heading">Notifications</h2>
        <span class="badge" aria-label="unread notifications">
          {{ overview()?.unreadCount ?? 0 }}
        </span>
      </header>
      @if (overview(); as o) {
        @if (o.items.length) {
          <ul>
            @for (n of o.items; track n.id) {
              <li>
                <p class="title">{{ n.title }}</p>
                <p class="message">{{ n.message }}</p>
                <time [attr.datetime]="n.timestamp">
                  {{ n.timestamp | date: 'medium' }}
                </time>
              </li>
            }
          </ul>
        } @else {
          <p class="empty">No notifications.</p>
        }
      } @else {
        <p class="empty">No notifications available.</p>
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
      header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: var(--space-3);
      }
      h2 {
        margin: 0;
        font-size: 1.25rem;
      }
      .badge {
        background: var(--color-primary);
        color: var(--color-primary-contrast);
        padding: 0.15rem 0.6rem;
        border-radius: 999px;
        font-weight: 600;
        min-width: 1.5rem;
        text-align: center;
      }
      ul {
        list-style: none;
        margin: 0;
        padding: 0;
        display: grid;
        gap: var(--space-3);
      }
      li {
        border-left: 3px solid var(--color-primary);
        padding: var(--space-2) var(--space-3);
        background: var(--color-bg);
        border-radius: 0 var(--radius) var(--radius) 0;
      }
      .title {
        margin: 0 0 var(--space-1) 0;
        font-weight: 600;
      }
      .message {
        margin: 0;
        color: var(--color-text-muted);
      }
      time {
        font-size: 0.85em;
        color: var(--color-text-muted);
      }
      .empty {
        color: var(--color-text-muted);
      }
    `,
  ],
})
export class NotificationsWidgetComponent {
  readonly overview = input<NotificationOverview | null>(null);
}
