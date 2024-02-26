<a href="flux-capacitor.io">
    <img src="https://flux-capacitor.io/assets/brand/flux-capacitor-white.svg" alt="Flux Capacitor logo" title="Flux Capacitor" align="right" height="60" />
</a>

Flux Capacitor java client
======================

This repository contains the 'official' Java client for Flux Capacitor service.

Installation
======================

### Maven users

Add these dependencies to your project's POM:

```xml

<dependency>
    <groupId>io.flux-capacitor</groupId>
    <artifactId>java-client</artifactId>
    <version>${flux-capacitor.version}</version>
</dependency>
<dependency>
    <groupId>io.flux-capacitor</groupId>
    <artifactId>java-client</artifactId>
    <version>${flux-capacitor.version}</version>
    <classifier>tests</classifier>
    <scope>test</scope>
</dependency>
``` 

### Gradle users

Add the following dependencies:

```
compile(group: 'io.flux-capacitor', name: 'java-client', version: '${flux-capacitor.version}')
testCompile(group: 'io.flux-capacitor', name: 'java-client', version: '${flux-capacitor.version}', classifier: 'tests')
```

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
        fluxCapacitor.registerHandlers(new HelloWorldEventHandler());
        fluxCapacitor.eventGateway().publish(new HelloWorld());
    }
}
```

Output:

```
Hello World!
```

With Spring this example gets even simpler. Just change `ExampleMain` as shown below,
and add `@Component` to `HelloWorldEventHandler`.

```java

@SpringBootApplication
@Import(FluxCapacitorSpringConfig.class)
public class ExampleMain {
    public static void main(String... args) {
        SpringApplication.run(ExampleMain.class, args);
        FluxCapacitor.publishEvent(new HelloWorld());
    }
}
```

Features
======================

The java client supports all features of Flux Capacitor but also offers plenty of additional
functionality. Here's a summary of the most important features:

## Publishing and handling

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
        UserProfile userProfile = FluxCapacitor.queryAndWait(new GetUserProfile(event.getUserId()));
        //do something with the user profile
    }
}
```

Aside from the message payload you can also include metadata with your message. Metadata are contextual data of a
technical nature that relate to the message, eg who was the sender of this command, or what was the user agent of the
web client of the user. Each message (event, command, query, result, etc.) can contain metadata. In Flux Capacitor
metadata is simply a key value map of strings, with convenience methods to (de)serialize values on the fly if required.

Here’s an example in which metadata is included in a command that is sent when an http endpoint is invoked:

```java
class UserEndpoint {
    @PUT
    @Path("/user")
    void createUser(UserProfile profile, @HeaderParam("user-agent") String userAgent) {
        FluxCapacitor.sendCommand(new CreateUser(...), Metadata.of("userAgent", userAgent));
    }
}
```

Note that we’ve used JAX-RS to define an http endpoint in this example, but of course this works with any other http
library as well.

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

### Tracking messages

By default, handlers will consume and handle messages asynchronously. When a message, like a command, is published, it 
is sent to Flux Capacitor. Flux Capacitor will then log the message for immediate (or delayed) consumption by message 
consumers. Message consumers then stream these messages to message handlers like those seen in earlier examples.

By default, a handler will join the default consumer for a given message type. E.g. in the example below, `MyHandler` 
will join the default consumer for commands.

```java
class MyHandler {
    @HandleCommand
    void handle(SomeCommand command) {
        ...
    }
}
```

It is easy to configure `MyHandler` to join a different consumer, however. The easiest way is by annotating the class
with `@Consumer` as follows:

```java
@Consumer(name = "MyConsumer")
class MyHandler {
    @HandleCommand
    void handle(SomeCommand command) {
        ...
    }
}
```

In the example above, we've created a consumer called `MyConsumer` and added `MyHandler` to it. Aside from using an
annotation it is also possible to configure consumers inside your Spring config.

You can configure the behavior of this consumer using the `@Consumer` annotation:

```java
@Consumer(name = "MyConsumer", threads = 2, maxFetchSize = 100)
class MyHandler {
    @HandleCommand
    void handle(SomeCommand command) {
        ...
    }
}
```

Here, we've configured 
this consumer to consume its commands using 2 threads. Each of these 2 threads will run a "tracker" that will fetch 
commands from Flux Capacitor and pass those to its handlers. In the example we've configured the Flux client to never 
fetch more than 100 messages at once. This is an easy way for clients to supply some back-pressure while consuming loads
of messages. By default, a consumer uses 1 thread per application instance and a maximum fetch size of 1024. These defaults 
will be fine for most consumers.

