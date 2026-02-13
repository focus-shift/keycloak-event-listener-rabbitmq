package com.github.aznamier.keycloak.event.provider;

import org.junit.jupiter.api.Test;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.AuthDetails;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EventAdminNotificationMqMsgTest {

    @Test
    void create_copiesAllFieldsCorrectly() {
        AdminEvent adminEvent = new AdminEvent();

        AuthDetails authDetails = new AuthDetails();
        authDetails.setRealmId("auth-realm");
        authDetails.setClientId("admin-cli");
        authDetails.setUserId("admin-user");
        authDetails.setIpAddress("10.0.0.1");

        adminEvent.setAuthDetails(authDetails);
        adminEvent.setError("some-error");
        adminEvent.setOperationType(OperationType.CREATE);
        adminEvent.setRealmId("test-realm-id");
        adminEvent.setRepresentation("{\"username\":\"newuser\"}");
        adminEvent.setResourcePath("users/user-123");
        adminEvent.setResourceType(ResourceType.USER);
        adminEvent.setTime(9876543210L);
        adminEvent.setDetails(Map.of("detail-key", "detail-value"));

        EventAdminNotificationMqMsg msg = EventAdminNotificationMqMsg.create(adminEvent);

        assertThat(msg.getAuthDetails()).isNotNull();
        assertThat(msg.getAuthDetails().getRealmId()).isEqualTo("auth-realm");
        assertThat(msg.getAuthDetails().getClientId()).isEqualTo("admin-cli");
        assertThat(msg.getAuthDetails().getUserId()).isEqualTo("admin-user");
        assertThat(msg.getAuthDetails().getIpAddress()).isEqualTo("10.0.0.1");
        assertThat(msg.getError()).isEqualTo("some-error");
        assertThat(msg.getOperationType()).isEqualTo(OperationType.CREATE);
        assertThat(msg.getRealmId()).isEqualTo("test-realm-id");
        assertThat(msg.getRepresentation()).isEqualTo("{\"username\":\"newuser\"}");
        assertThat(msg.getResourcePath()).isEqualTo("users/user-123");
        assertThat(msg.getResourceType()).isEqualTo(ResourceType.USER);
        assertThat(msg.getResourceTypeAsString()).isEqualTo("USER");
        assertThat(msg.getTime()).isEqualTo(9876543210L);
        assertThat(msg.getDetails()).containsEntry("detail-key", "detail-value");
    }

    @Test
    void create_nullErrorAndAuthDetailsPreserved() {
        AdminEvent adminEvent = new AdminEvent();
        adminEvent.setOperationType(OperationType.DELETE);
        adminEvent.setResourceType(ResourceType.GROUP);
        adminEvent.setRealmId("realm");
        adminEvent.setTime(0L);

        EventAdminNotificationMqMsg msg = EventAdminNotificationMqMsg.create(adminEvent);

        assertThat(msg.getError()).isNull();
        assertThat(msg.getAuthDetails()).isNull();
    }
}
