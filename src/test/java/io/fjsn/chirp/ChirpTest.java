package io.fjsn.chirp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.fjsn.chirp.annotation.ChirpField;
import io.fjsn.chirp.annotation.ChirpPacket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

@ExtendWith(MockitoExtension.class)
class ChirpTest {

    @Mock private JedisPool jedisPool;

    @Mock private Jedis jedis;

    @Spy private ChirpRegistry registry;

    private Chirp chirp;

    @ChirpPacket
    public static class SimplePacket {

        @ChirpField private String data = "test-data";

        public SimplePacket() {}
    }

    @BeforeEach
    void setup() {
        when(jedisPool.getResource()).thenReturn(jedis);
        chirp = new Chirp("test-channel", "test-origin", registry, jedisPool);
        registry.registerDefaultConverters();
    }

    @Test
    void publishShouldSerializeAndSendPacketToJedis() {
        registry.registerPacket(SimplePacket.class);
        SimplePacket packet = new SimplePacket();
        ArgumentCaptor<String> channelCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);

        chirp.publish(packet);

        verify(jedis).publish(channelCaptor.capture(), messageCaptor.capture());

        assertThat(channelCaptor.getValue()).isEqualTo("chirp:test-channel");
        assertThat(messageCaptor.getValue())
                .isNotNull()
                .contains("\"type\":\"SIMPLE_PACKET\"")
                .contains("\"origin\":\"test-origin\"")
                .contains("\"data\":\"test-data\"");
    }
}
