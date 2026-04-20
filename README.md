# BFF-Referenzarchitektur

Eine lauffähige Referenzimplementierung des **Backend-for-Frontend (BFF)**-
Patterns mit einer tokenfreien Angular-SPA, Keycloak als Identity Provider,
Redis-basierten Sessions und drei aggregierten Downstream-Microservices.

Ziel dieses Projekts ist es, Enterprise-Architekturmuster durchgängig zu
demonstrieren — Sicherheit, Session-Handling, parallele Aggregation,
hexagonale Schichtung, Testabdeckung und Barrierefreiheit — so, dass sich
alles mit einem einzigen Befehl lokal starten lässt.

> **Hinweis zum BAFA-Blueprint.** Dieses Repository wird als *Referenz-
> Template* für neue BAFA-Projekte gepflegt. Alle Java-Packages liegen unter
> dem Namespace `de.bafa.*` (z. B. `de.bafa.bff`, `de.bafa.userservice`),
> die Maven-Koordinaten nutzen die groupId `de.bafa`. Wer ein Modul als
> Ausgangspunkt für ein neues Produkt kopiert, behält die rollenbasierten
> Artefaktnamen (`bff`, `user-service`, …) bei und führt bei Bedarf einen
> produktspezifischen Unter-Namespace ein (z. B. `de.bafa.<produkt>.bff`).
> Jede Klasse trägt ein didaktisches JavaDoc, das das jeweils demonstrierte
> Pattern erklärt — nicht nur diese README, sondern auch den Quellcode
> lesen.

---

## Architektur-Überblick

```
            ┌──────────────┐         ┌──────────────┐
  Browser → │   Angular    │  ─────► │    nginx     │
            │ (keine Token)│         │ (statische   │
            └──────────────┘         │  SPA +       │
                                     │  Reverse-    │
                                     │   Proxy)     │
                                     └──────┬───────┘
                                            │ same-origin
                                            ▼
                                     ┌──────────────┐         ┌─────────────┐
                                     │     BFF      │ ◄─────► │   Keycloak  │
                                     │ Spring Boot  │  OIDC   │   (OIDC)    │
                                     │   WebFlux    │         └─────────────┘
                                     │              │
                                     │  ┌────────┐  │         ┌─────────────┐
                                     │  │Session │◄─┼───────► │    Redis    │
                                     │  │ Tokens │  │         └─────────────┘
                                     │  └────────┘  │
                                     │              │
                                     │   parallel   │
                                     │   Mono.zip   │
                                     └──┬───┬───┬───┘
                                        │   │   │
                       ┌────────────────┘   │   └──────────────────┐
                       ▼                    ▼                      ▼
                ┌──────────────┐    ┌──────────────┐        ┌──────────────┐
                │ user-service │    │ notification │        │   activity   │
                │   (REST,     │    │   service    │        │   service    │
                │ JWT-gesichert)│   │   (REST,     │        │   (REST,     │
                └──────────────┘    │ JWT-gesichert)│       │ JWT-gesichert)│
                                    └──────────────┘        └──────────────┘
```

Eine detailliertere Sicht (inklusive Sequenzdiagrammen für Login,
API-Aufruf und Logout) liegt in
[`docs/architecture.md`](docs/architecture.md).

---

## Repository-Struktur

```
bff-reference/
├── pom.xml                     # Aggregator-POM — ein `mvn verify` für alles
├── docker-compose.yml          # kompletter Stack mit einem Befehl
├── .env.example                # alle konfigurierbaren Werte
├── keycloak/                   # Realm-Export + Keycloak-Dockerfile
├── bff/                        # Spring Boot 3 BFF (WebFlux) — de.bafa.bff
├── services/
│   ├── user-service/           # Spring Boot 3 Resource Server — de.bafa.userservice
│   ├── notification-service/   #                                 de.bafa.notificationservice
│   └── activity-service/       #                                 de.bafa.activityservice
├── frontend/                   # Angular 21 SPA, von nginx ausgeliefert
└── docs/
    ├── architecture.md
    ├── security-concept.md
    ├── confluence.md           # Confluence-taugliche Gesamtdoku
    └── adr/                    # Architecture Decision Records
```

---

## Voraussetzungen

- Docker und Docker Compose (Compose v2)
- Java 21 LTS  *(nur nötig, wenn `mvn verify` außerhalb von Docker laufen
  soll)*
- Maven 3.9+   *(dito)*
- Node 20+     *(nur nötig für `ng build` außerhalb von Docker)*

