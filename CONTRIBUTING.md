# Contributing to `bff-reference`

Dieses Repository ist ein **Blueprint-Template**, kein Produkt. Jede
Änderung muss die Template-Eigenschaften bewusst erhalten: didaktische
Kommentare, keine produktspezifischen Fachlichkeiten, reproduzierbarer
Build.

Für die Schritt-für-Schritt-Anleitung, wie man aus dem Template ein
eigenes Projekt aufsetzt (ohne dieses Template selbst zu verändern),
siehe [`docs/getting-started-new-project.md`](docs/getting-started-new-project.md).

---

## Was in diesem Repo willkommen ist

- **Security-Bumps** (Spring Boot, Angular, Base-Images, NVD-CVE-Fixes).
  Diese kommen in der Regel automatisch via Dependabot als PRs herein —
  Manuelle Nachzieh-PRs sind ebenfalls willkommen, wenn Dependabot einen
  Fall nicht erwischt hat.
- **Pattern-Verbesserungen**, die die Blueprint-Qualität heben — etwa
  besseres JavaDoc, klarere Port-Adapter-Trennung, zusätzliche ADRs,
  zusätzliche Test-Patterns.
- **Doku-Ergänzungen**, die neue Teams beim Einstieg unterstützen.
- **Ergänzende Security-Gates** im Build (z. B. weitere Scanner, SBOM-
  Generierung).

## Was nicht in dieses Repo gehört

- **Produktspezifische Fachlichkeit.** Der User-Service ist absichtlich
  synthetisch. Wer echte Fachlogik einbauen möchte, legt dafür ein
  abgeleitetes Produkt-Repo an (siehe [Getting-Started-Guide](docs/getting-started-new-project.md)).
- **Breaking Changes ohne ADR.** Jede architektonisch wirkende Änderung
  braucht einen begleitenden ADR in `docs/adr/`, damit nachfolgende
  Teams die Begründung rekonstruieren können.
- **Stillschweigende Senkungen des Coverage-Gates** (80 % Line / 70 %
  Branch) oder der CVSS-Schwelle (CVSSv3 ≥ 7). Beide sind durch das
  Blueprint garantiert und dürfen nur mit explizitem Consent der
  Template-Ownership fallen.
- **Dauerhafte CVE-/Finding-Suppressions ohne Begründung und Ablaufdatum.**
  Die Datei-Formate für Suppressions (`dependency-check-suppressions.xml`,
  `.trivyignore`) haben jeweils im Kopfkommentar dokumentiert, welche
  Nachweise jede Ausnahme beibringen muss. Keine stille Unterdrückung.

---

## PR-Checkliste

Vor `gh pr create`:

- [ ] `mvn -B -ntp verify` grün (inklusive OWASP Dependency-Check —
  siehe README-Abschnitt für den `NVD_API_KEY`-Setup-Hinweis)
- [ ] `trivy fs --scanners vuln .` grün auf HIGH/CRITICAL
- [ ] Wenn `frontend/` betroffen: `npm ci && npm run build` grün; keine
  neuen Peer-Dep-Warnings
- [ ] Neue oder geänderte Public-APIs haben aktualisiertes JavaDoc
- [ ] Architekturwirksame Änderungen von einem ADR in `docs/adr/`
  begleitet
- [ ] Stale Versions-Angaben in der README-Technologie-Matrix
  synchronisiert, falls eine Version angefasst wurde
- [ ] Commit-Message folgt dem bestehenden Stil: *Was + Warum* in der
  Subject-Zeile, Details im Body; kein „fix stuff"

---

## Commit- und Branch-Konvention

- **Feature-Branch** pro Änderung (`chore/bump-xyz`, `docs/overhaul`,
  `feat/add-service-foo`). Niemals direkt auf `main`.
- **Kleine, häufige Commits** statt großer unstaged Piles. Merge-Strategie
  ist Fast-Forward; Rebasen vor dem Merge auf `main` ist erwünscht.
- **Co-Authored-By**-Footer behalten, wenn KI-Assistenten am Commit
  beteiligt waren — Audit-Spur der Tooling-Lineage.

---

## Release- und Versionierung

Das Template versioniert sich über `git tag v<semver>` auf `main`. Ein
Release enthält:

- Reactor-weiten Build-Nachweis (CI-Pipeline oder lokaler `mvn verify`)
- Updates der Technologie-Matrix in der README
- Ggf. Release-Notes im Repo-Description auf GitHub (keine separate
  CHANGELOG.md — der Commit-Verlauf ist die Einzelquelle).

---

## Kontakt

**Template-Ownership**: `{{TEAM_ODER_KONTAKT}}`

**Security-Findings mit CVE-Relevanz**: bitte privat melden, nicht als
öffentlicher Issue (`{{SECURITY_MAILBOX}}`).
