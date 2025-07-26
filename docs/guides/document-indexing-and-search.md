## Document Indexing and Search

Flux Capacitor provides a powerful and flexible document store that lets you persist and query models using full-text
search, filters, and time-based constraints.

This system is especially useful for:

- Querying across entities (e.g., active users, recent payments)
- Supporting projections for read APIs or dashboards
- Tracking workflows, external states, or business processes
- Replacing the need for a traditional read model database

---

### Manual Indexing

You can index any object manually using:

```java
FluxCapacitor.index(myObject);
```

This stores `myObject` in the document store so it can be queried later via `FluxCapacitor.search(...)`.

- If the object is annotated with `@Searchable`, any declared `collection`, `timestampPath`, or `endPath` will be used.
- If a field is annotated with `@EntityId`, it becomes the document ID. Otherwise, a random ID is generated.
- Timestamps can be inferred from annotated paths or passed explicitly.

You can also specify the collection in which the object should be stored directly:

```java
FluxCapacitor.index(myObject, "customCollection");
```

---

### Searchable Domain Models

Many models in Flux (e.g. aggregates or stateful handlers) are automatically indexable:

- `@Aggregate(searchable = true)`
- `@Stateful` (implicitly `@Searchable`)
- Directly annotate any POJO with `@Searchable`

This enables automatic indexing after updates or message handling, without needing to call `FluxCapacitor.index(...)`
manually.

```java

@Aggregate(searchable = true)
public record UserAccount(@EntityId UserId userId,
                          UserProfile profile,
                          boolean accountClosed) {
}
```

By default, the collection name is derived from the class’s **simple name** (UserAccount → `"UserAccount"`),
unless explicitly overridden via an annotation like `@Aggregate`, `@Stateful` or `@Searchable` or in the search/index
call:

```java
@Aggregate(searchable = true, collection = "users", timestampPath = "profile/createdAt")
```

---

### Querying Indexed Documents

Use the fluent `search(...)` API:

```java
List<UserAccount> admins = FluxCapacitor
        .search("users")
        .match("admin", "profile/role")
        .inLast(Duration.ofDays(30))
        .sortBy("profile/lastLogin", true)
        .fetch(100);
```

You can also query by class:

```java
List<UserAccount> users = FluxCapacitor
        .search(UserAccount.class)
        .match("Netherlands", "profile.country")
        .fetchAll();
```

> **Note:** You can choose to split path segments using either a dot (`.`) or a slash (`/`).  
> For example, `profile.name` and `profile/name` are treated identically.  
> This flexibility can be useful when working with tools or serializers that prefer one style over the other.

---

### Common Filtering Constraints

Flux supports a rich set of constraints:

- `lookAhead("cat", paths...)` – search-as-you-type lookups
- `query("*text & (cat* | hat)", paths...)` – full-text search
- `match(value, path)` – field match
- `matchFacet(name, value)` – match field with `@Facet`
- `between(min, max, path)` – numeric or time ranges
- `since(...)`, `before(...)`, `inLast(...)` – temporal filters
- `anyExist(...)` – match if *any* of the fields are present
- Logical operations: `not(...)`, `all(...)`, `any(...)`

Example:

