# Neues Projekt auf Basis dieses Templates aufsetzen

Diese Anleitung richtet sich an BAFA-Teams, die dieses Repository als
**Blueprint** für ein eigenes Produkt übernehmen wollen — mit einer
Angular-21-SPA im Frontend, einem Spring-Boot-4-BFF als Session-/
Aggregationsschicht und einem oder mehreren JWT-gesicherten
Microservices im Backend.

Wer die architektonische Motivation erst verstehen will, findet sie in
der Folienpräsentation [`BFF_Demo_Praesentation.pptx`](BFF_Demo_Praesentation.pptx).
Dieses Dokument setzt die Entscheidung als gefallen voraus und führt
Schritt für Schritt durch den Start.

---

## Zeit- und Aufwandseinschätzung

| Schritt                                          | Zeit  |
|--------------------------------------------------|-------|
| 1. Repo provisionieren                           | 5 min |
| 2. Namensgebung + Monorepo-/Polyrepo-Entscheidung | 15 min (einmalige Diskussion) |
| 3. Namespace-Refactoring                         | 20 min |
| 4. Keycloak-Realm & `.env` anpassen              | 10 min |
| 5. Erster Smoke-Test (`docker compose up`)       | 5 min |
| 6. Ersten eigenen Microservice bauen             | 30–60 min |
| **Summe bis „grün lauffähige Kopie mit Produktnamen"** | **~1 h** |

---

## 1. Voraussetzungen

Lokal installiert:

- Docker + Docker Compose v2
- Java 25 LTS, Maven 3.9+ *(optional — die Docker-Images bringen ihr
  eigenes JDK mit; nur nötig, wenn du `mvn verify` ohne Container fahren
  willst)*
- Node 22 LTS *(optional — nur für `ng build` ohne Container)*
- Git, Zugang zur Zielgruppe (GitHub/GitLab) bei BAFA

Die genauen Versions-Pins stehen in der [Technologie-Matrix der README](../README.md).

---

## 2. Repo provisionieren

### Option A — GitHub „Use this template" (empfohlen)

1. Im Template-Repo auf GitHub **"Use this template"** klicken → „Create a
   new repository".
2. Zielorganisation (`bafa/<produkt>`) und Repo-Name wählen.
3. Lokal klonen, mit neuem Upstream arbeiten.

Vorteil: kein Commit-Verlauf des Templates in der Produkt-History. Der
gemeinsame Startpunkt bleibt trotzdem rekonstruierbar (Git-Log des
Templates zu diesem Zeitpunkt).

### Option B — Fork + Rebase

Sinnvoll, wenn dein Team vorhat, **Upstream-Updates des Templates**
(Security-Bumps, neue ADRs, verbesserte Patterns) regelmäßig
zurückzumergen. Dann:

```bash
git clone <dein-produkt-repo>
git remote add template <url-dieses-templates>
git fetch template
# Periodische Merges: git merge template/main --no-ff
```

### Option C — Flat Copy

Für Teams ohne laufendes Upstream-Interesse:

```bash
git clone <template>
rm -rf <template>/.git
cd <template>
git init && git add . && git commit -m "Initial import from bff-reference"
```

Damit geht die Commit-History verloren — simpel, aber keine Audit-Spur
auf das Template.

---

## 3. Namensgebung und Monorepo- vs. Polyrepo-Entscheidung

### 3.1 Produkt-Namespace

**Empfehlung:** Produkt-spezifischen Unter-Namespace unter `de.bafa.*`
einziehen.

| Produkt (Beispiel) | groupId             | Java-Package              |
|--------------------|---------------------|---------------------------|
| Förderportal       | `de.bafa.foerderportal` | `de.bafa.foerderportal.bff`, `de.bafa.foerderportal.userservice`, … |
| Antragstool        | `de.bafa.antrag`    | `de.bafa.antrag.bff`, `de.bafa.antrag.userservice`, …              |

Rollenbasierte Artefaktnamen (`bff`, `user-service`, …) bleiben erhalten.
Das macht Pfade, Dockerfile-Labels und K8s-Resource-Namen über alle
BAFA-Produkte hinweg konsistent.

