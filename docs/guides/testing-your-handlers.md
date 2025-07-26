## Testing your Handlers

Flux Capacitor comes with a flexible, expressive testing framework based on the given-when-then pattern. This enables
writing behavioral tests for your handlers without needing to mock the infrastructure.

Here’s a basic example:

```java
TestFixture testFixture = TestFixture.create(new UserEventHandler());

@Test
void newUserGetsWelcomeEmail() {
    testFixture.whenEvent(new CreateUser(userId, myUserProfile))
            .expectCommands(new SendWelcomeEmail(myUserProfile));
}
```

This test ensures that when a `CreateUser` event occurs, a `SendWelcomeEmail` command is issued by the handler.

### Testing complete workflows

You can test full workflows across multiple handlers:

```java
TestFixture fixture = TestFixture.create(new UserCommandHandler(), new UserEventHandler());

@Test
void creatingUserTriggersEmail() {
    fixture.whenCommand(new CreateUser(userProfile))
            .expectCommands(new SendWelcomeEmail(userProfile));
}
```

Use `expectOnlyCommands()` to assert that **only** the expected command was issued:

[//]: # (@formatter:off)
```java
fixture.whenCommand(new CreateUser(userProfile))
       .expectOnlyCommands(new SendWelcomeEmail(userProfile));
```
[//]: # (@formatter:on)

You can also match by class, predicate, or Hamcrest matcher:

[//]: # (@formatter:off)
```java
fixture.whenCommand(new CreateUser(userProfile))
       .expectCommands(SendWelcomeEmail.class,
                       isA(AddUserToOrganization.class));
```
[//]: # (@formatter:on)

### Chained expectations

Multiple expectations can be chained to test the full sequence of events and commands:

[//]: # (@formatter:off)
```java
fixture.whenCommand(new CreateUser(userProfile))
       .expectCommands(new SendWelcomeEmail(userProfile))
       .expectEvents(new UserStatsUpdated(...));
```
[//]: # (@formatter:on)

You can also chain multiple **inputs** using `.andThen()` to simulate a sequence of events, commands, or queries:

[//]: # (@formatter:off)
```java
fixture.whenCommand(new CreateUser(userProfile))
       .expectCommands(new SendWelcomeEmail(userProfile))
       .andThen()
       .whenQuery(new GetUser(userId))
       .expectResult(userProfile);
```
[//]: # (@formatter:on)

This example first triggers a `CreateUser` command, expects a `SendWelcomeEmail` event, and then issues a `GetUser`
query,
asserting that it returns the expected result.

### Using givenXxx() for preconditions

Use `givenCommands`, `givenEvents`, etc., to simulate preconditions:

[//]: # (@formatter:off)
```java
fixture.givenCommands(new CreateUser(userProfile), new ResetPassword(...))
       .whenCommand(new UpdatePassword(...))
       .expectEvents(UpdatePassword.class);
```
[//]: # (@formatter:on)

### Providing External JSON Files

Test fixtures support loading inputs from external JSON resources. This allows you to keep your tests clean and reuse
structured input data.

Any `givenXyz(...)`, `whenXzy(...)`, or `expectXyz(...)` method argument that is a `String` ending with `.json` will be
interpreted as a **classpath resource path**, and deserialized accordingly.

For example:

[//]: # (@formatter:off)
```java
fixture.givenCommands("create-user.json")
    .whenQuery(new GetUser(userId))
    .expectResult("user-profile.json");
```
[//]: # (@formatter:on)

If your test class is in the `org.example` package, this will resolve to `/org/example/create-user.json` in the
classpath, unless the JSON path is absolute (starts with `/`), e.g.:

```java
fixture.givenCommands("/users/create-user.json");
```

#### Class Resolution with `@class`

Each JSON file must include a `@class` property to enable deserialization:

```json
{
  "@class": "org.example.CreateUser",
  "userId": "3290328",
  "email": "foo.bar@example.com"
}
```

If your classes or packages are annotated with `@RegisterType`, you can use **simple class names**:

```json
{
  "@class": "CreateUser"
}
```

Or partial paths:

```json
{
  "@class": "example.CreateUser"
}
```

> This enables readable and concise references while still resolving to the correct type.

#### Inheriting from Other JSON Files

JSON resources can **extend** other resources using the `@extends` keyword:

```json
{
  "@extends": "create-user.json",
  "details": {
    "lastName": "Johnson"
  }
}
```

This will recursively merge the referenced file (`/org/example/create-user.json`) with the current one, allowing you
to override or augment deeply nested structures.

> 🧠 This is especially useful for composing test scenarios with shared defaults or inheritance-like setups.

---

### Adding or asserting metadata

Wrap your payload in a Message to add or assert metadata:

```java

@Test
void newAdminGetsAdditionalEmail() {
    testFixture.whenCommand(new Message(new CreateUser(...),
    Metadata.of("roles", Arrays.asList("Customer", "Admin"))))
        .expectCommands(new SendWelcomeEmail(...),
    new SendAdminEmail(...));
}
```

### Result and exception assertions

You can assert the result returned by a command or query:

[//]: # (@formatter:off)
```java
fixture.givenCommands(new CreateUser(userProfile))
       .whenQuery(new GetUser(userId))
       .expectResult(userProfile);
```
[//]: # (@formatter:on)

To assert an exception:

[//]: # (@formatter:off)
```java
fixture.givenCommands(new CreateUser(userProfile))
        .whenCommand(new CreateUser(userProfile))
        .expectExceptionalResult(IllegalCommandException.class);
```
[//]: # (@formatter:on)

### User-aware tests

You can also simulate a command from a specific user:

[//]: # (@formatter:off)
```java
var user = new MyUser("pete");

fixture.whenCommandByUser(user, "confirm-user.json")
       .expectExceptionalResult(UnauthorizedException.class);
```
[//]: # (@formatter:on)

You can also pass a user ID string directly instead of a full User object. The test fixture will resolve it using the
configured `UserProvider` (by default loaded via Java's `ServiceLoader`):

[//]: # (@formatter:off)
```java
fixture
    .givenCommands("create-user-pete.json")
    .whenCommandByUser("pete", "confirm-user.json")
    .expectExceptionalResult(UnauthorizedException.class);
```
[//]: # (@formatter:on)

In this example, the string "pete" is resolved to a `User` instance using the active `UserProvider`, and the test
asserts
that the confirmation is unauthorized.

### Verifying side effects

Use `expectThat()` or `expectTrue()` to assert custom logic or verify interactions (e.g., using Mockito):

[//]: # (@formatter:off)
```java
fixture.whenCommand("create-user-pete.json")
       .expectThat(fc -> Mockito.verify(emailService).sendEmail(...));
```
[//]: # (@formatter:on)

### Triggering side effects manually

Use `whenExecuting()` to test code outside of message dispatching, like HTTP calls:

[//]: # (@formatter:off)
```java
fixture.whenExecuting(fc -> httpClient.put("/user", "/users/user-profile-pete.json"))
       .expectEvents("create-user-pete.json");
```
[//]: # (@formatter:on)

### Asynchronous tests

By default, `TestFixture.create(...)` creates synchronous fixtures where handlers are executed locally in the same
thread
that publishes the message. This provides fast, deterministic behavior ideal for most unit tests.

However, in production your handlers are typically dispatched asynchronously by consumers running on separate threads.
To better simulate this behavior in tests — especially when testing event-driven flows or stateful consumers — you can
use an asynchronous fixture instead.

```java
TestFixture fixture = TestFixture.createAsync(new MyHandler(), MyStatefulHandler.class);
```

This ensures that:

- Handlers are tracked via the same consumer infrastructure used in production.
- Behavior involving asynchronous dispatch, retries, or stateful models is tested realistically.
- Eventual consistency is respected (e.g., expect...() calls will wait for outcomes to materialize).

> **Note:** Handlers annotated with `@LocalHandler` are executed synchronously, even in async fixtures, just as they
> would in production.

### Using Test Fixtures in Spring

Flux Capacitor provides seamless integration with Spring Boot for testing. You can inject a `TestFixture` directly:

```java

@SpringBootTest
class AsyncAppTest {

    @Autowired
    TestFixture fixture;

    @Test
    void testSomething() {
        fixture.whenCommand("commands/my-command.json")
                .expectEvents("events/expected-event.json");
    }
}
```

- ✅ **Spring Boot**: No manual setup needed—`TestFixture` is auto-configured.
- ⚠️ **Spring Core (non-Boot)**: Manually import the test configuration:

  ```java
  @Import(FluxCapacitorTestConfig.class)
  ```

  This ensures the `TestFixture` and related infrastructure are available in the Spring context.

---

#### Switching to Synchronous Mode

By default, the injected fixture is **asynchronous**. To use a **synchronous** fixture instead:

##### Globally via `application.properties`:

```properties
fluxcapacitor.test.sync=true
```

##### Or per test class:

```java

@TestPropertySource(properties = "fluxcapacitor.test.sync=true")
@SpringBootTest
class SyncAppTest {

    @Autowired
    TestFixture fixture;

    // test logic...
}
```

### Testing schedules

Flux Capacitor’s scheduling engine makes it easy to test time-based workflows. Scheduled messages are handled just
like any other message, except they are delayed until their due time.

Use the `TestFixture` to simulate the passage of time and trigger scheduled actions. Here’s a typical example:

```java
TestFixture testFixture = TestFixture.create(new UserCommandHandler(), new UserLifecycleHandler());

@Test
void accountIsTerminatedAfterClosing() {
    testFixture
            .givenCommands(new CreateUser(myUserProfile),
                           new CloseAccount(userId))
            .whenTimeElapses(Duration.ofDays(30))
            .expectEvents(new AccountTerminated(userId));
}
```

In this test:

- We schedule the AccountTerminated message as a result of the CloseAccount command.
- Then we simulate that 30 days have passed using whenTimeElapses(...).
- Finally, we verify that the expected AccountTerminated event was published.

You can also test cancellation logic:

```java

@Test
void accountReopeningCancelsTermination() {
    testFixture
            .givenCommands(new CreateUser(myUserProfile),
                           new CloseAccount(userId),
                           new ReopenAccount(userId))
            .whenTimeElapses(Duration.ofDays(30))
            .expectNoEventsLike(AccountTerminated.class);
}
```

If needed, you can advance time to an absolute timestamp using:

[//]: # (@formatter:off)
```java
fixture.whenTimeAdvancesTo(Instant.parse("2050-12-31T00:00:00Z"));
```
[//]: # (@formatter:on)

This is especially useful for workflows tied to specific deadlines or calendar dates.