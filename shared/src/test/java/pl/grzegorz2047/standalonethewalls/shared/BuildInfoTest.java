package pl.grzegorz2047.standalonethewalls.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BuildInfoTest {
    @Test
    void generatedBuildProvenanceMatchesRuntimeAccessor() throws IOException {
        try (InputStream input =
                Objects.requireNonNull(
                        BuildInfo.class.getResourceAsStream(BuildInfo.BUILD_PROVENANCE_RESOURCE),
                        "generated build provenance")) {
            String content = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(BuildInfo.parseRepositoryCommit(content)).isEqualTo(BuildInfo.repositoryCommit());
        }

        String githubCommit = System.getenv("GITHUB_SHA");
        if (githubCommit != null) {
            assertThat(BuildInfo.repositoryCommit())
                    .contains(githubCommit.toLowerCase(Locale.ROOT));
        }
    }

    @Test
    void unavailableMarkerProducesEmptyRepositoryCommit() {
        assertThat(BuildInfo.parseRepositoryCommit("schemaVersion=1\nrepositoryCommit=unavailable\n"))
                .isEmpty();
    }

    @Test
    void fullRepositoryCommitIsNormalizedToLowercase() {
        String uppercaseCommit = "ABCDEF0123456789ABCDEF0123456789ABCDEF01";

        assertThat(
                        BuildInfo.parseRepositoryCommit(
                                "schemaVersion=1\nrepositoryCommit=" + uppercaseCommit + "\n"))
                .contains(uppercaseCommit.toLowerCase(Locale.ROOT));
    }

    @Test
    void malformedBuildProvenanceIsRejected() {
        for (String malformed :
                new String[] {
                    "schemaVersion=2\nrepositoryCommit=unavailable\n",
                    "schemaVersion=1\ncommit=unavailable\n",
                    "schemaVersion=1\nrepositoryCommit=unavailable",
                    "schemaVersion=1\nrepositoryCommit=abc\n",
                    "schemaVersion=1\nrepositoryCommit=0123456789abcdef0123456789abcdef0123456g\n",
                    "schemaVersion=1\nrepositoryCommit=unavailable\nextra=true\n"
                }) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> BuildInfo.parseRepositoryCommit(malformed));
        }
    }

    @Test
    void sixtyFourCharacterRepositoryCommitIsAccepted() {
        String commit = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

        assertThat(BuildInfo.parseRepositoryCommit("schemaVersion=1\nrepositoryCommit=" + commit + "\n"))
                .isEqualTo(Optional.of(commit));
    }
}
