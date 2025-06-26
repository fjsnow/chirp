## 🐦 chirp

Simple reflection-based annotation-based redis pub sub packet system

### Quick start

In your main function, create an instance of Chirp. Optionally use `scan` which will automatically detect and register packets, listeners, and converters.

```java
// using builder
Chirp chirp = Chirp.builder()
                    .channel("announcement")
                    .origin("server-1")
                    .scan("your.main.package")
                    // .packet(ExamplePacket.class)
                    // .listener(new ExamplePacketListener())
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
}```

Setup a listener by annotating a class with `@ChirpListener`, then register handler method(s) with `@ChirpHandler`.

A handler method expects one argument of type `ChirpPacketEvent<T>`, with `T` being the event to subscribe too.

```java
@ChirpListener
public class ExamplePacketListener {

    @ChirpHandler
    public void onExamplePacket(ChirpPacketEvent<ExamplePacket> event) {
        ExamplePacket packet = event.getPacket();
        System.out.println("Recieved: " + packet.getRandom());
    }

}```

Chirp fields will automatically work with all primitive data types and their boxed versions, as well as String and UUID. If you want to use anything else, you will need to register a custom converter.

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
}```
