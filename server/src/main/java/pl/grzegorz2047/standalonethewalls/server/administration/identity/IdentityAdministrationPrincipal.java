package pl.grzegorz2047.standalonethewalls.server.administration.identity;

import java.util.Objects;
import java.util.Set;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalIdentityAdministratorId;

/** Authenticated administrator identity and its immutable local capabilities. */
public record IdentityAdministrationPrincipal(
        LocalIdentityAdministratorId administratorId,
        Set<IdentityAdministrationPermission> permissions) {
    public IdentityAdministrationPrincipal {
        administratorId = Objects.requireNonNull(administratorId, "administratorId");
        permissions = Set.copyOf(Objects.requireNonNull(permissions, "permissions"));
    }

    public boolean has(IdentityAdministrationPermission permission) {
        return permissions.contains(Objects.requireNonNull(permission, "permission"));
    }
}