Der Quickstart unten braucht **nur Docker**.

---

## Quickstart

```bash
cp .env.example .env
docker compose up --build
```

Dann <http://localhost> im Browser öffnen und mit dem Demo-Nutzer einloggen:

| Benutzername         | Passwort |
|----------------------|----------|
| `demo@example.com`   | `demo123`|

Du landest auf dem Dashboard, das Daten aus allen drei Downstream-Services
in einer einzigen Antwort aggregiert zeigt — alles vom BFF zusammengesetzt.

Die Keycloak-Admin-UI ist unter <http://localhost:8080> erreichbar, mit den
Zugangsdaten aus `.env` (`KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_PASSWORD`,
Default `admin` / `admin`).

---

## Warum ein BFF?

Das Backend-for-Frontend-Pattern platziert eine serverseitige Komponente
zwischen SPA und die eigentlichen Business-APIs. In diesem Projekt besitzt
der BFF den gesamten OAuth2-/OIDC-Ablauf mit Keycloak, hält Access- und
Refresh-Token in einem serverseitigen Session-Store (Redis) und exponiert
der Angular-App eine kleine, frontend-geformte HTTP-API, die ausschließlich
über Session-Cookies konsumiert wird.

Die Vorteile, die wir zeigen wollen:

- **Keine Tokens im Browser.** Die SPA sieht niemals ein Access-, Refresh-
  oder ID-Token. Es gibt keinen `localStorage`-/`sessionStorage`-/
  In-Memory-Token-Cache, der per XSS gestohlen werden könnte.
- **Aggregation an einer Stelle.** Die Dashboard-Ansicht braucht Daten aus
  drei Microservices. Der Fan-out im BFF (`Mono.zip`, parallel, Timeout
  pro Service, fehlertolerant) hält die SPA dumm und die Netzwerk-
  Round-Trips gering.
- **Starke CSRF-Position.** Weil die SPA mit Cookies authentifiziert, wird
  `SameSite=Lax` mit dem Double-Submit-CSRF-Token von Spring Security
  kombiniert. Die Begründung liegt in
  [`docs/security-concept.md`](docs/security-concept.md).
- **Eine einzige Security-Boundary.** Token-Validierung, Refresh-Handling
  und Logout-Invalidierung leben im BFF — die Downstream-Services
  validieren lediglich JWTs.

Die vollständige Begründung ist als ADRs in
[`docs/adr/`](docs/adr/) festgehalten.

---

## Tokenfreies Frontend

Das Angular-Frontend hängt bewusst **nicht** von `angular-oauth2-oidc` oder
einem anderen browserseitigen OIDC-Client ab. Es kennt nur drei Dinge:

1. Jeden API-Aufruf mit `withCredentials: true` senden, damit das Session-
   Cookie automatisch angehängt wird.
2. Kommt eine Antwort mit HTTP 401 zurück, den Browser zu `/login`
   navigieren und den BFF den OIDC-Authorization-Code-Flow starten lassen.
3. Bei zustandsändernden Requests das CSRF-Token aus dem `XSRF-TOKEN`-
   Cookie als `X-XSRF-TOKEN`-Header zurücksenden (Angulars `HttpClient`
   macht das transparent, wenn er mit `withXsrfConfiguration` konfiguriert
   ist).

Das ist der gesamte Authentifizierungs-Vertrag auf Client-Seite.

---

## Session- und Cookie-Strategie

| Eigenschaft      | Wert                          | Grund                                                 |
|------------------|-------------------------------|-------------------------------------------------------|
| Cookie-Name      | `SESSION`                     | Spring-Session-Default                                |
| `HttpOnly`       | `true`                        | Für JavaScript nicht erreichbar (XSS-Härtung)         |
| `Secure`         | `true` (`false` in Dev)       | HTTPS erzwingen in Nicht-Lokalumgebungen              |
| `SameSite`       | `Lax`                         | Überlebt Top-Level-Navigation von Keycloak            |
| `Path`           | `/`                           | Gesamte BFF-Oberfläche                                |
| `Max-Age`        | `BFF_SESSION_TIMEOUT_SECONDS` | Auf Keycloak-Refresh-Token-Laufzeit abgestimmt        |

- Das `SESSION`-Cookie speichert nur eine opake Session-ID. Die
  eigentlichen Access- und Refresh-Tokens leben in **Redis**, mit der
  Session-ID als Schlüssel.
