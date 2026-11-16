# ADR 0001: Kotlin, KSP and Hilt with AGP 9

Status: Accepted

## Context

Hilt and Room need KSP for annotation processing. AGP 9 ships with built-in Kotlin, and KSP does
not support it. KSP also lags behind Kotlin: KSP 2.3.12 targets Kotlin 2.3.20, and there is no KSP
release for the Kotlin 2.4 line.

## Decision

- `android.builtInKotlin=false` in `gradle.properties`, and the `org.jetbrains.kotlin.android`
  plugin is applied again.
- `android.newDsl=false` as well. This is a second, independent switch: with only
  `builtInKotlin=false`, AGP 9's new DSL (on by default) still rejects
  `org.jetbrains.kotlin.android`:

  ```text
  The 'org.jetbrains.kotlin.android' plugin is not compatible with AGP's 9.0
  new DSL (`android.newDsl=true` is enabled by default).
  ```

- Kotlin (including the Compose compiler plugin) stays on the newest version KSP supports,
  currently Kotlin 2.3.20 with KSP 2.3.12. Hilt 2.59.2 is the first Hilt whose Gradle plugin
  supports AGP 9.
- `kotlin.compilerOptions.jvmTarget` is set to `JvmTarget.JVM_17` explicitly in every Android
  module, matching Java's `sourceCompatibility`/`targetCompatibility`. Without it the separate
  Kotlin plugin uses the JDK running Gradle (21) as its target and the build fails with
  "Inconsistent JVM-target compatibility detected".
- Dagger-generated factories cannot use Kotlin default parameter values, so every constructor
  parameter needs a binding; the background dispatcher is provided with the `@DefaultDispatcher`
  qualifier (`DispatcherModule`).

## Consequences

- Kotlin cannot be upgraded independently: a Kotlin upgrade waits for a matching KSP release.
- Once KSP supports AGP's built-in Kotlin, both switches can be removed together with the
  `org.jetbrains.kotlin.android` plugin.
