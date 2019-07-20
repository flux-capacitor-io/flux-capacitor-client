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
git clone https://github.com/flux-capacitor-io/flux-capacitor-client.git
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

class HelloWorld {
}
```

Create a handler for the event:

```java
package com.example;

import io.fluxcapacitor.javaclient.tracking.handling.HandleEvent;

class HelloWorldEventHandler {
    @HandleEvent
    void handle(HelloWorld event) {
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
        FluxCapacitor fluxCapacitor 
            = DefaultFluxCapacitor.builder().build(InMemoryClient.newInstance());
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

Above all, Flux Capacitor lets you publish and subscribe to messages (events, commands, queries, etc.). Those messages 
can come from your own application or from any other application connected to the same Flux Capacitor service.

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
        //send a welcome email to the user
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

Aside from the message payload you can also include metadata with your message. Metadata are contextual data of a technical nature that relate to the message, eg who was the sender of this command, or what was the user agent of the web client of the user. Each message (event, command, query, result) can contain metadata. In Flux Capacitor metadata is simply a key value map of strings, with convenience methods to (de)serialize values on the fly if required. 

Here’s an example in which metadata is included in a command that is sent when an http endpoint is invoked:

```java
class UserEndpoint {
    @PUT @Path("/user")
    void createUser(UserProfile profile, @HeaderParam("user-agent") String userAgent) {
        FluxCapacitor.sendCommand(new CreateUser(...), Metadata.of("userAgent", userAgent));
    }
}
```


Note that we’ve used JAX-RS to define an http endpoint in this example, but of course this works for any other http library as well. 

To read the metadata of a message in your handler simply add it as a method parameter:

```java
class UserCommandHandler {
    @HandleCommand
    void handle(CreateUser command, Metadata metadata) {
        String userAgent = metadata.get("userAgent");
        ...
    }
}
```


### Testing your handlers

The java client for Flux Capacitor comes with an easy to use given-when-then testing framework. Here’s a basic example:

```java
private final TestFixture testFixture = new TestFixture(new UserEventHandler());

@Test
void newUserGetsEmail() {
    testFixture.whenEvent(new UserCreated(myUserProfile)).expectCommands(new SendWelcomeEmail(myUserProfile));
}
```

This test assures that a UserEventHandler instance issues a command to send a welcome email when a new user is created. 

In the example a single handler instance is passed to the test fixture. However, you can pass any number of handlers to the fixture. That way you can even test for second (or higher) order effects to take place. In the above example a welcome email is sent after a user is created. Presumably this entire process starts with a command to create a user, even if it is the event that triggers the email. By simply passing multiple handlers to the test fixture you can test this entire process:

```java
private final TestFixture testFixture = new TestFixture(new UserCommandHandler(), new UserEventHandler());

@Test
void newUserGetsEmail() {
    testFixture.whenCommand(new CreateUser(myUserProfile)).expectCommands(new SendWelcomeEmail(myUserProfile));
}
```

In the examples so far the test fixture checks if *at least* one command is issued that is equal to the one passed to the expectCommands() method. To make sure no other commands are issued use expectOnlyCommands() instead.

Additionally you may want to use a matcher in your test instead of a command instance. To do that simply pass a Hamcrest matcher to the expectCommands() method. You can even mix matchers and command instances like so:

```java
@Test
void newUserGetsEmail() {
    testFixture.whenEvent(new UserCreated(myUserProfile))
        .expectCommands(new SendWelcomeEmail(myUserProfile), isA(AddUserToOrganization.class));
}
```

So far we’ve only tested if certain commands were issued in response to a prior message. Of course you can also test if events are published or queries are issued in response to a command (or another event or query). I.e. you can test for any combination of input and output message. Chaining is also possible, eg:

```java
@Test
void newUserGetsEmail() {
    testFixture.whenEvent(new UserCreated(myUserProfile)).expectCommands(new SendWelcomeEmail(myUserProfile))
        .expectEvents(new UserStatsUpdated(...));
}
```

In most cases your tests will contain preconditions. You can use the givenXxx() methods on the test fixture for that. Here’s an example:

```java
@Test
void userResetsPassword() {
    testFixture.givenCommands(new CreateUser(...), new ResetPassword(...))
        .whenCommand(new UpdatePassword(...)).expectEvents(new PasswordUpdatedEvent(...));
}
```

Sometimes you need to make sure the right metadata is included in a published message. Or conversely you need to add metadata to your messages in the given/when phase to trigger specific behavior. To do that simply pass a Message instance instead of the bare message payload to the fixture:

```java
@Test
void newAdminGetsAdditionalEmail() {
    testFixture.whenCommand(new Message(new CreateUser(...), Metadata.of("roles", Arrays.asList("Customer", "Admin")))
        .expectCommands(new SendWelcomeEmail(...), new SendAdminEmail(...));
}
```

Testing for a correct reply to a command or query is also easy:

```java
@Test
void newUserCanBeQueried() {
    testFixture.givenCommands(new CreateUser(userProfile)).whenQuery(new GetUser(userId)).expectResult(userProfile);
}
```

This is very handy to test for command validation too:


```java
@Test
void userCannotBeCreatedTwice() {
    testFixture.givenCommands(new CreateUser(userProfile)).whenCommand(new CreateUser(userProfile))
        .expectException(IllegalCommandException.class);
}
```

You can configure the FluxCapacitor in your test instance to you use any kind of interceptor, same as in production. In the example below we add an interceptor that authenticates the sender of a command:

```java
private final TestFixture testFixture = new TestFixture(FluxCapacitor.builder()
    .registerHandlerInterceptor(new AuthenticationInterceptor()), new UserCommandHandler());

@Test
void unauthenticatedUserCannotChangeProfile() {
    testFixture.givenCommands(new CreateUser(userProfile)).whenCommand(new UpdateProfile(userProfile))
        .expectException(AuthenticationException.class);
}
```

In some cases you may want to test if some process was triggered, eg a call was made to another service, or an entry was added to your Elasticsearch store or such. Additionally you may want to test a process that does not begin with a message (as opposed to all examples above). For those types of tests you can opt to pass a Runnable in the given, when or then phase:

```java
@Test
void welcomeEmailActuallyGetsSent() {
    testFixture.whenCommand(new CreateUser(userProfile)).expect(() -> Mockito.verify(emailService).sendEmail(...))
}

@Test
void userEndpointWorks() {
    testFixture.when(() -> httpClient.put("/user", userProfile)).expectEvents(new UserCreated(...))
}
```

All tests we’ve seen so far have one major difference compared to the way your app runs in production. A TestFixture 
registers your handlers as "local" handlers. A local handler handles a message in the publication thread; 
this way your test runs single threaded and fast. However, in reality your handlers will run in separate consumer threads. 
For most tests this makes no difference, but in case it does for your test you can use a `StreamingTestFixture` instead 
of the normal `TestFixture`. All functionality is the same, but you will notice that your tests run slower because we now 
need to deal with eventual consistency. Eg we will not immediately know if an event got published by a handler; 
we need to simply wait for it to happen in a reasonable time frame. 

If you use Spring it’s easy to perform integration tests across your entire application. All you need to do is to 
pass your Spring config to the test fixture. Note however, that this is currently only supported on the 
`StreamingTestFixture`: 

```java
private final StreamingTestFixture testFixture = new StreamingTestFixture(springConfig);
```

### Event Sourcing

Aside from keeping track of published events in a global event log Flux Capacitor Service can also store event logs for single entities (also named aggregates in DDD) the way a true event store does. 

This feature makes it possible to store your entities as a series of historical events, which comes with a number of advantages over storing your entities as snapshots the way is done in a traditional ‘CRUD’ application. One advantage that is particularly attractive is that your message handlers do not require a database to load an entity. This can reduce the complexity and cost of your applications and infrastructure quite drastically. 

Flux Capacitor Client comes with easy to use APIs to set up event sourcing for your entities. Here’s a simple example of an event sourced User model:

```java
@EventSourced
class User {
    UserProfile userProfile;

@ApplyEvent
User(UserProfile userProfile) {
    this.userProfile = userProfile;
}

@ApplyEvent
User apply(EmailChanged event) {
    return new User(this.userProfile.withEmail(event.getEmail()));
}

...
}
```

Basically all you need to do is provide methods annotated by @ApplyEvent that describe how historical events need to be applied in order to rebuild the entity model. To create a new entity you’ll need to annotate either a constructor method (as in the above example) or a static factory method:

```java
@EventSourced
class User {
UserProfile userProfile;

User(UserProfile userProfile) {
    this.userProfile = userProfile;
}

@ApplyEvent
static User createUser(UserProfile userProfile) {
    return new User(userProfile);
}

...
}
```

To modify an existing entity simply annotate an instance method, like the one to change the user’s email in the first example. Although mutable models are supported it is definitely recommended to make your model classes immutable. That’s why a new User instance is returned when the EmailChanged event is applied. So, if you use something like Lombok or Kotlin we recommend marking and treating your model classes as value objects.

So far, we’ve discussed how a model class can be annotated for event sourcing, but how can such a model be loaded and modified? The following example shows how this is done from within a command handling method:

```java
class UserCommandHandler {
    @HandleCommand
    void handle(CreateUser command) {
        FluxCapacitor.loadAggregate(command.getUserId(), User.class).apply(new UserCreated(...));
    }
}
```

When you invoke the static loadAggregate method the user entity with given user id is automatically event sourced and returned as Entity object. Once loaded you can apply new events to the entity as in the above example. Those events will be committed to the Flux Capacitor service in a single batch after your command handler method has returned without exceptions. Flux Capacitor Service will then store those events in the event log of this user *and* make them available for event tracking so event handlers can receive them.

Additionally you can provide assertions on the loaded model before applying new events:

```java
class UserCommandHandler {
    @HandleCommand
    void handle(CreateUser command) {
        FluxCapacitor.loadAggregate(command.getUserId(), User.class).assertThat(user -> user == null, "User already exists").apply(...);
    }
}
```

Basic settings related to the storage of the model can simply be provided using the `@EventSourced` annotation:

```java
EventSourced(cached = true, snapshotPeriod = 100)
class User {
    UserProfile userProfile;
    
    @ApplyEvent
    User(UserProfile userProfile) {
        this.userProfile = userProfile;
    }
    
    ...
}
```

In the example above the User model will now be locally cached after event sourcing to prevent it from having to be 
event sourced each time a new command is handled. Additionally a snapshot of the User is stored by the 
Flux Capacitor Service every 100 events. Snapshots are useful for entities that consist of many events (>200) 
because they reduce loading delays. Note that you may choose to override these defaults when loading the aggregate:

```java
class UserCommandHandler {
    @HandleCommand
    void handle(CreateUser command) {
        FluxCapacitor.get().eventSourcing().load(command.getUserId(), User.class, 
        true, //disables caching
        true //disables snapshotting
        );
    }
}
```

### Scheduling

Flux Capacitor also allows you to schedule messages for the future. Scheduled messages are stored and read like any 
other message except that they are not released before their deadline and can be deleted. Here’s an example of a 
command handler that permanently deletes a user account one month after a user asks to close his or her account:

```java
class UserCommandHandler {
    @HandleCommand
    void handle(CloseAccount command) {
        FluxCapacitor.scheduler().schedule("CloseAccount-" + command.getUserId(), Instant.now.plus(1, months), 
        new TerminateAccount(...));
        ...
    }
    
    @HandleCommand
    void handle(OpenAccount command) {
        FluxCapacitor.scheduler().cancelSchedule("CloseAccount-" + command.getUserId());
        ...
    }
    
    @HandleSchedule
    void handle(TerminateAccount schedule) { 
        //terminate the account
    }
    
    ...
}
```
Note how the schedule can easily be cancelled if the user chooses to reopen the account within the month.

### Key value store

Flux Capacitor Service comes with a basic key value store. This is for instance used to store snapshots of event 
sourced models. Here are some examples of how to store, get or delete a value:

```java
class SettingsHandler {
    @HandleCommand
    void handle(UpdateSettings command) {
        FluxCapacitor.get().keyValueStore().store("app-settings", command.getSettings());
    }
    
    @HandleQuery
    Settings handle(GetSettings query) {
        return FluxCapacitor.get().keyValueStore().get("app-settings");
    }
    
    @HandleCommand
    void handle(DeleteAllSettings command) {
        FluxCapacitor.get().keyValueStore().delete("app-settings");
    }
}
```

### Serialization and upcasting

Flux Capacitor Client uses a serializer to transform message payloads and other stored values to a byte[] before 
transmitting those to Flux Capacitor Service. The client comes packaged with a serializer that uses Jackson to 
convert to json and uses it by default, but it is easy to roll your own serializer by extending ‘AbstractSerializer’.

Before deserializing any stored value a serializer will first attempt to _upcast_ the value using a chain of upcasters. 
Upcasters are so called because they transform serialized values to be compatible with the latest revision of the
 value class. 

Say you changed the name of a field in your event class. An upcaster can modify the serialized event payload before 
the data is deserialized to a Java instance. This all happens in your application at the client side; the messages 
stored in Flux Capacitor Service are not modified.

To mark a change in the revision of a message payload simply annotate its class:

```java
@Revision(1)
class UserCreated {
    String userId; //renamed from id
    ...
}
```

Assuming that you’re using the default `JacksonSerializer`, here’s how you would write an upcaster for the change:

```java
class UserUpcaster {
    @Upcast(type="com.example.UserCreated", revision=0)
    ObjectNode upcastUserCreatedTo1(ObjectNode json) {
        return json.rename(...);
    }
}
```

This upcaster will be applied to all revision 0 events of the UserCreated event. After upcasting, the revision of 
the serialized event will automatically be incremented by 1. 


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