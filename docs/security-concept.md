# Sicherheitskonzept

Dieses Dokument beschreibt das Threat-Modell des BFF-Referenzprojekts, die
implementierten Gegenmaßnahmen und — ebenso wichtig — was bewusst außerhalb
des Scopes liegt.

## Vertrauensgrenzen

```
┌────────────────────┐  nicht vertrauenswürdig
│      Browser       │  (vom Angreifer kontrollierbares JavaScript möglich)
└──────────┬─────────┘
           │  HTTP(S), ausschließlich Cookies
           ▼
┌────────────────────┐  vertrauenswürdig
│   nginx + BFF      │
│ (aus Browsersicht  │
│  gleicher Origin)  │
└──────────┬─────────┘
           │  internes Docker-Netzwerk
           ▼
┌────────────────────┐  vertrauenswürdig
│  Keycloak, Redis,  │
│  Downstream-Svcs   │
└────────────────────┘
```

Das Einzige, was der Browser von unserem System sieht, ist der nginx-Origin
(in der Dev-Umgebung `http://localhost`). Er spricht niemals direkt mit
Keycloak oder den Microservices.

## Threat-Modell

Der Fokus liegt auf den OWASP-Top-10-Bedrohungen, die für eine tokenfreie
SPA-+-BFF-Architektur realistisch sind.

| # | Bedrohung                                      | Angriff                                                                                                            | Gegenmaßnahme                                                                                                                                                 |
|---|------------------------------------------------|--------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1 | **Token-Exfiltration über XSS**                | Schädliches JS liest Access-/Refresh-Token aus `localStorage`/Speicher und schickt sie an den Angreifer            | Gar keine Tokens im Browser. Access-/Refresh-Tokens existieren nur serverseitig in Redis; der Browser hält nur eine opake Session-ID in einem `HttpOnly`-Cookie. |
| 2 | **Session-Hijacking über XSS**                 | Schädliches JS liest `document.cookie` und stiehlt das Session-Cookie                                              | `SESSION`-Cookie ist `HttpOnly` → aus JS nicht erreichbar. Der Inhalt ist eine opake ID, außerhalb von Redis wertlos.                                           |
| 3 | **CSRF auf zustandsändernde Endpunkte**        | Drittseite sendet ein Formular an `/logout`, `/api/...` während der Nutzer eine gültige Session hat                | Spring Security CSRF mit Double-Submit-Cookie (`XSRF-TOKEN` + `X-XSRF-TOKEN`-Header). Zustandsändernde Methoden erfordern Übereinstimmung beider Werte.        |
| 4 | **Session-Fixation**                           | Angreifer besorgt sich eine Session-ID, bringt den Nutzer zur Nutzung und verwendet sie nach dem Login weiter       | Spring Session erzeugt nach erfolgreicher Authentifizierung eine *neue* Session-ID (Default-Strategie `changeSessionId`).                                       |
| 5 | **Abfangen des Authorization-Codes**           | Netzwerk-Angreifer fängt den OAuth2-`code` im Transport ab                                                          | Confidential-Client (Client-Secret auf dem BFF), kurzlebige Codes, HTTPS in jeder Nicht-Lokalumgebung (`BFF_COOKIE_SECURE=true`).                             |
| 6 | **Open-Redirect nach Login**                   | Angreifer baut eine Login-URL mit einem `redirect_uri` auf eine externe Seite                                       | Gültige Redirect-URIs sind in Keycloak gewhitelistet (`http://localhost:8090/*`); der BFF redirected nach Login ausschließlich auf den eigenen Frontend-Origin. |
| 7 | **Brute-Force-Login**                          | Angreifer probiert viele Passwörter gegen Keycloak                                                                  | Keycloak-Brute-Force-Protection ist im Realm-Export aktiviert.                                                                                                  |
| 8 | **Sensible Daten im Log**                      | Tokens, Session-IDs oder PII landen in stdout/Logdateien                                                            | Keine `DEBUG`-Logs für `org.springframework.security.oauth2`, keine `DEBUG`-Logs für `org.springframework.web.reactive.function.client`. SLF4J + Logback, strukturiert. |
| 9 | **Impersonation von Downstream-Services**      | Angreifer erreicht einen Microservice direkt und umgeht den BFF                                                     | Services sind nur im internen Docker-Netzwerk erreichbar; Browser kommen nicht ran. Zusätzlich validieren die Services JWTs über JWKS gegen Keycloak.          |
| 10| **Cache-Poisoning / veraltete index.html**     | Eine gecachte `index.html` verweist auf ein gehashtes Bundle, das nicht mehr existiert — oder umgekehrt            | nginx setzt `Cache-Control: no-store` auf `index.html` und `immutable` auf gehashte Assets.                                                                    |
| 11| **Clickjacking**                                | Angreifer bettet die SPA in ein iframe, um Klicks zu kapern                                                         | `X-Frame-Options: DENY` in nginx gesetzt.                                                                                                                       |
| 12| **MIME-Sniffing**                               | Browser rät den Content-Type und führt unerwartete Inhalte aus                                                      | `X-Content-Type-Options: nosniff` in nginx gesetzt.                                                                                                             |