### Testing your handlers

The java client for Flux Capacitor comes with an easy to use given-when-then testing framework. Here’s a basic example:

```java
final TestFixture testFixture = new TestFixture(new UserEventHandler());

@Test
void newUserGetsEmail() {
    testFixture.whenEvent(new UserCreated(myUserProfile)).expectCommands(new SendWelcomeEmail(myUserProfile));
}
```

This test assures that a UserEventHandler instance issues a command to send a welcome email when a new user is created.

In the example a single handler instance is passed to the test fixture. However, you can pass any number of handlers to
the fixture. That way you can even test for second (or higher) order effects to take place. In the above example a
welcome email is sent after a user is created. Presumably this entire process starts with a command to create a user,
even if it is the event that triggers the email. By simply passing multiple handlers to the test fixture you can test
this entire process:

```java
private final TestFixture testFixture = new TestFixture(new UserCommandHandler(), new UserEventHandler());

@Test
void newUserGetsEmail() {
    testFixture.whenCommand(new CreateUser(myUserProfile)).expectCommands(new SendWelcomeEmail(myUserProfile));
}
```

In the examples so far the test fixture checks if *at least* one command is issued that is equal to the one passed to
the expectCommands() method. To make sure no other commands are issued use expectOnlyCommands() instead.

Additionally, you may want to test if a command of a given class was issued. To do that simply pass the command class
to the expectCommands() method. You can also test using predicates or Hamcrest matchers. You can even mix matchers and
command instances like so:

```java

@Test
void newUserGetsEmailAndIsAddedToOrganization() {
    testFixture.whenEvent(new UserCreated(myUserProfile))
            .expectCommands(new SendWelcomeEmail(myUserProfile), isA(AddUserToOrganization.class));
}
```

So far we’ve only tested if certain commands were issued in response to a prior message. Of course, you can also test if
events are published or queries are issued in response to a command (or another event or query). I.e. you can test for
any combination of input and output message. Chaining is also possible, eg:

```java

@Test
void newUserGetsEmail() {
    testFixture.whenEvent(new UserCreated(myUserProfile)).expectCommands(new SendWelcomeEmail(myUserProfile))
            .expectEvents(new UserStatsUpdated(...));
}
```

In most cases your tests will contain preconditions. You can use the givenXxx() methods on the test fixture for that.
Here’s an example:

```java

@Test
void userResetsPassword() {
    testFixture.givenCommands(new CreateUser(...),new ResetPassword(...))
        .whenCommand(new UpdatePassword(...)).expectEvents(new PasswordUpdatedEvent(...));
}
```

Sometimes you need to make sure the right metadata is included in a published message. Or conversely you need to add
metadata to your messages in the given/when phase to trigger specific behavior. To do that simply pass a Message
instance instead of the bare message payload to the fixture:

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

You can configure the FluxCapacitor in your test instance to you use any kind of interceptor, same as in production. In
the example below we add an interceptor that authenticates the sender of a command:

```java
private final TestFixture testFixture = new TestFixture(FluxCapacitor.builder()
    .registerHandlerInterceptor(new AuthenticationInterceptor()), new UserCommandHandler());

@Test
void unauthenticatedUserCannotChangeProfile() {
    testFixture.givenCommands(new CreateUser(userProfile)).whenCommand(new UpdateProfile(userProfile))
            .expectException(AuthenticationException.class);
}
```

In some cases you may want to test if some process was triggered, eg a call was made to another service, or an entry was
added to your Elasticsearch store or such. Additionally you may want to test a process that does not begin with a
message (as opposed to all examples above). For those types of tests you can opt to pass a Runnable in the given, when
or then phase:

```java

@Test
void welcomeEmailActuallyGetsSent() {
    testFixture.whenCommand(new CreateUser(userProfile)).expect(fc -> Mockito.verify(emailService).sendEmail(...))
}

@Test
void userEndpointWorks() {
    testFixture.when(fc -> httpClient.put("/user", userProfile)).expectEvents(new UserCreated(...))
}
```

All tests we’ve seen so far have one major difference compared to the way your app runs in production. A TestFixture
registers your handlers as "local" handlers. A local handler handles a message in the publication thread;
this way your test runs single threaded and fast. However, in reality your handlers will run in separate consumer
threads.
For most tests this makes no difference, but in case it does for your test you can use a `StreamingTestFixture` instead
of the normal `TestFixture`. All functionality is the same, but you will notice that your tests run slower because we
now
need to deal with eventual consistency. Eg we will not immediately know if an event got published by a handler;
we need to simply wait for it to happen in a reasonable time frame.

