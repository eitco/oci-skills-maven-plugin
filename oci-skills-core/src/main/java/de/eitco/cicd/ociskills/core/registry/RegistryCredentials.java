package de.eitco.cicd.ociskills.core.registry;

/**
 * Credentials to authenticate at a registry. {@link #toString()} never reveals the password.
 */
public record RegistryCredentials(String username, String password) {

    private static final RegistryCredentials ANONYMOUS = new RegistryCredentials(null, null);

    public static RegistryCredentials anonymous() {
        return ANONYMOUS;
    }

    public boolean isAnonymous() {
        return username == null || username.isEmpty();
    }

    @Override
    public String toString() {
        return isAnonymous() ? "RegistryCredentials[anonymous]" : "RegistryCredentials[username=" + username + ", password=***]";
    }
}
