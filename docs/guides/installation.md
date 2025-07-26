## Installation

### Maven Users

Import the [Flux Capacitor BOM](https://mvnrepository.com/artifact/io.flux-capacitor/flux-capacitor-bom) in your
`dependencyManagement` section to centralize version management:

```xml

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.flux-capacitor</groupId>
            <artifactId>flux-capacitor-bom</artifactId>
            <version>${flux-capacitor.version}</version> <!-- See version badge above -->
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Then declare only the dependencies you actually need (no version required):

```xml

<dependencies>
    <dependency>
        <groupId>io.flux-capacitor</groupId>
        <artifactId>java-client</artifactId>
    </dependency>
    <dependency>
        <groupId>io.flux-capacitor</groupId>
        <artifactId>java-client</artifactId>
        <classifier>tests</classifier>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>io.flux-capacitor</groupId>
        <artifactId>test-server</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>io.flux-capacitor</groupId>
        <artifactId>proxy</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

> 💡 **Note:** The `test-server` and `proxy` modules are **optional**, and can be included if you want to test your
> application locally against an **in-memory Flux server**.

---

### Gradle Users

Use [platform BOM support](https://docs.gradle.org/current/userguide/platforms.html) to align dependency versions
automatically:

<details>
<summary><strong>Kotlin DSL (build.gradle.kts)</strong></summary>

```kotlin
dependencies {
    implementation(platform("io.flux-capacitor:flux-capacitor-bom:${fluxCapacitorVersion}"))
    implementation("io.flux-capacitor:java-client")
    testImplementation("io.flux-capacitor:java-client", classifier = "tests")
    testImplementation("io.flux-capacitor:test-server")
    testImplementation("io.flux-capacitor:proxy")
}
```

</details>

<details>
<summary><strong>Groovy DSL (build.gradle)</strong></summary>

```groovy
dependencies {
    implementation platform("io.flux-capacitor:flux-capacitor-bom:${fluxCapacitorVersion}")
    implementation 'io.flux-capacitor:java-client'
    testImplementation('io.flux-capacitor:java-client') {
        classifier = 'tests'
    }
    testImplementation 'io.flux-capacitor:test-server'
    testImplementation 'io.flux-capacitor:proxy'
}
```

</details>

---

> 🔖 **Tip:** You only need to update the version number **once** in the BOM reference. All included modules will
> automatically align
> with it.