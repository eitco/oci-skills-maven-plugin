package de.eitco.cicd.ociskills.core.git;

/**
 * Credentials to authenticate at a git server - usually a user name and an access token. {@link #toString()} never
 * reveals the password.
 */
public record GitCredentials(String username, String password) {

    private static final GitCredentials ANONYMOUS = new GitCredentials(null, null);

    public static GitCredentials anonymous() {
        return ANONYMOUS;
    }

    public boolean isAnonymous() {
        return username == null || username.isEmpty();
    }

    @Override
    public String toString() {
        return isAnonymous() ? "GitCredentials[anonymous]" : "GitCredentials[username=" + username + ", password=***]";
    }
}
