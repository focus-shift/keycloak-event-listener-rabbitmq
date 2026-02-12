package com.github.aznamier.keycloak.event.provider;

import com.github.aznamier.keycloak.event.provider.support.KeycloakTestSupport;
import com.github.aznamier.keycloak.event.provider.support.MessageAssert;
import com.github.aznamier.keycloak.event.provider.support.RabbitMqTestConsumer;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.UserRepresentation;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.io.File;
import java.time.Duration;
import java.util.List;

import static com.github.aznamier.keycloak.event.provider.support.KeycloakTestSupport.uniqueUsername;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
class RabbitMqEventListenerProviderIT {

    private static final String RABBITMQ_NETWORK_ALIAS = "rabbitmq";
    private static final String EXCHANGE = "amq.topic";

    private static final Network NETWORK = Network.newNetwork();

    private static final List<File> PROVIDER_DEPENDENCIES = Maven.resolver()
            .loadPomFromFile("./pom.xml")
            .resolve("com.rabbitmq:amqp-client")
            .withoutTransitivity()
            .asList(File.class);

    @Container
    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:4.2-management")
            .withNetwork(NETWORK)
            .withNetworkAliases(RABBITMQ_NETWORK_ALIAS);

    @Container
    static final KeycloakContainer KEYCLOAK = new KeycloakContainer()
            .withNetwork(NETWORK)
            .withProviderClassesFrom("target/classes")
            .withProviderLibsFrom(PROVIDER_DEPENDENCIES)
            .withRealmImportFile("test-realm-realm.json")
            .withCustomCommand("--spi-events-listener-keycloak-to-rabbitmq-url=" + RABBITMQ_NETWORK_ALIAS)
            .withCustomCommand("--spi-events-listener-keycloak-to-rabbitmq-port=5672")
            .withCustomCommand("--spi-events-listener-keycloak-to-rabbitmq-username=" + RABBITMQ.getAdminUsername())
            .withCustomCommand("--spi-events-listener-keycloak-to-rabbitmq-password=" + RABBITMQ.getAdminPassword())
            .withCustomCommand("--spi-events-listener-keycloak-to-rabbitmq-exchange=" + EXCHANGE)
            .withCustomCommand("--spi-events-listener-keycloak-to-rabbitmq-vhost=/")
            .withCustomCommand("--spi-events-listener-keycloak-to-rabbitmq-use_tls=false")
            .dependsOn(RABBITMQ);

    private static RabbitMqTestConsumer consumer;
    private static KeycloakTestSupport keycloakSupport;

    @BeforeAll
    static void setUp() throws Exception {
        consumer = new RabbitMqTestConsumer(RABBITMQ, EXCHANGE);
        keycloakSupport = new KeycloakTestSupport(KEYCLOAK, "test-realm");
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (consumer != null) {
            consumer.close();
        }
    }

    // Routing key format: KK.EVENT.CLIENT.<realm>.<SUCCESS|ERROR>.<client>.<type>
    @Nested
    class ClientNotification {

        @Test
        void loginSuccess_publishesEvent() throws Exception {
            String routingKey = "KK.EVENT.CLIENT.test-realm.SUCCESS.test-client.LOGIN";

            try (var subscription = consumer.subscribe(routingKey)) {

                try (Keycloak kc = Keycloak.getInstance(
                        KEYCLOAK.getAuthServerUrl(),
                        "test-realm",
                        "testuser",
                        "testpassword",
                        "test-client")) {
                    kc.tokenManager().getAccessToken();
                }

                await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                        assertThat(subscription.receivedMessages())
                                .singleElement()
                                .satisfies(msg ->
                                        MessageAssert.assertThat(msg)
                                                .hasRoutingKey(routingKey)
                                                .hasJsonPath("$['@class']", "com.github.aznamier.keycloak.event.provider.EventClientNotificationMqMsg")
                                                .hasJsonPath("$.type", "LOGIN")
                                                .hasNonBlankJsonPath("$.realmId")
                                                .hasJsonPath("$.clientId", "test-client")
                                                .hasNonBlankJsonPath("$.userId")
                                                .isPrettyPrinted()
                                                .hasContentType("application/json")
                                                .hasContentEncoding("UTF-8")
                                                .hasAppId("Keycloak")
                                                .hasHeaderContaining("__TypeId__", "EventClientNotificationMqMsg")
                                )
                );
            }
        }

