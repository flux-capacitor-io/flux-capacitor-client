<a href="flux-capacitor.io">
    <img src="https://flux-capacitor.io/assets/brand/flux-capacitor-white.svg" alt="Flux Capacitor logo" title="Flux Capacitor" align="right" height="60" />
</a>

Flux Capacitor java client
======================

This is the official supported Java library for the Flux Capacitor service. 
To read more about Flux Capacitor visit [flux-capacitor.io](https://flux-capacitor.io).


Installation
======================
### Maven users

Add this dependency to your project's POM:

```xml
<dependency>
    <groupId>io.flux-capacitor</groupId>
    <artifactId>java-client</artifactId>
    <version>${flux-capacitor.version}</version>
</dependency>
``` 

### Gradle users

Add the following dependency:

```
compile 'io.flux-capacitor:java-client:${flux-capacitor.version}'
```

### Others

```
git clone --recursive https://github.com/flux-capacitor-io/flux-capacitor-client.git
cd flux-capacitor-client
mvn install
```

Then manually copy the following JARs to your project:
* `java-client/target/*.jar`
* `common/target/*.jar`


Basic example
======================

Create a new project and add an event class:

```java
package com.example;

public class HelloWorld {
}
```

Create a handler for the event:

```java
package com.example;

import io.fluxcapacitor.javaclient.tracking.handling.HandleEvent;

public class HelloWorldEventHandler {
    @HandleEvent
    public void handle(HelloWorld event) {
        System.out.println("Hello World!");
    }
}
```

Publish the event:

```java
package com.example;

import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor;
import io.fluxcapacitor.javaclient.configuration.client.InMemoryClient;

public class ExampleMain {
    public static void main(final String[] args) {
        FluxCapacitor fluxCapacitor = DefaultFluxCapacitor.builder().build(InMemoryClient.newInstance());
        fluxCapacitor.startTracking(new HelloWorldEventHandler());
        fluxCapacitor.eventGateway().publish(new HelloWorld());
    }
}
```

Output: 

```
Hello World!
```

Features
======================

The java client supports all major features of Flux Capacitor Service but also offers plenty of additional 
functionality. Here's a summary of the most important features:

### Publishing and tracking

Above all, Flux Capacitor lets you publish and subscribe to messages in real time. Those messages can come from your own 
application or from any other application connected to the same Flux Capacitor service.

Here's an example of an event handler that dispatches a command to send a welcome email when a new user is created:

```java
class UserEventHandler {
    @HandleEvent
    void handle(UserCreated event) {
        FluxCapacitor.sendCommand(new SendWelcomeEmail(event.getUserProfile()));
    }
}
```

This handler sends a command using the static `sendCommand` method on `FluxCapacitor`. Flux Capacitor gets 
injected as a threadlocal before a handler is invoked, so you can make use of static methods on `FluxCapacitor`. This
prevents unnecessary dependency injections and makes for cleaner code.
 
As you can see you can just annotate a method to start listening for messages. To listen for the command sent by this
event handler you would create the following handler:

```java
class EmailCommandHandler {
    @HandleCommand
    void handle(SendWelcomeEmail command) {
        //send the actual email
    }
}
```

The results of commands and queries will be sent back to Flux Capacitor service as a Result message. The application
that originally issued the query or command will listen for new Results sent to Flux Capacitor. To send back a Result 
simply return a value from the handler. In the following example the returned `UserProfile` will be sent back 
as a Result message.

```java
class UserQueryHandler {
    @HandleQuery
    UserProfile handle(GetUserProfile query) {
        //return the user profile
    }
}
```

Here's an example of an event handler that sends a query and waits for the result:

```java
class UserEventHandler {
    @HandleEvent
    void handle(ResetPassword event) {
        UserProfile userProfile = FluxCapacitor.sendQueryAndWait(new GetUserProfile(event.getUserId()));
        //do something with the user profile
    }
}
```

### Testing handlers

### Event Sourcing

### Upcasting

### Scheduling

### Data protection

### Payload validation

### Correlating messages

### Metrics

### Miscellaneous


Configuration
======================

You can easily override the default client configuration, just have a look at `FluxCapacitorBuilder`. Here are some of
the most important things you can do:

### Configuring message consumers

Consider the following configuration:

```java
fluxCapacitorBuilder
   .addConsumerConfiguration(MessageType.EVENT, ConsumerConfiguration.builder()
            .name("webshop")
            .handlerFilter(h -> h.getClass().getPackage().getName().startsWith("com.example.webshop"))
            .trackingConfiguration(TrackingConfiguration.builder().threads(4).maxFetchBatchSize(128).build())
            .build());
``` 

This will configure a custom consumer for events. It will only contain handlers in the `com.example.webshop` package 
or any of its sub-packages (handlers in other locations will use the default consumer). The consumer uses four threads
to fetch events and pass them to its handlers. Each thread will fetch no more than 128 events at a time. 

This is just a sample of consumer settings that you can modify. For other settings check the Javadoc.

### Registering interceptors

Interceptors can be used to modify, block or record outgoing and incoming messages. They are the
equivalent of a common web filter in a CRUD application. Here's how to register interceptors:

```java
fluxCapacitorBuilder
   // registers an interceptor for outgoing messages
   .registerDispatchInterceptor(new SomeDispatchInterceptor())
   
   // registers an interceptor for incoming events
   .registerHandlerInterceptor(new SomeEventHandlerInterceptor(), MessageType.EVENT);
```

### Overriding common settings

The client uses defaults that will be suitable for most applications. 
However, to squeeze every ounce of performance or obtain additional insights 
about your application you can disable or enable common settings:

```java
fluxCapacitorBuilder
   // don't perform a constraint validation check on incoming commands and queries 
   .disablePayloadValidation()
   
   // store metrics about the performance of handlers in your application
   .collectTrackingMetrics();
```

Bootstrap with Spring
======================

You can easily configure and start your app with spring (or spring boot). 
All you need to do is import `FluxCapacitorSpringConfig` into your own configuration and 
you're good to go:

```java
@Configuration
@Import(FluxCapacitorSpringConfig.class)
@ComponentScan
public class SampleSpringConfig {
}
```

To change the default config just inject and modify the `FluxCapacitorBuilder` instance:

```java
@Configuration
@Import(FluxCapacitorSpringConfig.class)
@ComponentScan
public class SampleSpringConfig {
    @Autowired
    public void configure(FluxCapacitorBuilder builder) {
        builder.collectTrackingMetrics()
                .addHandlerInterceptor(new AuthenticationInterceptor(), MessageType.COMMAND, MessageType.QUERY);
    }
}
```

By default the spring config will use an in-memory version of Flux Capacitor which is useful for testing but not for
anything else. To connect to a real Flux Capacitor service simply register a Client bean in your configuration:

```java
@Configuration
@Import(FluxCapacitorSpringConfig.class)
@ComponentScan
public class SampleSpringConfig {
    @Bean
    public Client fluxCapacitorClient() {
        return WebSocketClient.newInstance(new Properties("sample-app", System.getProperty("flux-capacitor-endpoint")));
    }
}
```

Spring beans with handler annotations like `@HandleEvent` are automatically registered for tracking.  
If you want to send a message just inject the `FluxCapacitor` instance and send the message:

```java
public class SpringExample {
    public static void main(final String[] args) {
        ApplicationContext applicationContext = new AnnotationConfigApplicationContext(SampleSpringConfig.class);
        applicationContext.getBean(FluxCapacitor.class).eventGateway().publish(new HelloWorld());
    }
}
```

If you want to handle messages only if they were sent by the current application you can define a local handler. 
By annotating your spring bean with `@LocalHandler` it will be automatically registered as local handler:

```java
@Component
@LocalHandler
public class SomeLocalHandler {
    @HandleEvent
    public void handle(ApplicationStarted event) {
        //do something
    }
}
```

Aside from detecting handlers Flux Capacitor also detects Upcasters automatically. If a spring bean has methods 
annotated with `@Upcast` it will be automatically registered with the Serializer. 