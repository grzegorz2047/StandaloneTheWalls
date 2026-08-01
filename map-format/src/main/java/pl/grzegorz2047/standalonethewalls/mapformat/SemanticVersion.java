package pl.grzegorz2047.standalonethewalls.mapformat;

import java.util.Objects;

/** Parsed canonical Semantic Version used for map revisions. */
public record SemanticVersion(int major, int minor, int patch, String prerelease, String build) {
    public SemanticVersion {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("version components cannot be negative");
        }
        prerelease = Objects.requireNonNull(prerelease, "prerelease");
        build = Objects.requireNonNull(build, "build");
    }

    @Override
    public String toString() {
        StringBuilder value = new StringBuilder()
                .append(major)
                .append('.')
                .append(minor)
                .append('.')
                .append(patch);
        if (!prerelease.isEmpty()) {
            value.append('-').append(prerelease);
        }
        if (!build.isEmpty()) {
            value.append('+').append(build);
        }
        return value.toString();
    }
}
