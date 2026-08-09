# Java Project Bundler

Desktop utility that packages Java projects (Maven or Gradle) into a self-contained
distribution bundle: application JARs, a bundled JVM, runtime config, assets, and a
silent native launcher executable written in Rust. See [`SDD.md`](SDD.md) for the
full design spec.

## Requirements

- JDK 17 or newer (build toolchain; the project targets Java 17 bytecode)
- Maven 3.9+ (or use the bundled `mvnw` wrapper)

## Build & run

```sh
mvn verify            # compile + run tests
mvn javafx:run        # launch the desktop app
```

## Running from IntelliJ IDEA

JavaFX is pulled in as a plain Maven dependency, so the app must be launched
through the JavaFX launcher rather than as a raw `Application` class.

**Recommended** — open the Maven tool window (`View ▸ Tool Windows ▸ Maven`) and
double-click `java-project-bundler ▸ Plugins ▸ javafx ▸ javafx:run`. This runs
exactly the same command as `mvn javafx:run`.

**Alternative (green Run button)** — run the `com.jpb.bundler.Main` class instead
of `MainApp`. `Main` is a plain launcher that delegates to JavaFX's own launcher,
so it works directly off the classpath without any VM options.

## Project structure

```
src/main/java/com/jpb/bundler/
  Main.java, MainApp.java        entry points
  build/                         packaging engine (Bundler, project detection)
  project/                       .jpb project model + persistence
  toolchain/                     JDK / rustc discovery + launcher support
  session/                       window session persistence
  ui/                            JavaFX views
src/test/java/                   unit tests (mirroring the packages above)
pom.xml                          Maven build
ROADMAP.md                       long-term plan
SDD.md                           design document
```