        @Test
        void loginFailure_publishesErrorEvent() throws Exception {
            String routingKey = "KK.EVENT.CLIENT.test-realm.ERROR.test-client.LOGIN_ERROR";

            try (var subscription = consumer.subscribe(routingKey)) {

                try (Keycloak kc = Keycloak.getInstance(
                        KEYCLOAK.getAuthServerUrl(),
                        "test-realm",
                        "testuser",
                        "wrongpassword",
                        "test-client")) {
                    kc.tokenManager().getAccessToken();
                } catch (Exception ignored) {
                    // expected
                }

                await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                        assertThat(subscription.receivedMessages())
                                .singleElement()
                                .satisfies(msg ->
                                        MessageAssert.assertThat(msg)
                                                .hasRoutingKey(routingKey)
                                                .hasJsonPath("$['@class']", "com.github.aznamier.keycloak.event.provider.EventClientNotificationMqMsg")
                                                .hasJsonPath("$.type", "LOGIN_ERROR")
                                                .hasNonBlankJsonPath("$.realmId")
                                                .hasJsonPath("$.clientId", "test-client")
                                                .hasNonBlankJsonPath("$.error")
                                                .hasHeaderContaining("__TypeId__", "EventClientNotificationMqMsg")
                                )
                );
            }
        }
    }

    // Routing key format: KK.EVENT.ADMIN.<realm>.<SUCCESS|ERROR>.<resourceType>.<operation>
    @Nested
    class AdminNotification {

        @Test
        void createUser_publishesEvent() throws Exception {
            String username = uniqueUsername();
            String routingKey = "KK.EVENT.ADMIN.test-realm.SUCCESS.USER.CREATE";

            try (var subscription = consumer.subscribe(routingKey)) {

                keycloakSupport.createUser(username);

                await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                        assertThat(subscription.receivedMessages())
                                .singleElement()
                                .satisfies(msg ->
                                        MessageAssert.assertThat(msg)
                                                .hasRoutingKey(routingKey)
                                                .hasJsonPath("$['@class']", "com.github.aznamier.keycloak.event.provider.EventAdminNotificationMqMsg")
                                                .hasJsonPath("$.operationType", "CREATE")
                                                .hasJsonPath("$.resourceType", "USER")
                                                .hasJsonPath("$.resourceTypeAsString", "USER")
                                                .hasJsonPathStartingWith("$.resourcePath", "users/")
                                                .hasNonNullJsonPath("$.authDetails")
                                                .hasJsonPathContaining("$.representation", username)
                                                .hasHeaderContaining("__TypeId__", "EventAdminNotificationMqMsg")
                                )
                );
            }
        }

        @Test
        void updateUser_publishesEvent() throws Exception {
            String username = uniqueUsername();
            keycloakSupport.createUser(username);
            String routingKey = "KK.EVENT.ADMIN.test-realm.SUCCESS.USER.UPDATE";

            try (var subscription = consumer.subscribe(routingKey)) {

                UserRepresentation user = keycloakSupport.findUser(username);
                user.setEmail("updated@example.com");
                keycloakSupport.updateUser(user);

                await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                        assertThat(subscription.receivedMessages())
                                .singleElement()
                                .satisfies(msg ->
                                        MessageAssert.assertThat(msg)
                                                .hasRoutingKey(routingKey)
                                                .hasJsonPath("$['@class']", "com.github.aznamier.keycloak.event.provider.EventAdminNotificationMqMsg")
                                                .hasJsonPath("$.operationType", "UPDATE")
                                                .hasJsonPath("$.resourceType", "USER")
                                                .hasJsonPath("$.resourceTypeAsString", "USER")
                                                .hasJsonPathStartingWith("$.resourcePath", "users/")
                                                .hasJsonPath("$.authDetails.realmName", "master")
                                                .hasJsonPathContaining("$.representation", "updated@example.com", username)
                                                .hasHeaderContaining("__TypeId__", "EventAdminNotificationMqMsg")
                                )
                );
            }
        }

        @Test
        void deleteUser_publishesEvent() throws Exception {
            String username = uniqueUsername();
            keycloakSupport.createUser(username);
            String routingKey = "KK.EVENT.ADMIN.test-realm.SUCCESS.USER.DELETE";

            try (var subscription = consumer.subscribe(routingKey)) {

                UserRepresentation user = keycloakSupport.findUser(username);
                keycloakSupport.deleteUser(user.getId());

                await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                        assertThat(subscription.receivedMessages())
                                .singleElement()
                                .satisfies(msg ->
                                        MessageAssert.assertThat(msg)
                                                .hasRoutingKey(routingKey)
                                                .hasJsonPath("$['@class']", "com.github.aznamier.keycloak.event.provider.EventAdminNotificationMqMsg")
                                                .hasJsonPath("$.operationType", "DELETE")
                                                .hasJsonPath("$.resourceType", "USER")
                                                .hasJsonPath("$.resourceTypeAsString", "USER")
                                                .hasJsonPathStartingWith("$.resourcePath", "users/")
                                                .hasNonNullJsonPath("$.authDetails")
                                                .hasHeaderContaining("__TypeId__", "EventAdminNotificationMqMsg")
                                )
                );
            }
        }
    }
}
