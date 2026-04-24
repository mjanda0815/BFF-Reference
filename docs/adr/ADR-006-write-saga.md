# ADR-006: Serverseitige Saga-Orchestrierung für verteilte Schreibvorgänge

- **Status:** Akzeptiert
- **Datum:** 2026-04-24 (aktualisiert 2026-04-24 um Resilienz-Spezifikation)

## Kontext

Das Dashboard-Pattern (ADR-004) zeigt, wie der BFF *parallele Reads* aus den
drei Downstream-Services zusammenführt. Lese-Aggregation allein ist aber ein
verhältnismäßig schwaches Argument für einen BFF — Reads lassen sich auch
clientseitig parallelisieren, und Fehlertoleranz lässt sich (umständlich) im
Browser bauen. Der eigentliche Mehrwert wird bei **komplexen Writes** sichtbar,
in denen mehrere Services in einer fachlich zusammengehörigen Transaktion
mutiert werden müssen.

Beispiel-Use-Case: eine systemweite Ankündigung („Announcement") erzeugen —
der Nutzer wird im `user-service` abonniert, im `notification-service` wird
ein Broadcast angelegt, im `activity-service` wird ein Audit-Eintrag
erzeugt. Gibt es ein zentrales XA-/2PC-Setup? Nein — jeder Service hat sein
eigenes Datenmodell. Eine verteilte Transaktion gibt es nicht; verfügbar ist
nur das **Saga-Pattern** mit expliziter Kompensation.

Die architektonische Frage lautet: *Wer* orchestriert? Zwei Optionen:

1. **Clientseitige Saga**: Das Frontend (Angular) fährt die drei Service-
   Calls sequentiell, hält den Zustand in TypeScript, baut Kompensations-
   Calls bei Fehlern.
2. **Serverseitige Saga**: Der BFF führt die Saga aus. Das Frontend sendet
   *ein* Kommando und rendert das zurückgegebene Ausführungsprotokoll.

Die Folien der Architektur-Session (`docs/BFF_Demo_Praesentation.pptx`, Slide
7) positionieren Option 2 als den entscheidenden Mehrwert eines BFF:
*„Hier wird der Mehrwert am deutlichsten: Keine Orchestrierungslogik in
TypeScript."*

## Entscheidung

Der BFF orchestriert verteilte Schreibvorgänge über alle drei Services per
**Saga mit expliziter Kompensation**:

- **Forward-Phase**: user → notification → activity. Jeder Step liefert einen
  `Mono<Void>`; bei Erfolg wird eine Kompensations-Closure auf einen
  Deque gepusht.
- **Fehlerpfad**: Fällt ein Forward-Step aus, ruft der Orchestrator die
  aufgelaufenen Kompensationen in **Reverse-Order** auf.
- **Best-Effort-Kompensation**: Eine fehlgeschlagene Kompensation kippt das
  Saga-Outcome von `compensated` auf `failed`, stoppt aber den Rest nicht —
  damit ein Operator exakt sieht, welcher Downstream in einem inkonsistenten
  Zustand zurückbleibt.
- **Zustand pro Invocation**: `SagaExecution` kapselt Log und Kompensations-
  Stack je Request; kein geteilter Zustand zwischen parallelen Sagas.
- **Ergebnis an die SPA**: `AnnouncementSagaResult` mit `outcome`,
  `announcementId` (servergeneriert, in allen Services identisch) und der
  vollständigen Protokoll-Sequenz. Die SPA rendert das verbatim.

Die Implementierung lebt in:

- `de.bafa.bff.application.DistributedWriteSagaOrchestrator`
- Drei Write-Ports in `de.bafa.bff.domain.port.*AnnouncementWritePort`
  (**bewusst getrennt** von den Read-Ports, damit Read-Consumer nicht
  versehentlich Mutations-Autorität bekommen)
- Drei WebClient-Adapter in `de.bafa.bff.adapter.client.*AnnouncementWriteClient`
- Pro Service einen `AnnouncementController` mit POST (Write) + DELETE
  (Compensate)
- Pro Service ein in-memory Store (`AnnouncementSubscriptionStore`,
  `AnnouncementBroadcastStore`, `AnnouncementActivityStore`) mit
  **idempotentem Upsert** (`putIfAbsent`) — duplicate POSTs mit derselben
  `announcementId` liefern den ursprünglichen Datensatz, nicht zweimal.

## Resilienz-Spezifikation

Jeder Forward-Step der Saga ist durch zwei koordinierte Safety-Nets geschützt:

| Mechanismus           | Wert                         | Filterregel                                                                  |
|-----------------------|------------------------------|------------------------------------------------------------------------------|
| Per-Step-Timeout      | 5 s                          | TimeoutException wird als transient klassifiziert                             |
| Retry mit Backoff     | 2 Versuche, 200 ms Start     | nur bei transienten Fehlern: TimeoutException, WebClientRequestException, 5xx |
| Kompensations-Timeout | 5 s                          | kein Retry — Kompensation ist Best-Effort                                     |

Die Klassifikator-Methode `DistributedWriteSagaOrchestrator.isTransient` ist
package-private getestet. Permanente Fehler (4xx-Responses, Validierungsfehler,
Programmierfehler) bypass-en den Retry bewusst — ein Retry auf eine
Validierungs-Response ist reine Latenz, keine Reparatur.

Der Retry funktioniert zusammen mit der Store-Idempotenz: ein Retry nach
erfolgreich geschriebenem Forward-Step aber verlorener Response erzeugt keine
Duplikate, weil das Downstream-Service den bestehenden Datensatz
zurückliefert statt zu überschreiben. Das ist der Vertrag, den die drei
`save()`-Methoden mit `putIfAbsent` einhalten.

## Konsequenzen

### Positiv

- **Kein Workflow-State im Browser.** Die SPA hält nur `in-flight / result /
  error` — drei Zustände. Kompensationslogik, Ordering und Teilfehler-
  Semantik bleiben im Backend, wo sie testbar und auditierbar sind.
- **Ein einziger Netzwerk-Call pro Workflow.** Die SPA macht einen
  `POST /api/announcements`; fällt das Netzwerk in der Mitte aus, ist es das
  Problem der Saga (die automatisch kompensiert), nicht des Browsers.
- **Token-Handling bleibt beim BFF.** Jeder Forward-Step-Call fährt mit dem
  Bearer-Token aus der Session; der Browser sieht keinen Access-Token.
- **Reproducible Failure-Injection für Demos.** Das `failAt`-Feld im
  Command + das `forceFail`-Flag im Downstream-Command triggern HTTP 500
  auf Wunsch — die Kompensations-Pfade lassen sich damit didaktisch
  sauber vorführen, ohne auf echte Fehler zu warten.
- **Log-als-Contract.** Das Ausführungsprotokoll ist Teil der API-Antwort;
  Operatoren und QA sehen exakt dieselbe Sequenz wie der Nutzer im
  UI-Panel.

### Negativ

- **Single-Instance-Store.** Idempotenz ist über `putIfAbsent` *pro
  Service-Instanz* gelöst, aber nicht über Instanzen hinweg: läuft
  user-service zweifach und landet der Retry auf einer anderen Instanz, sieht
  diese die `announcementId` nicht und schreibt dennoch einen zweiten
  Datensatz. Für ein horizontal skalierendes Setup wird der
  `ConcurrentHashMap` durch einen shared Backing-Store ersetzt (Redis —
  steht ohnehin im Stack —, oder JPA + Postgres). Der Idempotenz-Vertrag
  der `save()`-Methode bleibt identisch, nur das Backend wechselt. Das
  Template lässt diesen Schritt bewusst offen, um die Persistenz-
  Agnostik (siehe Confluence-Doc §7.3) zu halten.
- **Kein globales Saga-Timeout.** Jeder Forward-Step hat 5 s Step-Timeout,
  ein Top-Level-Saga-Wrap ist bewusst nicht gesetzt: ein `.timeout()` auf
  das Top-Level-`Mono` würde mid-flight auch laufende Kompensationen
  abbrechen und damit das Best-Effort-Prinzip untergraben. Schutz vor
  runaway-Sagas gibt die Summe der Step-Timeouts: worst-case 3 Steps ×
  (1 + 2) Versuche × 5 s ≈ 45 s, plus Backoff. Wer ein strikteres Budget
  braucht, setzt das im Ingress/Gateway.
- **Kein Event-Sourcing.** Die Saga-State-Machine ist implizit in der
  Methodenfolge kodiert. Für mehr als drei Steps oder verzweigte Workflows
  empfiehlt sich ein explizites State-Machine-Framework (z. B. Axon, Camunda,
  Spring Statemachine) — bewusst nicht im Template, um den Pattern-Kern
  unverdeckt zu zeigen.
- **Demo-Endpunkt forceFail retryt mit.** `forceFail=true` lässt das Service
  HTTP 500 werfen, das ist ein transienter Status nach unserem Classifier.
  Demos sehen daher 3 Anläufe à 200–600 ms, bevor die Kompensation startet
  — insgesamt ≈ 600 ms Verzögerung vor dem Ergebnis. Didaktisch ist das
  sogar nützlich (der Retry-Mechanismus ist im Log sichtbar), kostet aber
  Zeit im Live-Vortrag.

### Verworfene Alternative: Clientseitige Saga im Browser

- Browser müsste den OAuth2-Access-Token halten (oder pro Step beim BFF
  abholen) — bricht mit dem Token-freien-Frontend-Prinzip aus ADR-002.
- TypeScript-Zustandsmaschine und Kompensations-Logik duplizieren pro
  Frontend-Variante (Web, Mobile), statt einmal im BFF zu leben.
- Partial-Failure-Recovery über verlorene Browser-Tabs unmöglich: schließt
  der Nutzer den Tab zwischen zwei Steps, bleibt der Cross-Service-State
  inkonsistent, ohne dass irgendwer kompensiert.
- Testaufwand verdoppelt sich: Jede Kompensation braucht einen Karma-/Jest-
  Test im Frontend *und* kann durch Race-Conditions im Browser-Event-Loop
  auf Arten fehlschlagen, die ein serverseitiger Reactor-Flow nicht kennt.

Die Folienpräsentation zeigt genau dieses Gegenüber (Slide 7) und macht die
Entscheidung zum didaktischen Kern der Sitzung — dieses ADR hält sie für
zukünftige Leserinnen fest.

### Verworfene Alternative: Axon/Camunda/Spring Statemachine

- Zu viel Werkzeug für drei Steps. Die Lernkurve kostet mehr Zeit als die
  Pattern-Klarheit gewinnt.
- Zusätzliche Infrastruktur (Event-Store, ggf. eigene Datenbank) widerspricht
  der bewussten Persistenz-Agnostik des Templates (siehe Confluence-Doc §7.3).
- Für ein Blueprint-Projekt ist **„zeig das Pattern explizit"** mehr wert als
  „nutze das produktionstaugliche Framework".
