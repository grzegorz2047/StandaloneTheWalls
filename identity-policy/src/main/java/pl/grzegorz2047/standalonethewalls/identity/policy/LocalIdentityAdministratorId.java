package pl.grzegorz2047.standalonethewalls.identity.policy;

import java.util.Objects;
import java.util.regex.Pattern;

/** Stable public identifier of an administrator or automation actor. */
public record LocalIdentityAdministratorId(String value) {
    private static final Pattern FORMAT = Pattern.compile("[a-z0-9][a-z0-9_.-]{0,63}");

    public LocalIdentityAdministratorId {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid local identity administrator ID");
        }
    }
}
