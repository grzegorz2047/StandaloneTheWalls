package pl.grzegorz2047.standalonethewalls.mapformat;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Result of validating one untrusted manifest draft. */
public record MapManifestValidation(
        Optional<MapManifest> manifest, List<MapValidationIssue> issues) {

    public MapManifestValidation {
        Objects.requireNonNull(manifest, "manifest");
        issues = List.copyOf(issues);
        if (manifest.isPresent() == !issues.isEmpty()) {
            throw new IllegalArgumentException(
                    "a validation result must contain either a manifest or issues");
        }
    }

    public static MapManifestValidation valid(MapManifest manifest) {
        return new MapManifestValidation(Optional.of(manifest), List.of());
    }

    public static MapManifestValidation invalid(List<MapValidationIssue> issues) {
        if (issues.isEmpty()) {
            throw new IllegalArgumentException("invalid result requires at least one issue");
        }
        return new MapManifestValidation(Optional.empty(), issues);
    }

    public boolean isValid() {
        return manifest.isPresent();
    }
}
