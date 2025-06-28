package io.fjsn.chirp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.fjsn.chirp.annotation.ChirpHandler;
import io.fjsn.chirp.annotation.ChirpListener;
import io.fjsn.chirp.annotation.ChirpPacket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChirpRegistryTest {

    private ChirpRegistry registry;

    @ChirpPacket
    public static class ValidPacket {}

    @ChirpPacket
    public static class InvalidPacket {

        public InvalidPacket(String arg) {}
    }

    @ChirpListener
    public static class TestListener {

        @ChirpHandler
        public void onPacket(ChirpPacketEvent<ValidPacket> event) {}
    }

    @BeforeEach
    void setUp() {
        registry = new ChirpRegistry();
    }

    @Test
    void shouldRegisterPacketSuccessfully() {
        registry.registerPacket(ValidPacket.class);

        assertThat(registry.getPacketRegistry()).containsKey("VALID_PACKET");
        assertThat(registry.getPacketSchemaRegistry()).containsKey("VALID_PACKET");
    }

    @Test
    void shouldThrowExceptionWhenRegisteringPacketWithNoNoArgConstructor() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> registry.registerPacket(InvalidPacket.class));

        assertThat(exception.getMessage()).contains("must have a no-argument constructor");
    }

    @Test
    void shouldRegisterListenerSuccessfully() {
        TestListener listener = new TestListener();

        registry.registerListener(listener);

        assertThat(registry.getListenerRegistry()).containsKey(listener);
        assertThat(registry.getListenerRegistry().get(listener)).hasSize(1);
    }
}
