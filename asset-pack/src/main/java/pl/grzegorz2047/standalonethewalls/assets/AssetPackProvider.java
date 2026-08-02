package pl.grzegorz2047.standalonethewalls.assets;

import java.io.IOException;
import java.io.InputStream;

/** Opens exactly the archive named by one immutable asset lock entry. */
@FunctionalInterface
public interface AssetPackProvider {
    InputStream open(AssetPackReference reference) throws IOException;
}
