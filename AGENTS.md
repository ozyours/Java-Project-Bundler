# AGENTS.md

Guidance for AI coding agents working in this repository.

## Project

Java Project Bundler — a JavaFX desktop utility that packages Java projects
(Maven or Gradle) into self-contained distribution bundles: application JARs, a
bundled JVM (jlink), runtime config, assets, and a silent native launcher
executable written in Rust. See `README.md` and `SDD.md` (design doc) for
details.

## Commands

- `./mvnw verify` — compile + run unit tests
- `./mvnw javafx:run` — launch the desktop app

## Layout

```
src/main/java/com/jpb/bundler/
  Main.java, MainApp.java       entry points
  Engine/Build/                 packaging engine (Bundler, ProjectDetector, BuildTool)
  Engine/Project/               .jpb project model + persistence (ISerializable, JpbProject)
  Engine/Session/               window session persistence (Session, SessionStore)
  Engine/Toolchain/             JDK / rustc discovery + launcher support
  UI/                           JavaFX views (DashboardView, ProjectChooserView)
src/main/resources/com/jpb/bundler/
  launcher.rs                   Rust launcher source, compiled by the bundler at build time
  UI/dashboard.css              dashboard stylesheet
src/test/java/                  JUnit 5 unit tests (mirroring the packages above)
```

## Naming conventions

This codebase follows a strict identifier naming rule. Apply it to every new or
edited identifier in the Java sources under `src/`:

| Scope | Convention | Examples |
|---|---|---|
| Field variable / function (class members: fields, methods, constants, enum values) | CapitalizeCase | `ProjectName`, `GetProjectName()`, `MaxRecentProjects`, `Maven` |
| Local variable (declared inside a method body, incl. loop variables) | `__` + CapitalizeCase | `__SourcePath`, `__Bundle`, `__Line` |
| Function argument (method/constructor parameter, incl. lambda and catch parameters) | `_` + CapitalizeCase | `_Project`, `_Argument1`, `_Argument2` |

Notes:

- Method names required by the language or runtime are kept as-is even though
  they violate the rule: `main`, overridden methods (`start`, `call`), and
  interface methods that already match the style (`Serialize`, `Deserialize`,
  `OnSerialize`, `OnDeserialize`).
- CapitalizeCase means first letter uppercase, rest camelCase (no separators):
  `MyVariable`, not `MY_VARIABLE` and not `my_variable`.
- Javadoc `@param` tags, `{@link}` and `{@value}` references must match the
  actual renamed identifiers.
- The Rust launcher resource (`launcher.rs`) keeps idiomatic Rust naming
  (snake_case) — the rule applies to the Java sources only.
