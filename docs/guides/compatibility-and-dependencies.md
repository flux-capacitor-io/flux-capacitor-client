## Compatibility and Dependencies

### Java Version

Flux Capacitor requires **JDK 21 or higher** to compile and run. It is actively tested on **JDK 24** and remains
compatible with recent versions.

---

### Runtime Dependencies

#### 🧾 Serialization

| Dependency                                               | Scope   |
|----------------------------------------------------------|---------|
| `com.fasterxml.jackson.core:jackson-databind`            | runtime |
| `com.fasterxml.jackson.datatype:jackson-datatype-jsr310` | runtime |
| `com.fasterxml.jackson.datatype:jackson-datatype-jdk8`   | runtime |
| `org.msgpack:msgpack-core`                               | runtime |
| `org.lz4:lz4-java`                                       | runtime |

#### 🔍 Payload Validation

| Dependency                                    | Scope   |
|-----------------------------------------------|---------|
| `org.hibernate.validator:hibernate-validator` | runtime |
| `org.glassfish.expressly:expressly`           | runtime |
| `org.jboss.logging:jboss-logging`             | runtime |

#### 🌐 Web and Transport

| Dependency                            | Scope   |
|---------------------------------------|---------|
| `io.undertow:undertow-websockets-jsr` | runtime |
| `io.jooby:jooby`                      | runtime |

#### ⚙️ Utilities

| Dependency                         | Scope   |
|------------------------------------|---------|
| `org.apache.commons:commons-lang3` | runtime |

---

### Optional Integrations

#### 🌱 Spring Support

| Dependency                           | Scope    |
|--------------------------------------|----------|
| `org.springframework:spring-context` | optional |
| `org.springframework:spring-test`    | test     |

#### 💻 Kotlin Support

| Dependency                                           | Scope    |
|------------------------------------------------------|----------|
| `com.fasterxml.jackson.module:jackson-module-kotlin` | optional |
| `org.jetbrains.kotlin:kotlin-stdlib-jdk8`            | optional |
| `org.jetbrains.kotlin:kotlin-reflect`                | optional |
| `org.jetbrains.kotlin:kotlin-test`                   | test     |

---

### Test Dependencies

| Dependency                                   | Scope |
|----------------------------------------------|-------|
| `org.junit.jupiter:junit-jupiter-engine`     | test  |
| `org.junit.platform:junit-platform-launcher` | test  |
| `org.mockito:mockito-core`                   | test  |
| `org.jooq:joor`                              | test  |
| `org.hamcrest:hamcrest-library`              | test  |
| `org.jboss.resteasy:resteasy-undertow`       | test  |

---

> ℹ️ Dependencies marked as `optional` are **not required** unless you use the corresponding integration.  
> For example, Spring or Kotlin support is automatically detected when available but safely ignored otherwise.

---

### Versioning Policy

Flux Capacitor aims to stay up-to-date with its core dependencies. We strive to:

- Use the **latest stable versions** where possible,
- Avoid breaking backward compatibility for common transitive consumers,
- Update frequently, especially for frameworks like **Jackson**, **Spring**, **Hibernate Validator**, and **JUnit`.