# Flux Capacitor Code Samples

This guide summarizes common patterns in Flux Capacitor applications. Examples illustrate typical commands,
queries, aggregates, web endpoints, authentication, and testing—using projects, tasks, users, and other domains as
illustrative use cases.

## 1. Domain Modeling

- **Aggregates**: Define the consistency boundary and root of your domain. Annotate with `@Aggregate` to enable event
  sourcing and optional search indexing.
  ```java
  @Aggregate(searchable = true, eventPublication = EventPublication.IF_MODIFIED)
  @Builder(toBuilder = true)
  public record Project(
      @EntityId ProjectId projectId,
      ProjectDetails details,
      UserId ownerId,
      @With @Member List<Task> tasks
  ) {}
  ```

- **Entities and Value Objects**: Model nested elements and immutable data types.
  ```java
  public record Task(
      @EntityId TaskId taskId,
      TaskDetails details,
      boolean completed,
      UserId assigneeId
  ) {}

  public record ProjectDetails(@NotBlank String name) {}

  public record TaskDetails(@NotBlank String name) {}
  ```

## 2. Global Identifiers

- Strongly typed IDs in `com.example.app.todo.api`:
  ```java
  public class ProjectId extends Id<Project> {
      public ProjectId(String id) { super(id); }
  }

  public class TaskId extends Id<Task> {
      public TaskId(String id) { super(id); }
  }
  ```
- Use `FluxCapacitor.generateId(ProjectId.class)` or `...generateId(TaskId.class)` when creating new IDs.

## 3. Commands

- **Command interfaces**: Define a common interface per aggregate or module to group related commands and manage
  dispatch. Typically annotate it with `@TrackSelf` and `@Consumer(name="...")` from
  `io.fluxcapacitor.javaclient.tracking`.
  ```java
  import io.fluxcapacitor.javaclient.FluxCapacitor;
  import io.fluxcapacitor.javaclient.tracking.Consumer;
  import io.fluxcapacitor.javaclient.tracking.TrackSelf;
  import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
  import jakarta.validation.constraints.NotNull;

  @TrackSelf    // asynchronously track commands after dispatch
  @Consumer(name = "user-update") // logical consumer for command handling
  public interface UserUpdate {
      @NotNull UserId userId();

      @HandleCommand
      default void handle() {
          FluxCapacitor.loadAggregate(userId())
                       .assertAndApply(this);
      }
  }
  ```
  A generic pattern—apply similarly for other domains like `OrderUpdate` or `InventoryUpdate`.

- **Command definitions**: Implement the interface with a `record`. Use Jakarta Validation, role-based checks, and
  event-sourcing annotations:
  ```java
  @RequiresRole(Role.admin)
  public record CreateUser(
      UserId userId,
      @Valid UserDetails details,
      Role role
  ) implements UserUpdate
  ```

- **CreateTask (sub-entity)**: Under a parent aggregate interface, return the new sub-entity instance.
  ```java
  public record CreateTask(
      @NotNull ProjectId projectId,
      @NotNull TaskId taskId,
      @Valid @NotNull TaskDetails details
  ) implements ProjectUpdate, AssertOwner {
      @Apply
      Task apply() {
          return Task.builder()
                     .taskId(taskId)
                     .details(details)
                     .completed(false)
                     .build();
      }
  }
  ```

- **CompleteTask (sub-entity)**: Enforce legal checks on both parent and entity before updating.
  ```java
  public record CompleteTask(
      @NotNull ProjectId projectId,
      @NotNull TaskId taskId
  ) implements ProjectUpdate {
      @AssertLegal
      void assertPermission(Project project, Task task, Sender sender) {
          if (!sender.isAdmin()
              && !project.ownerId().equals(sender.userId())
              && !(task.assigneeId() != null && task.assigneeId().equals(sender.userId()))) {
              throw new IllegalCommandException("You don't have permission to complete this task.");
          }
      }

      @Apply
      Task apply(Task task) {
          return task.toBuilder()
                     .completed(true)
                     .build();
      }
  }
  ```

## 4. Queries

- **Query objects**: Implement `Request<T>` to define the return type of a query. For example, use
  `Request<List<Project>>` for listing projects.

- **List projects** (filter by owner unless admin):
  ```java
  public record ListProjects() implements Request<List<Project>> {
      @HandleQuery
      List<Project> handleQuery(Sender sender) {
          return FluxCapacitor.search(Project.class)
                              .match(sender.isAdmin() ? null : sender.userId(), "ownerId")
                              .fetch(100);
      }
  }
  ```