- Ein zweites Cookie `XSRF-TOKEN` trägt das CSRF-Token (bewusst nicht
  `HttpOnly` — die SPA muss es lesen und als Header zurückspiegeln können).
- Beim Logout wird das Refresh-Token bei Keycloak revoziert, der
  Redis-Eintrag gelöscht und beide Cookies im Browser abgelaufen.

Threat-Modell und verworfene Alternativen siehe
[`docs/security-concept.md`](docs/security-concept.md).

---

## Redis-Layout

Der BFF nutzt Spring Session (`spring-session-data-redis`) und schreibt
Session-State unter Schlüsseln der Form:

```
spring:session:sessions:<session-id>           # Session-Attribute
spring:session:expirations:<bucket>            # Ablauf-Index
spring:session:sessions:expires:<session-id>   # Ablauf-Marker pro Session
```

Innerhalb der Session-Attribute werden die OAuth2-Access- und Refresh-
Tokens über den Spring-`OAuth2AuthorizedClient`-Mechanismus abgelegt —
niemals im Klartext in irgendeinem anderen Key.

---

## Keycloak

Ein vollständiger Realm-Export liegt in `keycloak/realm-export.json` und
wird beim Container-Start über `start-dev --import-realm` importiert. Er
enthält:

- Realm `bff-demo`
- Confidential-Client `bff-client` (Authorization-Code-Flow, Secret aus
  `.env`)
- Testnutzer `demo@example.com` / `demo123`
- Aktivierte Brute-Force-Protection
- Access-Token-Laufzeit: 5 Minuten; Refresh-Token-Laufzeit: 30 Minuten

Die Keycloak-Admin-UI liegt unter <http://localhost:8080>, mit
`KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_PASSWORD` aus `.env`.

---

## Tests

### Backend (BFF + Services)

Im Repository-Root liegt ein Aggregator-POM, sodass der komplette Stack
mit einem Befehl gebaut wird:

```bash
mvn verify         # baut und testet alle vier Module
```

Für die Arbeit an einem einzelnen Modul: `mvn verify` im Modulverzeichnis
ausführen.

`mvn verify` führt (pro Modul) aus:

- Unit-Tests (`*Test.java`, Surefire)
- Integrationstests (`*IT.java`, Failsafe, Testcontainers — nur BFF)
- JaCoCo-Coverage-Gate: ≥ 80 % Line, ≥ 70 % Branch — der Build bricht bei
  Unterschreitung. Alle vier Module erzwingen dieselben Schwellen, damit
  das Blueprint eine konsistente Qualitätsgrenze demonstriert; Bootstrap-
  Klassen und triviale Record-DTOs sind vom Gate ausgeschlossen.

### Frontend

```bash
cd frontend
npm ci
npx ng build --configuration production
```

Für interaktive Komponententests führt `npm test` den in `angular.json`
konfigurierten Karma-/Jest-Harness aus.

---

## Container-Vulnerability-Scans (Trivy)

