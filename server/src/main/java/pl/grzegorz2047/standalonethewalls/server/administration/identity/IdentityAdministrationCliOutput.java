package pl.grzegorz2047.standalonethewalls.server.administration.identity;

import java.util.List;
import java.util.Objects;

/** Deterministic rendered lines plus semantic command success for process exit mapping. */
public record IdentityAdministrationCliOutput(List<String> lines, boolean successful) {
    public IdentityAdministrationCliOutput {
        lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("CLI output must contain at least one line");
        }
        if (lines.stream().anyMatch(line -> line == null || line.isEmpty())) {
            throw new IllegalArgumentException("CLI output lines must be non-empty");
        }
    }
}
