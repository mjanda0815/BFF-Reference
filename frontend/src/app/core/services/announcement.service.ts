import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AnnouncementCommand,
  AnnouncementSagaResult,
  FailAtTarget,
} from '../models/announcement.model';

/**
 * Single-call transport to the BFF's distributed-write saga endpoint.
 *
 * The SPA intentionally owns no saga logic: this service dispatches one command
 * and returns whatever the BFF sends back. Step ordering, compensation and
 * workflow state all live in the DistributedWriteSagaOrchestrator on the server.
 *
 * `withCredentials: true` is redundant thanks to the global auth interceptor,
 * but kept explicit for didactic symmetry with DashboardService — a reader
 * copying this service should see the contract in one place.
 *
 * Für die Übernahme in ein neues Produkt:
 *   - Pfad ('/api/announcements') gegen den produktspezifischen Endpoint
 *     austauschen. Payload-Typ anpassen.
 *   - Weitere Logik NICHT hier einbauen (kein Retry, kein Error-Mapping).
 *     Das ist bewusst so: die SPA bleibt dünn, Resilienz sitzt im BFF.
 */
@Injectable({ providedIn: 'root' })
export class AnnouncementService {
  private readonly http = inject(HttpClient);

  /** Dispatches the saga command. `failAt: 'none'` is not sent. */
  execute(command: AnnouncementCommand): Observable<AnnouncementSagaResult> {
    const payload: { message: string; failAt?: FailAtTarget } = {
      message: command.message,
    };
    if (command.failAt !== 'none') {
      payload.failAt = command.failAt;
    }
    return this.http.post<AnnouncementSagaResult>(
      '/api/announcements',
      payload,
      { withCredentials: true },
    );
  }
}
