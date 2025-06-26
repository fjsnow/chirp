## 🐦 chirp

A simple annotation-driven Redis Pub/Sub packet system powered by reflection. Designed for Minecraft servers, but compatible with any Java application.

### Installation

You can use Chirp with Maven or Gradle via Jitpack. The latest version is `1.0`. An example for Maven is provided below:

#### Maven

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
<dependencies>
    <dependency>
        <groupId>com.github.fjsnow</groupId>
        <artifactId>chirp</artifactId>
        <version>1.0</version>
        <scope>compile</scope>
    </dependency>
</dependencies>
```

You will need to shade in chirp into your project. Relocate it's package to include `shaded` otherwise the scan feature will try re-register the default converters.

### Quick start

In your main function, create an instance of Chirp. Optionally use `scan` which will automatically detect and register packets, listeners, and converters.

`channel` is required to allow multiple different programs to use the same Redis server without conflicts, and `origin` is optional but often useful to know _where_ a packet originated from - you may want to set it as `Bukkit#getServerName()` or similar. If you don't provide it, a random ID will be assigned to your server. Chirp allows you to repeat origins as they in the end are simply treated as strings - But this may have unintended side effects if you plan on using the origin for logic.

```java
// using builder
Chirp chirp = Chirp.builder()
                    // enable debug logging, useful for development
                    // .debug(true)
                    .channel("announcement")
                    .origin("server-1")
                    .scan("your.main.package")
                    // rather, if you don't want to automatically register these / they have scan set to false
                    // .packet(ExamplePacket.class)
                    // .listener(new ExamplePacketListener())
                    // .converter(Integer.class, new IntegerConverter)
                    .redis("localhost", 6379)
                    .build();
```

Create a packet class and annotate it with `@ChirpPacket`. Annotate any fields you want transferred using `@ChirpField`.

```java
@ChirpPacket
public class ExamplePacket {
    @ChirpField private String random;
    public String getRandom() { return random; }

    public ExamplePacket(String random) {
        this.random = random;
    }
}
```

You can now send packets using `Chirp#publish`

```java
chirp.publish(new ExamplePacket("Hello, world!"));
```

Setup a listener by annotating a class with `@ChirpListener`, then register handler method(s) with `@ChirpHandler`.

A handler method expects one argument of type `ChirpPacketEvent<T>`, with `T` being the event to subscribe too.

You can extract the recieved packet using `ChirpPacketEvent#getPacket`, and there is other important information contained too in the event, notably `#getOrigin`, `#getSent`, `#getRecieved`, and `#getLatency`.

```java
@ChirpListener
public class ExamplePacketListener {

    @ChirpHandler
    public void onExamplePacket(ChirpPacketEvent<ExamplePacket> event) {
        ExamplePacket packet = event.getPacket();
        System.out.println("Recieved: " + packet.getRandom());
    }

}
```

> [!WARNING]
> If you are using Chirp on a Minecraft server, be aware that the packet handlers do not run on the main thread. If you need to perform actions that require the main thread (like interacting with Bukkit APIs), you will need to schedule those actions using `Bukkit#getScheduler().runTask(...)` or similar methods.

Chirp fields will automatically work with all primitive data types and their boxed versions, as well as `String` and `UUID`. If you want to use other types, you will need to register a custom converter.

Todo so, annotate a class with `@ChirpConverter` and implement `FieldConverter<T>` on it. You will simply need to provide a `serialize` and `deserialize` method, converting to and from `T` and `String` respectively.

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

### Scan

The recommended way for most projects using Chirp is to use the scan, which will automatically register packets, listeners and converters. If you want to opt-in to scanning overall but not for specific classes, you can set `scan` to false on the annotation itself. This may be required if you have classes that require a constructor with arguments, as Chirp will not be able to instantiate them automatically.

```java

@ChirpListener(scan = false)
public class ExampleListener { /* ... */ }

// ExampleListener requires a JavaPlugin instance, therefore we can not automatically register it.
chirp
    .scan("your.main.package")
    .listener(new ExampleListener(this))
```
