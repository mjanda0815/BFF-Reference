# ADR-005: Maven als Build-Tool für alle Java-Module

- **Status:** Akzeptiert
- **Datum:** 2026-04-06 (aktualisiert 2026-04-14)

## Kontext

Das Projekt enthält vier Java-Module (BFF + drei Microservices), die ein
Build-Tool, Dependency-Management, eine Test-Phase, ein Coverage-Gate und
einen Container-Image-Build brauchen. Die beiden realistischen Optionen
sind **Maven** und **Gradle**.

## Entscheidung

Wir verwenden **Maven 3.9+** mit einer `pom.xml` pro Modul sowie einem
Aggregator-POM auf Root-Ebene (hinzugefügt in Version 1.0.0), damit der
gesamte Stack mit einem einzigen `mvn verify` aus dem Repository-Root
gebaut werden kann. Jedes Modul bleibt dabei eigenständig: eigenes
Dockerfile, eigenes JaCoCo-Gate, eigene Dependencies — sodass ein Team,
das nur ein einzelnes Modul als Blueprint übernehmen will, es ohne
Reactor-Magie herausschneiden kann.

## Konsequenzen

### Positiv

- **Enterprise-Standard.** Maven ist mit weitem Abstand das
  verbreitetste Java-Build-Tool in regulierten/enterprise-Umgebungen.
  Jede Person kann eine `pom.xml` lesen; eine eigene Gradle-DSL kann man
  nicht voraussetzen.
- **Deklarativ und reproduzierbar.** Gleiche `pom.xml` und gleiche
  Maven-Version liefern dasselbe Build-Ergebnis — über Maschinen und
  Monate hinweg.
- **Stabiles Plugin-Ökosystem.** Die benötigten Plugins —
  `spring-boot-maven-plugin`, `maven-surefire-plugin`,
  `maven-failsafe-plugin`, `jacoco-maven-plugin` — sind entweder
  first-party oder ausgereift und brechen zwischen Versionen selten.
- **Klare Trennung Unit- vs. Integrationstest.** Surefire erkennt
  `*Test.java`, Failsafe erkennt `*IT.java`, Failsafe läuft in einer
  späteren Lifecycle-Phase. Keine Custom-Task-Verdrahtung nötig.
- **Durchsetzbares Coverage-Gate.** `jacoco-maven-plugin` mit dem
  `check`-Goal lässt den Build bei sinkender Coverage fehlschlagen —
  eine harte Projektanforderung (≥ 80 % Line, ≥ 70 % Branch).
- **Optionaler Reactor-Build.** Der Root-`pom.xml` aggregiert die vier
  Module als Reactor. `mvn verify` auf Root-Ebene baut und testet alles
  auf einen Schlag; ein einzelnes Modul kann weiterhin isoliert gebaut
  werden.

### Negativ

- **Verbose.** Eine funktionierende `pom.xml` für ein Spring-Boot-
  WebFlux-Projekt mit Coverage-Gates hat 150+ Zeilen. Gradles Kotlin-DSL
  ist kompakter.
- **Langsamer als ein gut abgestimmter Gradle-Build**, besonders bei
  großen Multi-Module-Builds. Für ein Projekt dieser Größe ist der
  Unterschied nicht relevant.
- **Dependency-Conflict-Resolution ist Nearest-Wins**, was gelegentlich
  überrascht. Abfedern über `<dependencyManagement>` für alles, worauf
  es ankommt.

### Verworfene Alternative: Gradle

- Starkes Build-Tool, besonders für große und Custom-Builds, aber das
  Enterprise-Standard-Argument und die Einfachheit des Maven-Lifecycles
  für ein Projekt ohne ungewöhnliche Build-Anforderungen haben den
  Ausschlag gegeben.
- Außerdem: Der Projektauftrag listet Maven explizit als Build-Tool für
  den BFF, und dasselbe Tool für alle Java-Module zu verwenden ist
  konsistenter.
