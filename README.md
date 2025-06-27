## 🐦 chirp

A simple annotation-driven Redis Pub/Sub packet system powered by reflection.

Designed for Minecraft servers, but compatible with any Java application.

### Installation

You can install Chirp via Jitpack in your favourite package manager below. The latest version is `1.1.0`.

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
    <version>1.1.0</version>
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
            <version>3.4.1</version>
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
    implementation 'io.fjsn:chirp:1.1.0'
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
    implementation("io.fjsn:chirp:1.0.0")
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

To get started with Chirp, create an instance using the builder pattern. This is typically done in your main function, such as:

- `main()` in a standard Java application
- `onEnable()` in a Spigot plugin

Here’s how to configure it step by step:

1. **Create the builder with `Chirp.builder()`**

2. **Set the `channel`**
   This is a **required** string used to prevent multiple instances of Chirp from interferring with each other.
   - Do this with: `.channel("your-plugin-name")`

3. **Set the `origin` (optional but recommended)**
   A unique ID (e.g., `"server-1"` or `"lobby-1"`) that identifies your server instance. Useful when filtering or tracking sources.
   - Do this with: `.origin("lobby-1")`

4. **Register packets, listeners, and converters (More on what these are below)**
   You can do this manually or automatically:
   - Manually:
     - `.packet(YourPacket.class)`
     - `.listener(new YourPacketListener())`
     - `.converter(YourType.class, new YourTypeConverter())`
   - Or automatically:
     - `.scan("your.package.name")` — Detects all annotated classes below the given package. (See caveats at the bottom)

5. **Configure Redis connection**
   Provide your Redis host and port:
   - `.redis("localhost", 6379)`
   - Or with password: `.redis("localhost", 6379, "yourPassword")`

6. **Optionally, enable `debug` mode**
   This will log out when packets, listeners and converters get registered, as well as any outgoing and incoming packets. Useful during development.
   - `.debug(true)`

7. **Finish setup with `.build()`**

##### Example

```java
Chirp chirp = Chirp.builder()
    .channel("announcements")  // required: channel
    .origin("server-1") // optional: this instance's id
    .scan("io.fjsn.plugin") // optional: scan automatically to register packets, listeners, and converters
    .redis("localhost", 6379) // required: redis connection info
    .build(); // build's and connects chirp to redis!
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

To send a packet, simply create an instance of your packet and publish it via `Chirp#publish`

##### Example

```java
ExamplePacket packet = new ExamplePacket("random string :P");
chirp.publish(packet);
```

> [!NOTE]
> `Chirp#publish` will broadcast out your packet, but that means the broadcasting server will also receive it! The sane default in Chirp is the broadcasting server ignores this incoming packet. To override this, passing `true` as a second argument in `Chirp#publish` will make handlers process it on the broadcasting server.

#### Listening to packets

To listen and handle incoming packets, create and annotate a class with `@ChirpListener`, then annotate handler method(s) with `@ChirpHandler`.

Each handler method expects on argument of type `ChirpPacketEvent<T>`, a wrapper around a generic packet with attached metadata.

`ChirpPacketEvent` contains important information regarding your event, notably:
- `ChirpPacketEvent#getPacket` - Extracts the packet (type `T`)
- `ChirpPacketEvent#getOrigin` - Extract the server where the packet originated from
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
        System.out.println("It took " + packet.getLatency() + "ms for us to receive it!");
    }

}
```

Once again, if you have scanning enabled and this class lives within the package, Chirp will automatically register this listener on load. Otherwise, register it manually using `.listener(new ExamplePacketListener())` on your `ChirpBuilder`.

> [!WARNING]
> If you are using Chirp on a Minecraft server, be aware that the packet handlers do not run on the main thread. If you need to perform actions that require the main thread (like interacting with Bukkit APIs), you will need to schedule those actions using `Bukkit#getScheduler().runTask(...)` or similar methods.

#### Creating a custom converter

During transfer, Chirp needs to serialise your packet to a String then later deserialise it. By default, Chirp comes with a range of converters for all of Java's primitive types and their boxed equivalents. Due to their popularity, Chirp also has converters for `String` and `UUID`.

Chirp can also automatically serialise Lists, Sets and Maps, and nested Objects, as long as they're made up of types that have a converter registered.

To create a custom converter, create and annotate a class with `@ChirpConverter` and implement `FieldConverter<T>` on it.

You will simply need to provide a `serialize` and `deserialize` method, converting to and from `T` and `String` respectively.

##### Example

```java
@ChirpConverter
public class IntegerConverter implements FieldConverter<Integer> {
    public String serialize(Integer value) {
        return String.valueOf(value);
    }

    public Integer deserialize(String value) {
        return Integer.parseInt(value);
    }
}
```

Once again, if you have scanning enabled and this class lives within the package, Chirp will automatically register this converter on load. Otherwise, register it manually using `.converter(Integer.class, new IntegerListener())` on your `ChirpBuilder`.

### Callbacks

Redis Pub/Sub is inherently fire-and-forget, meaning it doesn't support request-response or callbacks natively. However, Chirp extends this model by offering support for automatic callbacks via the `ChirpCallback<T>` class.

To use it, pass a `ChirpCallback<T>` in `Chirp#publish`, where T is your response time, when publishing a packet. Chirp will automatically handle the routing, waiting, and response delivery for you.

Your callback should include:
- A consumer to handle the response (`ChirpPacketEvent<T>`).
- An optional timeout handle if no response is received (`Runnable`).
- An optional time to live (TTL) for the callback, which defaults to 1 second.

##### Example

```java
MessagePacket packet = new MessagePacket(receiverName, sender.getName(), message);
        plugin.getChirp()
                .publish(
                        packet,
                        true,
                        ChirpCallback.<MessageResponsePacket>of(
                                response -> {
                                    sender.sendMessage(
                                            mm.deserialize(
                                                    "<gray>(You -> "
                                                            + receiverName
                                                            + ") <white>"
                                                            + message));
                                },
                                () -> {
                                    sender.sendMessage(
                                            mm.deserialize("<red>Failed to send message."));
                                },
                                200L));
```

### Scanner

The easiest way for small projects using Chirp is to use the scanner, which will automatically register packets, listeners and converters.

If you want to opt-in to scanning overall but not for specific packets, listeners or converters (for example if they have constructors with arguments), you can set `scan` to false on the specific annotation itself.


##### Example

```java
@ChirpListener(scan = false)
public class ExampleListener { /* ... */ }

chirp
    .scan("io.fjsn.plugin")
    .listener(new ExampleListener(this))
```

> [!WARNING]
> While scanning may seem easy at first, if you use any sort of obfuscation or your package gets relocated, it will very likely break the scanner. In this case, manual registration would be required.
