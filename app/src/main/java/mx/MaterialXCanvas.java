package mx;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;

public class MaterialXCanvas extends JPanel {

    // --- Camera State ---
    private double panX = 0, panY = 0, zoom = 1.0;
    private boolean isPanning = false;
    private boolean isSpacePressed = false;

    // --- Graph Data ---
    private final List<MxNode> nodes = new ArrayList<>();
    private final List<Link> links = new ArrayList<>();

    // --- Selection & Interaction State ---
    private final Set<MxNode> selectedNodes = new LinkedHashSet<>();
    private final Set<Link> selectedLinks = new LinkedHashSet<>();
    private final Map<MxNode, Point2D> dragOffsets = new HashMap<>();

    private IOPort dragStartPort = null;
    private Point2D linkDragWorldPos = null;

    // --- Hover State ---
    private MxNode hoveredNode = null;
    private Link hoveredLink = null;

    private boolean cycleWarningFlash = false;
    private long cycleWarningTime = 0;

    private int lastMouseX = 0, lastMouseY = 0;

    public MaterialXCanvas() {
        setBackground(MaterialXTheme.BACKGROUND_COLOR);
        setDoubleBuffered(true);
        setFocusable(true);
        setupDummyGraph();
        setupInputListeners();
    }

    // ==========================================
    // 1. DUMMY DATA
    // ==========================================
    private void setupDummyGraph() {
        MxNode texcoord = new MxNode("texcoord", -300, 100, new Color(80, 160, 80));
        texcoord.addOutput("out", new Color(120, 120, 200));

        MxNode image = new MxNode("image", -50, 50, new Color(180, 80, 80));
        image.addInput("uv", new Color(120, 120, 200));
        image.addOutput("out_color", new Color(220, 200, 80));

        MxNode surface = new MxNode("standard_surface", 250, 100, new Color(80, 120, 200));
        surface.addInput("base_color", new Color(220, 200, 80));
        surface.addOutput("out", new Color(80, 200, 100));

        var time = new MxNode("Time", 50, -50, new Color(50, 50, 50));
        time.addOutput("Time", new Color(50, 50, 50));

        nodes.add(texcoord);
        nodes.add(image);
        nodes.add(surface);
        nodes.add(time);

        links.add(new Link(texcoord.getOutput("out"), image.getInput("uv")));
        links.add(new Link(image.getOutput("out_color"), surface.getInput("base_color")));
    }

