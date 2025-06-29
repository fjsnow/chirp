package io.fjsn.chirp.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;

import io.fjsn.chirp.ChirpRegistry;
import io.fjsn.chirp.annotation.ChirpField;
import io.fjsn.chirp.annotation.ChirpPacket;
import io.fjsn.chirp.internal.serialization.PacketSerializer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

class PacketSerializerTest {

    private ChirpRegistry registry;

    @ChirpPacket
    public static class TestPacket {

        @ChirpField private String message;
        @ChirpField private int number;

        public TestPacket() {}

        public TestPacket(String message, int number) {
            this.message = message;
            this.number = number;
        }
    }

    @BeforeEach
    void setUp() {
        registry = new ChirpRegistry();
        registry.registerDefaultConverters();
        registry.registerPacket(TestPacket.class);
    }

    @Test
    void shouldSerializeAndDeserializePacket() throws ReflectiveOperationException {
        TestPacket originalPacket = new TestPacket("Hello, Chirp!", 42);
        UUID packetId = UUID.randomUUID();
        String origin = "test-server";

        JsonObject json =
                PacketSerializer.serialize(
                        originalPacket,
                        packetId,
                        origin,
                        false,
                        null,
                        false,
                        System.currentTimeMillis(),
                        registry);
        TestPacket deserializedPacket = (TestPacket) PacketSerializer.deserialize(json, registry);

        assertThat(deserializedPacket)
                .isNotNull()
                .isInstanceOf(TestPacket.class)
                .usingRecursiveComparison()
                .isEqualTo(originalPacket);
    }
}
