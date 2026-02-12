package com.github.aznamier.keycloak.event.provider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RealmProvider;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RabbitMqConfigTest {

    @Mock
    private KeycloakSession session;

    @Mock
    private RealmProvider realmProvider;

    @Mock
    private RealmModel realmModel;

    @Mock
    private KeycloakContext keycloakContext;

    // --- normalizeKey ---

    @Test
    void normalizeKey_plainAlphanumericUnchanged() {
        assertThat(RabbitMqConfig.normalizeKey("ABC123")).isEqualTo("ABC123");
    }

    @Test
    void normalizeKey_spacesReplacedWithUnderscore() {
        assertThat(RabbitMqConfig.normalizeKey("hello world")).isEqualTo("hello_world");
    }

    @Test
    void normalizeKey_specialCharactersStripped() {
        assertThat(RabbitMqConfig.normalizeKey("foo@bar!baz")).isEqualTo("foobarbaz");
    }

    @Test
    void normalizeKey_hashAndStarPreserved() {
        assertThat(RabbitMqConfig.normalizeKey("routing.#.key.*")).isEqualTo("routing.#.key.*");
    }

    @Test
    void normalizeKey_hyphensAndDotsPreserved() {
        assertThat(RabbitMqConfig.normalizeKey("my-key.name")).isEqualTo("my-key.name");
    }

    // --- removeDots ---

    @Test
    void removeDots_dotsRemoved() {
        assertThat(RabbitMqConfig.removeDots("a.b.c")).isEqualTo("abc");
    }

    @Test
    void removeDots_nullReturnsNull() {
        assertThat(RabbitMqConfig.removeDots(null)).isNull();
    }

    @Test
    void removeDots_noDotsUnchanged() {
        assertThat(RabbitMqConfig.removeDots("nodots")).isEqualTo("nodots");
    }

    // --- calculateRoutingKey for Event ---

    @Test
    void calculateRoutingKey_clientSuccessEvent() {
        Event event = new Event();
        event.setRealmId("realm-id");
        event.setClientId("my-client");
        event.setType(EventType.LOGIN);

        when(session.realms()).thenReturn(realmProvider);
        when(realmProvider.getRealm("realm-id")).thenReturn(realmModel);
        when(realmModel.getName()).thenReturn("test-realm");

        String routingKey = RabbitMqConfig.calculateRoutingKey(event, session);

        assertThat(routingKey).isEqualTo("KK.EVENT.CLIENT.test-realm.SUCCESS.my-client.LOGIN");
    }

    @Test
    void calculateRoutingKey_clientErrorEvent() {
        Event event = new Event();
        event.setRealmId("realm-id");
        event.setClientId("my-client");
        event.setType(EventType.LOGIN_ERROR);
        event.setError("invalid_credentials");

        when(session.realms()).thenReturn(realmProvider);
        when(realmProvider.getRealm("realm-id")).thenReturn(realmModel);
        when(realmModel.getName()).thenReturn("test-realm");

        String routingKey = RabbitMqConfig.calculateRoutingKey(event, session);

        assertThat(routingKey).contains("ERROR");
    }

    @Test
    void calculateRoutingKey_clientEventDotsInRealmRemoved() {
        Event event = new Event();
        event.setRealmId("realm-id");
        event.setClientId("my.client");
        event.setType(EventType.LOGIN);

        when(session.realms()).thenReturn(realmProvider);
        when(realmProvider.getRealm("realm-id")).thenReturn(realmModel);
        when(realmModel.getName()).thenReturn("my.realm");

        String routingKey = RabbitMqConfig.calculateRoutingKey(event, session);

        assertThat(routingKey)
                .contains("myrealm")
                .contains("myclient")
                .doesNotContain("my.realm")
                .doesNotContain("my.client");
    }

    // --- calculateRoutingKey for AdminEvent ---

    @Test
    void calculateRoutingKey_adminSuccessEvent() {
        AdminEvent adminEvent = new AdminEvent();
        adminEvent.setOperationType(OperationType.CREATE);
        adminEvent.setResourceType(ResourceType.USER);

        when(session.getContext()).thenReturn(keycloakContext);
        when(keycloakContext.getRealm()).thenReturn(realmModel);
        when(realmModel.getName()).thenReturn("test-realm");

        String routingKey = RabbitMqConfig.calculateRoutingKey(adminEvent, session);

        assertThat(routingKey).isEqualTo("KK.EVENT.ADMIN.test-realm.SUCCESS.USER.CREATE");
    }

    @Test
    void calculateRoutingKey_adminErrorEvent() {
        AdminEvent adminEvent = new AdminEvent();
        adminEvent.setOperationType(OperationType.CREATE);
        adminEvent.setResourceType(ResourceType.USER);
        adminEvent.setError("some-error");

        when(session.getContext()).thenReturn(keycloakContext);
        when(keycloakContext.getRealm()).thenReturn(realmModel);
        when(realmModel.getName()).thenReturn("test-realm");

        String routingKey = RabbitMqConfig.calculateRoutingKey(adminEvent, session);

        assertThat(routingKey).contains("ERROR");
    }

    // --- writeAsJson ---

    @Test
    void writeAsJson_prettyModeReturnsFormattedJson() {
        Map<String, String> obj = Map.of("key", "value");
        String json = RabbitMqConfig.writeAsJson(obj, true);

        assertThat(json).contains("\n");
        assertThat(json).contains("\"key\"");
        assertThat(json).contains("\"value\"");
    }

    @Test
    void writeAsJson_compactModeReturnsSingleLine() {
        Map<String, String> obj = Map.of("key", "value");
        String json = RabbitMqConfig.writeAsJson(obj, false);

        assertThat(json).doesNotContain("\n");
        assertThat(json).contains("\"key\"");
        assertThat(json).contains("\"value\"");
    }

    // --- createFromScope ---

    @Test
    void createFromScope_nullScopeUsesDefaults() {
        RabbitMqConfig cfg = RabbitMqConfig.createFromScope(null);

        assertThat(cfg.getHostUrl()).isEqualTo("localhost");
        assertThat(cfg.getPort()).isEqualTo(5672);
        assertThat(cfg.getUsername()).isEqualTo("admin");
        assertThat(cfg.getPassword()).isEqualTo("admin");
        assertThat(cfg.getExchange()).isEqualTo("amq.topic");
    }
}