If you use Spring it’s easy to perform integration tests across your entire application. All you need to do is to
pass your Spring config to the test fixture. Note however, that this is currently only supported on the
`StreamingTestFixture`:

```java
private final StreamingTestFixture testFixture = new StreamingTestFixture(springConfig);
```

### Event Sourcing

Aside from keeping track of published events in a global event log Flux Capacitor Service can also store event logs for
single entities (also named aggregates in DDD) the way a true event store does.

This feature makes it possible to store your entities as a series of historical events, which comes with a number of
advantages over storing your entities as snapshots the way is done in a traditional ‘CRUD’ application. One advantage
that is particularly attractive is that your message handlers do not require a database to load an entity. This can
reduce the complexity and cost of your applications and infrastructure quite drastically.

Flux Capacitor Client comes with easy to use APIs to set up event sourcing for your entities. Here’s a simple example of
an event sourced User model:

```java

@EventSourced
class User {
    UserProfile userProfile;

    @Apply
    User(UserProfile userProfile) {
        this.userProfile = userProfile;
    }

    @Apply
    User apply(EmailChanged event) {
        return new User(this.userProfile.withEmail(event.getEmail()));
    }
    
    ...
}
```

Basically all you need to do is provide methods annotated by @Apply that describe how historical events need to be
applied in order to rebuild the entity model. To create a new entity you’ll need to annotate either a constructor
method (as in the above example) or a static factory method:

```java

@EventSourced
class User {
    UserProfile userProfile;

    User(UserProfile userProfile) {
        this.userProfile = userProfile;
    }

    @Apply
    static User createUser(UserProfile userProfile) {
        return new User(userProfile);
    }
    
    ...
}
```

To modify an existing entity simply annotate an instance method, like the one to change the user’s email in the first
example. Although mutable models are supported it is definitely recommended to make your model classes immutable. That’s
why a new User instance is returned when the EmailChanged event is applied. So, if you use something like Lombok or
Kotlin we recommend marking and treating your model classes as value objects.

So far, we’ve discussed how a model class can be annotated for event sourcing, but how can such a model be loaded and
modified? The following example shows how this is done from within a command handling method:

```java
class UserCommandHandler {
    @HandleCommand
    void handle(CreateUser command) {
        FluxCapacitor.loadAggregate(command.getUserId(), User.class).apply(new UserCreated(...));
    }
}
```

When you invoke the static `loadAggregate` method the user entity with given user id is automatically event sourced and
returned as Entity object. Once loaded you can apply new events to the entity as in the above example. Those events will
be committed to the Flux Capacitor service in a single batch after your command handler method has returned without
exceptions. Flux Capacitor Service will then store those events in the event log of this user *and* make them available
for event tracking so event handlers can receive them.

Additionally you can provide assertions on the loaded model before applying new events:

```java
class UserCommandHandler {
    @HandleCommand
    void handle(CreateUser command) {
        FluxCapacitor.loadAggregate(command.getUserId(), User.class)
                .assertThat(user -> user == null, "User already exists").apply(...);
    }
}
```

Basic settings related to the storage of the model can simply be provided using the `@EventSourced` annotation:

```java
EventSourced(cached =true, snapshotPeriod =100)

class User {
    UserProfile userProfile;

    @Apply
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
        FluxCapacitor.get().aggregateRepository().load(command.getUserId(), User.class,
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

In the example above you can see that the schedule can be easily cancelled if the user
chooses to reopen the account within the month.

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
convert to json and uses it by default, but it is easy to roll your own serializer by extending `AbstractSerializer`.

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
    @Upcast(type = "com.example.UserCreated", revision = 0)
    ObjectNode upcastUserCreatedTo1(ObjectNode json) {
        return json.rename(...);
    }
}
```

This upcaster will be applied to all revision 0 events of the UserCreated event. After upcasting, the revision of
the serialized event will automatically be incremented by 1.

Note that the upcaster above applies its modifications to a Jackson ObjectNode instead of the raw serialized bytes.
This is possible because the `JacksonSerializer` first converts the `byte[]` to a `JsonNode` before applying any
registered upcasters.

