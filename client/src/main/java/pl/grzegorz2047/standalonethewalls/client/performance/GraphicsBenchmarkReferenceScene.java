package pl.grzegorz2047.standalonethewalls.client.performance;

import com.jme3.asset.AssetManager;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.light.PointLight;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Sphere;
import java.util.Objects;

/** Builds the deterministic representative scene used by the integrated-GPU benchmark. */
public final class GraphicsBenchmarkReferenceScene {
    public static final String SCENARIO_ID = "integrated-gpu-reference";
    public static final int SCENARIO_VERSION = 2;
    public static final int TEAM_COUNT = 4;
    public static final int PLAYERS_PER_TEAM = 8;
    public static final int STRUCTURE_GEOMETRY_COUNT = 20;
    public static final int VEGETATION_INSTANCE_COUNT = 32;
    public static final int VEGETATION_GEOMETRY_COUNT = VEGETATION_INSTANCE_COUNT * 2;
    public static final int VFX_PROXY_GEOMETRY_COUNT = 12;
    public static final int NON_DYNAMIC_LIGHT_COUNT = 2;
    public static final int LIGHT_COUNT = NON_DYNAMIC_LIGHT_COUNT + TEAM_COUNT;
    public static final int TOTAL_GEOMETRY_COUNT =
            1
                    + TEAM_COUNT * PLAYERS_PER_TEAM
                    + STRUCTURE_GEOMETRY_COUNT
                    + VEGETATION_GEOMETRY_COUNT
                    + VFX_PROXY_GEOMETRY_COUNT;

    public static final String ROOT_NAME = "IntegratedGpuBenchmarkReference";
    public static final String TERRAIN_NODE_NAME = "Terrain";
    public static final String TEAMS_NODE_NAME = "Teams";
    public static final String STRUCTURES_NODE_NAME = "Structures";
    public static final String VEGETATION_NODE_NAME = "Vegetation";
    public static final String VFX_NODE_NAME = "Vfx";

    private GraphicsBenchmarkReferenceScene() {
        throw new AssertionError("No instances");
    }

    public static Node build(AssetManager assetManager) {
        return build(assetManager, GraphicsQualityPreset.HIGH);
    }

    public static Node build(AssetManager assetManager, GraphicsQualityPreset preset) {
        Objects.requireNonNull(assetManager, "assetManager");
        Objects.requireNonNull(preset, "preset");
        Node root = new Node(ROOT_NAME);

        root.attachChild(buildTerrain(assetManager));
        root.attachChild(buildTeams(assetManager));
        root.attachChild(buildStructures(assetManager));
        root.attachChild(buildVegetation(assetManager, preset));
        root.attachChild(buildVfx(assetManager));
        addLights(root, preset);
        return root;
    }

    public static int vegetationInstanceCount(GraphicsQualityPreset preset) {
        Objects.requireNonNull(preset, "preset");
        int count =
                Math.toIntExact(Math.round(VEGETATION_INSTANCE_COUNT * preset.vegetationDensity()));
        if (count < 0 || count > VEGETATION_INSTANCE_COUNT) {
            throw new IllegalArgumentException("preset vegetation density is outside scene bounds");
        }
        return count;
    }

    public static int geometryCount(GraphicsQualityPreset preset) {
        return 1
                + TEAM_COUNT * PLAYERS_PER_TEAM
                + STRUCTURE_GEOMETRY_COUNT
                + vegetationInstanceCount(preset) * 2
                + VFX_PROXY_GEOMETRY_COUNT;
    }

    public static int teamPointLightCount(GraphicsQualityPreset preset) {
        Objects.requireNonNull(preset, "preset");
        return Math.min(TEAM_COUNT, preset.maximumDynamicLights());
    }

    public static int lightCount(GraphicsQualityPreset preset) {
        return NON_DYNAMIC_LIGHT_COUNT + teamPointLightCount(preset);
    }

