## Scheduling

Flux Capacitor allows scheduling messages for future delivery using the `MessageScheduler`.

Here’s an example that schedules a termination event 30 days after an account is closed:

```java
class UserLifecycleHandler {
    @HandleEvent
    void handle(AccountClosed event) {
        FluxCapacitor.schedule(
                new TerminateAccount(
                        event.getUserId()),
                "AccountClosed-" + event.getUserId(),
                Duration.ofDays(30)
        );
    }

    @HandleEvent
    void handle(AccountReopened event) {
        FluxCapacitor.cancelSchedule(
                "AccountClosed-" + event.getUserId());
    }

    @HandleSchedule
    void handle(TerminateAccount schedule) {
        // Perform termination
    }
}
```

Alternatively, you can schedule commands using `scheduleCommand`:

```java
class UserLifecycleHandler {
    @HandleEvent
    void handle(AccountClosed event) {
        FluxCapacitor.scheduleCommand(
                new TerminateAccount(
                        event.getUserId()),
                "AccountClosed-" + event.getUserId(),
                Duration.ofDays(30));
    }

    @HandleEvent
    void handle(AccountReopened event) {
        FluxCapacitor.cancelSchedule("AccountClosed-" + event.getUserId());
    }
}
```

### Periodic scheduling

Flux Capacitor supports recurring message schedules via the `@Periodic` annotation. This makes it easy to run tasks on a
fixed interval or cron-based schedule — useful for polling, maintenance, background processing, and more.

You can apply `@Periodic` to either a `Schedule` payload or a `@HandleSchedule` method:

```java

@Periodic(delay = 5, timeUnit = TimeUnit.MINUTES)
public record RefreshData(String index) {
}
```

This schedules RefreshData to run every 5 minutes.

Or, to use a cron expression:

```java

@Periodic(cron = "0 0 * * MON", timeZone = "Europe/Amsterdam")
@HandleSchedule
void weeklySync(PollData schedule) {
    ...
}
```

This example triggers the `weeklySync` method every Monday at 00:00 in the Amsterdam time zone.

#### Behavior and advanced options

- `@Periodic` works only for scheduled messages (see `@HandleSchedule`).
- The schedule **automatically reschedules** itself after each invocation unless canceled.
- You may:
    - Return `void` or `null` to continue with the same schedule.
    - Return a `Duration` or `Instant` to override the next deadline.
    - Return a new payload or `Schedule` to customize the next cycle.
- If an error occurs:
    - The schedule continues by default (`continueOnError = true`).
    - You may specify a fallback delay via `delayAfterError`.
    - Throw `CancelPeriodic` from the handler to stop the schedule completely.
- To prevent startup activation, use `@Periodic(autoStart = false)`.
- The schedule ID defaults to the class name, but can be customized with `scheduleId`.

Here's an example of robust polling with error fallback:

```java

@Periodic(delay = 60, timeUnit = TimeUnit.MINUTES, delayAfterError = 10)
@HandleSchedule
void pollExternalService(PollTask pollTask) {
    try {
        externalService.fetchData();
    } catch (Exception e) {
        log.warn("Polling failed, will retry in 10 minutes", e);
        throw e;
    }
}
```

In this case:

- The task runs every hour normally.
- If it fails, it retries after 10 minutes.
- It resumes the original schedule if the next invocation succeeds.