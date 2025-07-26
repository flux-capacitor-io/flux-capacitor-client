## Basic example

Create a new project and add an event class:

```java
public record HelloWorld() {
}
```

Create a handler for the event:

```java
public class HelloWorldEventHandler {
    @HandleEvent
    void handle(HelloWorld event) {
        System.out.println("Hello World!");
    }
}
```

Publish the event:

```java
public class ExampleMain {
    public static void main(final String[] args) {
        var fluxCapacitor = DefaultFluxCapacitor.builder()
                .build(LocalClient.newInstance());
        fluxCapacitor.registerHandlers(new HelloWorldEventHandler());
        fluxCapacitor.eventGateway().publish(new HelloWorld());
    }
}
```

Output:

```
Hello World!
```

### With Spring

Flux Capacitor integrates seamlessly with Spring. Here’s how the above example looks with Spring Boot:

```java

@SpringBootApplication
public class ExampleMain {
    public static void main(String... args) {
        SpringApplication.run(ExampleMain.class, args);
        FluxCapacitor.publishEvent(new HelloWorld());
    }
}
```

And annotate your handler with `@Component`:

```java

@Component
public class HelloWorldEventHandler {
    @HandleEvent
    void handle(HelloWorld event) {
        System.out.println("Hello World!");
    }
}
```

> ⚠️ Using Spring non-Boot? Add `@Import(FluxCapacitorSpringConfig.class)` to register FluxCapacitor and related beans.

### Testing your handler

Flux Capacitor includes a powerful TestFixture utility for testing your handlers without needing a full application
or infrastructure setup.

Here’s how to test our HelloWorldEventHandler from earlier:

```java
class HelloWorldEventHandlerTest {

    @Test
    void testHelloWorldHandler() {
        TestFixture.create(new HelloWorldEventHandler())
                .whenEvent(new HelloWorld())
                .expectThat(fc -> System.out.println(
                        "Event handled successfully!"));
    }
}
```

This will invoke your handler exactly like Flux Capacitor would in production, but entirely in memory and synchronously
by default.

> ✅ We’ll explore more powerful testing patterns — including assertions on results, published commands, exceptions,
> and full event flows — later in this guide.