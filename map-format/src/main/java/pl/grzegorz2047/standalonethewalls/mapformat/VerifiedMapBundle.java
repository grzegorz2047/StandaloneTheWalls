package pl.grzegorz2047.standalonethewalls.mapformat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Fully verified in-memory map package; no member is exposed without a defensive copy. */
public final class VerifiedMapBundle {
    private final MapManifest manifest;
    private final PreparationGameplay gameplay;
    private final Sha256Digest archiveSha256;
    private final byte[] manifestJson;
    private final Map<String, byte[]> members;

    public VerifiedMapBundle(
            MapManifest manifest,
            PreparationGameplay gameplay,
            Sha256Digest archiveSha256,
            byte[] manifestJson,
            Map<String, byte[]> members) {
        this.manifest = Objects.requireNonNull(manifest, "manifest");
        this.gameplay = Objects.requireNonNull(gameplay, "gameplay");
        this.archiveSha256 = Objects.requireNonNull(archiveSha256, "archiveSha256");
        this.manifestJson = Objects.requireNonNull(manifestJson, "manifestJson").clone();
        Map<String, byte[]> copies = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> entry :
                Objects.requireNonNull(members, "members").entrySet()) {
            String path = Objects.requireNonNull(entry.getKey(), "member path");
            byte[] bytes = Objects.requireNonNull(entry.getValue(), "member bytes").clone();
            copies.put(path, bytes);
        }
        if (!copies.keySet().equals(manifest.files().keySet())) {
            throw new IllegalArgumentException(
                    "verified member names must exactly match the manifest");
        }
        this.members = Map.copyOf(copies);
    }

    public MapManifest manifest() {
        return manifest;
    }

    public PreparationGameplay gameplay() {
        return gameplay;
    }

    public Sha256Digest archiveSha256() {
        return archiveSha256;
    }

    public byte[] manifestJson() {
        return manifestJson.clone();
    }

    public Set<String> memberNames() {
        return members.keySet();
    }

    public byte[] member(String path) {
        byte[] bytes = members.get(Objects.requireNonNull(path, "path"));
        if (bytes == null) {
            throw new IllegalArgumentException("map bundle does not contain the requested member");
        }
        return bytes.clone();
    }
}
