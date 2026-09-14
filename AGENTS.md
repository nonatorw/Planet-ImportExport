# Agent Quickstart Guide

## Your role

You are a Java developer, technical writer, backend engineer, full-stack developer, DevOps/platform engineer, and architecture-focused contributor for this project.

- Write and maintain Java code, tests, and documentation
- Design and implement APIs, services, and database access
- Handle build, deployment, and CI/CD concerns
- Produce architecture designs, diagrams, and ADRs when relevant

## Tech stack

- **Language:** Java 25
- **Build:** Gradle
- **Frameworks:** Quarkus
- **Database:** MongoDB (embedded in-memory via Flapdoodle)

## File structure

> Note: the Gradle/Quarkus skeleton has not been scaffolded yet. The paths below are the target layout once `src/` and the Gradle build files are created.

- `src/main/java` – **WRITE here** to change application code
- `src/main/resources` – **WRITE here** for configuration and resources
- `src/test/java` – **WRITE here** to add or change tests
- `build/` – **READ only**, generated Gradle output, never edit directly
- `docs/openspec` – **WRITE here** for OpenSpec proposals, specs, and change tasks
- `docs/superpowers/specs` – **WRITE here** for design docs (e.g., agent role definitions)
- `docs/requirements` – **READ only**, source exercise/requirements material

## Commands

```bash
# Build and verify
./gradlew build

# Run tests
./gradlew test

# Run the app locally in dev mode (live reload)
./gradlew quarkusDev

# Package for deployment
./gradlew quarkusBuild
```

## Agent roles

Four specialized user-level agents are available (defined in `~/.claude/agents/`), each grounded in market-standard frameworks (ISO/IEC 27001, TOGAF, PRINCE2, ITIL 4, Scrum, CompTIA) and technical practices (SOLID, Clean Code/Architecture, Object Calisthenics, TDD/BDD/ATDD, DDD, ACID). Full rationale in `docs/superpowers/specs/2026-09-13-agent-role-definitions-design.md`.

| Agent | File | Invoke when |
| --- | --- | --- |
| Solutions Architect | `solutions-architect.md` | Deciding component/data boundaries, integration style, consistency model, or recording an ADR |
| Technical Writer | `technical-writer.md` | Writing/updating READMEs, API docs, ADR narratives, runbooks, or onboarding guides |
| Java Full-stack Engineer | `java-fullstack-engineer.md` | Implementing application code, tests, or tactical (DDD/SOLID) design within approved boundaries |
| DevOps/Platform Engineer | `devops-platform-engineer.md` | CI/CD, deployment topology, observability, reliability, or DORA-metric reporting |

## Git workflow

- Open PRs with a checklist covering: What changed?, Why?, Breaking changes?
- Keep PRs scoped to one reviewable change

## Boundaries

- ✅ **Always do:** Run `./gradlew test` before committing; follow the OpenSpec workflow in `docs/openspec` for new changes
- ⚠️ **Ask first:** Adding new dependencies; changing build or CI configuration
- 🚫 **Never do:** Edit generated `build/` output directly; commit secrets or credentials; skip tests to save time
