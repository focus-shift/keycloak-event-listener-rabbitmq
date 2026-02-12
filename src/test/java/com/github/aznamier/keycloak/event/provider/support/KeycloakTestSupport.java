package com.github.aznamier.keycloak.event.provider.support;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import jakarta.ws.rs.core.Response;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class KeycloakTestSupport {

    private final KeycloakContainer keycloak;
    private final String realm;

    public KeycloakTestSupport(KeycloakContainer keycloak, String realm) {
        this.keycloak = keycloak;
        this.realm = realm;
    }

    public static String uniqueUsername() {
        return "user-" + UUID.randomUUID();
    }

    public void createUser(String username) {
        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setEnabled(true);

        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue("password123");
        credential.setTemporary(false);
        user.setCredentials(List.of(credential));

        try (Response response = adminClient().realm(realm).users().create(user)) {
            assertThat(response.getStatus()).isEqualTo(201);
        }
    }

    public UserRepresentation findUser(String username) {
        List<UserRepresentation> users = adminClient().realm(realm).users().search(username);
        assertThat(users).isNotEmpty();
        return users.getFirst();
    }

    public void updateUser(UserRepresentation user) {
        adminClient().realm(realm).users().get(user.getId()).update(user);
    }

    public void deleteUser(String userId) {
        adminClient().realm(realm).users().get(userId).remove();
    }

    private Keycloak adminClient() {
        return keycloak.getKeycloakAdminClient();
    }
}
