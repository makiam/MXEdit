package mx;

import java.awt.Color;

/**
 * Centralized theme and configuration for the MaterialX Node Editor UI.
 * All colors, stroke widths, and layout constants are defined here for easy customization.
 */
public final class MaterialXTheme {
    private MaterialXTheme() {} // Prevent instantiation

    // ==========================================
    // COLORS
    // ==========================================
    public static final Color BACKGROUND_COLOR = new Color(125, 125, 125);
    public static final Color GRID_COLOR = new Color(140, 140, 140);
    public static final Color CYCLE_WARNING_COLOR = new Color(255, 0, 0, 60);

    // Node Colors
    public static final Color NODE_BG_COLOR = new Color(245, 245, 245);
    public static final Color NODE_STROKE_COLOR = new Color(40, 40, 40);
    public static final Color HOVER_STROKE_COLOR = new Color(150, 150, 150);
    public static final Color SELECTED_STROKE_COLOR = new Color(0, 122, 255);
    public static final Color HOVER_OVERLAY = new Color(255, 255, 255, 25);
    public static final Color SELECTED_OVERLAY = new Color(0, 122, 255, 30);

    // Link Colors
    public static final Color DEFAULT_LINK_COLOR = new Color(80, 80, 80);
    public static final Color HOVER_LINK_COLOR = new Color(130, 180, 255);
    public static final Color SELECTED_LINK_COLOR = new Color(255, 165, 0);
    public static final Color PREVIEW_LINK_COLOR = new Color(255, 255, 255, 150);

    // ==========================================
    // NODE DIMENSIONS
    // ==========================================
    public static final double NODE_WIDTH = 180.0;
    public static final double HEADER_HEIGHT = 25.0;
    public static final double PORT_SPACING = 24.0;
    public static final double PORT_RADIUS = 5.0;

    // ==========================================
    // INTERACTION & HIT AREAS
    // ==========================================
    public static final double PORT_HIT_RADIUS_SCREEN = 12.0;
    public static final float LINK_HIT_WIDTH = 12.0f;

    // ==========================================
    // STROKE WIDTHS
    // ==========================================
    public static final float PORT_STROKE_WIDTH = 1.0f;
    public static final float NODE_DEFAULT_STROKE_WIDTH = 1.5f;
    public static final float NODE_HOVER_STROKE_WIDTH = 2.0f;
    public static final float NODE_SELECTED_STROKE_WIDTH = 2.5f;

    public static final float LINK_DEFAULT_STROKE_WIDTH = 2.5f;
    public static final float LINK_HOVER_STROKE_WIDTH = 3.5f;
    public static final float LINK_SELECTED_STROKE_WIDTH = 4.0f;

    // ==========================================
    // ZOOM LIMITS
    // ==========================================
    public static final double MIN_ZOOM = 0.1;
    public static final double MAX_ZOOM = 5.0;
}
