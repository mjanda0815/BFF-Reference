# BFF-Referenzarchitektur (BAFA)

*Confluence-Dokumentation für das Template-Repository `bff-reference`.*
*Diese Seite beschreibt, was das Projekt ist, warum es existiert und wie neue
BAFA-Produkte es als Ausgangspunkt übernehmen.*

> **Repository:** `{{BAFA_REPO_URL}}` — *(Platzhalter; URL wird nach Anlage
> des Repos bei BAFA nachgereicht.)*
> **Namespace:** alle Java-Module liegen unter `de.bafa.*`
> **Stand:** Referenz-Version 1.0.0
> **Ownership:** `{{TEAM_ODER_KONTAKT}}`

---

## 1. Zweck dieses Dokuments

Die BFF-Referenzarchitektur ist ein **lauffähiges Template** für BAFA-Projekte,
die ein modernes Web-Frontend auf Basis von Angular zusammen mit mehreren
JWT-geschützten Backend-Microservices bereitstellen wollen.

Neue BAFA-Produkte sollen dieses Repository **als Startpunkt kopieren** und
anschließend an ihre Fachdomäne anpassen. Das Dokument beschreibt:

- welche Architekturmuster das Template demonstriert,
- wie es lokal betrieben wird,
- wie Entwicklerinnen und Entwickler es für ein neues Produkt adaptieren,
- welche Sicherheits-, Test- und Betriebsanforderungen schon erfüllt sind,
- wo konkret im Code die Patterns zu finden sind.

Das Template ist bewusst **didaktisch kommentiert**: jede Klasse trägt ein
JavaDoc, das Rolle, Pattern und Stolperfallen erklärt. Wer den Code liest,
soll die Entscheidungen verstehen — nicht nur abtippen.

---

## 2. Wann ist dieses Template die richtige Wahl?

**Passend, wenn das neue Produkt …**

- ein Web-Frontend (Angular) braucht, das gegen einen zentralen Identity
  Provider (Keycloak) authentifiziert,
- mehrere Microservices hinter einer gemeinsamen Fassade bündeln möchte,
- OAuth2/OIDC-Tokens nicht im Browser halten will (XSS-Härtung),
- Session-Management, CSRF-Schutz und Token-Refresh zentral und nicht pro
  Service lösen möchte,
- BITV-2.0-/WCAG-2.1-AA-Anforderungen an die Barrierefreiheit erfüllen muss.

**Nicht passend, wenn …**

- reine Server-zu-Server-APIs ohne Browser-Frontend gebaut werden
  (dann reichen Resource Server ohne BFF),
- eine Mobile-App als primärer Client dient (dann ist ein klassischer
  Auth-Flow mit PKCE im Gerät passender),
- das Frontend statisch ausgeliefert wird und kein Login braucht.

---

## 3. Architektur-Überblick

```
            ┌──────────────┐         ┌──────────────┐
  Browser → │   Angular    │  ─────► │    nginx     │
            │  (no tokens) │         │ (static SPA  │
            └──────────────┘         │  + reverse   │
                                     │   proxy)     │
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
                                     │   parallel   │
                                     │   Mono.zip   │
                                     └──┬───┬───┬───┘
                                        │   │   │
                       ┌────────────────┘   │   └──────────────────┐
                       ▼                    ▼                      ▼
                ┌──────────────┐    ┌──────────────┐        ┌──────────────┐
                │ user-service │    │ notification │        │   activity   │
                │   (REST,     │    │   service    │        │   service    │
                │  JWT-secured)│    │  (REST,      │        │   (REST,     │
                └──────────────┘    │  JWT-secured)│        │  JWT-secured)│
                                    └──────────────┘        └──────────────┘
```

### 3.1 Die zentralen Patterns

