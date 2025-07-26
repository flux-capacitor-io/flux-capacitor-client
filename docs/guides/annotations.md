# Flux Capacitor Annotations Reference

This document provides a comprehensive list of all custom annotations available in the Flux Capacitor Java client library.

## Authentication & Authorization

- **`io.fluxcapacitor.javaclient.tracking.handling.authentication.RequiresUser`** - Indicates that a handler or message requires the presence of an authenticated user, implemented as a meta-annotation over RequiresAnyRole.

- **`io.fluxcapacitor.javaclient.tracking.handling.authentication.RequiresAnyRole`** - Restricts message handlers or payloads to users with at least one of the specified roles, with configurable exception throwing behavior.

- **`io.fluxcapacitor.javaclient.tracking.handling.authentication.NoUserRequired`** - Allows a message handler or payload to be invoked even if no user is authenticated, overriding broader authentication requirements.

- **`io.fluxcapacitor.javaclient.tracking.handling.authentication.ForbidsUser`** - Prevents handler invocation or message processing when a user is currently authenticated, useful for anonymous-only flows.

- **`io.fluxcapacitor.javaclient.tracking.handling.authentication.ForbidsAnyRole`** - Excludes handlers or payloads from being processed if the current user has any of the specified roles.

## Web Framework

- **`io.fluxcapacitor.javaclient.web.WebParam`** - Meta-annotation for parameter annotations used to inject values from HTTP requests based on the specified WebParameterSource.

- **`io.fluxcapacitor.javaclient.web.ServeStatic`** - Declares a static file handler that serves files from a resource or file system location at specified web paths.

- **`io.fluxcapacitor.javaclient.web.QueryParam`** - Injects an HTTP query parameter into a handler method parameter.

- **`io.fluxcapacitor.javaclient.web.PathParam`** - Injects a path variable from the URI into a handler method parameter.

- **`io.fluxcapacitor.javaclient.web.Path`** - Declares a path prefix that contributes to the final URI of a web handler, supporting hierarchical path composition.

- **`io.fluxcapacitor.javaclient.web.HeaderParam`** - Injects an HTTP request header into a handler method parameter.

- **`io.fluxcapacitor.javaclient.web.FormParam`** - Injects form data parameters from HTTP requests into handler method parameters.

- **`io.fluxcapacitor.javaclient.web.CookieParam`** - Injects HTTP cookie values into handler method parameters.

- **`io.fluxcapacitor.javaclient.web.HandleWebResponse`** - Marks a method as a handler for WebResponse messages, typically used to inspect response messages from web request handlers.

- **`io.fluxcapacitor.javaclient.web.HandleWeb`** - Marks a method as a handler for incoming web requests with configurable path patterns, HTTP methods, and response publishing behavior.

- **`io.fluxcapacitor.javaclient.web.HandleGet`** - Handles incoming HTTP GET requests for specified paths, a specialization of HandleWeb.

- **`io.fluxcapacitor.javaclient.web.HandlePost`** - Handles incoming HTTP POST requests for specified paths, a specialization of HandleWeb.

## Message Handling

- **`io.fluxcapacitor.javaclient.tracking.handling.HandleMessage`** - Meta-annotation that declares an annotation marks a method as a message handler for a specific MessageType.

- **`io.fluxcapacitor.javaclient.tracking.handling.HandleCommand`** - Marks a method as a handler for command messages, used for operations that change system state.

- **`io.fluxcapacitor.javaclient.tracking.handling.HandleQuery`** - Marks a method as a handler for query messages, responsible for answering questions by returning computed or retrieved values.

- **`io.fluxcapacitor.javaclient.tracking.handling.HandleEvent`** - Marks a method as a handler for event messages, typically used for reacting to state changes.

- **`io.fluxcapacitor.javaclient.tracking.handling.HandleSchedule`** - Marks a method as a handler for scheduled messages, enabling time-based or periodic processing.

- **`io.fluxcapacitor.javaclient.tracking.handling.HandleResult`** - Marks a method as a handler for result messages from previously executed requests.

- **`io.fluxcapacitor.javaclient.tracking.handling.HandleNotification`** - Marks a method as a handler for notification messages.

- **`io.fluxcapacitor.javaclient.tracking.handling.HandleMetrics`** - Marks a method as a handler for metrics messages.

- **`io.fluxcapacitor.javaclient.tracking.handling.HandleError`** - Marks a method as a handler for error messages.

- **`io.fluxcapacitor.javaclient.tracking.handling.HandleDocument`** - Marks a method as a handler for document messages.

- **`io.fluxcapacitor.javaclient.tracking.handling.HandleCustom`** - Marks a method as a handler for custom message types.

## Handler Configuration

- **`io.fluxcapacitor.javaclient.tracking.handling.Stateful`** - Declares a class as a stateful message handler whose state is persisted and can receive messages via Association.

- **`io.fluxcapacitor.javaclient.tracking.handling.LocalHandler`** - Marks a handler as local, meaning it's invoked immediately in the publishing thread rather than asynchronously through tracking.