### 3.2 Monorepo vs. Polyrepo

**Dieses Template ist ein Monorepo:** Ein Git-Repo enthält BFF, drei
Downstream-Services, Frontend und Doku. Ein `git clone` reicht, ein
`mvn verify` baut alles.

#### Wann Monorepo weiter machen?

- **Ein Team** (oder mehrere eng abgestimmte Teams) besitzt das gesamte
  Produkt.
- **Release-Zyklen** der Module sind synchron: BFF, Services und
  Frontend werden zusammen deployed.
- **Schnittstellen-Refactorings** — z. B. ein neues Feld im
  `DashboardData`-Record, das durch Domain-Ports, WebClient-Adapter,
  BFF-Controller und SPA-Komponente reicht — sollen als **ein Commit**
  begutachtet werden.
- **Test-Determinismus**: Jeder CI-Lauf sieht denselben BFF gegen
  dieselben Services; keine Versionsdrift über Repos hinweg.

Für die meisten BAFA-Projekte ist das der Default — **im Zweifel
Monorepo, solange ein Team das Ganze verantwortet**.

#### Wann auf Polyrepo splitten?

- Separate Teams mit **eigenem Release-Kalender** übernehmen einzelne
  Services (z. B. ein zentraler User-Service, der von mehreren
  Produkten konsumiert wird).
- Unterschiedliche **Compliance-Kreise** (z. B. ein Service verarbeitet
  Sozialdaten und braucht einen eigenen, abgeschirmten Release-Flow).
- Das Frontend hat **mehrere Deploy-Ziele** (z. B. Kiosk-Variante +
  Sachbearbeitungs-Variante) und soll unabhängig vom Backend rollieren.
- Die CI-Dauer eines reactor-weiten `mvn verify` wird spürbar hinderlich
  (in der Regel ab ~15+ Modulen, nicht bei vier).

**Split-Pfad, falls nötig:**

```
# Ziel-Struktur pro Service-Repo:
<produkt>-userservice/
├── .github/workflows/
├── pom.xml                 # kein Aggregator mehr, Standalone Spring Boot
├── src/
└── Dockerfile

# Weitere Polyrepo-Artefakte:
<produkt>-notificationservice/
<produkt>-activityservice/
<produkt>-bff/
<produkt>-frontend/
<produkt>-platform-docs/    # ADRs, Architektur, zentral gepflegt
```

Für den Schnitt zwischen BFF und Services bieten sich **OpenAPI-Specs**
in einem `contracts`-Repo an, die von BFF und Services gleichermaßen
konsumiert und im CI gegen die Implementierung geprüft werden.

**Migrationshilfe**: Wer später vom Monorepo auf Polyrepo wechselt, kann
mit `git filter-repo --subdirectory-filter services/user-service` die
Module inklusive Historie rausschneiden.

#### Kompromiss: Thin Monorepo + veröffentliche Libraries

Domain-Modelle und DTOs, die sowohl der BFF als auch ein Service kennt,
können als separates Maven-Modul + Artefakt veröffentlicht und über die
interne Maven-Registry eingebunden werden. Das bleibt im Monorepo-
Topologiemodell, entkoppelt aber Versionen an den heißesten Stellen.

---

## 4. Namespace-Refactoring

Das Template liegt bereits unter `de.bafa.*`. Die Umbenennung auf das
Produkt-Package dauert ~20 Minuten, läuft aber gut scriptbar:

