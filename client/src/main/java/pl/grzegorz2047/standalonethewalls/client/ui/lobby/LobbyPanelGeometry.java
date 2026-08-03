package pl.grzegorz2047.standalonethewalls.client.ui.lobby;

import java.util.ArrayList;
import java.util.List;
import pl.grzegorz2047.standalonethewalls.client.ui.pointer.UiRect;

/** Responsive, renderer-independent rectangles for the four stable team panels. */
public record LobbyPanelGeometry(LobbyPanelLayout layout, List<UiRect> panels) {
    private static final float HORIZONTAL_MARGIN = 32f;
    private static final float PANEL_GAP = 16f;
    private static final float TOP_RESERVED = 145f;
    private static final float BOTTOM_RESERVED = 230f;

    public LobbyPanelGeometry {
        if (panels.size() != ConnectedLobbyModel.DISPLAY_TEAM_ORDER.size()) {
            throw new IllegalArgumentException("team panel geometry requires four rectangles");
        }
        panels = List.copyOf(panels);
    }

    public static LobbyPanelGeometry forViewport(float width, float height) {
        if (!Float.isFinite(width) || !Float.isFinite(height) || width <= 0f || height <= 0f) {
            throw new IllegalArgumentException("viewport dimensions must be finite and positive");
        }
        LobbyPanelLayout layout = LobbyPanelLayout.forViewportWidth(width);
        float availableWidth =
                width - (2f * HORIZONTAL_MARGIN) - ((layout.columns() - 1) * PANEL_GAP);
        float top = height - TOP_RESERVED;
        float availableHeight = top - BOTTOM_RESERVED - ((layout.rows() - 1) * PANEL_GAP);
        if (availableWidth <= 0f || availableHeight <= 0f) {
            throw new IllegalArgumentException("viewport is too small for team lobby panels");
        }
        float panelWidth = availableWidth / layout.columns();
        float panelHeight = availableHeight / layout.rows();
        List<UiRect> panels = new ArrayList<>(ConnectedLobbyModel.DISPLAY_TEAM_ORDER.size());
        for (int index = 0; index < ConnectedLobbyModel.DISPLAY_TEAM_ORDER.size(); index++) {
            int column = index % layout.columns();
            int row = index / layout.columns();
            float left = HORIZONTAL_MARGIN + (column * (panelWidth + PANEL_GAP));
            float bottom = top - ((row + 1) * panelHeight) - (row * PANEL_GAP);
            panels.add(new UiRect(left, bottom, panelWidth, panelHeight));
        }
        return new LobbyPanelGeometry(layout, panels);
    }
}