    // ==========================================
    // 2. INPUT HANDLING & STATE MACHINE
    // ==========================================
    private void setupInputListeners() {
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                lastMouseX = e.getX();
                lastMouseY = e.getY();

                Point mouseScreen = e.getPoint();
                Point2D worldPos = screenToWorld(mouseScreen);

                if (SwingUtilities.isMiddleMouseButton(e) || (isSpacePressed && SwingUtilities.isLeftMouseButton(e))) {
                    isPanning = true;
                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                    return;
                }

                if (SwingUtilities.isLeftMouseButton(e)) {
                    boolean shift = e.isShiftDown();

                    // 1. Check ALL Ports (Output or Input) to start link creation
                    for (MxNode n : nodes) {
                        for (IOPort p : n.outputs) {
                            if (isPortHit(p, mouseScreen)) {
                                dragStartPort = p;
                                linkDragWorldPos = worldPos;
                                setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
                                return;
                            }
                        }
                        for (IOPort p : n.inputs) {
                            if (isPortHit(p, mouseScreen)) {
                                dragStartPort = p;
                                linkDragWorldPos = worldPos;
                                setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
                                return;
                            }
                        }
                    }

                    // 2. Check Link Selection (Multi-select supported)
                    Link hitLink = findLinkAt(mouseScreen);
                    if (hitLink != null) {
                        selectedNodes.clear();
                        if (shift) {
                            if (selectedLinks.contains(hitLink)) selectedLinks.remove(hitLink);
                            else selectedLinks.add(hitLink);
                        } else {
                            if (!selectedLinks.contains(hitLink)) {
                                selectedLinks.clear();
                                selectedLinks.add(hitLink);
                            }
                        }
                        hoveredLink = null;
                        repaint();
                        return;
                    }

                    // 3. Check Node Hit / Multi-Selection
                    MxNode hitNode = null;
                    for (int i = nodes.size() - 1; i >= 0; i--) {
                        if (nodes.get(i).contains(worldPos)) { hitNode = nodes.get(i); break; }
                    }

                    if (hitNode != null) {
                        selectedLinks.clear();

                        if (!selectedNodes.contains(hitNode) && !shift) {
                            selectedNodes.clear();
                        }

                        if (shift) {
                            if (selectedNodes.contains(hitNode)) selectedNodes.remove(hitNode);
                            else selectedNodes.add(hitNode);
                        } else {
                            selectedNodes.add(hitNode);
                        }

                        dragOffsets.clear();
                        for (MxNode n : selectedNodes) {
                            dragOffsets.put(n, new Point2D.Double(worldPos.getX() - n.x, worldPos.getY() - n.y));
                        }
                        bringSelectedToFront();
                    } else {
                        selectedNodes.clear();
                        selectedLinks.clear();
                    }
                }
                repaint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                int dx = e.getX() - lastMouseX;
                int dy = e.getY() - lastMouseY;

                if (isPanning) {
                    panX += dx;
                    panY += dy;
                    lastMouseX = e.getX();
                    lastMouseY = e.getY();
                    repaint();
                    return;
                }

                Point2D worldPos = screenToWorld(e.getPoint());

                if (dragStartPort != null) {
                    linkDragWorldPos = worldPos;
                    lastMouseX = e.getX();
                    lastMouseY = e.getY();
                    repaint();
                    return;
                }

                if (!selectedNodes.isEmpty() && dragOffsets.size() == selectedNodes.size()) {
                    for (MxNode n : selectedNodes) {
                        Point2D off = dragOffsets.get(n);
                        n.x = worldPos.getX() - off.getX();
                        n.y = worldPos.getY() - off.getY();
                    }
                    lastMouseX = e.getX();
                    lastMouseY = e.getY();
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (dragStartPort != null && linkDragWorldPos != null) {
                    Point mouseScreen = e.getPoint();

                    if (dragStartPort.isOutput) {
                        // Dragging from Output -> Looking for Input
                        IOPort targetInput = findInputPortAt(mouseScreen);
                        if (targetInput != null && targetInput != dragStartPort) {
                            boolean isOccupied = links.stream().anyMatch(l -> l.target == targetInput);
                            if (!isOccupied &&
                                    targetInput.owner != dragStartPort.owner &&
                                    !wouldCreateCycle(dragStartPort.owner, targetInput.owner)) {
                                links.add(new Link(dragStartPort, targetInput));
                            } else {
                                triggerCycleWarning();
                            }
                        }
                    } else {
                        // Dragging from Input -> Looking for Output
                        IOPort targetOutput = findOutputPortAt(mouseScreen);
                        if (targetOutput != null && targetOutput != dragStartPort) {
                            if (targetOutput.owner != dragStartPort.owner &&
                                    !wouldCreateCycle(targetOutput.owner, dragStartPort.owner)) {
                                // If input was already connected, remove old link (reconnect)
                                links.removeIf(l -> l.target == dragStartPort);
                                links.add(new Link(targetOutput, dragStartPort));
                            } else {
                                triggerCycleWarning();
                            }
                        }
                    }

                    dragStartPort = null;
                    linkDragWorldPos = null;
                    setCursor(Cursor.getDefaultCursor());
                    repaint();
                    return;
                }

                isPanning = false;
                dragOffsets.clear();
                setCursor(Cursor.getDefaultCursor());
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                double oldZoom = zoom;
                double factor = e.getWheelRotation() > 0 ? 1 / 1.1 : 1.1;
                zoom = Math.max(MaterialXTheme.MIN_ZOOM, Math.min(MaterialXTheme.MAX_ZOOM, zoom * factor));

                double mx = e.getX(), my = e.getY();
                panX = mx - (mx - panX) * (zoom / oldZoom);
                panY = my - (my - panY) * (zoom / oldZoom);
                repaint();
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                lastMouseX = e.getX();
                lastMouseY = e.getY();

                if (isPanning || dragStartPort != null) return;

                Point2D worldPos = screenToWorld(e.getPoint());
                Point mouseScreen = e.getPoint();

                MxNode newHoveredNode = null;
                for (int i = nodes.size() - 1; i >= 0; i--) {
                    if (nodes.get(i).contains(worldPos)) {
                        newHoveredNode = nodes.get(i);
                        break;
                    }
                }

                Link newHoveredLink = null;
                if (newHoveredNode == null) {
                    newHoveredLink = findLinkAt(mouseScreen);
                }

                if (selectedLinks.contains(newHoveredLink)) {
                    newHoveredLink = null;
                }

                boolean changed = (newHoveredNode != hoveredNode || newHoveredLink != hoveredLink);
                hoveredNode = newHoveredNode;
                hoveredLink = newHoveredLink;

                if (changed) {
                    setCursor((hoveredNode != null || hoveredLink != null) ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
                    repaint();
                }
            }
        };

        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);

        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_SPACE) {
                    isSpacePressed = true;
                    if (!isPanning) setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                }

                if (e.getKeyCode() == KeyEvent.VK_DELETE || e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
                    boolean graphChanged = false;

                    if (!selectedLinks.isEmpty()) {
                        links.removeAll(selectedLinks);
                        selectedLinks.clear();
                        graphChanged = true;
                    }

                    if (!selectedNodes.isEmpty()) {
                        List<Link> connectedLinks = new ArrayList<>();
                        for (Link l : links) {
                            if (selectedNodes.contains(l.source.owner) || selectedNodes.contains(l.target.owner)) {
                                connectedLinks.add(l);
                            }
                        }
                        links.removeAll(connectedLinks);
                        nodes.removeAll(selectedNodes);
                        selectedNodes.clear();
                        graphChanged = true;
                    }

                    if (graphChanged) {
                        hoveredNode = null;
                        hoveredLink = null;
                        dragOffsets.clear();
                        repaint();
                    }
                }

                if (e.getKeyCode() == KeyEvent.VK_F) {
                    panX = getWidth()/2.0; panY = getHeight()/2.0; zoom=1.0; repaint();
                }
            }
            @Override public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_SPACE) {
                    isSpacePressed = false;
                    if (!isPanning) setCursor(Cursor.getDefaultCursor());
                }
            }
        });
    }

    // ==========================================
    // 3. RENDERING
    // ==========================================
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        drawGrid(g2d);

        AffineTransform camera = new AffineTransform();
        camera.translate(panX, panY);
        camera.scale(zoom, zoom);
        g2d.setTransform(camera);

        for (Link link : links) {
            CubicCurve2D curve = createLinkCurve(link.source.getWorldCenterX(), link.source.getWorldCenterY(), link.target.getWorldCenterX(), link.target.getWorldCenterY());
            drawCurve(g2d, curve, false, selectedLinks.contains(link), link == hoveredLink);
        }

        if (dragStartPort != null && linkDragWorldPos != null) {
            double x1, y1, x2, y2;
            if (dragStartPort.isOutput) {
                x1 = dragStartPort.getWorldCenterX(); y1 = dragStartPort.getWorldCenterY();
                x2 = linkDragWorldPos.getX(); y2 = linkDragWorldPos.getY();
            } else {
                x1 = linkDragWorldPos.getX(); y1 = linkDragWorldPos.getY();
                x2 = dragStartPort.getWorldCenterX(); y2 = dragStartPort.getWorldCenterY();
            }
            CubicCurve2D curve = createLinkCurve(x1, y1, x2, y2);
            drawCurve(g2d, curve, true, false, false);
        }

        for (MxNode node : nodes) drawNode(g2d, node);

        g2d.dispose();

        if (cycleWarningFlash && System.currentTimeMillis() - cycleWarningTime < 800) {
            Graphics2D g2dScreen = (Graphics2D) g.create();
            g2dScreen.setColor(MaterialXTheme.CYCLE_WARNING_COLOR);
            g2dScreen.fillRect(0, 0, getWidth(), getHeight());
            g2dScreen.dispose();
            repaint();
        }
    }

    private void drawGrid(Graphics2D g2d) {
        double gridSize = 40.0;
        while (gridSize * zoom < 15) gridSize *= 4;
        Point2D tl = screenToWorld(new Point(0, 0));
        Point2D br = screenToWorld(new Point(getWidth(), getHeight()));
        int sx = (int)(Math.floor(tl.getX()/gridSize)*gridSize), ex = (int)(Math.ceil(br.getX()/gridSize)*gridSize);
        int sy = (int)(Math.floor(tl.getY()/gridSize)*gridSize), ey = (int)(Math.ceil(br.getY()/gridSize)*gridSize);

        g2d.setColor(MaterialXTheme.GRID_COLOR);
        for (double x = sx; x <= ex; x += gridSize) {
            Point p1 = worldToScreen(new Point2D.Double(x, tl.getY())), p2 = worldToScreen(new Point2D.Double(x, br.getY()));
            g2d.drawLine(p1.x, p1.y, p2.x, p2.y);
        }
        for (double y = sy; y <= ey; y += gridSize) {
            Point p1 = worldToScreen(new Point2D.Double(tl.getX(), y)), p2 = worldToScreen(new Point2D.Double(br.getX(), y));
            g2d.drawLine(p1.x, p1.y, p2.x, p2.y);
        }

        // Draw Origin Axes (X = Red, Y = Green)
        Point origin = worldToScreen(new Point2D.Double(0, 0));
        g2d.setColor(new Color(200, 50, 50, 100));
        g2d.drawLine(0, origin.y, getWidth(), origin.y);
        g2d.setColor(new Color(50, 200, 50, 100));
        g2d.drawLine(origin.x, 0, origin.x, getHeight());
    }

    private void drawNode(Graphics2D g2d, MxNode node) {
        boolean isSelected = selectedNodes.contains(node);
        boolean isHovered = node == hoveredNode;
        double h = node.getHeight(), w = MaterialXTheme.NODE_WIDTH;

        if (isSelected) {
            g2d.setColor(MaterialXTheme.SELECTED_OVERLAY);
            g2d.fill(new RoundRectangle2D.Double(node.x - 2, node.y - 2, w + 4, h + 4, 12, 12));
        }

        g2d.setColor(MaterialXTheme.NODE_BG_COLOR);
        g2d.fill(new RoundRectangle2D.Double(node.x, node.y, w, h, 10, 10));

        g2d.setColor(node.headerColor);
        g2d.fill(new RoundRectangle2D.Double(node.x, node.y, w, MaterialXTheme.HEADER_HEIGHT, 10, 10));
        g2d.fill(new Rectangle2D.Double(node.x, node.y + 15, w, 10));

        g2d.setColor(MaterialXTheme.NODE_STROKE_COLOR);
        g2d.setStroke(new BasicStroke(MaterialXTheme.NODE_DEFAULT_STROKE_WIDTH));
        g2d.draw(new RoundRectangle2D.Double(node.x, node.y, w, h, 10, 10));

        g2d.setColor(Color.DARK_GRAY);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 12));
        g2d.drawString(node.name, (float)(node.x + 10), (float)(node.y + 17));

        g2d.setFont(new Font("SansSerif", Font.PLAIN, 11));
        for (IOPort p : node.inputs) {
            g2d.drawString(p.name, (float)(node.x + 15), (float)(p.getWorldCenterY() + 4));
        }
        for (IOPort p : node.outputs) {
            FontMetrics fm = g2d.getFontMetrics();
            g2d.drawString(p.name, (float)(node.x + w - 15 - fm.stringWidth(p.name)), (float)(p.getWorldCenterY() + 4));
        }

        if (isHovered && !isSelected) {
            g2d.setColor(MaterialXTheme.HOVER_OVERLAY);
            g2d.fill(new RoundRectangle2D.Double(node.x, node.y, w, h, 10, 10));
            g2d.setColor(MaterialXTheme.HOVER_STROKE_COLOR);
            g2d.setStroke(new BasicStroke(MaterialXTheme.NODE_HOVER_STROKE_WIDTH));
            g2d.draw(new RoundRectangle2D.Double(node.x, node.y, w, h, 10, 10));
        }

        if (isSelected) {
            g2d.setColor(MaterialXTheme.SELECTED_STROKE_COLOR);
            g2d.setStroke(new BasicStroke(MaterialXTheme.NODE_SELECTED_STROKE_WIDTH));
            g2d.draw(new RoundRectangle2D.Double(node.x - 2, node.y - 2, w + 4, h + 4, 12, 12));
        }

        for (IOPort p : node.inputs) drawPort(g2d, p, node);
        for (IOPort p : node.outputs) drawPort(g2d, p, node);
    }

    private void drawPort(Graphics2D g2d, IOPort port, MxNode owner) {
        double cx = port.getWorldCenterX(), cy = port.getWorldCenterY();
        double r = MaterialXTheme.PORT_RADIUS;

        g2d.setColor(port.typeColor);
        g2d.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
        g2d.setColor(Color.BLACK);
        g2d.setStroke(new BasicStroke(MaterialXTheme.PORT_STROKE_WIDTH));
        g2d.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));

        g2d.setColor(Color.WHITE);
        if (!port.isOutput) {
            g2d.fill(new Ellipse2D.Double(cx - 1.5, cy - 1.5, 3, 3));
        } else {
            g2d.setStroke(new BasicStroke(1.2f));
            g2d.draw(new Ellipse2D.Double(cx - 2, cy - 2, 4, 4));
        }
    }

    private void drawCurve(Graphics2D g2d, CubicCurve2D curve, boolean isPreview, boolean isSelected, boolean isHovered) {
        Color color;
        float width;
        if (isSelected) {
            color = MaterialXTheme.SELECTED_LINK_COLOR;
            width = MaterialXTheme.LINK_SELECTED_STROKE_WIDTH;
        } else if (isHovered) {
            color = MaterialXTheme.HOVER_LINK_COLOR;
            width = MaterialXTheme.LINK_HOVER_STROKE_WIDTH;
        } else if (isPreview) {
            color = MaterialXTheme.PREVIEW_LINK_COLOR;
            width = MaterialXTheme.LINK_DEFAULT_STROKE_WIDTH;
        } else {
            color = MaterialXTheme.DEFAULT_LINK_COLOR;
            width = MaterialXTheme.LINK_DEFAULT_STROKE_WIDTH;
        }

        g2d.setColor(color);
        g2d.setStroke(new BasicStroke(width));
        g2d.draw(curve);
    }

    // ==========================================
    // 4. HIT-TESTING & UTILITIES
    // ==========================================
    /** Unifies Bezier curve generation for both Rendering and Hit-Testing */
    private static CubicCurve2D createLinkCurve(double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dist = Math.max(40.0, Math.abs(dx) * 0.5);
        double ctrlX1 = x1 + (dx >= 0 ? dist : -dist);
        double ctrlX2 = x2 + (dx >= 0 ? -dist : dist);
        return new CubicCurve2D.Double(x1, y1, ctrlX1, y1, ctrlX2, y2, x2, y2);
    }

    private boolean isPortHit(IOPort port, Point mouseScreen) {
        Point portScreen = worldToScreen(new Point2D.Double(port.getWorldCenterX(), port.getWorldCenterY()));
        return portScreen.distance(mouseScreen) <= MaterialXTheme.PORT_HIT_RADIUS_SCREEN;
    }

    private IOPort findInputPortAt(Point mouseScreen) {
        for (MxNode n : nodes) {
            for (IOPort p : n.inputs) {
                if (isPortHit(p, mouseScreen)) return p;
            }
        }
        return null;
    }

    private IOPort findOutputPortAt(Point mouseScreen) {
        for (MxNode n : nodes) {
            for (IOPort p : n.outputs) {
                if (isPortHit(p, mouseScreen)) return p;
            }
        }
        return null;
    }

    private Link findLinkAt(Point mouseScreen) {
        Point2D mouseWorld = screenToWorld(mouseScreen);
        float worldHitWidth = (float)(MaterialXTheme.LINK_HIT_WIDTH / zoom);

        for (int i = links.size() - 1; i >= 0; i--) {
            Link l = links.get(i);
            if (l.target == null) continue;

            CubicCurve2D curve = createLinkCurve(
                    l.source.getWorldCenterX(), l.source.getWorldCenterY(),
                    l.target.getWorldCenterX(), l.target.getWorldCenterY()
            );

            Shape hitShape = new BasicStroke(worldHitWidth).createStrokedShape(curve);
            if (hitShape.contains(mouseWorld)) {
                return l;
            }
        }
        return null;
    }

    private void bringSelectedToFront() {
        List<MxNode> toMove = new ArrayList<>(selectedNodes);
        nodes.removeAll(toMove);
        nodes.addAll(toMove);
    }

    private void triggerCycleWarning() {
        cycleWarningFlash = true;
        cycleWarningTime = System.currentTimeMillis();
        repaint();
    }

    private boolean wouldCreateCycle(MxNode source, MxNode target) {
        if (source == target) return true;
        Set<MxNode> visited = new HashSet<>();
        Deque<MxNode> stack = new ArrayDeque<>();
        stack.push(target);
        while (!stack.isEmpty()) {
            MxNode curr = stack.pop();
            if (curr == source) return true;
            if (visited.add(curr)) {
                for (Link l : links) {
                    if (l.source.owner == curr && l.target != null) {
                        stack.push(l.target.owner);
                    }
                }
            }
        }
        return false;
    }

    public Point2D screenToWorld(Point screen) {
        return new Point2D.Double((screen.x - panX) / zoom, (screen.y - panY) / zoom);
    }

    public Point worldToScreen(Point2D world) {
        return new Point((int)(world.getX() * zoom + panX), (int)(world.getY() * zoom + panY));
    }

    // ==========================================
    // 5. DATA MODELS
    // ==========================================
    public static class MxNode {
        public String name;
        public double x, y;
        public Color headerColor;
        public List<IOPort> inputs = new ArrayList<>();
        public List<IOPort> outputs = new ArrayList<>();

        public MxNode(String name, double x, double y, Color headerColor) {
            this.name = name; this.x = x; this.y = y; this.headerColor = headerColor;
        }
        public void addInput(String name, Color c) { inputs.add(new IOPort(name, false, this, c)); }
        public void addOutput(String name, Color c) { outputs.add(new IOPort(name, true, this, c)); }
        public IOPort getInput(String name) { return inputs.stream().filter(p -> p.name.equals(name)).findFirst().orElse(null); }
        public IOPort getOutput(String name) { return outputs.stream().filter(p -> p.name.equals(name)).findFirst().orElse(null); }
        public double getHeight() { return Math.max(1, Math.max(inputs.size(), outputs.size())) * MaterialXTheme.PORT_SPACING + MaterialXTheme.HEADER_HEIGHT + 10; }

        public boolean contains(Point2D worldPos) {
            double w = MaterialXTheme.NODE_WIDTH, h = getHeight();
            return worldPos.getX() >= x && worldPos.getX() <= x + w && worldPos.getY() >= y && worldPos.getY() <= y + h;
        }
    }

    public static class IOPort {
        public String name;
        public boolean isOutput;
        public MxNode owner;
        public Color typeColor;

        public IOPort(String name, boolean isOutput, MxNode owner, Color typeColor) {
            this.name = name; this.isOutput = isOutput; this.owner = owner; this.typeColor = typeColor;
        }
        public double getWorldCenterX() { return isOutput ? owner.x + MaterialXTheme.NODE_WIDTH : owner.x; }
        public double getWorldCenterY() {
            List<IOPort> list = isOutput ? owner.outputs : owner.inputs;
            return owner.y + MaterialXTheme.HEADER_HEIGHT + 15 + (list.indexOf(this) * MaterialXTheme.PORT_SPACING);
        }
    }

    public static class Link {
        public IOPort source;
        public IOPort target;
        public Link(IOPort source, IOPort target) { this.source = source; this.target = target; }
    }
}