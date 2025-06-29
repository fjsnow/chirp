## 🐦 chirp

A simple annotation-driven Redis Pub/Sub packet system powered by reflection.

Engineered for high-performance inter-service communication in Java applications, particularly well-suited for distributed Minecraft networks over multiple individual instances and/or proxies.

#### About Chirp's performance

Chirp is designed for speed. During initialization, Chirp indexes and builds a comprehensive schema (a tree-like structure) for all registered packets and listeners. This pre-computation minimizes the need for costly runtime reflection when serializing and deserializing data - Allowing the ease of use of annotations while maintaining high throughput and low latency. We also validate any issues with your packets, listeners, and converters at startup, and later we assert these assumptions at runtime, minimising unnecessary overhead.

### Installation

You can install Chirp via Jitpack in your favourite package manager below. The latest version is `2.0.0`.

<details>

<summary><strong>Maven</strong></summary>

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>io.fjsn</groupId>
    <artifactId>chirp</artifactId>
    <version>2.0.0</version>
</dependency>
```

<details>
<summary>Optional: Shade Chirp to avoid conflicts</summary>

If you're bundling Chirp into your own library or application, you can use the Maven Shade Plugin to relocate its packages and prevent classpath conflicts:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-shade-plugin</artifactId>
            <version>3.5.2</version>
            <executions>
                <execution>
                    <phase>package</phase>
                    <goals>
                        <goal>shade</goal>
                    </goals>
                    <configuration>
                        <relocations>
                            <relocation>
                                <pattern>io.fjsn.chirp</pattern>
                                <shadedPattern>com.yourdomain.shaded.chirp</shadedPattern>
                            </relocation>
                        </relocations>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```
</details>
</details>

<details>
<summary><strong>Gradle (Groovy DSL)</strong></summary>

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'io.fjsn:chirp:2.0.0'
}
```

<details>
<summary>Optional: Shade Chirp to avoid conflicts</summary>

Requires the [Shadow plugin](https://imperceptiblethoughts.com/shadow/):

```groovy
shadowJar {
    relocate 'io.fjsn.chirp', 'com.yourdomain.shaded.chirp'
}
```

</details>
</details>

<details>
<summary><strong>Gradle (Kotlin DSL)</strong></summary>

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("io.fjsn:chirp:2.0.0")
}
```

<details>
<summary>Optional: Shade Chirp to avoid conflicts</summary>