Aside from modifying message payload you can also modify the message type (class name) or metadata of a message.
To achieve that you need to apply your upcaster to a `Data` object:

```java

@Upcast(type = "com.example.UserCreated", revision = 0)
Data<ObjectNode> upcastUserCreatedTo1(Data<ObjectNode> data) {
    data.setType("com.example.CustomerCreated");
    return data;
}
```

Aside from modifying a serialized message it is also possible to drop or split a message up in multiple messages.
To drop a message from a stream of messages simply have the upcaster method return void:

```java

@Upcast(type = "com.example.UserCreated", revision = 0)
void dropUserCreated(ObjectNode json) {
}
```

Note again that this will not delete the message from the Flux Capacitor service but only from the client read stream.

To split up or optionally drop a message simply return a stream of Data objects:

```java

@Upcast(type = "com.example.UserCreated", revision = 0)
Stream<Data<ObjectNode>> upcastUserCreatedTo1(Data<ObjectNode> data) {
    return Stream.of(data, new Data<>(...));
}
```

Upcasting is not limited to message payloads but can be used for all stored data, including aggregate snapshots and
values stored in the key value store. Simply apply the upcaster to the serialized type and you are good to go.
Here’s an example of an upcaster modifying a User snapshot:

```java

@Upcast(type = "com.example.User", revision = 0)
ObjectNode upcastUserTo1(ObjectNode json) {
    ... //do something to the json
    return json;
}
```

To activate an upcaster you can register it manually with the serializer. Or if you’re using Spring and your
upcaster class is a bean it will be automatically registered.

###Parameter resolvers

The parameters of annotated handler methods (e.g. a method annotated with `@HandleEvent`) are fully customizable
using parameter resolvers. By default a couple of common parameter resolvers are registered to resolve the message
payload, raw message or its metadata.

By default, the first parameter of an annotated handler method is assumed to refer to the message payload.
Here’s a method with parameters for the payload, metadata and raw serialized message:

```java

@HandleEvent
void handle(UserCreated event, Metadata metadata, SerializedMessage message) {
    // do something special 
}
```

You can easily create your own parameter resolver. For instance, here’s one that will inject the message timestamp
to parameters of type `Instant`:

```java
public class TimestampParameterResolver implements ParameterResolver<DeserializingMessage> {
    @Override
    public Function<DeserializingMessage, Object> resolve(Parameter p) {
        if (p.getType().equals(Instant.class)) {
            return message -> Instant.of(message.getTimestamp());
        }
        return null;
    }
}
```

You can register your custom parameter resolver with the `FluxCapacitorBuilder` and then use it anywhere:

```java

@HandleEvent
void handle(UserCreated event, Instant timestamp) {
    // use the message time
}
```

### Message interceptors

Flux Capacitor client allows messages to be intercepted before they are handled or dispatched.
This can be useful in many situations. E.g. to check if a user is authorized to issue a command you can
write following interceptor:

```java
class AuthorizingInterceptor implements HandlerInterceptor {
    @Override
    public Function<DeserializingMessage, Object> interceptHandling(Function<DeserializingMessage,
            Handler<DeserializingMessage> handler, String consumer) {
        return m -> {
            Sender sender = Sender.fromMetadata(m.getMetadata());
            RequiresRole requiresRole = m.getPayloadClass().getAnnotation(RequiresRole.class);
            if (requiresRole != null) {
                if (sender == null) {
                    throw new RequiresAuthenticationException(m.getPayload());
                }
                if (Arrays.stream(requiresRole.value()).noneMatch(sender::hasRole)) {
                    throw new UnauthorizedException(sender.getUserName(), m.getPayload());
                }
            }
            return function.apply(m);
        };
    }
}
```

Note that the `RequiresRole` annotation and `Sender` object in the above example are project specific and not
included in the client.

The next few sections discuss some supported functionalities that have been implemented using message interceptors.

### Data protection

Sometimes you are dealing with messages that contain sensitive information. This could be e.g. a credit card,
or the entire profile of a user. Flux Capacitor client enables this part of a message to be removed from the message
payload and stored inside the key-value store of the service where it can be easily encrypted or removed forever
if needed.

Here's an example of a command to create a user where the user profile is sensitive:

```java

@Value
class CreateUser {
    @ProtectData
    UserProfile userProfile;
    
    ...
}
```

Placing `@ProtectData` on a top level field of a message will place the user profile in the key value store under a
randomly generated key. By placing the key in the metadata of the message the value will be automatically restored
when the command is handled:

