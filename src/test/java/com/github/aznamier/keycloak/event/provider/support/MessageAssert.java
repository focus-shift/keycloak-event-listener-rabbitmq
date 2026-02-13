package com.github.aznamier.keycloak.event.provider.support;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;

public class MessageAssert extends AbstractAssert<MessageAssert, RabbitMqTestConsumer.Message> {

    private final DocumentContext json;

    private MessageAssert(RabbitMqTestConsumer.Message actual) {
        super(actual, MessageAssert.class);
        this.json = JsonPath.parse(actual.body());
    }

    public static MessageAssert assertThat(RabbitMqTestConsumer.Message message) {
        return new MessageAssert(message);
    }

    // --- Routing key ---

    public MessageAssert hasRoutingKey(String expected) {
        isNotNull();
        Assertions.assertThat(actual.routingKey())
                .as("routing key")
                .isEqualTo(expected);
        return this;
    }

    // --- JSON body (JsonPath) ---

    public MessageAssert hasJsonPath(String path, String expectedValue) {
        Assertions.assertThat(json.read(path, String.class))
                .as("JSON path <%s>", path)
                .isEqualTo(expectedValue);
        return this;
    }

    public MessageAssert hasNonBlankJsonPath(String path) {
        Assertions.assertThat(json.read(path, String.class))
                .as("JSON path <%s>", path)
                .isNotBlank();
        return this;
    }

    public MessageAssert hasNonNullJsonPath(String path) {
        Assertions.assertThat(json.read(path, Object.class))
                .as("JSON path <%s>", path)
                .isNotNull();
        return this;
    }

    public MessageAssert hasJsonPathStartingWith(String path, String prefix) {
        Assertions.assertThat(json.read(path, String.class))
                .as("JSON path <%s>", path)
                .startsWith(prefix);
        return this;
    }

    public MessageAssert hasJsonPathContaining(String path, String... substrings) {
        Assertions.assertThat(json.read(path, String.class))
                .as("JSON path <%s>", path)
                .contains(substrings);
        return this;
    }

    public MessageAssert isPrettyPrinted() {
        Assertions.assertThat(actual.body())
                .as("pretty-printed body")
                .contains("\n");
        return this;
    }

    // --- AMQP properties ---

    public MessageAssert hasContentType(String expected) {
        Assertions.assertThat(actual.properties().getContentType())
                .as("content type")
                .isEqualTo(expected);
        return this;
    }

    public MessageAssert hasContentEncoding(String expected) {
        Assertions.assertThat(actual.properties().getContentEncoding())
                .as("content encoding")
                .isEqualTo(expected);
        return this;
    }

    public MessageAssert hasAppId(String expected) {
        Assertions.assertThat(actual.properties().getAppId())
                .as("app ID")
                .isEqualTo(expected);
        return this;
    }

    public MessageAssert hasHeaderContaining(String key, String substring) {
        Assertions.assertThat(actual.properties().getHeaders())
                .as("AMQP headers")
                .containsKey(key);
        Assertions.assertThat(actual.properties().getHeaders().get(key).toString())
                .as("header <%s>", key)
                .contains(substring);
        return this;
    }
}