Requires the [Shadow plugin](https://imperceptiblethoughts.com/shadow/):

```kotlin
tasks.named<ShadowJar>("shadowJar") {
    relocate("io.fjsn.chirp", "com.yourdomain.shaded.chirp")
}
```

</details>
</details>


### Quick start

#### Setting Up Chirp

To get started with Chirp, create an instance using the builder pattern. This is typically done in your application's bootstrap phase, such as:

-   `main()` in a standard Java application
-   `onEnable()` in a Spigot plugin

Here’s how to configure it step by step:

1.  **Create the builder with `Chirp.builder()`**

2.  **Set the `channel`**
    This is a **required** string used to prevent multiple instances of Chirp from interferring with each other across different applications.
    -   Do this with: `.channel("your-application-name")`

3.  **Set the `origin` (optional but recommended)**
    A unique ID (e.g., `"service-1"` or `"lobby-1"`) that identifies your specific service instance. Useful when filtering or tracking sources.
    -   Do this with: `.origin("lobby-1")`

4.  **Register packets, listeners, and converters**
    You can do this manually or automatically (see "Scanner" section below for automatic registration):
    -   Manually:
        -   `.packet(YourPacket.class)`
        -   `.listener(new YourPacketListener())`
        -   `.converter(YourType.class, new YourTypeConverter())`
        -   For multiple registrations, optionally use: `.packets(YourPacket1.class, YourPacket2.class)`, `.listeners(new YourListener1(), new YourListener2())`, and `.converters(Map.of(TypeA.class, new ConverterA(), TypeB.class, new ConverterB()))`.

5.  **Configure Redis connection**
    Provide your Redis host and port:
    -   Authless: `.redis("localhost", 6379)`
    -   Or with password: `.redis("localhost", 6379, "yourPassword")`

6.  **Optionally, enable `debug` mode**
    This will log out details related to packets, listeners and converters getting registered, outgoing and incoming packets, and timing information.
    -   `.debug(true)`

7.  **Finish setup with `.build()`**

##### Example

```java
Chirp chirp = Chirp.builder()
    .channel("announcements")  // required: channel
    .origin("service-1") // optional: this instance's id
    .scan("io.fjsn.plugin") // optional: scan automatically to register packets, listeners, and converters
    .redis("localhost", 6379) // required: redis connection info
    .build(); // builds and connects chirp to redis!
```

Now you're ready to start sending and receiving packets!


#### Creating a packet

To create a packet, simply create a class and annotate it with `@ChirpPacket`. Any field you want to be transferred must be annotated with `@ChirpField`.
Notably, you must also provide a no-args constructor, and fields must be non-final.

##### Example

```java
@ChirpPacket
public class ExamplePacket {

    @ChirpField private String random;
    public String getRandom() { return random; }

    public ExamplePacket() {}
    public ExamplePacket(String random) {
        this.random = random;
    }
}
```

If you have scanning enabled and this class lives within the package, Chirp will automatically register this packet on load. Otherwise, register it manually using `.packet(ExamplePacket.class)` on your `ChirpBuilder`.

#### Sending a packet

To send a packet, simply create an instance of your packet and publish it via `Chirp#publish`. If you want to broadcast it to a specific service/origin, provide its ID as an additional argument.

##### Example

```java
ExamplePacket packet = new ExamplePacket("random string :P");
chirp.publish(packet); // will send it to every service on the channel
chirp.publish(packet, "service-2"); // will send it only to 'service-2' on the channel
```

> [!NOTE]
> When you publish a packet, the originating service will also receive its own message via Redis Pub/Sub. By default, Chirp handlers on the broadcasting service will ignore this self-sent packet. To allow the originating service to process its own published packets, pass `true` as an additional `self` argument in `Chirp#publish`.

#### Listening to packets

To listen and handle incoming packets, create and annotate a class with `@ChirpListener`, then annotate handler method(s) with `@ChirpHandler`.

Each handler method expects one argument of type `ChirpPacketEvent<T>`, a wrapper around a generic packet with attached metadata.

`ChirpPacketEvent` contains important information regarding your event, notably:
- `ChirpPacketEvent#getPacket` - Extracts the packet (type `T`)
- `ChirpPacketEvent#getOrigin` - Extract the service where the packet originated from
- `ChirpPacketEvent#getSent` - Gets the timestamp in milliseconds at which the packet was sent
- `ChirpPacketEvent#getReceived` - Gets the timestamp in milliseconds at which the packet was received
- `ChirpPacketEvent#getLatency` - Gets the latency of how long it took for the packet to be received

##### Example

```java
@ChirpListener
public class ExamplePacketListener {

    @ChirpHandler
    public void onExamplePacket(ChirpPacketEvent<ExamplePacket> event) {
        ExamplePacket packet = event.getPacket();
        System.out.println("The random text was " + packet.getRandom());
        System.out.println("It took " + event.getLatency() + "ms for us to receive it!");
    }

}
```

If you have scanning enabled and this class lives within the package, Chirp will automatically register this listener on load. Otherwise, register it manually using `.listener(new ExamplePacketListener())` on your `ChirpBuilder`.

> [!WARNING]
> If you are using Chirp within a framework like Spigot for Minecraft servers, be aware that the packet handlers do not run on the main thread. If you need to perform actions that require the main thread (like interacting with Bukkit APIs), you will need to schedule those actions using `Bukkit#getScheduler().runTask(...)` or similar methods.

#### Converters

During transfer, Chirp needs to serialize your packet to a JSON structure then later deserialize it.

Out the box, Chirp comes with a range of converters for Java's primitive types (and their boxed equivalents), `String`, `UUID`, and `Optional<T>`, and some common collections like `List<T>`, `Set<T>`, and `Map<K, V>` (for Maps, `K`'s register must convert it to a `String`). It also supports nested objects.

##### Custom Converters

To create a custom converter, create and annotate a class with `@ChirpConverter` and implement `FieldConverter<T>` on it.

You will simply need to provide a `serialize` and `deserialize` method, converting to and from `T` and `JsonElement` respectively.

###### Example

```java
@ChirpConverter
public class IntegerConverter implements FieldConverter<Integer> {
    @Override
    public JsonElement serialize(Integer value, Type type, ChirpRegistry registry) { // Convert Integer to JsonElement
        if (value == null) {
            return null;
        }
        return new JsonPrimitive(value);
    }

    @Override
    public Integer deserialize(JsonElement json, Type type, ChirpRegistry registry) { // Convert JsonElement to Integer
        if (json == null || json.isJsonNull()) {
            return null;
        }
        if (!json.isJsonPrimitive() || !json.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(
                    "Expected JSON number primitive for Integer, got: "
                            + json.getClass().getSimpleName());
        }
        return json.getAsInt();
    }
}
```