```java
class UserCommandHandler {
    @HandleCommand
    void handle(CreateUser command) {
        // user profile will be automatically injected into the command before handling
    }
}
```

When you no longer need the protected data you can have Flux Capacitor client delete it for you after handling
the message:

```java
class UserCommandHandler {
    @HandleCommand
    @DropProtectedData
    void handle(CreateUser command) {
        // user profile will be automatically injected into the command before handling
    }
}
```

This will drop the value from the key value store altogether without modifying the original message. Note that,
even though the value cannot be recovered after dropping it, the message is still available for handling. After the
data was dropped from the key-value store the field value will be `null`.

### Payload validation

By default, Flux Capacitor client performs constraint JSR380 validations of commands and queries. Here's a sample of an
annotated command:

```java

@Value
class CreateUser {
    @NotBlank
    String userId;

    @NotNull
    @Valid
    UserProfile userProfile;
}
```

If you don't want automatic payload validation you can disable it
using `FluxCapacitorBuilder.disablePayloadValidation()`.

### Correlating messages

By default, Flux Capacitor client correlates related messages. For example, if an event is published as result of the
handling of a command, then metadata is added to the event that allows you to correlate it to the original command.

Following this example the following metadata entries would be added to the event:

* `$correlationId`: the index of the command
* `$traceId`: the index of the message that triggered the command, or the index of the command if no other message
  triggered that command
* `$trigger`: the type of the command, e.g. `com.example.CreateUser`
* `$triggerRoutingKey`: the value of the routing key of the command if any

### Message routing

In case multiple clients of the same consumer are subscribed to the Flux Capacitor service, the service uses
the `segment`
of a message to route the message to one of the clients. This segment is automatically determined from the message id
if the message segment was not provided by the client. However, Flux Capacitor client can automatically calculate
a segment from the so-called routing key on a message.

Here's an example where the field `userId` is used to determine the routing key:

```java

@Value
class CreateUser {
    @RoutingKey
    String userId;

    UserProfile userProfile;
}
```

By annotating the `userId` field with `@RoutingKey` the client will automatically calculate a segment using consistent
hashing and pass it to the message. The advantage of this is that all
commands pertaining to a given user will be handled by the same client. This prevents conflicts that would arise when
multiple clients would modify the user aggregate simultaneously.

When listening to events there are situations where you'll need to handle every event. In that case you should use
the `@HandleNotification` annotation on your handler method instead of the default `@HandleEvent`.

### Metrics

Flux Capacitor service is automatically collecting metrics about connected clients. These metrics are stored as
messages like any other and can hence be tracked like any other message. Here's an example of metrics handler:

 ```java
 class MetricsHandler {
    @HandleMetrics
    void handle(ConnectEvent event) {
        ...
    }

    @HandleMetrics
    void handle(AppendEvent event) {
        ...
    }
}
 ```

Clients can also choose to publish custom metrics events. Some useful metrics collected on the client can be
enabled using `FluxCapacitorBuilder.collectTrackingMetrics()`. This will collect metrics about handler methods on the
client including the time it took to handle the message. This enables you to easily detect badly performing handlers.

### Miscellaneous

#### Low-level APIs

The `FluxCapacitor` interface provides a lower-level client API via `FluxCapacitor.client()` that allows
you to muddle with messages and data in serialized form. There may be situations where this will come in handy,
for instance when you want to handle messages in batch.

#### Timeouts

Queries and commands sent using send-and-wait will time out after some time. By default this timeout period is
1 minute. You can change the default timeout by annotating the query or command class as follows:

```java

@Timeout(300_000) //timeout of 5 minutes
class VerySlowQuery {
    ...
}
```

#### Message identity

All messages receive an identifier when they are created. By default new random UUID will be used as identifier. You
can use a custom `IdentityProvider` to supply the id by changing `Message.identityProvider` to something else.

#### Error tracking

Whenever a handle method gives rise to an exception the error will be published to a dedicated error log.
You can optionally these errors in a central location using `@HandleError`.

#### Testing with time

All messages receive a timestamp when they are published. That timestamp is determined using an internal clock
located on `SerializedMessage`. While testing it is often beneficial to fix the time. You can do so as
follows:

 ```java
 class SomeTest {
    @Test
    void testWithFixedTime() {
        var clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        try {
            SerializedMessage.useCustomClock(clock);
            //test something
        } finally {
            SerializedMessage.useDefaultClock();
        }
    }
}
 ```

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