# sample-gradle-jar

A minimal Gradle project that demonstrates a known issue with the `veracode package` command when applied to Gradle projects that copy 3rd-party dependency JARs alongside their first-party JAR.

## What this project does

The project builds a small Java application and uses two standard Gradle conventions:

1. **First-party JAR** — the `jar` task compiles only the project's own source code into `build/libs/<project-name>.jar`. The JAR manifest `Class-Path` attribute lists the dependency JARs so the JVM can find them at runtime without bundling everything into a fat JAR.

2. **Dependency JARs** — a `copyDependencies` task copies every resolved `runtimeClasspath` JAR into `build/libs/libs/`. This task is wired to the `assemble` lifecycle so `./gradlew build` runs it automatically.

After `./gradlew build` the output tree looks like this:

```shell
build/libs/
├── sample-gradle-jar-1.0-SNAPSHOT.jar   ← first-party code only
└── libs/
    ├── jackson-annotations-2.17.1.jar
    ├── jackson-core-2.17.1.jar
    ├── jackson-databind-2.17.1.jar
    ├── logback-classic-1.5.6.jar
    ├── logback-core-1.5.6.jar
    └── slf4j-api-2.0.13.jar
```

This is a common, well-documented Gradle pattern — the project is representative of many real-world customer builds.

## The `veracode package` issue

When `veracode package` is run against this project, the post-build processing walks the build output directory and collects **all** JAR files it finds under `build/libs/`, including the 3rd-party dependency JARs in the `libs/` sub-directory.

This means the packager output directory ends up containing:

- `sample-gradle-jar-1.0-SNAPSHOT.jar` — the intended scan target
- `jackson-annotations-2.17.1.jar`, `jackson-core-2.17.1.jar`, `jackson-databind-2.17.1.jar` — 3rd-party
- `logback-classic-1.5.6.jar`, `logback-core-1.5.6.jar`, `slf4j-api-2.0.13.jar` — 3rd-party

**Impact:** when the packager output is submitted to a Veracode pipeline scan action in the Repo Tools workflows, the action treats every JAR as a unit of work and creates a scan job for each one. This causes 3rd-party open-source libraries — which are not customer code and are not the intended scan target — to be scanned alongside the first-party JAR, wasting scan quota, increasing scan time, causing potential rate limit failures both with the GitHub matrix jobs and pipeline scan rate limits, and polluting results with findings in code the customer does not own.

## Proposed fix

The post-build Gradle processing in the packager should use the Gradle project name (from `settings.gradle`'s `rootProject.name`, falling back to the root directory name if that field is absent) as a glob to select only the first-party JAR(s) for inclusion in the packager output directory.

For example, given `rootProject.name = 'sample-gradle-jar'`, the packager would apply a glob such as:

```shell
build/libs/sample-gradle-jar*.jar
```

This selects `sample-gradle-jar-1.0-SNAPSHOT.jar` and nothing else, excluding the dependency JARs regardless of where they appear under the build output tree.

The same approach extends naturally to WAR and EAR artifacts:

```shell
build/libs/sample-gradle-jar*.{jar,war,ear}
```

## Potential issues to consider

### 1. `rootProject.name` may be absent

If `settings.gradle` does not set `rootProject.name`, Gradle defaults to the root directory name. The packager must implement the same fallback — read `rootProject.name` from `settings.gradle` if present, otherwise use the directory name — to avoid producing a glob that matches nothing.

### 2. Multi-project (multi-module) builds

In a multi-project Gradle build the root project may produce no artifacts at all; artifacts are produced by sub-projects, each with their own name. The packager would need to recurse into each sub-project's `build/libs/` directory and apply the sub-project's name as the glob for that directory. Using only the root project name across all sub-directories would miss sub-project artifacts or match nothing.

### 3. Project name does not always match the artifact name

Gradle's `archivesBaseName` property (or `base.archivesName` in newer Gradle versions) can override the JAR filename independently of the project name. If a customer sets this property, the output JAR will not match a glob based on `rootProject.name`. The packager should check for an explicit `archivesBaseName` / `archivesName` override and prefer it over the project name when constructing the glob.

### 4. Version strings in filenames

The default Gradle JAR name is `<name>-<version>.jar`. A glob of `<name>*.jar` handles this correctly, but if a project sets `version = ''` (empty) the filename becomes `<name>.jar` — which the glob still matches, so this is safe.

### 5. Custom `destinationDirectory` on the `jar` task

Some projects configure the `jar` task to write output to a non-standard directory (e.g. `dist/` or `target/`). The packager's directory search would need to either honour any custom output directory declared in the build script or perform a broader search and then apply the name-based glob as a filter.

### 6. Snapshots and classifiers

Projects may publish multiple JAR variants with classifiers (e.g. `-sources.jar`, `-javadoc.jar`, `-tests.jar`). A glob of `<name>*.jar` would include these. If the intent is to scan only the main artifact, the glob could be tightened to `<name>-[0-9]*.jar` (version starts with a digit) or the packager could explicitly exclude known classifier suffixes (`-sources`, `-javadoc`, `-tests`).
