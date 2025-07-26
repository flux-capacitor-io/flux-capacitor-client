## Testing Web Endpoint Behavior

Flux Capacitor allows you to simulate and verify HTTP interactions as part of your test flows. Web requests
can be tested like any other command, query, or event.

Here’s a complete test for a `POST /games` handler that accepts a JSON request and publishes a command:

```java

@Test
void registerGame() {
    testFixture
            .whenPost("/games", "/game/game-details.json")  // Simulates POST with payload
            .expectResult(GameId.class)                     // Asserts a result is returned
            .expectEvents(RegisterGame.class);              // Asserts a command was published
}
```

The corresponding handler is:

```java

@HandlePost("/games")
CompletableFuture<GameId> addGame(GameDetails details) {
    return FluxCapacitor.sendCommand(new RegisterGame(details));
}
```

As always, the `.json` file is automatically loaded from the classpath, allowing you to cleanly separate test data:

📄 `/game/game-details.json`

```json
{
  "title": "Legend of the Skylands",
  "description": "An epic singleplayer adventure with puzzles and secrets.",
  "releaseDate": "2025-11-12T00:00:00Z",
  "tags": [
    "adventure",
    "puzzle",
    "singleplayer"
  ]
}
```

---

### Example: Querying with `GET`

You can test GET endpoints just as easily. This example first registers a game via `POST /games`, then fetches the list
of all games via `GET /games` and checks the result:

```java

@Test
void getGames() {
    testFixture
            .givenPost("/games", "/game/game-details.json")   // Precondition: register a game
            .whenGet("/games")                                // Perform GET request
            .<List<Game>>expectResult(r -> r.size() == 1)     // Assert one game is returned
            .expectWebResponse(r -> r.getStatus() == 200);    // Assert the status of the response
}
```

This corresponds to the following handler method:

```java

@HandleGet
@Path("/games")
CompletableFuture<List<Game>> getGames(@QueryParam String term) {
    return FluxCapacitor.query(new FindGames(term));
}
```

---

### Testing Error Responses and Exceptions

Flux Capacitor also allows you to verify how your web endpoints handle exceptional scenarios. This includes asserting
the type of exception thrown as well as inspecting the resulting HTTP status code or response body.

Here’s a test that triggers a `403 Forbidden` error by throwing an `IllegalCommandException`:

```java

@Test
void postReturnsError() {
    testFixture
            .whenPost("/error", "body")                              // Simulate POST request
            .expectExceptionalResult(IllegalCommandException.class)  // Assert thrown exception
            .expectWebResponse(r -> r.getStatus() == 403);           // Assert HTTP 403 response
}
```

The corresponding handler might look like:

```java

@HandlePost("/error")
void postForError(String body) {
    throw new IllegalCommandException("error: " + body);
}
```

This enables robust testing of both successful and failure paths for all your web request handlers.

---

### Path Parameter Substitution in Tests

When simulating web requests, Flux Capacitor automatically substitutes `{...}` placeholders in the request path using
results from previous steps:

- The result of the **first `when...()` step** is saved when `.andThen()` is called.
- If a subsequent path (e.g. `/games/{gameId}/buy`) contains placeholders, Flux Capacitor will:
- Attempt to replace each placeholder (like `{gameId}`) with the string value of a previously returned result.
- Track all resolved placeholders across steps. This allows chaining:

[//]: # (@formatter:off)
```java
testFixture.whenPost("/games", "/game/game-details.json") // returns gameId
     .andThen()
     .whenPost("/games/{gameId}/buy") // uses gameId, returns orderId
     .andThen()
     .whenPost("/games/{gameId}/refund/{orderId}"); // uses gameId from step 1, orderId from step 2
```
[//]: # (@formatter:on)

Substitutions are based purely on the order of results, not types or field names. The `.toString()` value of each result
is used to fill the next unresolved placeholder.

This keeps tests expressive and avoids boilerplate, especially for flows that involve chained resource creation (e.g.
`POST /games → POST /games/{gameId}/buy → ...`).