# io.casehub.platform.datasource.DataSourceRouter

**Package:** `io.casehub.platform.datasource`

**Kind:** `class`

CDI bridge routing `@ObservesAsync CloudEvent` events to registered DataSources.

<p>Startup: `@Observes StartupEvent` replays queued events
(`DataSourceRegistered`, `DataSourceDeregistered`, and
`DataSourceUpdated`) in order, then sets `started = true`.

<p>Runtime: `@ObservesAsync DataSourceRegistered` wires new DataSources using
convergent logic — resolves the current DataSource from the registry, replaces stale
entries, and skips if the DataSource was already deregistered. `@ObservesAsync
DataSourceDeregistered` unwires routes using identity comparison against the deregistered
DataSource instance, preventing removal of replacement entries.

<p>Both handlers are convergent — they produce the correct wired state regardless of
CDI event processing order.

<p>Routing logic:
<ol>
  <li>Extract `tenancyid` extension from CloudEvent</li>
  <li>For each wired DataSource: check tenancy match (tenant-specific OR platform-global)</li>
  <li>Check `DataSourceDescriptor.acceptedEventTypes()` pre-filter (if non-empty)</li>
  <li>Call `DataSource.add(Object)` — alpha network propagates to subscribers</li>
</ol>

## Fields

### `LOG` (`Logger`)

### `pendingEvents` (`java.util.List<java.lang.Object>`)

### `registry` (`DataSourceRegistry`)

### `started` (`java.util.concurrent.atomic.AtomicBoolean`)

### `wiredDataSources` (`java.util.List<io.casehub.platform.datasource.DataSourceRouter.WiredDataSource>`)

## Constructors

### `public DataSourceRouter(DataSourceRegistry registry)`

#### Parameters

- `registry` (`DataSourceRegistry`)

## Methods

### `private void applyUpdate(DataSourceUpdated event)`

#### Parameters

- `event` (`DataSourceUpdated`)

### `public void onCloudEvent(CloudEvent cloudEvent)`

#### Parameters

- `cloudEvent` (`CloudEvent`)

### `public void onDataSourceDeregistered(DataSourceDeregistered event)`

#### Parameters

- `event` (`DataSourceDeregistered`)

### `public void onDataSourceRegistered(DataSourceRegistered event)`

#### Parameters

- `event` (`DataSourceRegistered`)

### `public void onDataSourceUpdated(DataSourceUpdated event)`

#### Parameters

- `event` (`DataSourceUpdated`)

### `public void onStartup(StartupEvent ev)`

#### Parameters

- `ev` (`StartupEvent`)

### `private void unwireRoute(DataSourceDeregistered event)`

#### Parameters

- `event` (`DataSourceDeregistered`)

### `private void wireRoute(DataSourceRegistered event)`

#### Parameters

- `event` (`DataSourceRegistered`)
