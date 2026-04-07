# ADR-005: Use Maven as the build tool for all Java modules

- **Status:** Accepted
- **Date:** 2026-04-06

## Context

The project contains four Java modules (BFF + three microservices) that
need a build tool, dependency management, a test phase, a coverage gate
and a container image build. The two realistic choices are **Maven** and
**Gradle**.

## Decision

We use **Maven 3.9+** with one `pom.xml` per module. No multi-module
reactor at the top level — each module is independent and has its own
`mvn verify`, its own Dockerfile and its own JaCoCo gate.

## Consequences

### Positive

- **Enterprise default.** Maven is by a wide margin the most common Java
  build tool in regulated / enterprise environments. Everyone can read a
  `pom.xml`; custom Gradle DSL is not a skill one can assume.
- **Declarative and reproducible.** Given the same `pom.xml` and the same
  Maven version, the build result is stable across machines and months.
- **Stable plugin ecosystem.** The plugins we need —
  `spring-boot-maven-plugin`, `maven-surefire-plugin`,
  `maven-failsafe-plugin`, `jacoco-maven-plugin` — are all first-party or
  mature third-party and rarely break between versions.
- **Obvious unit vs integration test split.** Surefire picks up
  `*Test.java`, Failsafe picks up `*IT.java`, and Failsafe runs in a
  later lifecycle phase. No custom task wiring needed.
- **Enforceable coverage gate.** `jacoco-maven-plugin` with the `check`
  goal fails the build on a coverage drop, which is a hard requirement of
  the project brief (≥ 80 % line, ≥ 70 % branch).

### Negative

- **Verbose.** A working `pom.xml` for a Spring Boot WebFlux project with
  coverage gates is 150+ lines. Gradle's Kotlin DSL is more compact.
- **Slower than a well-tuned Gradle build**, especially for large
  multi-module builds. For a project of this size the difference is not
  material.
- **Dependency conflict resolution is nearest-wins**, which occasionally
  surprises. Mitigated by `<dependencyManagement>` for anything that
  matters.

### Rejected alternative: Gradle

- Strong build tool, especially for large and custom builds, but the
  enterprise-default argument and the simplicity of the Maven lifecycle
  for a project that has no unusual build requirements tipped the scale.
- Also: the project brief explicitly lists Maven as the build tool for the
  BFF, and using the same tool across all Java modules is more consistent.
