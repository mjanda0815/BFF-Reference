# ADR-004: Spring WebFlux für die Aggregationsschicht des BFF

- **Status:** Akzeptiert
- **Datum:** 2026-04-06

## Kontext

Der zentrale Mehrwert des BFF ist das Aggregieren dreier Downstream-Services
in einem Request. Für den Endpunkt `GET /api/dashboard` wollen wir:

- alle drei Downstream-Aufrufe **parallel**, nicht sequenziell, laufen
  lassen,
- pro Service ein **Timeout**, damit ein langsamer Service nicht das
  gesamte Dashboard blockiert,
- **Fehlertoleranz bei Teilausfall**: Fällt ein Service aus, werden die
  anderen beiden Widgets plus ein neutraler Platzhalter zurückgegeben,
  statt den Request zu verwerfen,
- **backpressure-freundliche IO**, weil der BFF fast ausschließlich auf
  Netzwerk-Sockets wartet, bis Downstream-Antworten kommen.

Die beiden realistischen Optionen unter Spring Boot 3:

1. **Spring MVC + `RestTemplate`/`RestClient`** mit einem Thread-Pool,
   der die Aufrufe per `CompletableFuture.supplyAsync` fan-outet.
2. **Spring WebFlux + `WebClient`** mit reaktiver Komposition
   (`Mono.zip`, `timeout`, `onErrorResume`).

## Entscheidung

Wir verwenden **Spring WebFlux** mit `WebClient` für den BFF. Die
Downstream-Microservices bleiben dagegen auf dem klassischen Servlet-Stack
(`spring-boot-starter-web`), weil ihr Job trivial ist und Latenz dort
keine Rolle spielt.

## Konsequenzen

### Positiv

- **Idiomatische parallele Komposition.** `Mono.zip(a, b, c)` führt die
  drei Downstream-Aufrufe nebenläufig aus, ohne manuelle Thread-Pool-
  oder Future-Buchhaltung. Der resultierende Code liest sich wie eine
  Spezifikation dessen, was der Endpunkt tut.
- **Pro Aufruf `.timeout(Duration)` und `.onErrorResume(...)`.** Genau
  die Operatoren, die wir für resiliente Aggregation brauchen — Timeouts
  und Teilausfall ergeben sich aus der Reactor-API, statt angeflanscht
  werden zu müssen.
- **Wenige Threads unter Last.** Der BFF ist IO-bound. Das Event-Loop-
  Modell von WebFlux kommt mit einer kleinen, festen Anzahl Threads aus,
  unabhängig von der Anzahl nebenläufiger Aggregationen.
- **First-Class-Support für OAuth2-Client.** Spring Securitys reaktiver
  `ServerOAuth2AuthorizedClientManager` integriert sich sauber mit
  `WebClient`, sodass Access-Token-Refresh für den
  Aggregation-Service transparent ist.

### Negativ

- **Reaktiv ist ein anderes Programmiermodell.** Stack-Traces, Debugging
  und versehentliches Blockieren sind schwieriger als in imperativem
  Code. Der Aggregation-Service hält die reaktive Oberfläche bewusst
  klein: die Application-Schicht exponiert `Mono<DashboardData>`, und
  weiter unten im Code blockiert nichts.
- **Mischen mit blockierenden Bibliotheken ist gefährlich.** Wir
  vermeiden es; alles, was mit der Außenwelt spricht (Keycloak,
  Downstream-Services, Redis über Lettuce), ist bereits non-blocking.
- **Test-Autorenschaft** nutzt `StepVerifier` / `WebTestClient` statt
  des vertrauteren `MockMvc`. Weniger ein Kostenpunkt als eine
  Lernkurve.

### Warum die Microservices bei Spring MVC bleiben

Jeder Microservice macht einen einzelnen datenbankfreien Lookup. Es gibt
keine Aggregation und keine sinnvolle IO, die sich parallelisieren ließe.
Spring MVC ist für diesen Job einfacher und hält die Servlet-basierte
Resource-Server-Konfiguration kurz.
