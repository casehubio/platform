# Decisions — StepParameterType / ParameterType Convergence (#439)

## D1: Keep separate enums, unify via ValueType delegation

**Choice:** StepParameterType and ParameterType remain separate enums. Both delegate scalar operations to ValueType via `scalarType()` (enum → ValueType) and `fromValueType()` (ValueType → enum) bridges. Scalar parsing, name resolution, and runtime validation are defined once on ValueType.

**Alternatives:**
- Merge into a single unified enum — pollutes module parameters with OBJECT/ARRAY (unusable in that context) or restricts step parameters from LIST (different semantics: comma-split string vs structured JSON array)
- Keep separate with no shared base — duplicates parsing logic, name aliases (DECIMAL→NUMBER), and runtime validation rules across three independent implementations

**Rationale:** The three enums serve genuinely different domains:
- **ValueType** (`io.casehub.yaml.core.type`): scalar foundation — STRING, INTEGER, NUMBER, BOOLEAN. Owns parse, validate, Java type mapping, name resolution.
- **ParameterType** (`io.casehub.yaml.core.module`): module parameters — ValueType + LIST. String-sourced. ParsedValue sealed type, canAccept() compatibility.
- **StepParameterType** (`io.casehub.yaml.core.step`): step catalog — ValueType + ARRAY + OBJECT. Runtime-sourced. JSON Schema-aligned validation.

LIST vs ARRAY is the key semantic gap that prevents merging: LIST is "comma-separated string → List\<String\>" (a string encoding convention); ARRAY is "structured JSON array, already a List\<?> at runtime." These are different concepts that happen to share a collection result type.

**What converged:**
- `StepParameterType.parseScalar()` → delegates to `ValueType.parse()` (was line-for-line duplicate)
- `StepParameterType.validate()` → delegates to `ValueType.validate()` for scalars
- `StepParameterType.isScalar()` → derives from `scalarType() != null` (was independent check)
- `StepParameterType.fromString()` → delegates scalar resolution to `ValueType.fromString()`
- `ParameterType.fromString()` → delegates scalar resolution to `ValueType.fromString()`
- DECIMAL alias defined once in `ValueType.fromString()`

**What stayed separate (correctly):**
- `ParameterType.parse()` → returns ParsedValue (sealed type wrapping), domain-specific
- `ParameterType.canAccept()` → module-specific structural compatibility
- `StepParameterType.validate()` for ARRAY/OBJECT → collection-type instanceof checks
- `ValueType.javaTypes()`/`accepts()` → compile-time Java source type matching (different semantics from runtime validate)

**Trade-offs:** Composition over merger means a 4-line `fromValueType()` switch on each domain type. This is the overhead of making the relationship explicit rather than implicit.

**Sources:** issue-429 D1 (ValueType extracted from CsvColumnType), issue-260 D1 (canAccept on ParameterType), issue-433 (StepParameterType created for step catalog)
**Exploration:** deep-analysis
**Status:** captured