| Pattern | Wo im Code? | Warum? |
|---|---|---|
| **Backend-for-Frontend** | `de.bafa.bff` (gesamt) | Tokens bleiben server-seitig, SPA spricht nur Session-Cookies. |
| **Parallele Aggregation** | `DashboardAggregationService` | Ein SPA-Request = ein BFF-Response aus 3 Quellen via `Mono.zip`. |
| **Verteilte Write-Saga** | `DistributedWriteSagaOrchestrator` | Ein SPA-Kommando = ein BFF-Workflow über 3 Services mit Kompensation in Reverse-Order. Siehe ADR-006. |
| **Hexagonale Architektur** | `domain/port` + `adapter/client` | Application-Layer kennt nur Ports (Interfaces), nie WebClient. |
| **Double-Submit-CSRF** | `SecurityConfig#csrfTokenRepository` | Cookie-Auth braucht expliziten CSRF-Schutz. |
| **Session-Token-Lifecycle** | `SessionTokenService` | Access/Refresh-Token ausschließlich in Redis. |
| **Partial-Failure-Tolerance** | `DashboardAggregationService` | Fällt ein Downstream aus, bleibt das Dashboard sichtbar. |
| **RP-initiated Logout** | `SecurityConfig#oidcLogoutSuccessHandler` | Lokale Session + Keycloak-SSO-Session werden gemeinsam beendet. |

### 3.2 Komponenten im Detail

| Komponente | Technologie | Rolle |
|---|---|---|
| **BFF** | Spring Boot 4, WebFlux, Java 25 LTS | OIDC-Client, Session-Management, CSRF, Aggregation |
| **user-service** | Spring Boot 4, Resource Server, Java 25 | Profildaten, JWT-Validierung |
| **notification-service** | Spring Boot 4, Resource Server, Java 25 | Benachrichtigungen, JWT-Validierung |
| **activity-service** | Spring Boot 4, Resource Server, Java 25 | Aktivitäten-/Audit-Log, JWT-Validierung |
| **Keycloak** | Keycloak 26.6 | Identity Provider, JWKS |
| **Redis** | Redis 8 | Session-Store (`spring-session-data-redis`) |
| **Frontend** | Angular 21 (Node 22 Build) | Token-freier SPA-Client |
| **nginx** | nginx 1.29 alpine | Statisches SPA-Hosting + Reverse-Proxy |

---

## 4. Repository-Struktur

```
bff-reference/
├── pom.xml                     # Aggregator-POM — `mvn verify` für alles
├── docker-compose.yml          # Gesamter Stack mit einem Befehl
├── .env.example                # Alle konfigurierbaren Werte
├── keycloak/                   # Realm-Export + Dockerfile
├── bff/                        # de.bafa.bff
│   └── src/main/java/de/bafa/bff/
│       ├── BffApplication.java
│       ├── adapter/            # web-Controller + REST-Clients
│       ├── application/        # Aggregation + Session-Token-Service
│       ├── config/             # Security, OAuth2, Redis, WebClient, CORS
│       ├── domain/
│       │   ├── model/          # Records für Dashboard, UserProfile, …
│       │   └── port/           # Hexagonale Ports (Interfaces)
│       └── security/           # Logout-Handler
├── services/
│   ├── user-service/           # de.bafa.userservice
│   ├── notification-service/   # de.bafa.notificationservice
│   └── activity-service/       # de.bafa.activityservice
├── frontend/                   # Angular 21 SPA
└── docs/
    ├── architecture.md
    ├── security-concept.md
    ├── confluence.md           # dieses Dokument
    └── adr/                    # Architecture Decision Records (ADR-001..005)
```

**Namespace-Konvention:**

- groupId in allen POMs: `de.bafa`
- Java-Packages: `de.bafa.<modulname>` (z. B. `de.bafa.bff`,
  `de.bafa.userservice`)
- Artefaktnamen (`bff`, `user-service`, …) sind **rollenbasiert** und bleiben
  beim Kopieren gleich — nur der Java-Namespace wird produktspezifisch.

---

## 5. Lokaler Start (Quickstart)

**Voraussetzungen:** Docker + Docker Compose v2. Java/Maven/Node nur nötig,
wenn außerhalb Dockers gebaut werden soll.

```bash
git clone {{BAFA_REPO_URL}}
cd bff-reference
cp .env.example .env
docker compose up --build
```

Danach:

- SPA: <http://localhost>
- Keycloak-Admin: <http://localhost:8080> (`admin` / `admin`, siehe `.env`)
- Test-User: `demo@example.com` / `demo123`

Der Dashboard-Aufruf zeigt Daten aus allen drei Services, die der BFF
parallel aggregiert hat.

### Build & Tests ohne Docker

```bash
mvn verify          # Root-Reactor: alle 4 Module, inkl. JaCoCo-Gates
```

Einzelnes Modul:

```bash
cd bff && mvn verify
```

---

## 6. Sicherheitskonzept (Kurzfassung)

