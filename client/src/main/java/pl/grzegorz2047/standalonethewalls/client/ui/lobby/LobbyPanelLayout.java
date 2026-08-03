package pl.grzegorz2047.standalonethewalls.client.ui.lobby;

/** Stable responsive arrangement for the four team panels. */
public enum LobbyPanelLayout {
    TWO_BY_TWO(2, 2),
    FOUR_COLUMNS(4, 1);

    private static final float FOUR_COLUMN_MINIMUM_WIDTH = 960f;

    private final int columns;
    private final int rows;

    LobbyPanelLayout(int columns, int rows) {
        this.columns = columns;
        this.rows = rows;
    }

    public int columns() {
        return columns;
    }

    public int rows() {
        return rows;
    }

    public static LobbyPanelLayout forViewportWidth(float width) {
        if (!Float.isFinite(width) || width <= 0f) {
            throw new IllegalArgumentException("viewport width must be finite and positive");
        }
        return width >= FOUR_COLUMN_MINIMUM_WIDTH ? FOUR_COLUMNS : TWO_BY_TWO;
    }
}
