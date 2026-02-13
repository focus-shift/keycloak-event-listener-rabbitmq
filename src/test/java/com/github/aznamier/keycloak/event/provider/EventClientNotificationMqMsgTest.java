package com.github.aznamier.keycloak.event.provider;

import org.junit.jupiter.api.Test;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EventClientNotificationMqMsgTest {

    @Test
    void create_copiesAllFieldsCorrectly() {
        Event event = new Event();
        event.setClientId("test-client");
        event.setDetails(Map.of("key", "value"));
        event.setError("some-error");
        event.setIpAddress("127.0.0.1");
        event.setRealmId("test-realm-id");
        event.setSessionId("session-123");
        event.setTime(1234567890L);
        event.setType(EventType.LOGIN);
        event.setUserId("user-456");

        EventClientNotificationMqMsg msg = EventClientNotificationMqMsg.create(event);

        assertThat(msg.getClientId()).isEqualTo("test-client");
        assertThat(msg.getDetails()).containsEntry("key", "value");
        assertThat(msg.getError()).isEqualTo("some-error");
        assertThat(msg.getIpAddress()).isEqualTo("127.0.0.1");
        assertThat(msg.getRealmId()).isEqualTo("test-realm-id");
        assertThat(msg.getSessionId()).isEqualTo("session-123");
        assertThat(msg.getTime()).isEqualTo(1234567890L);
        assertThat(msg.getType()).isEqualTo(EventType.LOGIN);
        assertThat(msg.getUserId()).isEqualTo("user-456");
    }

    @Test
    void create_nullErrorAndDetailsPreserved() {
        Event event = new Event();
        event.setClientId("test-client");
        event.setType(EventType.LOGIN);
        event.setRealmId("realm");
        event.setTime(0L);

        EventClientNotificationMqMsg msg = EventClientNotificationMqMsg.create(event);

        assertThat(msg.getError()).isNull();
        assertThat(msg.getDetails()).isNull();
    }
}
