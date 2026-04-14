# ADR-002: SPA-Authentifizierung per Session-Cookie statt Browser-Token

- **Status:** Akzeptiert
- **Datum:** 2026-04-06

## Kontext

Nachdem die Entscheidung für einen BFF (ADR-001) gefallen war, musste
festgelegt werden, wie die SPA sich bei jedem Request gegenüber dem BFF
authentifiziert. Zwei gängige Optionen:

1. **Bearer-Token im `Authorization`-Header**, wobei das Token entweder
   direkt von Keycloak bezogen oder vom BFF ausgestellt wird. Die SPA
   speichert es im Speicher oder `localStorage`.
2. **Opakes Session-Cookie**, das der BFF nach dem Login setzt und das
   der Browser bei jedem weiteren Request automatisch mitsendet.

## Entscheidung

Wir verwenden ein **opakes `HttpOnly`-Session-Cookie** (`SESSION`), das
Spring Session ausstellt. Die SPA hält keinerlei Tokens.

## Konsequenzen

### Positiv

- **XSS kann das Cookie nicht lesen.** `HttpOnly` entzieht das Cookie dem
  Zugriff über `document.cookie`. Ein XSS kann den Nutzer für die Dauer
  des Angriffs weiterhin imitieren, aber die Session-ID nicht so
  exfiltrieren, dass sie außerhalb des Browsers von einer
  angreifer-kontrollierten Maschine nutzbar wäre.
- **Automatische Mitlieferung.** Der Browser hängt das Cookie an jeden
  Same-Origin-Request. Die HTTP-Schicht der SPA ist trivial:
  `withCredentials: true`, fertig.
- **Günstige Rotation/Invalidation.** Der Server besitzt die Session.
  Logout, Timeout oder administrative Revocation ist ein einzelnes
  `DEL` auf Redis — keine Revocation-Liste, keine Token-Blacklist nötig.
- **Kein Token-Parsing im Frontend.** Kein JWT-Decoding, kein Clock-Skew-
  Handling, keine „ist mein Token bald abgelaufen"-Logik.

### Negativ

- **Erfordert Same-Origin-Deployment** (SPA und BFF teilen sich einen
  Origin via nginx-Reverse-Proxy). Cross-Origin-Cookie-Setups brauchen
  `SameSite=None; Secure`, was die CSRF-Position schwächt und daher hier
  vermieden wird.
- **CSRF-Schutz ist zwingend.** Weil der Browser das Cookie automatisch
  mitsendet, müssen wir uns gegen Cross-Site-Request-Forgery schützen.
  Die Double-Submit-Token-Implementierung ist im Sicherheitskonzept
  beschrieben.

### Verworfene Alternative: Access-Token im `Authorization`-Header aus der SPA

- Setzt Tokens XSS aus (entweder im `localStorage` oder in JS-Speicher,
  den das Angreiferskript erreichen kann).
- Erschwert Logout: Das Invalidieren eines bereits beim Client liegenden
  JWTs erfordert eine Blacklist oder sehr kurze Laufzeiten plus
  Refresh-Flow, was wiederum eigene Speicherprobleme mit sich bringt.
- Zwingt CORS + `Authorization`-Header-Verdrahtung ins Frontend bei
  jedem Aufruf, ohne echten Nutzen in einem Same-Origin-Deployment.
