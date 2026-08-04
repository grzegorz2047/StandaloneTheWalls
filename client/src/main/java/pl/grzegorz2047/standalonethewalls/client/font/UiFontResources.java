package pl.grzegorz2047.standalonethewalls.client.font;

import com.jme3.asset.AssetLoadException;
import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

/** Fail-closed access to the licensed, pinned and embedded PL/EN UI font. */
public final class UiFontResources {
    public static final String FONT_PATH = "Interface/Fonts/SunderfrontUI-Regular.fnt";
    static final String ATLAS_PATH = "Interface/Fonts/SunderfrontUI-Regular.png";

    private static final String ATLAS_RESOURCE =
            "/Interface/Fonts/SunderfrontUI-Regular.png.b64";
    private static final String EXPECTED_ATLAS_SHA256 =
            "128ec6830ed19db90eedfec6affd9283cca99ee0c51911dda28b8f49aeba240e";
    private static final int MAXIMUM_ATLAS_BYTES = 16 * 1024;
    private static final byte[] VERIFIED_ATLAS = decodeAndVerifyAtlas();

    private UiFontResources() {
        throw new AssertionError("No instances");
    }

    public static BitmapFont load(AssetManager assetManager) {
        AssetManager manager = Objects.requireNonNull(assetManager, "assetManager");
        manager.registerLocator("", UiFontAtlasLocator.class);
        try {
            return manager.loadFont(FONT_PATH);
        } catch (RuntimeException exception) {
            throw new AssetLoadException(
                    "embedded Sunderfront UI font is missing or corrupt", exception);
        }
    }

    static InputStream openVerifiedAtlas() {
        return new ByteArrayInputStream(VERIFIED_ATLAS);
    }

    private static byte[] decodeAndVerifyAtlas() {
        try (InputStream input = UiFontResources.class.getResourceAsStream(ATLAS_RESOURCE)) {
            if (input == null) {
                throw new AssetLoadException("embedded UI font atlas resource is missing");
            }
            byte[] encoded = input.readNBytes(MAXIMUM_ATLAS_BYTES * 2);
            byte[] decoded = Base64.getMimeDecoder().decode(encoded);
            if (decoded.length == 0 || decoded.length > MAXIMUM_ATLAS_BYTES) {
                throw new AssetLoadException("embedded UI font atlas exceeds its byte limit");
            }
            String digest = HexFormat.of().formatHex(sha256(decoded));
            if (!EXPECTED_ATLAS_SHA256.equals(digest)) {
                throw new AssetLoadException("embedded UI font atlas digest mismatch");
            }
            return decoded;
        } catch (IllegalArgumentException | IOException exception) {
            throw new AssetLoadException("embedded UI font atlas could not be decoded", exception);
        }
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError("SHA-256 is required by the Java platform", exception);
        }
    }
}
