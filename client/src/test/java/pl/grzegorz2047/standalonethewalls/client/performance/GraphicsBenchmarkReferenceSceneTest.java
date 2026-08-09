package pl.grzegorz2047.standalonethewalls.client.performance;

import static org.assertj.core.api.Assertions.assertThat;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.light.Light;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GraphicsBenchmarkReferenceSceneTest {
    @Test
    void buildsTheFullRepresentativeSceneWithoutARenderContext() {
        DesktopAssetManager assetManager = new DesktopAssetManager(true);

        Node scene = GraphicsBenchmarkReferenceScene.build(assetManager);

        assertThat(GraphicsBenchmarkReferenceScene.SCENARIO_VERSION).isEqualTo(2);
        assertThat(scene.getName()).isEqualTo(GraphicsBenchmarkReferenceScene.ROOT_NAME);
        assertThat(scene.getParent()).isNull();
        assertThat(scene.getQuantity()).isEqualTo(5);
        assertThat(countGeometries(scene))
                .isEqualTo(GraphicsBenchmarkReferenceScene.TOTAL_GEOMETRY_COUNT);
        assertThat(scene.getLocalLightList()).hasSize(GraphicsBenchmarkReferenceScene.LIGHT_COUNT);

        Node terrain = childNode(scene, GraphicsBenchmarkReferenceScene.TERRAIN_NODE_NAME);
        assertThat(terrain.getQuantity()).isOne();

        Node teams = childNode(scene, GraphicsBenchmarkReferenceScene.TEAMS_NODE_NAME);
        assertThat(teams.getQuantity()).isEqualTo(GraphicsBenchmarkReferenceScene.TEAM_COUNT);
        for (int teamIndex = 0;
                teamIndex < GraphicsBenchmarkReferenceScene.TEAM_COUNT;
                teamIndex++) {
            Node team = childNode(teams, "Team-" + teamIndex);
            assertThat(team.getQuantity())
                    .isEqualTo(GraphicsBenchmarkReferenceScene.PLAYERS_PER_TEAM);
        }

        Node structures = childNode(scene, GraphicsBenchmarkReferenceScene.STRUCTURES_NODE_NAME);
        assertThat(structures.getQuantity())
                .isEqualTo(GraphicsBenchmarkReferenceScene.STRUCTURE_GEOMETRY_COUNT);

        Node vegetation = childNode(scene, GraphicsBenchmarkReferenceScene.VEGETATION_NODE_NAME);
        assertThat(vegetation.getQuantity())
                .isEqualTo(GraphicsBenchmarkReferenceScene.VEGETATION_INSTANCE_COUNT);
        assertThat(countGeometries(vegetation))
                .isEqualTo(GraphicsBenchmarkReferenceScene.VEGETATION_GEOMETRY_COUNT);

        Node vfx = childNode(scene, GraphicsBenchmarkReferenceScene.VFX_NODE_NAME);
        assertThat(vfx.getQuantity())
                .isEqualTo(GraphicsBenchmarkReferenceScene.VFX_PROXY_GEOMETRY_COUNT);
        assertThat(vfx.getChildren())
                .allSatisfy(GraphicsBenchmarkReferenceSceneTest::assertTransparent);

        assertThat(fingerprint(scene))
                .isEqualTo(
                        fingerprint(
                                GraphicsBenchmarkReferenceScene.build(
                                        assetManager, GraphicsQualityPreset.HIGH)));
    }

    @Test
    void measuredPresetsApplyDeterministicVegetationDensityWithoutRemovingCoreWorkload() {
        DesktopAssetManager assetManager = new DesktopAssetManager(true);

        assertPresetWorkload(assetManager, GraphicsQualityPreset.LOW, 16);
        assertPresetWorkload(assetManager, GraphicsQualityPreset.MEDIUM, 24);
        assertPresetWorkload(assetManager, GraphicsQualityPreset.HIGH, 32);
    }

    @Test
    void independentBuildsHaveTheSameFingerprintWithoutSharingSceneObjects() {
        DesktopAssetManager assetManager = new DesktopAssetManager(true);

        for (GraphicsQualityPreset preset : GraphicsQualityPreset.values()) {
            Node first = GraphicsBenchmarkReferenceScene.build(assetManager, preset);
            Node second = GraphicsBenchmarkReferenceScene.build(assetManager, preset);
            Node firstTeams = childNode(first, GraphicsBenchmarkReferenceScene.TEAMS_NODE_NAME);
            Node secondTeams = childNode(second, GraphicsBenchmarkReferenceScene.TEAMS_NODE_NAME);
            Spatial firstPlayer = childNode(firstTeams, "Team-0").getChild(0);
            Spatial secondPlayer = childNode(secondTeams, "Team-0").getChild(0);

            assertThat(fingerprint(second)).isEqualTo(fingerprint(first));
            assertThat(second).isNotSameAs(first);
            assertThat(second.getChild(GraphicsBenchmarkReferenceScene.TERRAIN_NODE_NAME))
                    .isNotSameAs(first.getChild(GraphicsBenchmarkReferenceScene.TERRAIN_NODE_NAME));
            assertThat(secondPlayer).isNotSameAs(firstPlayer);
        }
    }

    @Test
    void fullLightSetIsNamedAndContainsAllRequiredWorkloadTypes() {
        Node scene = GraphicsBenchmarkReferenceScene.build(new DesktopAssetManager(true));
        List<String> lightNames = new ArrayList<>();
        List<Light.Type> lightTypes = new ArrayList<>();
        for (Light light : scene.getLocalLightList()) {
            lightNames.add(light.getName());
            lightTypes.add(light.getType());
        }

        assertThat(lightNames)
                .containsExactly(
                        "BenchmarkAmbient",
                        "BenchmarkSun",
                        "BenchmarkTeamLight-0",
                        "BenchmarkTeamLight-1",
                        "BenchmarkTeamLight-2",
                        "BenchmarkTeamLight-3");
        assertThat(lightTypes)
                .containsExactly(
                        Light.Type.Ambient,
                        Light.Type.Directional,
                        Light.Type.Point,
                        Light.Type.Point,
                        Light.Type.Point,
                        Light.Type.Point);
    }

    private static void assertPresetWorkload(
            DesktopAssetManager assetManager,
            GraphicsQualityPreset preset,
            int expectedVegetationInstances) {
        Node scene = GraphicsBenchmarkReferenceScene.build(assetManager, preset);
        Node teams = childNode(scene, GraphicsBenchmarkReferenceScene.TEAMS_NODE_NAME);
        Node structures = childNode(scene, GraphicsBenchmarkReferenceScene.STRUCTURES_NODE_NAME);
        Node vegetation = childNode(scene, GraphicsBenchmarkReferenceScene.VEGETATION_NODE_NAME);
        Node vfx = childNode(scene, GraphicsBenchmarkReferenceScene.VFX_NODE_NAME);

        assertThat(GraphicsBenchmarkReferenceScene.vegetationInstanceCount(preset))
                .isEqualTo(expectedVegetationInstances);
        assertThat(vegetation.getQuantity()).isEqualTo(expectedVegetationInstances);
        assertThat(countGeometries(vegetation)).isEqualTo(expectedVegetationInstances * 2);
        assertThat(countGeometries(scene))
                .isEqualTo(GraphicsBenchmarkReferenceScene.geometryCount(preset));
        assertThat(teams.getQuantity()).isEqualTo(GraphicsBenchmarkReferenceScene.TEAM_COUNT);
        assertThat(teams.getChildren())
                .allSatisfy(
                        team ->
                                assertThat(((Node) team).getQuantity())
                                        .isEqualTo(GraphicsBenchmarkReferenceScene.PLAYERS_PER_TEAM));
        assertThat(structures.getQuantity())
                .isEqualTo(GraphicsBenchmarkReferenceScene.STRUCTURE_GEOMETRY_COUNT);
        assertThat(vfx.getQuantity())
                .isEqualTo(GraphicsBenchmarkReferenceScene.VFX_PROXY_GEOMETRY_COUNT);
        assertThat(GraphicsBenchmarkReferenceScene.teamPointLightCount(preset))
                .isLessThanOrEqualTo(preset.maximumDynamicLights());
        assertThat(scene.getLocalLightList())
                .hasSize(GraphicsBenchmarkReferenceScene.lightCount(preset));
    }

    private static void assertTransparent(Spatial spatial) {
        assertThat(spatial.getQueueBucket()).isEqualTo(RenderQueue.Bucket.Transparent);
    }

    private static Node childNode(Node parent, String name) {
        return (Node) parent.getChild(name);
    }

    private static int countGeometries(Spatial spatial) {
        if (spatial instanceof Geometry) {
            return 1;
        }
        if (!(spatial instanceof Node node)) {
            return 0;
        }
        int count = 0;
        for (Spatial child : node.getChildren()) {
            count += countGeometries(child);
        }
        return count;
    }

    private static List<String> fingerprint(Spatial spatial) {
        List<String> fingerprint = new ArrayList<>();
        appendFingerprint(spatial, fingerprint);
        return fingerprint;
    }

    private static void appendFingerprint(Spatial spatial, List<String> output) {
        String typeAndName = spatial.getClass().getSimpleName() + ":" + spatial.getName();
        output.add(typeAndName + "@" + spatial.getLocalTranslation());
        if (spatial instanceof Node node) {
            for (Spatial child : node.getChildren()) {
                appendFingerprint(child, output);
            }
        }
    }
}