Das vollständige Threat-Modell liegt in
[`docs/security-concept.md`](security-concept.md). Hier die Kernpunkte:

| Aspekt | Lösung | Code-Ort |
|---|---|---|
| Tokens im Browser | **Keine** — alles serverseitig in Redis | `SessionTokenService` |
| Session-Cookie | `HttpOnly`, `Secure`*, `SameSite=Lax` | `BffProperties`, Spring Session |
| CSRF | Double-Submit (`XSRF-TOKEN` ↔ `X-XSRF-TOKEN`) | `SecurityConfig` |
| CORS | Nur konfigurierter Frontend-Origin erlaubt | `CorsConfig` |
| JWT-Validierung (Downstream) | JWKS via internen K8s-URL, `iss`-Claim gegen öffentlichen URL | `SecurityConfig` (je Service) |
| Logout | Lokale Session + Redis + Keycloak-SSO | `SessionInvalidationHandler`, `SecurityConfig` |
| Brute-Force-Schutz | Keycloak-Feature aktiv | `keycloak/realm-export.json` |
| Accessibility (BITV 2.0 AA) | Native HTML + Fokus + Reduced-Motion | `frontend/src` |

\* `Secure=true` ist in Prod Pflicht (`bff.cookie-secure=true`). Lokal ist
der Default `false`, weil Docker-HTTP kein TLS hat.

### Warum kein Token im Browser?

Access-Token im `localStorage`/`sessionStorage` sind durch einen einzigen
XSS-Fund kompromittierbar — Refresh-Token sogar dauerhaft. Der BFF-Ansatz
hält alle Tokens in einer Server-Session, die per `HttpOnly`-Cookie
referenziert wird. Der SPA-Code kann auf die Token-Werte nicht zugreifen,
also auch nicht versehentlich (oder durch injizierten Fremdcode) leaken.

---

## 7. Ein neues BAFA-Produkt auf Basis des Templates starten

Die **vollständige Schritt-für-Schritt-Anleitung** liegt in
[`getting-started-new-project.md`](getting-started-new-project.md).
Sie deckt — mit copy-and-paste-fähigen Kommandos —:

