package pl.grzegorz2047.standalonethewalls.client.font;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetLocator;
import com.jme3.asset.AssetManager;
import java.io.InputStream;

/** Serves the verified embedded UI atlas to jMonkeyEngine's standard PNG loader. */
public final class UiFontAtlasLocator implements AssetLocator {
    @Override
    public void setRootPath(String rootPath) {
        if (rootPath != null && !rootPath.isEmpty()) {
            throw new IllegalArgumentException("UI font atlas locator requires an empty root path");
        }
    }

    @Override
    public AssetInfo locate(AssetManager manager, AssetKey<?> key) {
        if (!UiFontResources.ATLAS_PATH.equals(key.getName())) {
            return null;
        }
        return new AssetInfo(manager, key) {
            @Override
            public InputStream openStream() {
                return UiFontResources.openVerifiedAtlas();
            }
        };
    }
}