    private static Node buildTerrain(AssetManager assetManager) {
        Node terrain = new Node(TERRAIN_NODE_NAME);
        Geometry ground = new Geometry("TerrainGround", new Box(24.0f, 0.25f, 24.0f));
        ground.setLocalTranslation(0.0f, -0.25f, 0.0f);
        ground.setMaterial(litMaterial(assetManager, new ColorRGBA(0.30f, 0.34f, 0.28f, 1.0f)));
        terrain.attachChild(ground);
        return terrain;
    }

    private static Node buildTeams(AssetManager assetManager) {
        Node teams = new Node(TEAMS_NODE_NAME);
        for (int teamIndex = 0; teamIndex < TEAM_COUNT; teamIndex++) {
            Node team = new Node("Team-" + teamIndex);
            Material material = litMaterial(assetManager, teamColor(teamIndex));
            float teamX = teamIndex % 2 == 0 ? -10.0f : 10.0f;
            float teamZ = teamIndex < 2 ? -10.0f : 10.0f;
            for (int playerIndex = 0; playerIndex < PLAYERS_PER_TEAM; playerIndex++) {
                Geometry player =
                        new Geometry(
                                "Team-" + teamIndex + "-Player-" + playerIndex,
                                new Box(0.35f, 0.9f, 0.35f));
                float offsetX = (playerIndex % 4 - 1.5f) * 1.2f;
                float offsetZ = (playerIndex / 4.0f - 0.5f) * 1.6f;
                player.setLocalTranslation(teamX + offsetX, 0.9f, teamZ + offsetZ);
                player.setMaterial(material);
                team.attachChild(player);
            }
            teams.attachChild(team);
        }
        return teams;
    }

    private static Node buildStructures(AssetManager assetManager) {
        Node structures = new Node(STRUCTURES_NODE_NAME);
        Material material = litMaterial(assetManager, new ColorRGBA(0.42f, 0.40f, 0.38f, 1.0f));
        for (int index = 0; index < STRUCTURE_GEOMETRY_COUNT; index++) {
            boolean tower = index % 5 == 0;
            Geometry structure =
                    new Geometry(
                            "Structure-" + index,
                            tower ? new Box(1.2f, 2.5f, 1.2f) : new Box(2.2f, 1.2f, 0.45f));
            float angle = (float) (Math.PI * 2.0d * index / STRUCTURE_GEOMETRY_COUNT);
            float radius = tower ? 15.0f : 12.5f;
            float y = tower ? 2.5f : 1.2f;
            structure.setLocalTranslation(
                    (float) Math.cos(angle) * radius, y, (float) Math.sin(angle) * radius);
            structure.rotate(0.0f, -angle, 0.0f);
            structure.setMaterial(material);
            structures.attachChild(structure);
        }
        return structures;
    }

    private static Node buildVegetation(AssetManager assetManager, GraphicsQualityPreset preset) {
        Node vegetation = new Node(VEGETATION_NODE_NAME);
        Material trunkMaterial =
                litMaterial(assetManager, new ColorRGBA(0.30f, 0.19f, 0.10f, 1.0f));
        Material canopyMaterial =
                litMaterial(assetManager, new ColorRGBA(0.18f, 0.42f, 0.16f, 1.0f));
        int vegetationCount = vegetationInstanceCount(preset);
        for (int index = 0; index < vegetationCount; index++) {
            Node plant = new Node("Vegetation-" + index);
            float angle = (float) (Math.PI * 2.0d * index / VEGETATION_INSTANCE_COUNT);
            float radius = 17.0f + (index % 4) * 1.25f;
            plant.setLocalTranslation(
                    (float) Math.cos(angle) * radius, 0.0f, (float) Math.sin(angle) * radius);

            Geometry trunk =
                    new Geometry("Vegetation-" + index + "-Trunk", new Box(0.18f, 1.1f, 0.18f));
            trunk.setLocalTranslation(0.0f, 1.1f, 0.0f);
            trunk.setMaterial(trunkMaterial);
            plant.attachChild(trunk);

            Geometry canopy =
                    new Geometry("Vegetation-" + index + "-Canopy", new Sphere(10, 12, 0.85f));
            canopy.setLocalTranslation(0.0f, 2.6f, 0.0f);
            canopy.setMaterial(canopyMaterial);
            plant.attachChild(canopy);
            vegetation.attachChild(plant);
        }
        return vegetation;
    }