```bash
# Beispiel: Produkt "foerderportal"
PRODUKT=foerderportal

# 4.1 Package-Verzeichnisse verschieben (Modul für Modul)
for M in bff services/user-service services/notification-service services/activity-service; do
  OLD="$M/src/main/java/de/bafa"
  NEW="$M/src/main/java/de/bafa/$PRODUKT"
  [ -d "$OLD/bff" ]                 && git mv "$OLD/bff"                 "$NEW/bff"
  [ -d "$OLD/userservice" ]         && git mv "$OLD/userservice"         "$NEW/userservice"
  [ -d "$OLD/notificationservice" ] && git mv "$OLD/notificationservice" "$NEW/notificationservice"
  [ -d "$OLD/activityservice" ]     && git mv "$OLD/activityservice"     "$NEW/activityservice"
  # Analog für src/test/java/...
done

# 4.2 Package- und Import-Statements anpassen
find . -name "*.java" -exec sed -i "s|de\.bafa\.bff|de.bafa.$PRODUKT.bff|g" {} +
find . -name "*.java" -exec sed -i "s|de\.bafa\.userservice|de.bafa.$PRODUKT.userservice|g" {} +
find . -name "*.java" -exec sed -i "s|de\.bafa\.notificationservice|de.bafa.$PRODUKT.notificationservice|g" {} +
find . -name "*.java" -exec sed -i "s|de\.bafa\.activityservice|de.bafa.$PRODUKT.activityservice|g" {} +

# 4.3 Maven groupIds
find . -name "pom.xml" -exec sed -i "s|<groupId>de\.bafa</groupId>|<groupId>de.bafa.$PRODUKT</groupId>|g" {} +

# 4.4 Spring Boot referenziert die Application-Klasse über <mainClass>
#     — sitzt im Boot-Plugin-Block, sollte durch (4.2) schon gegriffen sein.

# 4.5 JaCoCo-Exclude-Patterns in den POMs anpassen
find . -name "pom.xml" -exec sed -i "s|de/bafa/bff|de/bafa/$PRODUKT/bff|g" {} +
find . -name "pom.xml" -exec sed -i "s|de/bafa/userservice|de/bafa/$PRODUKT/userservice|g" {} +
# (analog für notificationservice, activityservice)

# 4.6 Grün-Check
mvn -B -ntp -DskipOwasp verify
```

Wenn der Reactor grün durchfährt, ist der Umzug sauber. Schlägt ein
Modul fehl, zeigt der Compiler meist punktgenau, welche Datei noch einen
alten Import hat.

---

## 5. Keycloak-Realm und `.env`

Vor dem ersten Start:

1. **Realm-Name**: `keycloak/realm-export.json` — `realm` umbenennen
   (`bff-demo` → `<produkt>`), Redirect-URIs auf eure Frontend-Domain
   anpassen.
2. **Client-Secret rotieren**: `KEYCLOAK_CLIENT_SECRET` in `.env` neu
   setzen (der importierte Realm muss denselben Wert als
   `secret`-Claim des Clients haben).
3. **Test-User** (`demo@example.com / demo123`) entfernen oder durch
   eigene Testaccounts ersetzen. Produktions-User kommen über eine
   Identity-Federation oder manuell in Keycloak.
4. **Token-Laufzeiten** (Access 5 min / Refresh 30 min) nach Produkt-
   Policy justieren.

Die `.env`-Defaults (`REDIS_HOST`, `USER_SERVICE_URL`, …) passen für
`docker compose up` out-of-the-box. Für Dev-Cluster/K8s werden sie über
Secrets bzw. ConfigMaps überschrieben.

---

## 6. Erster Smoke-Test

```bash
cp .env.example .env     # falls noch nicht geschehen
docker compose up --build
```

Erwartung:

- SPA unter <http://localhost>
- Keycloak-Admin unter <http://localhost:8080>
- Login mit Testuser zeigt das Dashboard mit Daten aus allen drei
  Services

Wenn hier etwas hakt, ist die schnellste Diagnose `docker compose ps`
+ `docker compose logs <service>`. Die README-Sektion *Troubleshooting*
listet die häufigsten Startprobleme.

---

## 7. Eigenen Microservice hinzufügen

Der Kopierpfad vom User-Service aus ist in ~30 Minuten fertig:

```bash
cp -r services/user-service services/meinservice
cd services/meinservice

# pom.xml: artifactId + finalName auf meinservice umbenennen
# src/main/java/de/bafa/<produkt>/userservice → .../meinservice
# SecurityConfig.java + Application.java bleiben strukturell gleich
# Controller + Domain-Record durch fachliche Varianten ersetzen
```

BFF-seitig dann:

