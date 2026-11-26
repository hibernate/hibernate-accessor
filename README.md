# Hibernate Accessor

## What is it?

A library that provides efficient field/property access and instance creation for main Hibernate projects.

It provides a single API (`AccessorFactory`) with pluggable strategies for reading
and writing object state and for instantiating objects via constructors.

Requires Java 17 or later.

## Getting started

Add the core module to your project:

```xml
<dependency>
    <groupId>org.hibernate.accessor</groupId>
    <artifactId>hibernate-accessor</artifactId>
    <version>0.1.0</version>
</dependency>
```

The core module includes the reflection, lambda, method handle, and ClassFile API strategies.
The ClassFile factory generates bytecode at runtime. On Java 24 or later it uses the JDK's
`java.lang.classfile` API. On Java 17 through 23, add the SmallRye backport as a runtime dependency.

```xml
<dependency>
    <groupId>io.smallrye.classfile</groupId>
    <artifactId>jdk-classfile-backport</artifactId>
    <version>27</version>
    <scope>runtime</scope>
</dependency>
```

The ASM and ByteBuddy strategies are available through their own modules, which each bring in the core.

```xml
<!-- ASM-based strategy -->
<dependency>
    <groupId>org.hibernate.accessor</groupId>
    <artifactId>hibernate-accessor-asm</artifactId>
    <version>0.1.0</version>
</dependency>

<!-- ByteBuddy-based strategy -->
<dependency>
    <groupId>org.hibernate.accessor</groupId>
    <artifactId>hibernate-accessor-bytebuddy</artifactId>
    <version>0.1.0</version>
</dependency>
```

## Usage

### Obtaining a factory

Built-in strategies are available via static factory methods:

```java
// Reflection-based (simplest, no extra setup)
AccessorFactory factory = AccessorFactory.reflection();

// Method-handle-based (mostly better performance, requires a MethodHandles.Lookup)
AccessorFactory factory = AccessorFactory.methodHandle(MethodHandles.lookup());

// Lambda-based (better performance, requires a MethodHandles.Lookup)
AccessorFactory factory = AccessorFactory.lambda(MethodHandles.lookup());

// ClassFile API-based (generates bytecode at runtime)
AccessorFactory factory = AccessorFactory.classFile(MethodHandles.lookup());

// ASM-based (generates bytecode at runtime using ASM)
AccessorFactory factory = AsmAccessorFactory.factory(MethodHandles.lookup());

// ByteBuddy-based (same approach as ASM, using ByteBuddy's shaded ASM)
AccessorFactory factory = ByteBuddyAccessorFactory.factory(MethodHandles.lookup());
```

### Reading and writing fields

```java
Field nameField = Person.class.getDeclaredField("name");

ValueReader<?> reader = factory.valueReader(nameField);
ValueWriter writer = factory.valueWriter(nameField);

Person person = new Person();
writer.set(person, "Alice");

Object name = reader.get(person);  // "Alice"
```

### Reading and writing via getter/setter methods

```java
Method getter = Person.class.getDeclaredMethod("getName");
Method setter = Person.class.getDeclaredMethod("setName", String.class);

ValueReader<?> reader = factory.valueReader(getter);
ValueWriter writer = factory.valueWriter(setter);

writer.set(person, "Bob");
Object name = reader.get(person);  // "Bob"
```

### Bulk (multi-value) access

Read or write several members in a single call. Each member may be a `Field` or a getter/setter
`Method`, and values are read/written in the order the members are specified. The first argument is
the concrete class whose instances will be accessed; each member must be declared on that class or
one of its supertypes.

```java
Field name = Person.class.getDeclaredField("name");
Field age = Person.class.getDeclaredField("age");

MultiValueReader reader = factory.multiValueReader(Person.class, name, age);
MultiValueWriter writer = factory.multiValueWriter(Person.class, name, age);

writer.set(person, new Object[] { "Alice", 30 });

Object[] values = reader.get(person);  // { "Alice", 30 }
```

