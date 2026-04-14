# ADR-001: Backend-for-Frontend (BFF) statt direkter SPA → API-Aufrufe

- **Status:** Akzeptiert
- **Datum:** 2026-04-06

## Kontext

Das Angular-Dashboard benötigt Daten von drei Downstream-Microservices
(user, notification, activity), die per OAuth2/OIDC über Keycloak
abgesichert sind. Zwei Architekturformen wurden erwogen:

1. **Direkt**: Die SPA hält einen eigenen OAuth2-Client, bezieht das
   Access-Token direkt von Keycloak und ruft jeden Microservice einzeln
   auf; die Antworten werden im Browser aggregiert.
2. **BFF**: Eine serverseitige Komponente besitzt den OAuth2-Flow, spricht
   im Namen des Nutzers mit den Microservices, aggregiert die Antworten
   und exponiert der SPA über Session-Cookies eine einzige,
   frontend-geformte API.

## Entscheidung

Wir verwenden einen **BFF**. Die Angular-Anwendung spricht ausschließlich
mit dem BFF, niemals direkt mit Keycloak oder einem Downstream-Service.
Tokens erreichen den Browser nicht.

## Konsequenzen

### Positiv

- **Keine Tokens im Browser.** Access- und Refresh-Tokens existieren nur
  serverseitig in Redis. XSS kann nicht stehlen, was nicht da ist.
  `localStorage` wird für Authentifizierung gar nicht verwendet.
- **Kleinerer Blast-Radius bei XSS.** Ein erfolgreicher XSS verleiht dem
  Angreifer die Autorität der aktuellen Seite (er kann weiterhin im Namen
  des Nutzers über das Session-Cookie Aufrufe tätigen), kann aber keine
  langlebigen Credentials exfiltrieren, die außerhalb des Browsers
  funktionieren würden.
- **Frontend bleibt dumm.** Die SPA implementiert keinen OIDC-Flow,
  kein Token-Refresh, keine Token-Speicherung, kein JWT-Parsing. Sie ist
  eine dünne Präsentationsschicht.
- **Aggregation an einer Stelle.** Dashboard-Daten aus drei Services werden
  einmal auf dem Server zusammengesetzt (`Mono.zip`, parallel, Timeout pro
  Service, fehlertolerant) und in einer einzigen Antwort ausgeliefert.
- **Bessere CORS-Position.** Der Browser muss nur mit seinem eigenen Origin
  sprechen. Downstream-Services können im internen Netzwerk verbleiben,
  ohne CORS-Themen.

### Negativ

- **Ein zusätzlicher Prozess im Betrieb.** Der BFF ist ein neues
  Deployable mit eigenem Skalierungs-, Logging- und Fehlerverhalten.
- **Session-State.** Wir hängen jetzt von Redis als Session-Speicher ab,
  was eine Betriebs-Abhängigkeit hinzufügt (siehe ADR-003 für die
  konkrete Wahl Redis).
- **Kopplung an die Frontend-Form.** Die BFF-API ist auf die aktuelle SPA
  zugeschnitten. Kommt ein zweites Frontend hinzu, konsumiert es entweder
  dieselbe BFF-API oder braucht einen zweiten BFF. Das ist eine bewusste
  Eigenschaft des Patterns, kein Fehler.

### Verworfene Alternative: SPA mit `angular-oauth2-oidc`

Hätte der SPA eigene Tokens gegeben — genau das wollen wir aus den oben
genannten Sicherheitsgründen ausdrücklich nicht. Außerdem verschiebt es
die Aggregation in den Client, erhöht Round-Trips und dupliziert Logik
pro Frontend.