1. Neuer Port in `bff/src/main/java/.../domain/port/MeinServicePort.java`
2. Neuer Adapter in `.../adapter/client/MeinServiceClient.java`
3. `WebClientConfig` um die neue Bean erweitern
4. `BffProperties` um die URL-Property erweitern
5. `DashboardAggregationService` (oder neuer Use-Case-Service) in
   `Mono.zip` einhängen
6. Root-`pom.xml` → `<modules>` erweitern
7. `docker-compose.yml` → neuer Service-Eintrag + `depends_on`

Tests für den neuen Service inklusive `SecurityConfigTest` übernehmen —
die 80 %/70 %-JaCoCo-Gates gelten reactor-weit.

---

## 8. Frontend anpassen

Die Angular-Seite ist der unverfänglichste Teil, weil sie keine
Authentifizierungs-Logik trägt:

- **Komponenten** unter `frontend/src/app/` durch deine fachlichen
  ersetzen; Typen an das eigene Domain-Modell anpassen.
- **HTTP-Client** nicht umkonfigurieren — `withCredentials: true` und
  `withXsrfConfiguration` sind Teil des BFF-Vertrags.
- **Routing** erweitern; das Beispiel zeigt nur `/dashboard`. Der 401-
  Interceptor (`auth.interceptor.ts`) leitet ungültige Sessions
  automatisch zum `/login`-Endpunkt des BFF — auch neue Routen erben
  das ohne Zutun.
- **Barrierefreiheit** nicht regressiv machen: siehe Abschnitt in der
  [README](../README.md#barrierefreiheit-bitv-20--wcag-21-aa).

Die Angular-Version ist 21 (LTS-Stand April 2026). Angular 22 GA wird
im Mai 2026 erwartet — der Upgrade-Pfad ist in der Project-Memory des
Templates vermerkt und durch Dependabot-PRs automatisiert.

---

## 9. Konfigurations-Härtung vor Go-Live

Checkliste (identisch mit confluence.md §7.6, für Vollständigkeit hier):

- [ ] `bff.cookie-secure=true` in allen Nicht-Lokalumgebungen
- [ ] `bff.frontend-origin` auf eure echte HTTPS-Domain
- [ ] Keycloak mit echtem Realm + whitelisted Redirect-URIs
- [ ] Redis mit Passwort + TLS (`spring.data.redis.ssl.enabled=true`)
- [ ] Client-Secret aus Vault/K8s-Secret, nicht `.env`
- [ ] Redis-Backup-Strategie oder bewusster Sessions-gehen-verloren-Accept
- [ ] `/actuator/prometheus` nur intern exponieren (ingress-level)
- [ ] Logstash-JSON-Appender in euer Log-Aggregat einhängen
- [ ] Produktive Tomcat-Connector-Tuning (Threadpool, Accept-Count)
- [ ] JaCoCo-Gates (80 %/70 %) nicht senken — Ausnahmen nur mit ADR

---

## 10. Weiterführende Dokumente

- [`../README.md`](../README.md) — Quickstart, Technologie-Matrix,
  Trivy/OWASP-Sektion
- [`architecture.md`](architecture.md) — Komponenten, Sequenzdiagramme
- [`security-concept.md`](security-concept.md) — Threat-Modell,
  Cookie-/CSRF-Strategie
- [`confluence.md`](confluence.md) — Confluence-Export mit der langen
  Erzählung (dieses Dokument ist die knappe Entscheider-Variante)
- [`adr/`](adr/) — die fünf tragenden ADRs, die die Auswahl der Patterns
  begründen
- [`BFF_Demo_Praesentation.pptx`](BFF_Demo_Praesentation.pptx) —
  Folien der Architektur-Session

---

## 11. Anlaufstellen

- **Template-Owner** bei Fragen zur Blueprint-Erweiterung:
  `{{TEAM_ODER_KONTAKT}}`
- **Upstream-Änderungen vorschlagen**: Pull Request an dieses Repo mit
  ADR-Begleitung (siehe [`../CONTRIBUTING.md`](../CONTRIBUTING.md))
