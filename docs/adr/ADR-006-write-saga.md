# ADR-006: Serverseitige Saga-Orchestrierung für verteilte Schreibvorgänge

- **Status:** Akzeptiert
- **Datum:** 2026-04-24

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

- **In-Memory-Store in den Services.** Die Demo hält Subscription-/
  Broadcast-/Activity-Datensätze in `ConcurrentHashMap`s pro Service-
  Instanz. Kompensation funktioniert damit nur, wenn dieselbe Instanz den
  Write und den Compensate entgegengenommen hat. Für ein horizontal
  skalierendes Setup muss der Store durch einen persistenten Backing-Store
  ersetzt werden (JPA/Redis) — üblicherweise mit einer Idempotency-Map,
  die `announcementId`-Duplicate-Writes erkennt.
- **Kein globales Saga-Timeout.** Einzelne WebClient-Calls haben 5 s
  Timeout (aus dem Aggregator-Pfad übernommen), der Gesamt-Saga-Run ist
  aber nicht umklammert. Bei hängender Kompensation hängt der
  BFF-Request. Für Produktion: `timeout(Duration.ofSeconds(30))` auf das
  Top-Level-`Mono` legen.
- **Kein Retry.** Vorüberschehende Fehler (Network-Blip) werden sofort als
  Kompensations-Auslöser behandelt. Ein produktives Setup würde einen
  idempotenten Retry mit Exponential-Backoff vorschalten (z. B. über
  `Retry.backoff(...)` von Reactor), bevor kompensiert wird.
- **Kein Event-Sourcing.** Die Saga-State-Machine ist implizit in der
  Methodenfolge kodiert. Für mehr als drei Steps oder verzweigte Workflows
  empfiehlt sich ein explizites State-Machine-Framework (z. B. Axon, Camunda,
  Spring Statemachine) — bewusst nicht im Template, um den Pattern-Kern
  unverdeckt zu zeigen.

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
