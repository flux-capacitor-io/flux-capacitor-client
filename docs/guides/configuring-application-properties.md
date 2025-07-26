## Configuring Application Properties

Flux Capacitor provides a static utility, `ApplicationProperties`, for resolving configuration values across
environments, tests, and production. It supports:

- Layered resolution from environment variables, system properties, and `.properties` files
- Placeholder substitution (e.g. `${my.env}`)
- Encrypted values with automatic decryption
- Typed access: `getBooleanProperty`, `getIntegerProperty`, etc.

### Property Resolution Order

Properties are resolved in the following order of precedence:

1. `EnvironmentVariablesSource` – e.g. `export MY_SETTING=value`
2. `SystemPropertiesSource` – e.g. `-Dmy.setting=value`
3. `ApplicationEnvironmentPropertiesSource` – e.g. `application-dev.properties`
4. `ApplicationPropertiesSource` – base fallback (`application.properties`)
5. *(Optional)*: Spring’s `Environment` is added as a fallback source if Spring is active

To specify the environment (`dev`, `prod`, etc.), define:

```bash
export ENVIRONMENT=dev
```

This allows `application-dev.properties` to override base properties.

### Example Usage

```java
String name = ApplicationProperties.getProperty("app.name", "DefaultApp");
boolean enabled = ApplicationProperties.getBooleanProperty("feature.toggle", true);
int maxItems = ApplicationProperties.getIntegerProperty("limit.items", 100);
```

### Encrypted Values

Flux Capacitor supports secure storage of secrets using its built-in encryption utility. To use encryption:

1. **Generate a new key** with:

   [//]: # (@formatter:off)
    ```java
    String key = DefaultEncryption.generateNewEncryptionKey();
    System.out.println(key);
    // => ChaCha20|KJh832h1f7shDFb...  -> Save and use as ENCRYPTION_KEY
    ```
   [//]: # (@formatter:on)

2. **Set the encryption key** via an environment variable or system property:

    ```bash
    export ENCRYPTION_KEY=ChaCha20|KJh832h1f7shDFb...
    ```

3. **Encrypt values** at build/deploy time:

   [//]: # (@formatter:off)
    ```java
    String encrypted = ApplicationProperties.encryptValue("secret-google-key");
    System.out.println(encrypted);
    // => encrypted|ChaCha20|mm8yeY8TXtNpdrwO:REdej56zvFXc:b7oQdmnpQpUzagKtma9JLQ==
    ```
   [//]: # (@formatter:on)

4. **Add encrypted values to your config**:

    ```properties
    google.apikey=encrypted|ChaCha20|mm8yeY8TXtNpdrwO:REdej56zvFXc:b7oQdmnpQpUzagKtma9JLQ==
    ```

5. **Resolve them normally in code**:

   [//]: # (@formatter:off)
    ```java
    String apiKey = ApplicationProperties.getProperty("google.apikey");
    // -> "secret-google-key"
    ```
   [//]: # (@formatter:on)

Decryption is transparent. Flux detects encrypted values and decrypts them automatically.

### In Tests

Properties can be defined in your `test/resources/application.properties` or overridden via system properties.

```bash
-Dmy.test.override=true
```

Or dynamically inject mock values:

[//]: # (@formatter:off)
```java
TestFixture.create(MyHandler.class)
    .withProperty("my.test.value", "stub")
    .whenCommand("/users/create-job.json")
    .expectSchedules(ScheduledJob.class);
```
[//]: # (@formatter:on)

> This is especially useful in integration or fixture-based tests.