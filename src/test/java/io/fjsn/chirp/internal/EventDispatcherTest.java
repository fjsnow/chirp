package io.fjsn.chirp.internal;

import static org.mockito.Mockito.*;

import io.fjsn.chirp.Chirp;
import io.fjsn.chirp.ChirpPacketEvent;
import io.fjsn.chirp.ChirpRegistry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class EventDispatcherTest {

    @Mock private ChirpRegistry registry;

    @Mock private Chirp chirp;

    @InjectMocks private EventDispatcher eventDispatcher;

    public static class MyListener {

        public void handlePacket(ChirpPacketEvent<MyPacket> event) {}
    }

    public static class MyPacket {}

    @Test
    void shouldDispatchEventToCorrectListener() throws Exception {
        MyListener listenerInstance = spy(new MyListener());
        MyPacket packet = new MyPacket();
        ChirpPacketEvent<MyPacket> event =
                new ChirpPacketEvent<>(
                        chirp, UUID.randomUUID(), packet, "origin", false, null, false, 0, 1);

        Method method = MyListener.class.getMethod("handlePacket", ChirpPacketEvent.class);
        HandlerMethod handlerMethod = new HandlerMethod(method, MyPacket.class);

        when(registry.getListenerRegistry())
                .thenReturn(Map.of(listenerInstance, List.of(handlerMethod)));

        eventDispatcher.dispatchEventToListeners(event);

        verify(listenerInstance, times(1)).handlePacket(event);
    }
}
