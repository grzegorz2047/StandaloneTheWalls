package pl.grzegorz2047.standalonethewalls.assets;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Test and offline fixture provider mapping exact locked HTTPS URIs to local files. */
public final class FileAssetPackProvider implements AssetPackProvider {
    private final Map<URI, Path> files;

    public FileAssetPackProvider(Map<URI, Path> files) {
        Objects.requireNonNull(files, "files");
        Map<URI, Path> normalized = new LinkedHashMap<>();
        files.forEach(
                (uri, path) -> {
                    URI key = Objects.requireNonNull(uri, "fixture URI");
                    Path value =
                            Objects.requireNonNull(path, "fixture path")
                                    .toAbsolutePath()
                                    .normalize();
                    if (normalized.putIfAbsent(key, value) != null) {
                        throw new IllegalArgumentException("duplicate fixture URI");
                    }
                });
        this.files = Map.copyOf(normalized);
    }

    @Override
    public InputStream open(AssetPackReference reference) throws IOException {
        AssetPackReference locked = Objects.requireNonNull(reference, "reference");
        Path path = files.get(locked.url());
        if (path == null) {
            throw new IOException("no local fixture is mapped for the locked asset URI");
        }
        if (Files.isSymbolicLink(path)
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("asset fixture must be a regular non-symbolic-link file");
        }
        return Files.newInputStream(path);
    }
}