- **Get single project**:
  ```java
  public record GetProject(@NotNull ProjectId projectId)
      implements Request<Project> {
      @HandleQuery
      Project handleQuery(Sender sender) {
          return FluxCapacitor.search(Project.class)
                               .match(projectId, "projectId")
                               .match(sender.isAdmin() ? null : sender.userId(), "ownerId")
                               .fetchFirstOrNull();
      }
  }
  ```

## 5. Web Endpoints

- Expose REST routes for projects and tasks:
  ```java
  @Component
  public class ProjectsEndpoint {
      @HandlePost("/projects")
      ProjectId createProject(ProjectDetails details) {
          var id = FluxCapacitor.generateId(ProjectId.class);
          FluxCapacitor.sendCommandAndWait(new CreateProject(id, details));
          return id;
      }

      @HandleGet("/projects")
      List<Project> listProjects() {
          return FluxCapacitor.queryAndWait(new ListProjects());
      }

      @HandleGet("/projects/{projectId}")
      Project getProject(@PathParam ProjectId projectId) {
          return FluxCapacitor.queryAndWait(new GetProject(projectId));
      }

      @HandlePost("/projects/{projectId}/tasks")
      TaskId createTask(@PathParam ProjectId projectId, TaskDetails details) {
          var taskId = FluxCapacitor.generateId(TaskId.class);
          FluxCapacitor.sendCommandAndWait(
              new CreateTask(projectId, taskId, details)
          );
          return taskId;
      }

      @HandlePost("/projects/{projectId}/tasks/{taskId}/complete")
      void completeTask(@PathParam ProjectId projectId,
                        @PathParam TaskId taskId) {
          FluxCapacitor.sendCommandAndWait(new CompleteTask(projectId, taskId));
      }

      @HandlePost("/projects/{projectId}/tasks/{taskId}/assign")
      void assignTask(@PathParam ProjectId projectId,
                      @PathParam TaskId taskId,
                      UserId assigneeId) {
          FluxCapacitor.sendCommandAndWait(
              new AssignTask(projectId, taskId, assigneeId)
          );
      }
  }
  ```

## 6. Authentication & Authorization

- Enforce roles directly on command interfaces or implementations. Example: an admin-only create-user command signature:
  ```java
  @RequiresRole(Role.admin)
  public record CreateUser(UserId userId, @Valid UserDetails details, Role role) implements UserUpdate
  ```

## 7. Testing Patterns

- **Project command tests**: Validate command behavior using JSON fixtures.
  ```java
  @Test
  void successfullyCreateProject() {
      fixture.whenCommand("/todo/create-project.json")
             .expectEvents("/todo/create-project.json");
  }

  @Test
  void creatingDuplicateProjectIsRejected() {
      fixture.givenCommands("/todo/create-project.json")
             .whenCommand("/todo/create-project.json")
             .expectExceptionalResult(IllegalCommandException.class);
  }
  ```

- **Project query tests**: Ensure correct query results and access control.
  ```java
  @Test
  void listProjectsReturnsOwnedProjects() {
      fixture.givenCommands("/todo/create-project.json")
             .whenQuery(new ListProjects())
             .expectResult(result -> !result.isEmpty());
  }

  @Test
  void nonOwnerCannotGetProject() {
      fixture.givenCommands("/todo/create-project.json")
             .givenCommands("/user/create-other-user.json")
             .whenQueryByUser("otherUser", "/todo/get-project.json")
             .expectNoResult();
  }
  ```

- **Task tests**: Commands on sub-entities.
  ```java
  @Nested
  class TasksTests {
      @Test
      void canCreateTaskForOwnedProject() {
          fixture.givenCommands("/todo/create-project.json")
                 .whenCommand("/todo/create-task.json")
                 .expectEvents("/todo/create-task.json");
      }

      @Test
      void cannotAssignCompletedTask() {
          fixture.givenCommands(
              "/todo/create-project.json", "/todo/create-task.json", "/todo/complete-task.json"
          )
                 .whenCommand("/todo/assign-task.json")
                 .expectExceptionalResult(IllegalCommandException.class);
      }
  }
  ```

- **Endpoint tests**: Test REST handlers in isolation.
  ```java
  @Nested
  class ProjectsEndpointTests {
      @BeforeEach
      void setUp() {
          fixture.registerHandlers(new ProjectsEndpoint())
                 .givenCommands("/user/create-other-user.json");
      }

      @Test
      void createProjectViaPost() {
          fixture.whenPost("/projects", "/todo/create-project-request.json")
                 .expectResult(ProjectId.class)
                 .expectEvents(CreateProject.class);
      }

      @Test
      void completeTaskViaEndpoint() {
          fixture.givenCommands("/todo/create-project.json", "/todo/create-task.json")
                 .whenPost("/projects/p1/tasks/t1/complete", null)
                 .expectEvents(CompleteTask.class);
      }
  }
  ```