For more complex types concerning generics, see an example [here](https://github.com/fjsnow/chirp/blob/2/src/main/java/io/fjsn/chirp/converter/impl/MapConverter.java).

If you have scanning enabled and this class lives within the package, Chirp will automatically register this converter on load. Otherwise, register it manually using `.converter(Integer.class, new IntegerConverter())` on your `ChirpBuilder`.

### Callbacks

Redis Pub/Sub is inherently fire-and-forget, meaning it doesn't support request-response or callbacks natively. However, Chirp extends this model by offering support for automatic callbacks via the `ChirpCallback<T>` class.

To use it, pass a `ChirpCallback<T>` in `Chirp#publish`, where `T` is your response packet, when publishing a packet. Chirp will automatically handle the routing, waiting, and response delivery for you.

Chirp provides two ways to define callbacks: `ofSingle` for when you expect a single response, and `ofMultiple` for when you want to collect multiple responses.

##### `ChirpCallback.ofSingle(expectedResponseClass, onResponse, [onTimeout], [ttl])`

For when you expect only one response.
-   `expectedResponseClass`: The class of the packet type you expect as a response.
-   `onResponse`: A `Consumer<ChirpPacketEvent<T>>` to handle the single response.
-   `onTimeout` (Optional): A `Runnable` to execute if no response is received within the TTL. If omitted, nothing will happen on timeout.
-   `ttl` (Optional): The time in milliseconds to wait for a response before timing out. Defaults to 200ms.

##### Example: Single Response Callback

```java
MessagePacket packet = new MessagePacket(receiverName, sender.getName(), message);
plugin.getChirp()
        .publish(
                packet,
                true,
                ChirpCallback.ofSingle(
                        MessageResponsePacket.class, // Expected response type
                        responseEvent -> { // Consumer for the single response
                            sender.sendMessage(
                                    mm.deserialize(
                                            "<gray>(You -> "
                                                    + receiverName
                                                    + ") <white>"
                                                    + message));
                        },
                        () -> { // Optional: On timeout
                            sender.sendMessage(
                                    mm.deserialize("<red>Failed to send message."));
                        },
                        200L // Optional: TTL in milliseconds
                ));
```

When using `ChirpCallback.ofSingle`, if multiple services respond to the initial event, the broadcasting service will only pick up and process the *first* response it detects.

##### `ChirpCallback.ofMultiple(expectedResponseClass, onResponseMultiple, [ttl], [maxResponses])`

For when you want to collect multiple responses.
-   `expectedResponseClass`: The class of the packet type you expect as a response.
-   `onResponseMultiple`: A `Consumer<List<ChirpPacketEvent<T>>>` to handle the collected responses. This will be invoked when the TTL expires or `maxResponses` is reached. If no responses are collected, the list will be empty.
-   `ttl` (Optional): The time in milliseconds to wait for responses before the callback finishes collecting. Defaults to 200ms.
-   `maxResponses` (Optional): The maximum number of responses to collect. Once this limit is reached, the `onResponseMultiple` consumer is invoked. Defaults to `Integer.MAX_VALUE`.


##### Example: Multiple Responses Callback

```java
// Assuming you want to get responses from all online services about a player's status
PlayerStatusRequestPacket request = new PlayerStatusRequestPacket(player.getUniqueId());

plugin.getChirp()
        .publish(
                request,
                ChirpCallback.ofMultiple(
                        PlayerStatusResponsePacket.class, // Expected response type
                        responses -> { // Consumer for a list of responses
                            if (responses.isEmpty()) {
                                System.out.println("No services responded with player status.");
                                return;
                            }
                            System.out.println("Received " + responses.size() + " status updates:");
                            for (ChirpPacketEvent<PlayerStatusResponsePacket> event : responses) {
                                PlayerStatusResponsePacket response = event.getPacket();
                                System.out.println("- Service: " + event.getOrigin() + ", Status: " + response.getStatus());
                            }
                        },
                        1000L, // Optional: TTL in milliseconds
                        5 // Optional: Max responses to collect
                ));
```

When using `ChirpCallback.ofMultiple`, the broadcasting service will collect all responses from all services that respond to the initial event, up to the specified `maxResponses` or until the `ttl` expires. The collected responses are then passed to the `onResponseMultiple` consumer. If no responses are received before the TTL expires, the consumer will be invoked with an empty list.


### Scanner

The `ChirpBuilder#scan(String packageName)` method provides a convenient way to automatically register packets, listeners, and converters by scanning your defined package for `@ChirpPacket`, `@ChirpListener`, and `@ChirpConverter` annotations. This eliminates the need for manual registration for each class.

#### Example

```java
Chirp chirp = Chirp.builder()
    .channel("announcements")
    .origin("service-1")
    .scan("io.fjsn.plugin") // Automatically scans and registers all packets, listeners, and converters in this package
    .redis("localhost", 6379)
    .build();
```

If you wish to enable scanning but exclude specific packets, listeners, or converters (for example, if they require specific constructor arguments or custom initialization logic), you can set `scan = false` on the respective annotation itself:

##### Example: Excluding a Listener from Scanning

```java
@ChirpListener(scan = false)
public class ExampleListener {
    // This listener has a constructor with arguments, so it must be registered manually.
    public ExampleListener(SomeDependency dependency) { /* ... */ }
}

// In your ChirpBuilder setup:
chirp
    .scan("io.fjsn.plugin") // All other scannable components in this package will be registered
    .listener(new ExampleListener(this)); // This specific listener is registered manually
```

> [!WARNING]
> The scanner relies on Java reflection at runtime to discover classes and their annotations. If your application undergoes any form of code obfuscation, bytecode manipulation, or package relocation (e.g., via the Maven Shade Plugin without proper relocation rules for your own code), the scanner will very likely fail to find and register your classes. In such scenarios, or for critical production environments where startup speed is paramount and reflection overhead is to be completely avoided, **manual registration of all packets, listeners, and converters is highly recommended** to ensure reliability and performance.
