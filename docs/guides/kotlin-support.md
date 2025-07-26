## Kotlin Support

Flux Capacitor provides full support for Kotlin, including:

- Use of `record`-like [data classes](https://kotlinlang.org/docs/data-classes.html),
- Optional types (e.g., `String?`) instead of `@Nullable`,
- Seamless use of Kotlin-specific language features (e.g., `when`, `sealed class`, `companion object`),
- Support for `Class<T>` parameters passed as `MyClass::class` or `MyClass::class.java`,
- Automatic recognition of Kotlin modules during deserialization (see below).

### Automatic Jackson Integration

Flux Capacitor includes [Jackson Kotlin Module](https://github.com/FasterXML/jackson-module-kotlin) integration when
available on the classpath. You do **not** need to manually register the module or use a service loader.
If the `jackson-module-kotlin` dependency is present, it will be loaded dynamically for JSON (de)serialization.

> 💡 If the module is missing, Flux Capacitor will fall back gracefully to standard Jackson behavior — no errors or
> warnings.

This enables correct serialization and deserialization of Kotlin constructs like:

- default parameters,
- `val` / `var` fields,
- nullability,
- `data class` equality and hashing,
- and Kotlin-style constructor parameter mapping.

> **Note:** Flux Capacitor does *not* require a Kotlin dependency itself. Kotlin support is purely optional and works
> automatically when Kotlin and its Jackson module are used in your application.