---

## Cookie-Strategie

Es werden zwei Cookies genutzt. Beide sind aus Browsersicht First-Party,
weil nginx den BFF unter demselben Origin reverse-proxyt.

### `SESSION`-Cookie (Spring Session)

| Attribut    | Wert                                              |
|-------------|---------------------------------------------------|
| `HttpOnly`  | `true` — aus JS nicht erreichbar                  |
| `Secure`    | `true` in Nicht-Lokalumgebungen (`BFF_COOKIE_SECURE`) |
| `SameSite`  | `Lax`                                             |
| `Path`      | `/`                                               |
| `Max-Age`   | `BFF_SESSION_TIMEOUT_SECONDS` (Default 1800 s)    |
| Inhalt      | Opake Spring-Session-ID                           |

### `XSRF-TOKEN`-Cookie (CSRF-Double-Submit)

| Attribut    | Wert                                              |
|-------------|---------------------------------------------------|
| `HttpOnly`  | **`false`** — bewusst, damit die SPA es lesen kann |
| `Secure`    | `true` in Nicht-Lokalumgebungen                   |
| `SameSite`  | `Lax`                                             |
| `Path`      | `/`                                               |
| Inhalt      | Zufälliges CSRF-Token                             |

### Warum `SameSite=Lax` und nicht `SameSite=Strict`?

`Strict` würde das Cookie bei jeder Cross-Site-Navigation blockieren —
auch beim Top-Level-Redirect von Keycloak zurück auf
`http://localhost/login/oauth2/code/keycloak` nach einem erfolgreichen
Login. Der Browser würde das Session-Cookie bei diesem Redirect nicht
mitsenden, Spring Security würde eine *zweite* Session anlegen und den
OAuth2-State verlieren. Nutzer würden in einer Endlos-Schleife landen.

`Lax` sendet das Cookie bei Top-Level-GET-Navigationen mit — und genau das
ist der Post-Login-Redirect —, blockt es aber weiterhin bei eingebetteten
Cross-Site-Subressource-Requests (dem klassischen CSRF-Vektor). Kombiniert
mit dem expliziten Double-Submit-CSRF-Token auf zustandsändernden
Endpunkten ergibt sich folgendes Bild:

| Szenario                                              | `Lax`                                           |
|-------------------------------------------------------|-------------------------------------------------|
| Login-Redirect Keycloak → BFF (Top-Level-GET)         | Cookie mitgesendet ✓ (funktioniert)             |
| Bösartiger Form-POST von `attacker.example` → BFF     | Cookie mitgesendet, aber CSRF-Check failt → 403 ✓ |
| Verstecktes `<img>` von `attacker.example` → BFF      | Cookie **nicht** mitgesendet ✓                  |
| SPA ruft `/api/dashboard` vom selben Origin           | Cookie mitgesendet ✓                            |

`Lax` ist also die schwächste Variante, bei der der Login-Flow noch
funktioniert; das CSRF-Token schließt die Lücke bei zustandsändernden
Requests.

### Cookie-Attribute in der lokalen Entwicklung

In der lokalen Entwicklung ist `BFF_COOKIE_SECURE=false`, weil `localhost`
über reines HTTP ausgeliefert wird. In jeder echten Umgebung muss das Flag
auf `true` umgestellt werden. Gelesen wird es aus der Umgebung; es gibt
keinen hart kodierten Override.

---

## CSRF-Strategie: Double-Submit-Cookie