1. Repo-Provisionierung (GitHub „Use this template" vs. Fork vs. Flat-Copy)
2. Produkt-Namensgebung und die **Monorepo-vs.-Polyrepo-Entscheidung**
   (was dieses Template voreinstellt, wann splitten)
3. Namespace-Refactoring (`sed`/`find`-Rezepte, Modul-für-Modul grün halten)
4. Keycloak-Realm- und `.env`-Anpassungen
5. Ersten Smoke-Test (`docker compose up`)
6. Eigenen Microservice hinzufügen (Service-Kopier-Vorlage + BFF-Side-Steps)
7. Frontend anpassen, ohne den BFF-Vertrag zu brechen
8. Konfigurations-Härtung vor Go-Live

Die ursprüngliche Kurzfassung in diesem Confluence-Export verwies früher
auf sich selbst; seit Version 1.1.0 wird die Pflege zentral im neuen Guide
geführt, damit nicht zwei Stellen parallel driften.

### 7.1 Produktiv-Checkliste (Kurzfassung)

Der Go-Live-Abschnitt aus dem Guide, hier als Copy für den schnellen Scan:

- [ ] `bff.cookie-secure=true` in allen Nicht-Lokalumgebungen
- [ ] `bff.frontend-origin` auf eure echte HTTPS-Domain
- [ ] Keycloak mit echtem Realm + whitelisted Redirect-URIs
- [ ] Redis mit Passwort + TLS (`spring.data.redis.ssl.enabled=true`)
- [ ] Client-Secret aus Vault/K8s-Secret, nicht `.env`
- [ ] Redis-Backup-Strategie oder bewusster Sessions-gehen-verloren-Accept
- [ ] `/actuator/prometheus` nur intern exponieren
- [ ] Logstash-JSON-Appender in euer Log-Aggregat einhängen
- [ ] JaCoCo-Gates (80 %/70 %) nicht senken — Ausnahmen nur mit ADR

---

## 8. Testing-Strategie

| Stufe | Wo? | Technologie |
|---|---|---|
| Unit-Tests | `*Test.java`, je Modul | JUnit 5, Mockito, `reactor-test` |
| Integrationstests | `*IT.java`, nur BFF | Testcontainers (Redis), MockWebServer |
| Coverage-Gate | alle Module | JaCoCo 80 %/70 % — Build bricht bei Unterschreitung |
| Frontend | `frontend/` | Karma/Jest + `ng build` |

Der BFF hat als einziges Modul zusätzlich **Testcontainers-basierte
Integrationstests** (`DashboardIT`, `SessionIT`), die einen echten
Redis-Container hochfahren. Downstream-Services brauchen das nicht, weil
sie stateless sind.

### Teststruktur des BFF (als Beispiel für Blueprint-Adapter)

```
bff/src/test/java/de/bafa/bff/
├── adapter/
│   ├── client/         # WebClient-Adapter gegen MockWebServer
│   └── web/            # Controller gegen @WebFluxTest
├── application/        # Services gegen gemockte Ports
├── config/             # SecurityConfig mit SpringBootTest
├── security/           # Logout-Handler Unit-Test
├── DashboardIT.java    # End-to-End mit Redis-Container
└── SessionIT.java      # Session-Lifecycle gegen Redis-Container
```

### Coverage-Gate konfigurieren

Das Gate sitzt im jeweiligen `pom.xml` unter `jacoco-maven-plugin`:

```xml
<limits>
  <limit>
    <counter>LINE</counter>
    <value>COVEREDRATIO</value>
    <minimum>0.80</minimum>
  </limit>
  <limit>
    <counter>BRANCH</counter>
    <value>COVEREDRATIO</value>
    <minimum>0.70</minimum>
  </limit>
</limits>
```

Ausgeschlossen sind Bootstrap-Klassen und reine Record-DTOs — beides hat
keine testbare Logik.

---

## 9. Konfigurations-Referenz

### 9.1 `bff`-Properties (BFF, `application.yml`)

| Property | Default | Pflicht? | Bedeutung |
|---|---|---|---|
| `bff.frontend-origin` | `http://localhost` | ja | CORS-/Redirect-Ziel des SPA |
| `bff.session-timeout-seconds` | `1800` | – | Session-TTL in Redis |
| `bff.cookie-secure` | `false` | **prod: `true`** | HTTPS-Only-Cookie |
| `bff.user-service-url` | — | ja | Interner URL des user-service |
| `bff.notification-service-url` | — | ja | Interner URL des notification-service |
| `bff.activity-service-url` | — | ja | Interner URL des activity-service |
| `bff.service-timeout-millis` | `5000` | – | Timeout pro Downstream-Call |

### 9.2 Keycloak-Properties (BFF)

| Property | Bedeutung |
|---|---|
| `keycloak.issuer-uri` | Backchannel-URL (Service-Mesh-intern) |
| `keycloak.public-issuer-uri` | Frontchannel-URL (Browser-erreichbar) |
| `keycloak.client-id` | OIDC-Client-ID |
| `keycloak.client-secret` | OIDC-Client-Secret (Vault!) |

### 9.3 Downstream-Service-Properties

| Property | Bedeutung |
|---|---|
| `keycloak.jwk-set-uri` | JWKS-Endpoint, aus dem der Resource Server Keys lädt |
| `keycloak.expected-issuer` | Erwarteter `iss`-Claim (öffentlicher Keycloak-URL) |

---

## 10. Architektur-Entscheidungen (ADRs)

Die tragenden Entscheidungen liegen als ADRs im Repo. Bei eigenen Abweichungen
**ebenfalls als ADR dokumentieren**, damit nachfolgende Teams die Gründe
nachvollziehen können.

| ADR | Thema |
|---|---|
| [ADR-001](adr/ADR-001-bff-pattern.md) | Warum BFF statt direktem SPA → API? |
| [ADR-002](adr/ADR-002-session-cookie.md) | Warum Session-Cookie statt Browser-Token? |
| [ADR-003](adr/ADR-003-redis-session-store.md) | Warum Redis als Session-Store? |
| [ADR-004](adr/ADR-004-webflux-aggregation.md) | Warum WebFlux für parallele Aggregation? |
| [ADR-005](adr/ADR-005-maven-build.md) | Warum Maven statt Gradle? |
| [ADR-006](adr/ADR-006-write-saga.md) | Warum serverseitige Saga für verteilte Writes? |

---

## 11. FAQ / Troubleshooting

**F: Warum WebFlux statt klassischem Spring MVC im BFF?**
A: Die Aggregation dreier Downstream-Services profitiert von nicht-blockierender
I/O (ein Thread statt drei). Siehe ADR-004. Die Downstream-Services bleiben
bewusst MVC, weil sie simple Synchron-Endpunkte sind.

**F: Muss ich Redis einsetzen?**
A: Ja, wenn der BFF horizontal skaliert werden soll. Ein lokaler In-Memory-
Store ist nur für Dev-Szenarien tauglich.

**F: Kann ich den BFF auch ohne Keycloak nutzen?**
A: Jeden OIDC-konformen Provider (Azure AD, Auth0, Ping, …). Die
Registrierung liegt in `KeycloakClientRegistrationConfig` und ist
programmatisch — für andere IdPs Endpoints entsprechend umstellen.

**F: Wie viele Tests schreibe ich minimal?**
A: Die JaCoCo-Gates (80 % Line / 70 % Branch) sind Pflicht. Darüber hinaus:
Security-Konfiguration gehört **immer** in einen Integrationstest, nicht in
einen Unit-Test — das Template macht das in `SecurityConfigTest` vor.

**F: Login-Loop zwischen `/login` und Keycloak.**
A: `KEYCLOAK_PUBLIC_ISSUER_URI` zeigt nicht auf den Host, den der Browser
erreicht. In Dev muss das auf `http://localhost:8080/...` auflösen.

**F: `403` auf POST/PUT/DELETE.**
A: CSRF-Token fehlt oder stale. SPA muss `XSRF-TOKEN`-Cookie lesen und als
`X-XSRF-TOKEN`-Header zurücksenden — Angular's `HttpClient` mit
`withXsrfConfiguration` tut das automatisch.

**F: Dashboard zeigt „keine Daten" obwohl User eingeloggt ist.**
A: Einer der Downstream-Services ist unten — der BFF liefert bewusst eine
partielle Antwort. `docker compose ps` bzw. Service-Logs prüfen.

**F: `mvn verify` scheitert am JaCoCo-Gate.**
A: Coverage ist unter die Schwelle gefallen. Tests ergänzen — das Gate
darf nicht gesenkt werden.

---

## 12. Weiterführende Dokumente

- [Architektur-Übersicht](architecture.md) — Sequenzdiagramme, Komponentensicht
- [Sicherheits-Konzept](security-concept.md) — Threat-Modell, Cookie-Flags, Logout-Chain
- [Getting-Started für neue Projekte](getting-started-new-project.md) —
  Schritt-für-Schritt-Bootstrap inkl. Namespace-Refactoring und der
  Monorepo-vs.-Polyrepo-Diskussion
- [ADR-Verzeichnis](adr/) — Entscheidungs-Log
- **Folien der Architektur-Session**: [`BFF_Demo_Praesentation.pptx`](BFF_Demo_Praesentation.pptx)
  im Ordner `docs/` — die Entscheider-Variante „Wann BFF, wann nicht" mit
  direktem Vergleich No-BFF/With-BFF. Die `.pptx`-Datei liegt bewusst in
  `docs/` (nicht im Repo-Root), damit alle dokumentarischen Artefakte
  gemeinsam versioniert werden; die README verlinkt sie prominent.
- Im Code: Jede Klasse hat Class-Level-JavaDoc mit Rolle, Pattern,
  Copy-Guidance. Start am besten bei:
  - `de.bafa.bff.BffApplication`
  - `de.bafa.bff.config.SecurityConfig`
  - `de.bafa.bff.application.DashboardAggregationService`
  - `de.bafa.userservice.SecurityConfig` (Muster für alle Downstream-Services)

---

## 13. Beitragen und Kontakt

**Pull Requests an das Template** sind willkommen, solange sie:

- die **Blueprint-Natur** erhalten (didaktische JavaDoc, keine produkt-
  spezifischen Fachlichkeiten),
- von einem ADR begleitet werden, wenn sie architektonisch wirken,
- `mvn verify` grün durchfahren (inkl. Coverage-Gates).

**Ansprechpartner:** `{{TEAM_ODER_KONTAKT}}`
**Slack / Teams-Kanal:** `{{CHANNEL}}`
**Issue-Tracker:** `{{BAFA_REPO_URL}}/issues`

---

*Version: 1.0 — Abgestimmt auf Repository-Tag `v1.0.0`.
Bei Änderungen an diesem Template Dokument aktualisieren und versionieren.*
