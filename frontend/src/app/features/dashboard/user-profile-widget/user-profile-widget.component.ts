import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { UserProfile } from '../../../core/models/dashboard.model';

@Component({
  selector: 'app-user-profile-widget',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <article class="card" aria-labelledby="profile-heading">
      <h2 id="profile-heading">Profile</h2>
      @if (profile(); as p) {
        <div class="profile">
          @if (p.avatarUrl) {
            <img
              class="avatar"
              [src]="p.avatarUrl"
              [alt]="'Avatar of ' + p.displayName"
              width="64"
              height="64"
            />
          }
          <dl>
            <dt>Name</dt>
            <dd>{{ p.displayName }}</dd>
            <dt>Role</dt>
            <dd>{{ p.role || '–' }}</dd>
            <dt>User ID</dt>
            <dd><code>{{ p.userId }}</code></dd>
          </dl>
        </div>
      } @else {
        <p class="empty">No profile information available.</p>
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
        color: var(--color-text);
      }
      .profile {
        display: flex;
        gap: var(--space-3);
        align-items: flex-start;
      }
      .avatar {
        border-radius: 50%;
        background: var(--color-bg);
        border: 2px solid var(--color-border);
      }
      dl {
        margin: 0;
        display: grid;
        grid-template-columns: max-content 1fr;
        gap: var(--space-1) var(--space-3);
      }
      dt {
        font-weight: 600;
        color: var(--color-text-muted);
      }
      dd {
        margin: 0;
      }
      .empty {
        color: var(--color-text-muted);
      }
      code {
        font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
        font-size: 0.9em;
      }
    `,
  ],
})
export class UserProfileWidgetComponent {
  readonly profile = input<UserProfile | null>(null);
}