[//]: # (@formatter:off)
```java
FluxCapacitor.search("payments")
    .match("FAILED", "status")
    .inLast(Duration.ofDays(1))
    .fetchAll();
```
[//]: # (@formatter:on)

---

### Matching Facet Fields

If you're filtering on a field that is marked with `@Facet`, it's better to use:

[//]: # (@formatter:off)
```java
.matchFacet("status", "archived")
```
[//]: # (@formatter:on)

instead of:

[//]: # (@formatter:off)
```java
.match("archived", "status")
```
[//]: # (@formatter:on)

While both achieve the same result, `matchFacet(...)` is generally **faster and more efficient**.  
That's because facet values are indexed and matched entirely at the data store level,  
whereas `.match(...)` may involve resolving the path in memory and combining constraints manually.

> 💡 **Tip:** Use `@Facet` on frequently-filtered fields (e.g. `status`, `type`, `category`) to take full advantage
> of this optimization.

---

### Facet Statistics

When a field or getter is annotated with `@Facet`, you can also retrieve **facet statistics** — e.g., how many documents
exist per value of a given property. This is useful for building **filters with counts**, such as product categories,
user roles, or status indicators.

#### Example: Product Breakdown by Category and Brand

Suppose you have the following model:

```java

@Searchable
public record Product(@Facet String category,
                      @Facet String brand,
                      String name,
                      BigDecimal price) {
}
```

You can retrieve facet stats like this:

[//]: # (@formatter:off)
```java
List<FacetStats> stats = FluxCapacitor.search(Product.class)
        .lookAhead("wireless")
        .facetStats();
```
[//]: # (@formatter:on)

This gives you document counts per facet value:

[//]: # (@formatter:off)
```json
[
  { "name": "category", "value": "headphones", "count": 55 },
  { "name": "brand", "value": "Acme", "count": 45 },
  { "name": "brand", "value": "NoName", "count": 10 }
]
```
[//]: # (@formatter:on)

Each `FacetStats` object will contain:

- the facet name (e.g., `category`)
- the distinct values (e.g., `"electronics"`, `"clothing"`)
- the number of documents per value

---

### Search Index exclusion

By default, all non-transient properties of a document are included in the search index. However, you can fine-tune what
fields get indexed using the following annotations:

#### `@SearchExclude`

Use `@SearchExclude` to exclude a field or type from search indexing. This prevents the property from being matched in
search queries, though it will still be present in the stored document and accessible at runtime.

```java

public record Order(String id,
                    Customer customer,
                    @SearchExclude byte[] encryptedPayload) {
}
```

You can also exclude entire types:

```java

@SearchExclude
public record EncryptedData(byte[] value) {
}
```

In this case, none of the properties of `EncryptedData` will be indexed, unless you override selectively with
`@SearchInclude`.

---

#### `@SearchInclude`

Use `@SearchInclude` to **override** an exclusion. This is functionally equivalent to `@SearchExclude(false)` and is
typically used on a field or class that would otherwise be excluded by inheritance or parent-level settings.

```java

@SearchExclude
public record BaseDocument(String internalNotes,
                           @SearchInclude String publicSummary) {
}
```

Here, `internalNotes` will not be indexed, but `publicSummary` will be.

---

### Behavior Summary

| Annotation                                  | Effect                                                           |
|---------------------------------------------|------------------------------------------------------------------|
| `@SearchExclude`                            | Prevents property/type from being indexed for search             |
| `@SearchExclude(false)` or `@SearchInclude` | Explicitly includes a property even if a parent type is excluded |
| *No annotation*                             | Field is included in the search index by default                 |

> **Note:** If you want a field to be completely omitted from storage (not just indexing), mark it as `transient`, use
`@JsonIgnore`, or another serialization-related annotation.

---

### Rapid Sorting and Filtering

To enable efficient **range filters** and **sorted results** in document searches, annotate properties with `@Sortable`:

```java

public record Product(@Sortable BigDecimal price,
                      @Sortable("releaseDate") Instant publishedAt) {
}
```

This tells Flux Capacitor to **pre-index** these fields in a lexicographically sortable format. When you issue a search
with a `between()` constraint or `.sort(...)` clause, the Flux Platform can evaluate it **directly in the data store** —
without needing to load and compare documents in memory.

#### 🚀 Optimized Search Example

```java
List<Product> results = FluxCapacitor.search(Product.class)
        .between("price", 10, 100)
        .sort("releaseDate")
        .fetch(100);
```

This performs a fast, index-backed range query across all products priced between €10 and €100, sorted by their
`releaseDate`.

> ⚠️ You can always sort by any field — even if it's not `@Sortable` — but performance will degrade because the sorting
> happens **after** all matching documents are loaded.

#### ⚙️ What Gets Indexed?

Flux Capacitor normalizes and encodes sortable fields depending on their value type:

| Type           | Behavior                                                                 |
|----------------|--------------------------------------------------------------------------|
| Numbers        | Padded base-10 string (preserves order, supports negatives)              |
| Instants       | ISO-8601 timestamp format                                                |
| Strings/Others | Normalized (lowercased, trimmed, diacritics removed)                     |

This ensures that sorting is **consistent** and **correct** across types and locales.

#### 🧠 Nested and Composite Values

If the sortable field is:

- A **collection** → Max element is indexed. Create a getter if you need sorting on the min element
- A **map** → Values are indexed using `key/propertyName` path
- A **nested object** annotated with `@Sortable` → Its `toString()` is used
- A **POJO with `@Sortable` fields** → Those nested values are indexed with prefixed paths

#### ⚠️ Important Notes

- **No retroactive indexing**: Adding `@Sortable` to a field does **not** automatically reindex existing documents.
- To apply sorting retroactively, trigger a reindex (e.g. with `@HandleDocument` and a bumped `@Revision`)
- Sorting and filtering still happen **within the Flux Platform**, but *without* `@Sortable` the logic falls back
  to **in-memory evaluation** — which is much slower.

> ✅ Use `@Sortable` together with `@Facet` if you want both sorting and aggregation/filtering on a field.

---

### Customizing Returned Fields

When performing a search, you can control which fields are included or excluded in the returned documents.

This is useful for:

- Hiding sensitive fields (e.g. private data, tokens)
- Reducing payload size
- Optimizing performance when only partial data is needed

#### Example

Given the following indexed document:

```json
{
  "id": "user123",
  "profile": {
    "name": "Alice",
    "email": "alice@example.com",
    "ssn": "123456789"
  },
  "roles": [
    "user",
    "admin"
  ]
}
```

You can exclude sensitive fields like so:

[//]: # (@formatter:off)
```java
FluxCapacitor.search("users")
     .exclude("profile.ssn")
     .fetch(50);
```
[//]: # (@formatter:on)

This will return:

```json
{
  "id": "user123",
  "profile": {
    "name": "Alice",
    "email": "alice@example.com"
  },
  "roles": [
    "user",
    "admin"
  ]
}
```

> You can also use `.includeOnly(...)` instead to explicitly whitelist fields.
---

### Streaming Results

Flux supports efficient streaming of large result sets:

[//]: # (@formatter:off)
```java
FluxCapacitor.search("auditTrail")
    .inLast(Duration.ofDays(7))
    .stream().forEach(auditEvent -> process(auditEvent));
```
[//]: # (@formatter:on)

---

### Deleting Documents

To remove documents from the index:

[//]: # (@formatter:off)
```java
FluxCapacitor.search("expiredTokens")
    .before(Instant.now())
    .delete();
```
[//]: # (@formatter:on)

---

### Summary

- Use `FluxCapacitor.index(...)` to manually index documents.
- Use `@Searchable` to configure the collection name or time range for an object.
- Use `@Aggregate(searchable = true)` or `@Stateful` for automatic indexing.
- Use `FluxCapacitor.search(...)` to query, stream, sort, and aggregate your documents.