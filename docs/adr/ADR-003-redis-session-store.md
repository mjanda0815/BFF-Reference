# ADR-003: Redis als Session-Store

- **Status:** Akzeptiert
- **Datum:** 2026-04-06

## Kontext

Der BFF hält pro Nutzer Zustand: den Spring-Security-Context, den
`OAuth2AuthorizedClient` (Access-Token + Refresh-Token + Ablauf) sowie
CSRF-State. Dieser Zustand muss überleben:

- einen Neustart oder Redeploy des BFF-Prozesses,
- den Betrieb des BFF in **mehreren Instanzen** hinter einem Load-Balancer,
- einige Minuten Client-Inaktivität, ohne einen Re-Login zu erzwingen.

Mögliche Backing-Stores:

1. **In-Memory** (Default-Servlet-`HttpSession`). Beim Neustart verloren,
   nicht zwischen Instanzen geteilt.
2. **JDBC** via `spring-session-jdbc`. Erfordert eine Datenbank und
   leitet Session-Traffic durch SQL, was schwerer ist als nötig.
3. **Redis** via `spring-session-data-redis`.
4. **Hazelcast / Infinispan**. Bringt einen vollständigen Cluster-Stack
   mit, den das Projekt sonst nicht braucht.

## Entscheidung

Wir verwenden **Redis 7** über `spring-session-data-redis` mit dem
Lettuce-Treiber.

## Konsequenzen

### Positiv

- **Neustart-sicher und horizontal skalierbar.** Mehrere BFF-Instanzen
  können sich dasselbe Redis teilen und sehen dieselben Sessions.
- **TTL inklusive.** Redis-Keys haben ein natives TTL; Spring Session
  verknüpft das Session-Timeout mit diesem TTL, sodass abgelaufene
  Sessions sich selbst aufräumen.
- **Geringer Betriebsaufwand.** Ein einzelner `redis:8-alpine`-Container
  ohne Persistenz (`--save "" --appendonly no`) reicht als Session-Store
  aus — Sessions sind per Definition ephemer, Verluste bei Redis-Ausfall
  sind akzeptabel (Nutzer loggen sich einfach erneut ein).
- **First-Class-Support in Spring.** `@EnableRedisHttpSession`,
  Lettuce-Connection-Factory, Serializer und Health-Indikatoren sind
  out-of-the-box verfügbar.

### Negativ

- **Redis ist jetzt eine Laufzeit-Abhängigkeit.** Der BFF kann ohne Redis
  nicht starten. Das ist im Compose-File durch einen Healthcheck und
  `depends_on.condition: service_healthy` berücksichtigt.
- **Serialisierung.** Spring Session serialisiert Session-Attribute per
  Default mit JDK-Serialisierung. Das bedeutet: Neue Felder in
  Session-Klassen müssen rückwärtskompatibel sein oder es muss ein
  Cache-Flush geplant werden.

### Verworfene Alternative: JDBC-Session-Store

- Würde eine Datenbank allein für Sessions erfordern.
- Session-Writes bei jedem Request werden zu SQL-Traffic, pro Request
  deutlich teurer als ein Redis-`SET`.
- Fügt Schema-Migrationen zu einer Komponente hinzu, deren gesamtes
  Datenmodell „eine Session-ID zeigt auf einen Blob" ist.

### Verworfene Alternative: Sticky-Sessions + In-Memory

- Bindet Nutzer an eine bestimmte BFF-Instanz. Rolling-Deploys loggen
  alle aus.
- Überlebt keinen Instanz-Crash.
- Widerspricht Cloud-Native-Rolling-Update-Patterns.
