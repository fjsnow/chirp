## 🐦 chirp

Simple reflection-based annotation-based redis pub sub packet system. Built in mind for Minecraft servers, but should work with any Java program.

### Quick start

In your main function, create an instance of Chirp. Optionally use `scan` which will automatically detect and register packets, listeners, and converters.

`channel` is required to allow multiple different programs to use the same Redis server without conflicts, and `origin` is optional but often useful to know _where_ a packet originated from - you may want to set it as `Bukkit#getServerName()` or similar. If you don't provide it, a random ID will be assigned to your server. Chirp allows you to repeat origins as they in the end are simply treated as strings - But this may have unintended side effects if you plan on using the origin for logic.

```java
// using builder
Chirp chirp = Chirp.builder()
                    .channel("announcement")
                    .origin("server-1")
                    .scan("your.main.package")
                    // rather, if you don't want to automatically register these, or if they have scan set to false
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

The recommended way for most projects using Chirp is to use the scan, which will automatically register packets, listeners and converters. However, if your listener or converters required dependencies to be injected Chirp cannot be automatically registered - Rather, set `scan` to false in `@ChirpListener` or `@ChirpConverter` respectively where you don't want them to be automatically picked up, and they will be ignored and you can manually register them, listeners or converters

```java
// ExampleListener requires a JavaPlugin instance, therefore we can not automatically register it.
// If `scan` is not set to false, Chirp will throw an exception if it cannot find a no-args constructor.
.scan("your.main.package")
.listener(new ExampleListener(this))```
