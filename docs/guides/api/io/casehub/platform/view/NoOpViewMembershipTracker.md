# io.casehub.platform.view.NoOpViewMembershipTracker

**Package:** `io.casehub.platform.view`

**Kind:** `class`

## Constructors

### `public NoOpViewMembershipTracker()`

## Methods

### `public java.util.Map<java.util.UUID,java.util.Map<java.util.UUID,java.lang.String>> getLastKnownMembership(java.util.Set<java.util.UUID> subjectIds)`

#### Parameters

- `subjectIds` (`java.util.Set<java.util.UUID>`)

### `public java.util.Map<java.util.UUID,java.lang.String> getLastKnownMembership(java.util.UUID subjectId)`

#### Parameters

- `subjectId` (`java.util.UUID`)

### `public java.util.Set<java.util.UUID> getSubjectsByView(java.util.UUID viewId)`

#### Parameters

- `viewId` (`java.util.UUID`)

### `public void removeMembership(java.util.UUID subjectId)`

#### Parameters

- `subjectId` (`java.util.UUID`)

### `public void removeMembershipByView(java.util.UUID viewId)`

#### Parameters

- `viewId` (`java.util.UUID`)

### `public void updateMembership(java.util.UUID subjectId, java.util.Map<java.util.UUID,java.lang.String> viewIdToName)`

#### Parameters

- `subjectId` (`java.util.UUID`)
- `viewIdToName` (`java.util.Map<java.util.UUID,java.lang.String>`)