    private static Node buildVfx(AssetManager assetManager) {
        Node vfx = new Node(VFX_NODE_NAME);
        Material material = transparentMaterial(assetManager);
        for (int index = 0; index < VFX_PROXY_GEOMETRY_COUNT; index++) {
            Geometry proxy = new Geometry("VfxProxy-" + index, new Box(0.08f, 1.6f, 2.4f));
            float angle = (float) (Math.PI * index / VFX_PROXY_GEOMETRY_COUNT);
            proxy.setLocalTranslation(
                    (float) Math.cos(angle) * 3.0f,
                    1.8f + (index % 3) * 0.25f,
                    (float) Math.sin(angle) * 3.0f);
            proxy.rotate(0.0f, angle, 0.0f);
            proxy.setMaterial(material);
            proxy.setQueueBucket(RenderQueue.Bucket.Transparent);
            vfx.attachChild(proxy);
        }
        return vfx;
    }

    private static void addLights(Node root, GraphicsQualityPreset preset) {
        AmbientLight ambient = new AmbientLight();
        ambient.setName("BenchmarkAmbient");
        ambient.setColor(new ColorRGBA(0.28f, 0.28f, 0.30f, 1.0f));
        root.addLight(ambient);

        DirectionalLight sun = new DirectionalLight();
        sun.setName("BenchmarkSun");
        sun.setColor(new ColorRGBA(0.85f, 0.82f, 0.76f, 1.0f));
        sun.setDirection(new Vector3f(-1.0f, -2.0f, -1.0f).normalizeLocal());
        root.addLight(sun);

        int teamLightCount = teamPointLightCount(preset);
        for (int teamIndex = 0; teamIndex < teamLightCount; teamIndex++) {
            PointLight point = new PointLight();
            point.setName("BenchmarkTeamLight-" + teamIndex);
            point.setColor(teamColor(teamIndex));
            point.setPosition(
                    new Vector3f(
                            teamIndex % 2 == 0 ? -10.0f : 10.0f,
                            5.0f,
                            teamIndex < 2 ? -10.0f : 10.0f));
            point.setRadius(16.0f);
            root.addLight(point);
        }
    }

    private static Material litMaterial(AssetManager assetManager, ColorRGBA color) {
        Material material = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        material.setBoolean("UseMaterialColors", true);
        material.setColor("Diffuse", color);
        material.setColor(
                "Ambient", new ColorRGBA(color.r * 0.45f, color.g * 0.45f, color.b * 0.45f, 1.0f));
        material.setColor("Specular", new ColorRGBA(0.08f, 0.08f, 0.08f, 1.0f));
        material.setFloat("Shininess", 4.0f);
        return material;
    }

    private static Material transparentMaterial(AssetManager assetManager) {
        Material material = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setColor("Color", new ColorRGBA(0.95f, 0.55f, 0.12f, 0.18f));
        material.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        material.getAdditionalRenderState().setDepthWrite(false);
        return material;
    }

    private static ColorRGBA teamColor(int teamIndex) {
        return switch (teamIndex) {
            case 0 -> new ColorRGBA(0.80f, 0.16f, 0.16f, 1.0f);
            case 1 -> new ColorRGBA(0.18f, 0.35f, 0.86f, 1.0f);
            case 2 -> new ColorRGBA(0.16f, 0.70f, 0.30f, 1.0f);
            case 3 -> new ColorRGBA(0.90f, 0.72f, 0.16f, 1.0f);
            default ->
                    throw new IllegalArgumentException("teamIndex is outside the benchmark range");
        };
    }
}