Ergänzend zum Java-Dependency-Scan (OWASP Dependency-Check im `mvn verify`)
werden die Container-Artefakte dieses Repositories üblicherweise mit
[Trivy](https://trivy.dev) geprüft. Im Repository-Root liegen zwei Dateien,
die das Verhalten des Scanners steuern:

| Datei              | Zweck                                                                                                                                                                              |
|--------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `trivy.yaml`       | Globale Scanner-Konfiguration, wird automatisch eingelesen, wenn `trivy` aus dem Repo-Root gestartet wird. Regelt hier primär **pfadbasierte Ausschlüsse** (`scan.skip-dirs`).       |
| `.trivyignore`     | **Einzel-Finding-Suppressions**: eine Zeile pro CVE-/Finding-ID. Jede Zeile MUSS kommentiert sein (Grund, Verantwortliche*r, Ablaufdatum via `exp:YYYY-MM-DD`).                      |

### Warum zwei Dateien?

Die beiden Mechanismen decken unterschiedliche Fälle ab und sollten nicht
vermischt werden:

- **Pfadbasiert (`trivy.yaml` → `skip-dirs`)** eignet sich, wenn ein ganzer
  Bereich des Repos nicht Teil des ausgelieferten Artefakts ist — etwa ein
  reiner Entwickler-Container, dessen Basis-Image upstream gepflegt wird.
  In diesem Repo ist das der Fall für `keycloak/`: Das dortige Dockerfile
  existiert nur, damit `docker compose up` lokal einen IdP mitstartet. Ein
  produktiver Einsatz nutzt eine extern betriebene Keycloak-Instanz, sodass
  die CVEs des Keycloak-Basis-Images hier weder reproduziert noch gepatcht
  werden sollten. Wer den Keycloak-Container doch produktiv ausrollt, muss
  diesen Eintrag entfernen und die Befunde dann auch bewerten.
- **Pro Finding (`.trivyignore`)** eignet sich für gezielte, begründete
  Ausnahmen eines *einzelnen* CVE oder Finding-IDs, die nachweislich nicht
  zutreffen (nicht erreichbarer Code-Pfad, Keyword-Fehltreffer). Das ist das
  direkte Pendant zu `dependency-check-suppressions.xml` auf Maven-Seite und
  folgt demselben Prinzip: jede Ausnahme ist ein dokumentierter, ablaufender
  Vertrag, keine stille Unterdrückung.

Java-Dependency-Befunde landen **nicht** in `.trivyignore`, sondern in
`dependency-check-suppressions.xml` — das ist ein separater Scanner (OWASP
Dependency-Check), der mit eigenem Suppression-Schema im Maven-Build läuft.

### Scan lokal ausführen

```bash
# Filesystem-Scan (nimmt trivy.yaml + .trivyignore automatisch)
trivy fs .

# Image-Scan für ein konkret gebautes App-Image
trivy image bff-reference/bff:latest
```

---

## Barrierefreiheit (BITV 2.0 / WCAG 2.1 AA)

Barrierefreiheit wird als harte Anforderung behandelt, nicht als
„nice-to-have":

- Natives, semantisches HTML (`header`, `main`, `nav`, `article`, `dl`,
  `button`) statt ARIA-Wrapper.
- Jedes interaktive Element ist per Tastatur erreichbar und bedienbar,
  mit gut sichtbarem `:focus-visible`-Outline.
- Lade- und Fehlerzustände werden über `role="status"` /
  `aria-live="polite"` exponiert, damit Screenreader sie ansagen.
- Farbpalette und Fokusfarben erfüllen WCAG-2.1-AA-Kontraste sowohl im
  Light- als auch im Dark-Mode (`prefers-color-scheme`).
- Ein Skip-Link erlaubt Tastatur-Nutzenden, den Header zu überspringen.
- `prefers-reduced-motion` wird respektiert.
- Keine Accessibility-Overlays — Barrierefreiheit wird auf Markup-Ebene
  implementiert.

---

## Architekturentscheidungen

| ADR | Thema |
|-----|-------|
| [ADR-001](docs/adr/ADR-001-bff-pattern.md) | Warum BFF statt direktem SPA → API |
| [ADR-002](docs/adr/ADR-002-session-cookie.md) | Warum Session-Cookie statt Browser-Token |
| [ADR-003](docs/adr/ADR-003-redis-session-store.md) | Warum Redis als Session-Store |
| [ADR-004](docs/adr/ADR-004-webflux-aggregation.md) | Warum WebFlux für parallele Aggregation |
| [ADR-005](docs/adr/ADR-005-maven-build.md) | Warum Maven statt Gradle |

---

## Troubleshooting

| Symptom | Wahrscheinliche Ursache | Lösung |
|---------|-------------------------|--------|
| `docker compose up` hängt beim `keycloak`-Healthcheck | Der erste Realm-Import dauert ~30 s | `start_period` abwarten, dann `docker compose logs keycloak` prüfen |
| Login-Schleife zwischen `/login` und Keycloak | `KEYCLOAK_PUBLIC_ISSUER_URI` ist aus deinem Browser nicht auflösbar | Sicherstellen, dass er auf `http://localhost:8080/...` zeigt und Port 8080 publiziert ist |
| `403` bei POST/PUT/DELETE | Fehlendes oder veraltetes CSRF-Cookie | SPA neu laden — Angular liest `XSRF-TOKEN` neu und spiegelt es als Header |
| Dashboard-Widgets zeigen „keine Daten" | Ein Downstream-Service ist unten | Der BFF liefert bewusst eine partielle Antwort; `docker compose ps` prüfen |
| `mvn verify` scheitert am JaCoCo-Gate | Coverage unter Schwelle gefallen | Tests ergänzen; die Schwelle ist per Design 80 % Line / 70 % Branch |

---

## Lizenz

Dies ist ein Referenz-/Lehrprojekt. Nutzung auf eigenes Risiko.