The ClassFile, ASM, and ByteBuddy factories use the same multi-value access behavior for both
[generation strategies](#strategies). When all members are declared on the supplied class, the
generated reader or writer accesses them directly. For inherited members, the generated reader or
writer dispatches through shared accessors for the declaring classes.

### Instantiating objects

```java
Constructor<Person> ctor = Person.class.getDeclaredConstructor();
Instantiator<Person> instantiator = factory.instantiator(ctor);

Person person = instantiator.create();
```

For constructors that take arguments:

```java
Constructor<Person> ctor = Person.class.getDeclaredConstructor(String.class, int.class);
Instantiator<Person> instantiator = factory.instantiator(ctor);

Person person = instantiator.create("Alice", 30);
```

## Configuration

Every factory method also accepts an `AccessorConfiguration`, which carries the
`MethodHandles.Lookup` along with additional properties:

```java
AccessorConfiguration configuration = new AccessorConfiguration(
        MethodHandles.lookup(),
        Map.of(AccessorConfiguration.DUMP_BYTECODE_DIR, "build/generated-accessors")
);

AccessorFactory factory = AsmAccessorFactory.factory(configuration);
```

Supported properties:

| Property | Constant | Purpose |
|---|---|---|
| `hibernate.accessor.lookup` | `AccessorConfiguration.LOOKUP` | The `MethodHandles.Lookup` used for access control. |
| `hibernate.accessor.access.context` | `AccessorConfiguration.ACCESS_CONTEXT` | Framework-owned access operations; its lookup takes precedence over `LOOKUP`. |
| `hibernate.accessor.bytecode.dump.dir` | `AccessorConfiguration.DUMP_BYTECODE_DIR` | Directory for dumping bytecode generated by the ClassFile, ASM, and ByteBuddy strategies. |

### Framework access context

Frameworks can supply an `AccessContext` using the constructor:

```java
AccessorConfiguration configuration = new AccessorConfiguration(new FrameworkAccess(), properties);
AccessorFactory factory = AccessorFactory.methodHandle(configuration);
// Also supported: AccessorFactory.reflection(configuration).
```

Implement the context **in the framework module**, so caller-sensitive JDK
operations execute with that module's identity:

```java
final class FrameworkAccess implements AccessContext {
    private final MethodHandles.Lookup lookup = MethodHandles.lookup();

    public MethodHandles.Lookup lookup() {
        return lookup;
    }

    public void ensureReads(Module target) {
        lookup.lookupClass().getModule().addReads(target);
    }

    public void makeAccessible(AccessibleObject member) {
        member.setAccessible(true);
    }
}
```

The context also governs reflection fallbacks. Entity packages need only be
opened to the framework module. Without a context, lookup-based factory calls
retain the existing `LookupAccess` implementation for JVM use.

## Strategies

| Strategy | Factory method | Mechanism | Notes |
|---|---|---|---|
| Reflection | `AccessorFactory.reflection()` | `java.lang.reflect` | Simplest. Shared singleton instance. |
| Lambda | `AccessorFactory.lambda(lookup)` | `LambdaMetafactory` | Better throughput after warm-up. Requires a `MethodHandles.Lookup` with appropriate access. |
| Method handle | `AccessorFactory.methodHandle(lookup)` | `java.lang.invoke.MethodHandle` | Better throughput than reflection. Requires a `MethodHandles.Lookup` with appropriate access. |
| ClassFile API | `AccessorFactory.classFile(lookup)` | ClassFile API bytecode generation | Included in the core module. Uses the SmallRye backport on Java 17–23. |
| ASM | `AsmAccessorFactory.factory(lookup)` | ASM bytecode generation | Requires `hibernate-accessor-asm`, which brings in `org.ow2.asm:asm`. |
| ByteBuddy | `ByteBuddyAccessorFactory.factory(lookup)` | ByteBuddy's shaded ASM | Requires `hibernate-accessor-bytebuddy`. |

The ClassFile, ASM, and ByteBuddy factories all accept a `MethodHandles.Lookup` and support
the same two runtime bytecode generation strategies.

### `BULK_SWITCH` (default)

Generates a shared accessor class per entity. Single-value readers, writers, and instantiators
delegate to it using field, method, or constructor indexes for `TABLESWITCH` dispatch.

### `PER_MEMBER`

Generates a dedicated class for each single-value reader or writer that accesses the field or
invokes the getter or setter directly. Constructor instantiators use reflection in this mode.

### Selecting a generation strategy

Set `hibernate.accessor.generation.strategy` in the `AccessorConfiguration` property map to
`BULK_SWITCH` or `PER_MEMBER`. Pass the configuration to the factory you use.

```java
AccessorConfiguration configuration = new AccessorConfiguration(
        MethodHandles.lookup(),
        Map.of("hibernate.accessor.generation.strategy", "PER_MEMBER")
);

AccessorFactory classFileFactory = AccessorFactory.classFile(configuration);
AccessorFactory asmFactory = AsmAccessorFactory.factory(configuration);
AccessorFactory byteBuddyFactory = ByteBuddyAccessorFactory.factory(configuration);
```

Each factory also has a typed configuration and a matching enum. The configuration classes
expose the property key as `GENERATION_STRATEGY`.

| Factory | Configuration | Enum |
|---|---|---|
| ClassFile API | `ClassFileAccessorConfiguration` | `ClassFileGenerationStrategy` |
| ASM | `AsmAccessorConfiguration` | `AsmGenerationStrategy` |
| ByteBuddy | `ByteBuddyAccessorConfiguration` | `ByteBuddyGenerationStrategy` |

## Ahead-of-time (AOT) generation

`hibernate-accessor-asm` exposes `org.hibernate.accessor.asm.aot.AsmAotGenerator`.
`hibernate-accessor-bytebuddy` exposes the equivalent `ByteBuddyAotGenerator` in
`org.hibernate.accessor.bytebuddy.aot`, generated from the same sources using shaded ASM.
These are build-time APIs, distinct from factories that generate accessors for already-loaded classes.
The ClassFile factory supports runtime generation only.

Chunk sizes can be configured at generation time with `new AsmAotGenerator(names, chunks)`
(or the equivalent Byte Buddy constructor). `AotChunkSizes` has independent `typeRead`,
`typeWrite`, `typeConstructor`, `memberRead`, `memberWrite`, `memberConstructor`, `typeLookup`,
and `memberLookup` components. The existing constructor uses `AotChunkSizes.DEFAULT`: 256
for integer dispatch and 500 for string lookups. All sizes must be positive. Member constructor
size is a conversion budget divided by the largest constructor arity (minimum 1), preserving
argument-aware splitting. Large sizes may exceed JVM bytecode limits for a given model.
Use the same generator configuration for host emission and generated implementations.

See [AOT dispatch benchmarks](performance/benchmark-aot/README.md) for fixed-workload chunk
sweeps, configurable entity/field counts, and HotSpot inlining diagnostics.

The caller supplies descriptor-based host/member metadata and `GenerationNames` (binary names of
four output classes). `generateClasses(hosts, withFallback)` returns a
map of binary class names to bytes: public bridges for non-public hosts, shared reader/writer/
instantiator implementations, and an `AccessorFactory` implementation. Individual `generateBridge`, `generateReader`, `generateWriter`, `generateInstantiator` and
`generateFactory` methods are also available for integrations that publish these pieces separately.
The host's position in
`hosts` is its class index in the factory and shared implementations only.
`emitHostMethods(visitor, host, readers, writers, constructors)`
adds lookup and dispatch methods before the downstream visitor's `visitEnd`; use a writer that
computes stack frames and maxima. Member indexes use a consistent sorted order. Accessors dispatch
first by host index, then by member index inside the transformed host, allowing private member access.

When using the low-level generator, the caller owns member discovery, final-field filtering, enhancement ordering, class definition,
module permissions and lifecycle. Resolve registrations against the final transformed bytecode before
emitting host methods. Do not supply writers for fields that remain final. Keep bridges in their host's
runtime package and classloader. Injected public methods expose registered private members; this API
is for trusted framework integration and does not establish module reads or exports.

Host lookup methods use an `Index` suffix (for example, `$__hibernateAccessor_fieldReaderIndex`)
and return an integer member index, or `-1` for an unknown key; zero is valid.
The generated factory validates declaring-class identity, calls the lookup and directly constructs
one of the three shared implementations with its application class index and the returned member index.
Hosts reference neither generated implementation classes nor runtime creation hooks. This lets independent
application classloaders use the same parent-loaded host, even when regenerating the application changes
its class indexes. Identical host bytes, member registrations and generator settings produce identical
transformed host bytes regardless of unrelated hosts being added, removed or reordered. Bridges forward
these integer lookups and must still be defined alongside their hosts.

GenerationNames specifies four generated class names. hostVisitor and emitHostMethods generate host 
lookup methods without requiring a host index. Injected lookup methods use an Index suffix and return
a member index via a (String)int descriptor. Generated factories use that index to construct accessors.
Hosts, bridges, implementations, and factories must be generated together. The same contract applies to
the Byte Buddy AOT API.

Initialize the generated factory with `create()`, or `create(AccessorFactory fallback)` when fallback
generation is enabled. Both are first-call-wins: the factory is a singleton initialized under double-checked
locking, so only the first call builds it and honours the `fallback` it was given. Later calls return that
same instance and silently discard their own `fallback` argument, and there is no way to replace it afterwards.
Install the intended fallback on the first call, before anything else can request the factory.

Generated code depends on the JDK, the core `hibernate-accessor` artifact (including its validation
SPI) and registered application classes. It has no dependency on ASM, Byte Buddy, or either generator
artifact: those dependencies can be removed after augmentation. Generation does not define classes or
resolve application classes through reflection.

Single-member writers and constructors support the library's primitive widening rules; narrowing
is rejected and constructor argument counts are checked. Both multi-value methods deliberately throw
`MultiValueAccessorGenerationException`, even when a fallback factory is supplied. Bulk AOT access
is deferred to a separate design.

The directory processors (`AsmAotProcessor` and `ByteBuddyAotProcessor`) live in the
non-published `testing/hibernate-accessor-aot-support` module. They are internal build/test
infrastructure, not part of the published accessor APIs. They copy an unpacked class directory,
discover members, transform selected hosts, and emit factories. Both AOT runners use this support
module; a future build-tool plugin can inform a separately designed tooling API.

AOT tests live in `testing/hibernate-accessor-tck-asm-aot` and
`testing/hibernate-accessor-tck-bytebuddy-aot`. Each runner's `unpackTck` task extracts the shared
TCK JAR, and `prepareTck` invokes the processor before test compilation. Only the prepared output
is on the test classpath: the original TCK JAR is a build input, never a runtime dependency.
JUnit discovers tests directly from the prepared directory, using a normal service provider for
the generated factory. Tests requiring bulk access carry `@Tag("multi-accessor")`; tests requiring
runtime class definition or classloader lifecycles carry `@Tag("runtime-class-definition")`.
The AOT runners exclude both tags. New untagged TCK tests run automatically.

The dedicated modules also test string-hash collisions, large dispatch tables and a standalone
JVM with generator dependencies absent. Run both backends with:

```shell
./gradlew :hibernate-accessor-tck-asm-aot:check :hibernate-accessor-tck-bytebuddy-aot:check
```

## Further reading

- [The quest for reflection-free access (Part 1)](https://in.relation.to/2026/10/22/quest-for-reflection-free-access/)
  explains why Hibernate Accessor was created and why existing JDK APIs did not meet its requirements.
- [A peek behind the curtain of modern runtime codegen (Part 2)](https://in.relation.to/2026/10/28/peek-behind-the-curtain-of-accessor-codegen/)
  covers the runtime bytecode-generation approach used by several Hibernate Accessor strategies.