Spring Securitys `CookieCsrfTokenRepository` (mit `withHttpOnlyFalse()`)
wird gemeinsam mit einem SPA-freundlichen Request-Handler
(`SpaCsrfTokenRequestHandler`) verwendet. Der Vertrag lautet:

1. Beim ersten Safe-Request stellt Spring Security ein `XSRF-TOKEN`-Cookie
   aus (nicht `HttpOnly`).
2. Angulars `HttpClient` ist mit
   `withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' })`
   konfiguriert — er liest das Cookie automatisch und spiegelt es bei jedem
   zustandsändernden Request als Header zurück.
3. Spring Security vergleicht Cookie und Header. Stimmen sie nicht überein,
   wird der Request mit 403 abgewiesen.

Ein Cross-Origin-Angreifer kann das `XSRF-TOKEN`-Cookie wegen
Same-Origin-Policy nicht lesen, kann den Header also nicht fälschen und
damit keinen CSRF-Angriff durchführen — selbst wenn der Browser das
`SESSION`-Cookie unter `Lax` bei manchen Top-Level-Navigationen noch
mitsenden würde.

---

## Token-Lebenszyklus

- **Access-Token**: 5 Minuten (Keycloak-Realm-Einstellung). Im
  `OAuth2AuthorizedClient` in Redis gecached, transparent durch Spring
  Securitys `ServerOAuth2AuthorizedClientManager` erneuert.
- **Refresh-Token**: 30 Minuten. Wird genutzt, um das Access-Token nach
  Ablauf zu erneuern. Ist das Refresh-Token selbst abgelaufen, antwortet
  der BFF mit 401, räumt die Session ab und die SPA startet einen neuen
  Login.
- **ID-Token**: Nach dem Login vorhanden, wird aber nicht an
  Downstream-Services weitergereicht — nur für die Benutzernamensanzeige
  und Logout-Hints genutzt.
- **Revocation beim Logout**: Der BFF ruft Keycloaks `/revoke`-Endpunkt
  für das Refresh-Token auf und löscht den Redis-Session-Eintrag atomar.

Tokens werden **niemals** geloggt, in HTTP-Antworten zurückgegeben oder in
irgendeinem Key außerhalb des Spring-Session-Namespace in Redis gespeichert.

---

## Was bewusst *nicht* implementiert ist

Dies ist ein Referenz-/Lehrprojekt. Folgende Punkte sind in einer
Produktionsumgebung üblicherweise vorhanden, werden aber bewusst außerhalb
des Scopes gelassen, um den Fokus auf dem BFF-Pattern zu halten:

- **HTTPS-Terminierung.** Lokal läuft reines HTTP. In Produktion
  terminiert man TLS an nginx oder einem vorgelagerten Load-Balancer und
  stellt `BFF_COOKIE_SECURE=true`.
- **Verteiltes Rate-Limiting.** nginx ist nicht mit `limit_req`
  konfiguriert. Keycloaks Brute-Force-Protection deckt den Login-Endpunkt
  ab, Anwendungs-Endpunkte werden nicht limitiert.
- **Web Application Firewall.** Kein ModSecurity / Coraza im Proxy-Pfad.
- **mTLS zwischen BFF und Downstream-Services.** Der JWT-Bearer ist der
  einzige Authentifizierungs-Layer zwischen ihnen. In einem Zero-Trust-
  Setup würde man mTLS obendrauf setzen.
- **Secret-Management.** Das Client-Secret liegt aus Reproduzierbarkeits-
  gründen in `.env`. In Produktion gehört es in Vault / ein Cloud-KMS /
  ein Kubernetes-Secret.
- **Audit-Logging von Security-Events** über das hinaus, was Spring
  Security und Keycloak standardmäßig emittieren.
- **Content Security Policy.** Ein strenger CSP ist umgebungsspezifisch
  (abhängig von geladenen CDNs, Analytics, Fonts) und wird daher in
  dieser Referenz nicht ausgeliefert. Die nginx-Konfiguration hat
  Platzhalter für die anderen Security-Header (`X-Frame-Options`,
  `X-Content-Type-Options`, `Referrer-Policy`, `Permissions-Policy`).

Jeder dieser Punkte ist eine sinnvolle Ergänzung, würde aber den Scope des
Projekts verdoppeln, ohne am BFF-Pattern selbst etwas hinzuzufügen.