- **`io.fluxcapacitor.javaclient.tracking.handling.Association`** - Declares how a message should be routed to a stateful handler instance by matching message properties with handler state.

- **`io.fluxcapacitor.javaclient.tracking.Consumer`** - Configures tracking behavior for message handlers including threading, error handling, and filtering options.

- **`io.fluxcapacitor.javaclient.tracking.TrackSelf`** - Marks a class to track itself as a Spring component with prototype scope.

- **`io.fluxcapacitor.javaclient.tracking.handling.Trigger`** - Declares a parameter or method that can trigger message publication or handling behavior.

## Domain Modeling

- **`io.fluxcapacitor.javaclient.modeling.Aggregate`** - Marks a class as the root of an aggregate in the domain model with extensive event sourcing and persistence configuration.

- **`io.fluxcapacitor.javaclient.modeling.EntityId`** - Marks a property as the unique identifier of an entity within an aggregate structure for automatic routing.

- **`io.fluxcapacitor.javaclient.modeling.Member`** - Declares child, grandchild, or descendant entities within an aggregate.

- **`io.fluxcapacitor.javaclient.modeling.AssertLegal`** - Defines legality checks that must pass before entity updates are applied.

- **`io.fluxcapacitor.javaclient.modeling.Alias`** - Provides alternative names for types during serialization and deserialization.

## Event Sourcing

- **`io.fluxcapacitor.javaclient.persisting.eventsourcing.Apply`** - Indicates that a method or constructor applies an update to an entity or creates/deletes an entity in event sourcing.

- **`io.fluxcapacitor.javaclient.persisting.eventsourcing.InterceptApply`** - Intercepts and potentially modifies entity updates before they are applied in event sourcing.

## Scheduling

- **`io.fluxcapacitor.javaclient.scheduling.Periodic`** - Declares a message or handler method as part of a periodic schedule with cron expression or fixed delay support.

## Publishing & Routing

- **`io.fluxcapacitor.javaclient.publishing.Timeout`** - Configures timeout behavior for requests sent using sendAndWait-like methods.

- **`io.fluxcapacitor.javaclient.publishing.routing.RoutingKey`** - Specifies routing keys for message distribution.

- **`io.fluxcapacitor.javaclient.publishing.dataprotection.ProtectData`** - Marks data fields for protection during serialization.

- **`io.fluxcapacitor.javaclient.publishing.dataprotection.DropProtectedData`** - Configures automatic removal of protected data fields.

## Search & Indexing

- **`io.fluxcapacitor.javaclient.persisting.search.Searchable`** - Marks classes for automatic indexing in the document store with search capabilities.

- **`io.fluxcapacitor.common.search.Sortable`** - Marks fields or properties as sortable in search indexes with optional custom property names.

- **`io.fluxcapacitor.common.search.Facet`** - Declares fields for faceted search capabilities with customizable facet names.

- **`io.fluxcapacitor.common.search.SearchInclude`** - Explicitly includes fields in search indexing, overriding exclusion rules.

- **`io.fluxcapacitor.common.search.SearchExclude`** - Excludes fields from search indexing with configurable activation behavior.

## Serialization & Casting

- **`io.fluxcapacitor.javaclient.common.serialization.casting.Cast`** - Meta-annotation indicating methods that perform revision-based type transformations.

- **`io.fluxcapacitor.javaclient.common.serialization.casting.Upcast`** - Marks methods that transform objects to newer revisions during deserialization.

- **`io.fluxcapacitor.javaclient.common.serialization.casting.Downcast`** - Marks methods that transform objects to older revisions during serialization.

- **`io.fluxcapacitor.javaclient.common.serialization.FilterContent`** - Controls content filtering during serialization processes.

- **`io.fluxcapacitor.common.serialization.Revision`** - Declares the revision number of a class for versioning and backward compatibility.

- **`io.fluxcapacitor.common.serialization.RegisterType`** - Automatically registers types for serialization with optional filtering by class name patterns.

## Validation

- **`io.fluxcapacitor.javaclient.tracking.handling.validation.ValidateWith`** - Specifies validation groups to include when validating a class using Bean Validation.

## Spring Integration

- **`io.fluxcapacitor.javaclient.configuration.spring.ConditionalOnProperty`** - Conditionally enables beans based on property values in Spring configuration.

- **`io.fluxcapacitor.javaclient.configuration.spring.ConditionalOnMissingProperty`** - Conditionally enables beans when specific properties are missing in Spring configuration.

- **`io.fluxcapacitor.javaclient.configuration.spring.ConditionalOnBean`** - Conditionally enables beans based on the presence of other beans in Spring context.

- **`io.fluxcapacitor.javaclient.configuration.spring.ConditionalOnMissingBean`** - Conditionally enables beans when specific beans are missing from Spring context.

## Testing & Utilities

- **`io.fluxcapacitor.javaclient.common.Nullable`** - Test annotation indicating nullable parameters or return values.

- **`io.fluxcapacitor.testserver.websocket.Handle`** - Test server annotation for handling websocket messages.

- **`io.fluxcapacitor.common.handling.Handle`** - Test annotation for generic message handling.