package cadstudio;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.datatransfer.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;
import javax.imageio.*;
import javax.imageio.stream.*;

/**
 * HousePlan CAD Desktop Studio
 * Professional Architectural 2D Drafting System in Java (Swing / Java2D).
 * Features:
 * - AutoCAD Selection Tools (Window / Crossing / Multi-Select / Grip Edit)
 * - Undo / Redo System
 * - High-Quality Architectural CAD Library (Doors, Windows, Furniture, Fixtures)
 * - Custom Modern Dark UI Buttons & Toggles (High Contrast & Visible States)
 * - Automatic Wall Cut-Through for Doors & Windows with Wall Snapping
 * - Automatic Seamless Corner Miter Joining & Endpoint Welding
 * - Real-time Collision Detection for Furniture & Fixtures
 * - In-Viewport Interactive Rotation Handle & Floating Action HUD
 */
public class HousePlanCADApp extends JFrame {

    public static final double FT_PX = 24.0; // 1 Foot = 24.0 px (1/4" = 1'-0" scale)

    // --- Custom Modern UI Components ---

    public static class ModernButton extends JButton {
        private Color bg, fg, border;

        public ModernButton(String text, Color bg, Color fg, Color border) {
            super(text);
            this.bg = bg; this.fg = fg; this.border = border;
            setFont(new Font("Segoe UI", Font.BOLD, 11));
            setFocusPainted(false); setBorderPainted(false);
            setContentAreaFilled(false); setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(5, 11, 5, 11));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean isPressed = getModel().isPressed();
            boolean isHover = getModel().isRollover();
            Color curBg = isPressed ? bg.darker() : (isHover ? brighter(bg, 25) : bg);
            Color curBorder = isHover ? fg : (border != null ? border : bg.brighter());
            g2.setColor(curBg);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
            g2.setColor(curBorder);
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
            g2.setColor(fg); g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(getText(), (getWidth() - fm.stringWidth(getText())) / 2,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }

        private static Color brighter(Color c, int amt) {
            return new Color(Math.min(255, c.getRed() + amt), Math.min(255, c.getGreen() + amt), Math.min(255, c.getBlue() + amt + 5));
        }
    }

    public static class ModernToggle extends JToggleButton {
        private Color activeBg, activeBorder, activeFg;
        private Color inactiveBg = new Color(24, 33, 53);
        private Color inactiveBorder = new Color(51, 65, 85);
        private Color inactiveFg = new Color(148, 163, 184);

        public ModernToggle(String text, boolean selected) {
            this(text, selected, Color.WHITE, new Color(56, 189, 248), new Color(13, 148, 136));
        }

        public ModernToggle(String text, boolean selected, Color activeFg, Color activeBorder, Color activeBg) {
            super(text, selected);
            this.activeFg = activeFg; this.activeBorder = activeBorder; this.activeBg = activeBg;
            setFont(new Font("Consolas", Font.BOLD, 11));
            setFocusPainted(false); setBorderPainted(false);
            setContentAreaFilled(false); setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(5, 10, 5, 10));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean isSel = isSelected(), isHover = getModel().isRollover();
            Color curBg = isSel ? activeBg : (isHover ? new Color(30, 41, 59) : inactiveBg);
            Color curBorder = isSel ? activeBorder : (isHover ? new Color(100, 116, 139) : inactiveBorder);
            Color curFg = isSel ? activeFg : (isHover ? Color.WHITE : inactiveFg);
            g2.setColor(curBg);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
            g2.setColor(curBorder);
            g2.setStroke(new BasicStroke(isSel ? 1.5f : 1.0f));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
            int textXOffset = 0;
            if (isSel) {
                g2.setColor(activeFg);
                g2.fillOval(7, getHeight() / 2 - 3, 6, 6);
                textXOffset = 4;
            }
            g2.setColor(curFg); g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(getText(), (getWidth() - fm.stringWidth(getText())) / 2 + textXOffset,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }
    }

    // --- Core Model Definitions ---

    public static class Wall implements Cloneable {
        public String id = UUID.randomUUID().toString();
        public double x1, y1, x2, y2;
        public double thicknessInches = 9.0;

        public Wall(double x1, double y1, double x2, double y2, double thicknessInches) {
            this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
            this.thicknessInches = thicknessInches;
        }

        public double getLength() { return Math.hypot(x2 - x1, y2 - y1); }

        @Override public Wall clone() {
            Wall w = new Wall(x1, y1, x2, y2, thicknessInches);
            w.id = id; return w;
        }
    }

    public static class CADElement implements Cloneable {
        public String id = UUID.randomUUID().toString();
        public String type, name;
        public double x, y, w, h;
        public double rotation = 0;
        public boolean flipH = false, flipV = false;
        public boolean hasCollision = false;
        public List<Point2D.Double> points = null; // Perimeter vertices for AutoCAD-style polygon hatch boundaries

        public CADElement(String type, String name, double x, double y, double w, double h) {
            this.type = type; this.name = name;
            this.x = x; this.y = y; this.w = w; this.h = h;
        }

        public CADElement(String type, String name, List<Point2D.Double> polyPoints) {
            this.type = type; this.name = name;
            this.points = new ArrayList<>();
            double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
            double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
            for (Point2D.Double pt : polyPoints) {
                this.points.add(new Point2D.Double(pt.x, pt.y));
                minX = Math.min(minX, pt.x); maxX = Math.max(maxX, pt.x);
                minY = Math.min(minY, pt.y); maxY = Math.max(maxY, pt.y);
            }
            this.x = minX; this.y = minY;
            this.w = Math.max(10.0, maxX - minX);
            this.h = Math.max(10.0, maxY - minY);
        }

        public void translate(double dx, double dy) {
            this.x += dx;
            this.y += dy;
            if (points != null) {
                for (Point2D.Double pt : points) {
                    pt.x += dx;
                    pt.y += dy;
                }
            }
        }

        public boolean isOpening() { return type.startsWith("door") || type.startsWith("window") || type.startsWith("opening"); }
        public boolean isDoor() { return type.startsWith("door"); }
        public boolean isWindow() { return type.startsWith("window"); }
        public boolean isWallOpening() { return type.startsWith("opening"); }

        public Path2D.Double getPolygonPath() {
            if (points == null || points.size() < 3) return null;
            Path2D.Double path = new Path2D.Double();
            path.moveTo(points.get(0).x, points.get(0).y);
            for (int i = 1; i < points.size(); i++) {
                path.lineTo(points.get(i).x, points.get(i).y);
            }
            path.closePath();
            return path;
        }

        public CADElement copy() {
            CADElement c;
            if (points != null && !points.isEmpty()) {
                List<Point2D.Double> shifted = new ArrayList<>();
                for (Point2D.Double p : points) shifted.add(new Point2D.Double(p.x + 24, p.y + 24));
                c = new CADElement(type, name, shifted);
            } else {
                c = new CADElement(type, name, x + 24, y + 24, w, h);
            }
            c.rotation = rotation; c.flipH = flipH; c.flipV = flipV; return c;
        }

        @Override public CADElement clone() {
            CADElement c;
            if (points != null && !points.isEmpty()) {
                c = new CADElement(type, name, points);
            } else {
                c = new CADElement(type, name, x, y, w, h);
            }
            c.id = id; c.rotation = rotation; c.flipH = flipH; c.flipV = flipV;
            c.hasCollision = hasCollision; return c;
        }
    }

    public static class CADDimension implements Cloneable {
        public String id = UUID.randomUUID().toString();
        public double x1, y1, x2, y2;
        public double offset = 24.0;

        public CADDimension(double x1, double y1, double x2, double y2) {
            this(x1, y1, x2, y2, 24.0);
        }

        public CADDimension(double x1, double y1, double x2, double y2, double offset) {
            this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
            this.offset = offset;
        }

        @Override public CADDimension clone() {
            CADDimension d = new CADDimension(x1, y1, x2, y2, offset);
            d.id = id; return d;
        }
    }

    // --- Undo/Redo Snapshot ---
    private static class AppState {
        final List<Wall> walls;
        final List<CADElement> elements;
        final List<CADDimension> dimensions;

        AppState(List<Wall> w, List<CADElement> e, List<CADDimension> d) {
            walls = new ArrayList<>(); for (Wall ww : w) walls.add(ww.clone());
            elements = new ArrayList<>(); for (CADElement ee : e) elements.add(ee.clone());
            dimensions = new ArrayList<>(); for (CADDimension dd : d) dimensions.add(dd.clone());
        }
    }

    // --- Application State ---
    public enum ToolMode { SELECT, DRAW_WALL, ROOM_BOX, FLOOR_ZONE, HATCH_POLYGON, MEASURE, PAN }

    // --- 8-Stage Architectural Workflow ---
    public enum ProjectStage {
        STAGE_1_WALLS("1. Walls & Structure", "Ext/Int Walls, Columns, Openings", "DOORS"),
        STAGE_2_FURNITURE("2. Furniture", "Beds, Sofas, Dining, Living", "LIVING"),
        STAGE_3_ELECTRICAL("3. Electrical / Lighting", "Fans, Lights, Switches, AC", "ELEC"),
        STAGE_4_PLUMBING("4. Plumbing / Bath", "WC, Basins, Showers, Kitchen", "BATH"),
        STAGE_5_FLOORING("5. Flooring / Tiles", "Tile Patterns & Hatch Zones", "FLOORING"),
        STAGE_6_EXTERIOR("6. Exterior & Landscape", "Roads, Trees, Vehicles, Garden", "SITE_EXT"),
        STAGE_7_DIMENSIONS("7. Dimensions & Tags", "Dimensions, Room Tags, North", "TAGS_DIMS"),
        STAGE_8_AGENT("8. Architect Agent", "Design Review & Flaw Checker", "TAGS_DIMS");

        public final String title;
        public final String desc;
        public final String defaultCatalogCard;

        ProjectStage(String title, String desc, String defaultCatalogCard) {
            this.title = title;
            this.desc = desc;
            this.defaultCatalogCard = defaultCatalogCard;
        }
    }

    private ProjectStage currentStage = ProjectStage.STAGE_1_WALLS;
    private final Map<ProjectStage, Boolean> stageLocked = new EnumMap<>(ProjectStage.class);
    private final Map<ProjectStage, Boolean> stageVisible = new EnumMap<>(ProjectStage.class);

    // Stage Stepper UI components
    private final Map<ProjectStage, ModernButton> stageButtons = new EnumMap<>(ProjectStage.class);
    private ModernButton stageFreezeBtn;
    private JLabel stageInfoLabel;

    // Architect Agent (Stage 8) Inspection Data & UI
    public static class AuditIssue {
        public enum Severity { ERROR, WARNING, INFO }
        public enum Category {
            STRUCTURAL("Structural Integrity", "Wall perimeters, support spans, columns"),
            INTERIOR("Interior & Clearance", "Walkways, circulation, door swings, daylight"),
            ELECTRICAL("Electrical & Lighting", "Light points, switches, ceiling fans, AC"),
            FLOORING("Flooring & Finishes", "Room floor finishes, tile coverage"),
            DIMENSIONS("Dimensions & Spacing", "Room area tags, annotations, north arrow");

            public final String title;
            public final String desc;
            Category(String t, String d) { this.title = t; this.desc = d; }
        }

        public Category category = Category.INTERIOR;
        public Severity severity;
        public String title;
        public String message;
        public String recommendation;
        public Object targetObj;
        public double markerX, markerY;
        public Runnable autoFixAction;

        public AuditIssue(Category cat, Severity sev, String title, String msg, String rec, Object target, double mx, double my, Runnable autoFix) {
            this.category = cat;
            this.severity = sev;
            this.title = title;
            this.message = msg;
            this.recommendation = rec;
            this.targetObj = target;
            this.markerX = mx;
            this.markerY = my;
            this.autoFixAction = autoFix;
        }

        public AuditIssue(Severity sev, String title, String msg, String rec, Object target, double mx, double my, Runnable autoFix) {
            this(Category.INTERIOR, sev, title, msg, rec, target, mx, my, autoFix);
        }
    }

    public static class ArchitectAuditReport {
        public int score = 100;
        public String grade = "A+";
        public List<AuditIssue> issues = new ArrayList<>();
        public int errorCount = 0;
        public int warningCount = 0;
        public int infoCount = 0;
    }

    private ArchitectAuditReport currentAuditReport = null;
    private JPanel agentInspectorPanel;
    private JLabel agentScoreLabel, agentGradeLabel, agentSummaryLabel;
    private JPanel agentIssuesListPanel;

    private final List<Wall> walls = new ArrayList<>();
    private final List<CADElement> elements = new ArrayList<>();
    private final List<CADDimension> dimensions = new ArrayList<>();

    private ToolMode currentTool = ToolMode.SELECT;
    private String wallHatchStyle = "DIAGONAL_45"; // "DIAGONAL_45", "SOLID_BLACK", "HOLLOW"
    private String activeHatchPattern = "floor-tiles-2x2"; // AutoCAD hatch pattern to fill perimeters with

    // Selection System (AutoCAD-style multi-select)
    private final LinkedHashSet<Object> selectedObjects = new LinkedHashSet<>();
    private Object primarySelected = null; // Last selected, for inspector

    // Undo / Redo
    private final Deque<AppState> undoStack = new ArrayDeque<>();
    private final Deque<AppState> redoStack = new ArrayDeque<>();
    private static final int MAX_UNDO = 60;

    private double wallThicknessInches = 9.0;
    private boolean orthoMode = false;
    private boolean snapToGrid = false;
    private double snapStepFeet = 0.0; // By default Smooth (no coarse 10' grid lock)
    private boolean showGrid = true;   // Option to hide / unhide background grid [F7]
    private boolean osnapEnabled = true;
    private boolean showSnapCursor = false;
    private boolean collisionDetectionEnabled = true;

    // Viewport Navigation
    private double zoom = 1.0;
    private double panX = 140.0, panY = 120.0;

    // Sidebar card switching references
    private CardLayout catalogCardLayout;
    private JPanel catalogContentCards;
    private final Map<String, ModernToggle> catalogTabs = new HashMap<>();

    // UI Components
    private CADViewportPanel viewportPanel;
    private JLabel statusCursorLabel, statusCollisionLabel, statusSelectionLabel;
    private ModernToggle orthoBtn, gridBtn, snapBtn, snapCursorBtn, collisionBtn;
    private ButtonGroup toolButtonGroup;
    private ModernButton undoBtn, redoBtn;

    private JTextField propNameField, propWidthField, propHeightField;
    private JSlider propRotSlider;
    private JLabel propTypeBadge;
    private JPanel propContentPanel, propEmptyPanel, propMultiPanel;
    private JLabel multiSelectLabel;
    private JPanel propLockBanner;
    private JLabel propLockLabel;
    private ModernButton propUnfreezeBtn;
    private boolean isUpdatingInspector = false;

    public HousePlanCADApp() {
        super("HDE Studio — Architectural 2D CAD");
        initUI();
        loadPreset30x40();
    }

    private void initUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1480, 920);
        setMinimumSize(new Dimension(1024, 600));
        setLocationRelativeTo(null);
        getContentPane().setLayout(new BorderLayout());

        JPanel topContainer = new JPanel();
        topContainer.setLayout(new BoxLayout(topContainer, BoxLayout.Y_AXIS));
        topContainer.add(createPrimaryHeader());
        topContainer.add(createDraftingToolbar());
        topContainer.add(createWorkflowStepperBar());
        getContentPane().add(topContainer, BorderLayout.NORTH);

        viewportPanel = new CADViewportPanel();
        getContentPane().add(viewportPanel, BorderLayout.CENTER);

        getContentPane().add(createCatalogSidebar(), BorderLayout.WEST);
        getContentPane().add(createInspectorSidebar(), BorderLayout.EAST);
        getContentPane().add(createStatusBar(), BorderLayout.SOUTH);

        setupKeyboardShortcuts();
    }

    private JPanel createPrimaryHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(15, 23, 42));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(30, 41, 59)));
        header.setPreferredSize(new Dimension(1000, 44));

        JPanel brandPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 8));
        brandPanel.setOpaque(false);
        JLabel titleLbl = new JLabel("HDE Studio");
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 15));
        titleLbl.setForeground(Color.WHITE);
        JLabel tagLbl = new JLabel(" ARCHITECTURAL CAD ");
        tagLbl.setFont(new Font("Consolas", Font.BOLD, 10));
        tagLbl.setForeground(new Color(56, 189, 248));
        tagLbl.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(56, 189, 248, 140), 1, true),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)));
        brandPanel.add(titleLbl); brandPanel.add(tagLbl);
        header.add(brandPanel, BorderLayout.WEST);

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 7));
        actionPanel.setOpaque(false);

        // Undo/Redo buttons
        undoBtn = new ModernButton("↶ Undo", new Color(24, 33, 53), new Color(148, 163, 184), new Color(51, 65, 85));
        undoBtn.setToolTipText("Undo (Ctrl+Z)");
        undoBtn.addActionListener(e -> undo());
        redoBtn = new ModernButton("↷ Redo", new Color(24, 33, 53), new Color(148, 163, 184), new Color(51, 65, 85));
        redoBtn.setToolTipText("Redo (Ctrl+Y)");
        redoBtn.addActionListener(e -> redo());

        JComboBox<String> presetsCombo = new JComboBox<>(new String[]{
                "Sample Plans...", "30' × 40' Wall Plan",
                "25' × 40' Wall Plan", "20' × 30' Wall Plan"});
        styleComboBox(presetsCombo);
        presetsCombo.addActionListener(e -> {
            int idx = presetsCombo.getSelectedIndex();
            if (idx == 1) loadPreset30x40();
            else if (idx == 2) loadPreset25x40();
            else if (idx == 3) loadPreset20x30();
        });

        ModernButton exportBtn = new ModernButton("Export PDF...", new Color(2, 132, 199), Color.WHITE, new Color(56, 189, 248));
        exportBtn.setToolTipText("Export architectural drawing as A4 or A3 PDF");
        exportBtn.addActionListener(e -> showExportDialog());
        ModernButton clearBtn = new ModernButton("Clear All", new Color(127, 29, 29), new Color(254, 205, 211), new Color(244, 63, 94));
        clearBtn.addActionListener(e -> clearCanvas());

        actionPanel.add(undoBtn); actionPanel.add(redoBtn);
        actionPanel.add(new JSeparator(JSeparator.VERTICAL));
        actionPanel.add(presetsCombo);
        actionPanel.add(exportBtn);
        actionPanel.add(clearBtn);
        header.add(actionPanel, BorderLayout.EAST);
        return header;
    }

    private JPanel createDraftingToolbar() {
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setBackground(new Color(17, 24, 39));
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(31, 41, 55)));
        toolbar.setPreferredSize(new Dimension(1000, 38));

        JPanel toolsGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 4));
        toolsGroup.setOpaque(false);

        toolButtonGroup = new ButtonGroup();
        ModernToggle selectBtn = createToolToggle("Select [S]", ToolMode.SELECT, toolButtonGroup, true);
        ModernToggle wallBtn = createToolToggle("Draw Wall [W]", ToolMode.DRAW_WALL, toolButtonGroup, false);
        ModernToggle roomBtn = createToolToggle("Room Box", ToolMode.ROOM_BOX, toolButtonGroup, false);
        ModernToggle floorBtn = createToolToggle("Floor / Tiles [T]", ToolMode.FLOOR_ZONE, toolButtonGroup, false);
        floorBtn.setToolTipText("Drag rectangle across any room to apply floor finishes or tile hatching");
        ModernToggle hatchBtn = createToolToggle("Hatch Perimeter [P]", ToolMode.HATCH_POLYGON, toolButtonGroup, false);
        hatchBtn.setToolTipText("HDE Studio Hatches: Click perimeter corners around any room to fill with active hatch/tile pattern");
        ModernToggle dimBtn = createToolToggle("Dimension", ToolMode.MEASURE, toolButtonGroup, false);
        dimBtn.setToolTipText("Point-to-Point Linear Dimension: Click Point 1, Click Point 2, drag mouse for offset");

        ModernToggle panBtn = createToolToggle("Pan [H]", ToolMode.PAN, toolButtonGroup, false);

        JComboBox<String> wallThickCombo = new JComboBox<>(new String[]{"9\" Wall (Ext)", "6\" Wall (Part)", "4.5\" Wall (Thin)"});
        styleComboBox(wallThickCombo);
        wallThickCombo.addActionListener(e -> {
            int idx = wallThickCombo.getSelectedIndex();
            wallThicknessInches = (idx == 0) ? 9.0 : (idx == 1 ? 6.0 : 4.5);
        });

        JComboBox<String> wallHatchCombo = new JComboBox<>(new String[]{"45° Brick Hatch", "Solid Black Poché", "Hollow Wall"});
        styleComboBox(wallHatchCombo);
        wallHatchCombo.setToolTipText("Select architectural wall hatch pattern (ANSI31 masonry vs solid poché)");
        wallHatchCombo.addActionListener(e -> {
            int idx = wallHatchCombo.getSelectedIndex();
            wallHatchStyle = (idx == 0) ? "DIAGONAL_45" : (idx == 1 ? "SOLID_BLACK" : "HOLLOW");
            viewportPanel.repaint();
        });

        ModernButton autoJoinBtn = new ModernButton("Auto-Join", new Color(24, 33, 53), new Color(56, 189, 248), new Color(14, 116, 144));
        autoJoinBtn.setToolTipText("Automatically weld all wall corners and T-junctions");
        autoJoinBtn.addActionListener(e -> { saveUndoState(); autoJoinAllWalls(); });

        toolsGroup.add(selectBtn);
        toolsGroup.add(wallBtn);
        toolsGroup.add(wallThickCombo);
        toolsGroup.add(wallHatchCombo);
        toolsGroup.add(roomBtn);
        toolsGroup.add(floorBtn);
        toolsGroup.add(hatchBtn);
        toolsGroup.add(dimBtn);
        toolsGroup.add(autoJoinBtn);
        toolsGroup.add(panBtn);
        toolbar.add(toolsGroup, BorderLayout.CENTER);
        return toolbar;
    }

    private ModernToggle createToolToggle(String label, ToolMode mode, ButtonGroup group, boolean selected) {
        ModernToggle btn = new ModernToggle(label, selected,
                Color.WHITE, new Color(56, 189, 248), new Color(2, 132, 199));
        btn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btn.addActionListener(e -> { currentTool = mode; viewportPanel.repaint(); });
        group.add(btn);
        return btn;
    }

    private void styleComboBox(JComboBox<?> combo) {
        combo.setFont(new Font("Segoe UI", Font.BOLD, 11));
        combo.setBackground(new Color(24, 33, 53));
        combo.setForeground(Color.WHITE);
        combo.setFocusable(false);
        combo.setOpaque(true);
        combo.setUI(new javax.swing.plaf.basic.BasicComboBoxUI() {
            @Override
            protected JButton createArrowButton() {
                JButton btn = new JButton() {
                    @Override
                    protected void paintComponent(Graphics g) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.setColor(new Color(24, 33, 53));
                        g2.fillRect(0, 0, getWidth(), getHeight());
                        g2.setColor(new Color(148, 163, 184));
                        g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        int cx = getWidth() / 2 - 1, cy = getHeight() / 2;
                        g2.drawLine(cx - 4, cy - 2, cx, cy + 2);
                        g2.drawLine(cx, cy + 2, cx + 4, cy - 2);
                        g2.dispose();
                    }
                };
                btn.setBorder(null);
                btn.setOpaque(false);
                btn.setContentAreaFilled(false);
                btn.setPreferredSize(new Dimension(20, 26));
                return btn;
            }

            @Override
            public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(new Color(24, 33, 53));
                g2.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
                g2.dispose();
            }

            @Override
            public void paintCurrentValue(Graphics g, Rectangle bounds, boolean hasFocus) {
                ListCellRenderer<Object> renderer = (ListCellRenderer<Object>) comboBox.getRenderer();
                Component c = renderer.getListCellRendererComponent(listBox, comboBox.getSelectedItem(), -1, false, false);
                c.setFont(comboBox.getFont());
                c.setForeground(Color.WHITE);
                c.setBackground(new Color(24, 33, 53));
                currentValuePane.paintComponent(g, c, comboBox, bounds.x, bounds.y, bounds.width, bounds.height, false);
            }
        });

        combo.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(51, 65, 85), 1, true),
                BorderFactory.createEmptyBorder(2, 6, 2, 4)));

        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel lbl = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                lbl.setBackground(isSelected ? new Color(2, 132, 199) : new Color(24, 33, 53));
                lbl.setForeground(Color.WHITE);
                lbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
                lbl.setBorder(new EmptyBorder(5, 8, 5, 8));
                return lbl;
            }
        });
    }

    // --- 8-Stage Step-by-Step Architectural Workflow Stepper Bar ---

    private JPanel createWorkflowStepperBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(new Color(11, 17, 32));
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(30, 41, 59)));
        bar.setPreferredSize(new Dimension(1000, 36));

        // Left Label
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        leftPanel.setOpaque(false);
        JLabel wfBadge = new JLabel("WORKFLOW STAGE:");
        wfBadge.setFont(new Font("Consolas", Font.BOLD, 10));
        wfBadge.setForeground(new Color(148, 163, 184));
        leftPanel.add(wfBadge);
        bar.add(leftPanel, BorderLayout.WEST);

        // Center 8 Stage Buttons
        JPanel stagesPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 3, 3));
        stagesPanel.setOpaque(false);

        for (ProjectStage s : ProjectStage.values()) {
            boolean isCur = (s == currentStage);
            ModernButton btn = new ModernButton(s.title,
                    isCur ? new Color(2, 132, 199) : new Color(24, 33, 53),
                    isCur ? Color.WHITE : new Color(148, 163, 184),
                    isCur ? new Color(56, 189, 248) : new Color(51, 65, 85));
            btn.setFont(new Font("Segoe UI", Font.BOLD, 10));
            btn.setToolTipText("Stage " + s.title + ": " + s.desc);
            btn.addActionListener(e -> setStage(s));
            stageButtons.put(s, btn);
            stagesPanel.add(btn);
        }
        bar.add(stagesPanel, BorderLayout.CENTER);

        // Right Controls: Freeze/Lock Toggle, Prev/Next Navigation
        JPanel rightControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 3));
        rightControls.setOpaque(false);

        stageFreezeBtn = new ModernButton("🔓 Unlock Layer", new Color(24, 33, 53), new Color(148, 163, 184), new Color(51, 65, 85));
        stageFreezeBtn.setFont(new Font("Segoe UI", Font.BOLD, 10));
        stageFreezeBtn.setToolTipText("Freeze / Lock all objects created in this stage to prevent accidental movement");
        stageFreezeBtn.addActionListener(e -> {
            boolean locked = Boolean.TRUE.equals(stageLocked.get(currentStage));
            stageLocked.put(currentStage, !locked);
            updateStageStepperUI();
            viewportPanel.repaint();
        });

        ModernButton prevBtn = new ModernButton("◀ Prev", new Color(24, 33, 53), new Color(148, 163, 184), new Color(51, 65, 85));
        prevBtn.setFont(new Font("Segoe UI", Font.BOLD, 10));
        prevBtn.addActionListener(e -> navigateStage(-1));

        ModernButton nextBtn = new ModernButton("Next Stage ▶", new Color(13, 148, 136), Color.WHITE, new Color(45, 212, 191));
        nextBtn.setFont(new Font("Segoe UI", Font.BOLD, 10));
        nextBtn.addActionListener(e -> navigateStage(1));

        rightControls.add(stageFreezeBtn);
        rightControls.add(new JSeparator(JSeparator.VERTICAL));
        rightControls.add(prevBtn);
        rightControls.add(nextBtn);
        bar.add(rightControls, BorderLayout.EAST);

        updateStageStepperUI();
        return bar;
    }

    private void navigateStage(int delta) {
        ProjectStage[] all = ProjectStage.values();
        int nextIdx = currentStage.ordinal() + delta;
        if (nextIdx >= 0 && nextIdx < all.length) {
            setStage(all[nextIdx]);
        }
    }

    public void setStage(ProjectStage stage) {
        this.currentStage = stage;
        clearSelection();

        // Auto-switch catalog sidebar card to relevant block library category
        if (catalogCardLayout != null && catalogContentCards != null && stage.defaultCatalogCard != null) {
            catalogCardLayout.show(catalogContentCards, stage.defaultCatalogCard);
            ModernToggle tab = catalogTabs.get(stage.defaultCatalogCard);
            if (tab != null) tab.setSelected(true);
        }

        // Default to SELECT mode so user can immediately click, inspect, and select objects
        if (stage == ProjectStage.STAGE_1_WALLS && walls.isEmpty()) {
            currentTool = ToolMode.DRAW_WALL;
            selectToolButton(ToolMode.DRAW_WALL);
        } else if (stage == ProjectStage.STAGE_7_DIMENSIONS && dimensions.isEmpty()) {
            currentTool = ToolMode.MEASURE;
            selectToolButton(ToolMode.MEASURE);
        } else {
            currentTool = ToolMode.SELECT;
            selectToolButton(ToolMode.SELECT);
        }

        // Run audit if Stage 8 (Architect Agent) is opened
        if (stage == ProjectStage.STAGE_8_AGENT) {
            runArchitectAgentAudit();
        }

        updateStageStepperUI();
        updateProperties();
        if (viewportPanel != null) viewportPanel.repaint();
    }

    private void updateStageStepperUI() {
        for (ProjectStage s : ProjectStage.values()) {
            ModernButton btn = stageButtons.get(s);
            if (btn == null) continue;
            boolean isCur = (s == currentStage);
            boolean isLocked = Boolean.TRUE.equals(stageLocked.get(s));

            String prefix = isLocked ? "🔒 " : (isCur ? "► " : "");
            btn.setText(prefix + s.title);

            if (isCur) {
                btn.bg = new Color(2, 132, 199);
                btn.fg = Color.WHITE;
                btn.border = new Color(56, 189, 248);
            } else if (isLocked) {
                btn.bg = new Color(30, 41, 59);
                btn.fg = new Color(251, 191, 36);
                btn.border = new Color(245, 158, 11);
            } else {
                btn.bg = new Color(24, 33, 53);
                btn.fg = new Color(148, 163, 184);
                btn.border = new Color(51, 65, 85);
            }
            btn.repaint();
        }

        if (stageFreezeBtn != null) {
            boolean curLocked = Boolean.TRUE.equals(stageLocked.get(currentStage));
            if (curLocked) {
                stageFreezeBtn.setText("🔒 Stage Locked (Frozen)");
                stageFreezeBtn.bg = new Color(120, 53, 15);
                stageFreezeBtn.fg = new Color(254, 240, 138);
                stageFreezeBtn.border = new Color(245, 158, 11);
                stageFreezeBtn.setToolTipText("Click to UNFREEZE objects in this stage so they can be edited");
            } else {
                stageFreezeBtn.setText("❄ Freeze Stage Layer");
                stageFreezeBtn.bg = new Color(24, 33, 53);
                stageFreezeBtn.fg = new Color(148, 163, 184);
                stageFreezeBtn.border = new Color(51, 65, 85);
                stageFreezeBtn.setToolTipText("Click to FREEZE / LOCK all elements of this stage from accidental movement");
            }
            stageFreezeBtn.repaint();
        }
    }

    public ProjectStage getObjectStage(Object obj) {
        if (obj instanceof Wall) return ProjectStage.STAGE_1_WALLS;
        if (obj instanceof CADDimension) return ProjectStage.STAGE_7_DIMENSIONS;
        if (obj instanceof CADElement) {
            CADElement el = (CADElement) obj;
            String t = el.type.toLowerCase();
            if (t.startsWith("door") || t.startsWith("window") || t.startsWith("opening")) {
                return ProjectStage.STAGE_1_WALLS;
            }
            if (t.startsWith("floor-")) return ProjectStage.STAGE_5_FLOORING;
            if (t.startsWith("column") || t.startsWith("stair") || t.startsWith("tree") || t.startsWith("shrub") || t.startsWith("plant") || t.startsWith("car")
                    || t.startsWith("bike") || t.startsWith("bicycle") || t.startsWith("road") || t.startsWith("parking")
                    || t.startsWith("street") || t.startsWith("pathway") || t.startsWith("bollard")) {
                return ProjectStage.STAGE_6_EXTERIOR;
            }
            if (t.startsWith("fan") || t.startsWith("ceiling-fan") || t.startsWith("light") || t.startsWith("switch") || t.startsWith("ac")) {
                return ProjectStage.STAGE_3_ELECTRICAL;
            }
            if (t.startsWith("toilet") || t.startsWith("basin") || t.startsWith("bath") || t.startsWith("shower")
                    || t.startsWith("washer") || t.startsWith("kitchen") || t.startsWith("stove") || t.startsWith("fridge")) {
                return ProjectStage.STAGE_4_PLUMBING;
            }
            if (t.equals("tag") || t.startsWith("north")) {
                return ProjectStage.STAGE_7_DIMENSIONS;
            }
            return ProjectStage.STAGE_2_FURNITURE;
        }
        return ProjectStage.STAGE_1_WALLS;
    }

    public boolean isObjectLocked(Object obj) {
        ProjectStage stage = getObjectStage(obj);
        return Boolean.TRUE.equals(stageLocked.get(stage));
    }

    // --- Catalog Sidebar ---

    private JPanel createCatalogSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setPreferredSize(new Dimension(270, 600));
        sidebar.setBackground(new Color(15, 23, 42));
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(30, 41, 59)));

        JLabel titleLbl = new JLabel("  CAD BLOCK LIBRARY");
        titleLbl.setFont(new Font("Consolas", Font.BOLD, 12));
        titleLbl.setForeground(new Color(56, 189, 248));
        titleLbl.setBorder(new EmptyBorder(12, 10, 8, 10));
        sidebar.add(titleLbl, BorderLayout.NORTH);

        catalogCardLayout = new CardLayout();
        catalogContentCards = new JPanel(catalogCardLayout);
        catalogContentCards.setBackground(new Color(15, 23, 42));

        JPanel catBar = new JPanel(new GridLayout(5, 2, 3, 3));
        catBar.setBackground(new Color(15, 23, 42));
        catBar.setBorder(new EmptyBorder(4, 6, 6, 6));

        ButtonGroup catGroup = new ButtonGroup();
        ModernToggle setsTab = createCategoryTab("Room Sets", "ROOM_SETS", catalogCardLayout, catalogContentCards, catGroup, true);
        ModernToggle floorTab = createCategoryTab("Flooring", "FLOORING", catalogCardLayout, catalogContentCards, catGroup, false);
        ModernToggle doorsTab = createCategoryTab("Doors", "DOORS", catalogCardLayout, catalogContentCards, catGroup, false);
        ModernToggle windowsTab = createCategoryTab("Windows", "WINDOWS", catalogCardLayout, catalogContentCards, catGroup, false);
        ModernToggle kitchenTab = createCategoryTab("Kitchen", "KITCHEN", catalogCardLayout, catalogContentCards, catGroup, false);
        ModernToggle bathTab = createCategoryTab("Bath", "BATH", catalogCardLayout, catalogContentCards, catGroup, false);
        ModernToggle livingTab = createCategoryTab("Living", "LIVING", catalogCardLayout, catalogContentCards, catGroup, false);
        ModernToggle dimsTab = createCategoryTab("Dims/Tags", "TAGS_DIMS", catalogCardLayout, catalogContentCards, catGroup, false);
        ModernToggle elecTab = createCategoryTab("Elect", "ELEC", catalogCardLayout, catalogContentCards, catGroup, false);
        ModernToggle siteTab = createCategoryTab("Site/Ext", "SITE_EXT", catalogCardLayout, catalogContentCards, catGroup, false);

        catalogTabs.put("ROOM_SETS", setsTab);
        catalogTabs.put("FLOORING", floorTab);
        catalogTabs.put("DOORS", doorsTab);
        catalogTabs.put("WINDOWS", windowsTab);
        catalogTabs.put("KITCHEN", kitchenTab);
        catalogTabs.put("BATH", bathTab);
        catalogTabs.put("LIVING", livingTab);
        catalogTabs.put("TAGS_DIMS", dimsTab);
        catalogTabs.put("ELEC", elecTab);
        catalogTabs.put("SITE_EXT", siteTab);

        catBar.add(setsTab); catBar.add(floorTab);
        catBar.add(doorsTab); catBar.add(windowsTab);
        catBar.add(kitchenTab); catBar.add(bathTab);
        catBar.add(livingTab); catBar.add(dimsTab);
        catBar.add(elecTab); catBar.add(siteTab);

        // FLOORING & TILE HATCHES (HDE Studio Perimeter Hatch Pattern Palette)
        JPanel floorPanel = createCatalogCategoryPanel();
        JLabel hatchIntroLbl = new JLabel("<html><b style='color:#38bdf8'>HDE Studio Hatches Palette</b><br><span style='color:#94a3b8'>Select pattern below to activate polyline hatch tool:</span></html>");
        hatchIntroLbl.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        hatchIntroLbl.setBorder(new EmptyBorder(4, 4, 8, 4));
        floorPanel.add(hatchIntroLbl);

        addHatchPatternItem(floorPanel, "Vitrified Tiles (2'×2')", "floor-tiles-2x2");
        addHatchPatternItem(floorPanel, "Ceramic Tiles (1'×1')", "floor-tiles-1x1");
        addHatchPatternItem(floorPanel, "Marble Large Tiles (2'×4')", "floor-tiles-2x4");
        addHatchPatternItem(floorPanel, "Hardwood Wood Planks", "floor-wood-plank");
        addHatchPatternItem(floorPanel, "45° Diagonal Diamond Tiles", "floor-tiles-diagonal");
        addHatchPatternItem(floorPanel, "Bathroom Mosaic 4\"", "floor-mosaic");
        addHatchPatternItem(floorPanel, "Concrete / Floor Screed", "floor-concrete");
        addHatchPatternItem(floorPanel, "Driveway Stone Pavers", "floor-pavers");
        catalogContentCards.add(wrapInScroll(floorPanel), "FLOORING");

        // ROOM SETS (Full pre-arranged furnished room layouts)
        JPanel roomSetsPanel = createCatalogCategoryPanel();
        addCatalogItem(roomSetsPanel, "Master Suite (16'×14')", "set-bedroom-master", 16.0, 14.0);
        addCatalogItem(roomSetsPanel, "Double Bedroom Set (14'×12')", "set-bedroom-double", 14.0, 12.0);
        addCatalogItem(roomSetsPanel, "Compact Guest Bed (11'×10')", "set-bedroom-compact", 11.0, 10.0);
        addCatalogItem(roomSetsPanel, "Twin Kids Room (14'×11')", "set-bedroom-twin", 14.0, 11.0);
        addCatalogItem(roomSetsPanel, "Master Bath Suite (10'×8')", "set-bath-master", 10.0, 8.0);
        addCatalogItem(roomSetsPanel, "Standard 3-Fixture Bath (8'×5')", "set-bath-standard", 8.0, 5.0);
        addCatalogItem(roomSetsPanel, "Powder Room / Half (5'×5')", "set-bath-powder", 5.0, 5.0);
        addCatalogItem(roomSetsPanel, "Spa Luxury Bath (12'×10')", "set-bath-luxury", 12.0, 10.0);
        addCatalogItem(roomSetsPanel, "Living Room Suite (16'×12')", "set-living-full", 16.0, 12.0);
        addCatalogItem(roomSetsPanel, "Dining Room Set (12'×10')", "set-dining-full", 12.0, 10.0);
        catalogContentCards.add(wrapInScroll(roomSetsPanel), "ROOM_SETS");

        // DOORS & OPENINGS
        JPanel doorsPanel = createCatalogCategoryPanel();
        addCatalogItem(doorsPanel, "Wall Opening 3'-0\"", "opening-wall", 3.0, 0.75);
        addCatalogItem(doorsPanel, "Wall Opening 3'-6\"", "opening-wall", 3.5, 0.75);
        addCatalogItem(doorsPanel, "Wall Opening 4'-0\"", "opening-wall", 4.0, 0.75);
        addCatalogItem(doorsPanel, "Wall Opening 5'-0\"", "opening-wall", 5.0, 0.75);
        addCatalogItem(doorsPanel, "Single Door 3'-0\"", "door", 3.0, 0.75);
        addCatalogItem(doorsPanel, "Main Door 3'-6\"", "door", 3.5, 0.75);
        addCatalogItem(doorsPanel, "Large Entry 4'-0\"", "door", 4.0, 0.75);
        addCatalogItem(doorsPanel, "French Double 5'-0\"", "door-double", 5.0, 0.75);
        addCatalogItem(doorsPanel, "Sliding Patio 6'-0\"", "door-sliding", 6.0, 0.75);
        addCatalogItem(doorsPanel, "Pocket Cavity 3'-0\"", "door-pocket", 3.0, 0.75);
        addCatalogItem(doorsPanel, "Bifold Closet 4'-0\"", "door-bifold", 4.0, 0.75);
        catalogContentCards.add(wrapInScroll(doorsPanel), "DOORS");

        // WINDOWS
        JPanel windowsPanel = createCatalogCategoryPanel();
        addCatalogItem(windowsPanel, "Casement 4'-0\"", "window", 4.0, 0.75);
        addCatalogItem(windowsPanel, "Living 5'-0\"", "window", 5.0, 0.75);
        addCatalogItem(windowsPanel, "Large 6'-0\"", "window", 6.0, 0.75);
        addCatalogItem(windowsPanel, "Sliding 2-Track 5'-0\"", "window-sliding", 5.0, 0.75);
        addCatalogItem(windowsPanel, "Bay Picture 8'-0\"", "window-bay", 8.0, 1.2);
        addCatalogItem(windowsPanel, "Toilet Vent 2'-0\"", "window-vent", 2.0, 0.75);
        catalogContentCards.add(wrapInScroll(windowsPanel), "WINDOWS");

        // KITCHEN (Rich architectural shapes & standard layouts)
        JPanel kitchenPanel = createCatalogCategoryPanel();
        addCatalogItem(kitchenPanel, "L-Shape Kitchen (10'×8')", "kitchen-l", 10.0, 8.0);
        addCatalogItem(kitchenPanel, "L-Shape Compact (8'×6')", "kitchen-l", 8.0, 6.0);
        addCatalogItem(kitchenPanel, "U-Shape Kitchen (10'×9')", "kitchen-u", 10.0, 9.0);
        addCatalogItem(kitchenPanel, "U-Shape Large (12'×10')", "kitchen-u", 12.0, 10.0);
        addCatalogItem(kitchenPanel, "Parallel / Galley (10'×7')", "kitchen-parallel", 10.0, 7.0);
        addCatalogItem(kitchenPanel, "Island Kitchen (12'×10')", "kitchen-island", 12.0, 10.0);
        addCatalogItem(kitchenPanel, "Grand Island (14'×12')", "kitchen-island", 14.0, 12.0);
        addCatalogItem(kitchenPanel, "Peninsula Kitchen (11'×9')", "kitchen-peninsula", 11.0, 9.0);
        addCatalogItem(kitchenPanel, "Straight Run (10'×2')", "kitchen-linear", 10.0, 2.0);
        addCatalogItem(kitchenPanel, "Kitchen Island (6'×3'-6\")", "kitchen-island-unit", 6.0, 3.5);
        addCatalogItem(kitchenPanel, "Breakfast Bar + Stools (6'×2')", "kitchen-bar", 6.0, 2.0);
        addCatalogItem(kitchenPanel, "Studio Kitchenette (6'×2')", "kitchen-compact", 6.0, 2.0);
        addCatalogItem(kitchenPanel, "Double Sink (3'×1'-8\")", "kitchen-sink", 3.0, 1.67);
        addCatalogItem(kitchenPanel, "4-Burner Stove (2'-6\"×2')", "stove", 2.5, 2.0);
        addCatalogItem(kitchenPanel, "Refrigerator (3'×2'-6\")", "fridge", 3.0, 2.5);
        addCatalogItem(kitchenPanel, "Built-in Oven Tower (2'×2')", "kitchen-oven", 2.0, 2.0);
        addCatalogItem(kitchenPanel, "Dishwasher Unit (2'×2')", "kitchen-dishwasher", 2.0, 2.0);
        catalogContentCards.add(wrapInScroll(kitchenPanel), "KITCHEN");

        // BATH
        JPanel bathPanel = createCatalogCategoryPanel();
        addCatalogItem(bathPanel, "Toilet WC 1'-8\"×2'-4\"", "toilet", 1.67, 2.33);
        addCatalogItem(bathPanel, "Wash Basin 2'-6\"×1'-8\"", "basin", 2.5, 1.67);
        addCatalogItem(bathPanel, "Bathtub 5'×2'-6\"", "bathtub", 5.0, 2.5);
        addCatalogItem(bathPanel, "Shower Stall 3'×3'", "shower", 3.0, 3.0);
        addCatalogItem(bathPanel, "Washing Machine 2'×2'", "washer", 2.0, 2.0);
        catalogContentCards.add(wrapInScroll(bathPanel), "BATH");

        // LIVING
        JPanel furnPanel = createCatalogCategoryPanel();
        addCatalogItem(furnPanel, "King Bed 6'-6\"×6'", "bed-king", 6.5, 6.0);
        addCatalogItem(furnPanel, "Single Bed 3'-6\"×6'-6\"", "bed-single", 3.5, 6.5);
        addCatalogItem(furnPanel, "3-Seater Sofa 7'×3'", "sofa-3seat", 7.0, 3.0);
        addCatalogItem(furnPanel, "Armchair 3'×3'", "sofa-armchair", 3.0, 3.0);
        addCatalogItem(furnPanel, "Dining 6-Seat 5'×3'", "dining-set", 5.0, 3.0);
        addCatalogItem(furnPanel, "Study Desk 4'×2'-6\"", "desk", 4.0, 2.5);
        addCatalogItem(furnPanel, "Wardrobe 5'×2'", "wardrobe", 5.0, 2.0);
        addCatalogItem(furnPanel, "TV Console 5'×1'-6\"", "tv-unit", 5.0, 1.5);
        addCatalogItem(furnPanel, "Bookshelf 4'×1'", "bookshelf", 4.0, 1.0);
        catalogContentCards.add(wrapInScroll(furnPanel), "LIVING");

        // DIMENSIONS & TAGS (Stage 7 - Pure Dimensions & Annotations)
        JPanel tagsDimsPanel = createCatalogCategoryPanel();
        JLabel dimToolsLbl = new JLabel("<html><b style='color:#38bdf8'>Linear Dimensions</b><br><span style='color:#94a3b8'>Standard point-to-point drafting:</span></html>");
        dimToolsLbl.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        dimToolsLbl.setBorder(new EmptyBorder(4, 4, 6, 4));
        tagsDimsPanel.add(dimToolsLbl);

        ModernButton linearDimBtn = new ModernButton("📏 Point-to-Point Dimension", new Color(24, 33, 53), new Color(56, 189, 248), new Color(14, 116, 144));
        linearDimBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        linearDimBtn.setMaximumSize(new Dimension(252, 30));
        linearDimBtn.setHorizontalAlignment(SwingConstants.LEFT);
        linearDimBtn.setToolTipText("AutoCAD-style 3-step dimension: Click Pt 1, Click Pt 2, Move mouse & click offset");
        linearDimBtn.addActionListener(e -> {
            currentTool = ToolMode.MEASURE;
            selectToolButton(ToolMode.MEASURE);
            viewportPanel.initPointToPointDimension();
        });
        tagsDimsPanel.add(linearDimBtn);
        tagsDimsPanel.add(Box.createVerticalStrut(4));

        ModernButton autoDimBtn = new ModernButton("⚡ Auto-Dimension All Walls", new Color(24, 33, 53), new Color(52, 211, 153), new Color(16, 185, 129));
        autoDimBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        autoDimBtn.setMaximumSize(new Dimension(252, 30));
        autoDimBtn.setHorizontalAlignment(SwingConstants.LEFT);
        autoDimBtn.setToolTipText("Generate exterior and interior perimeter dimensions automatically");
        autoDimBtn.addActionListener(e -> autoGenerateDimensions());
        tagsDimsPanel.add(autoDimBtn);
        tagsDimsPanel.add(Box.createVerticalStrut(4));

        ModernButton clearDimBtn = new ModernButton("✕ Clear All Dimensions", new Color(40, 20, 20), new Color(248, 113, 113), new Color(153, 27, 27));
        clearDimBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        clearDimBtn.setMaximumSize(new Dimension(252, 28));
        clearDimBtn.setHorizontalAlignment(SwingConstants.LEFT);
        clearDimBtn.addActionListener(e -> {
            if (!dimensions.isEmpty()) {
                saveUndoState();
                dimensions.clear();
                clearSelection();
                viewportPanel.repaint();
            }
        });
        tagsDimsPanel.add(clearDimBtn);
        tagsDimsPanel.add(Box.createVerticalStrut(10));

        JLabel tagsHeaderLbl = new JLabel("<html><b style='color:#38bdf8'>Architectural Room Tags & Symbols</b></html>");
        tagsHeaderLbl.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        tagsHeaderLbl.setBorder(new EmptyBorder(4, 4, 6, 4));
        tagsDimsPanel.add(tagsHeaderLbl);

        addCatalogItem(tagsDimsPanel, "Room Area Tag", "tag", 14.0, 10.0);
        addCatalogItem(tagsDimsPanel, "North Arrow", "north-arrow", 3.0, 3.0);
        addCatalogItem(tagsDimsPanel, "Hall / Living Tag", "tag", 16.0, 12.0);
        addCatalogItem(tagsDimsPanel, "Master Bedroom Tag", "tag", 14.0, 12.0);
        addCatalogItem(tagsDimsPanel, "Kitchen Tag", "tag", 10.0, 8.0);
        addCatalogItem(tagsDimsPanel, "Bathroom Tag", "tag", 8.0, 6.0);
        catalogContentCards.add(wrapInScroll(tagsDimsPanel), "TAGS_DIMS");

        // ELECTRICAL
        JPanel elecPanel = createCatalogCategoryPanel();
        addCatalogItem(elecPanel, "Ceiling Fan 4'×4'", "ceiling-fan", 4.0, 4.0);
        addCatalogItem(elecPanel, "AC Split Unit 3'×1'", "ac-unit", 3.0, 1.0);
        addCatalogItem(elecPanel, "Light Point 1'×1'", "light-point", 1.0, 1.0);
        addCatalogItem(elecPanel, "Switch Board 1'×0'-6\"", "switchboard", 1.0, 0.5);
        catalogContentCards.add(wrapInScroll(elecPanel), "ELEC");

        // SITE, LANDSCAPE, STRUCTURE & INFRASTRUCTURE
        JPanel siteExtPanel = createCatalogCategoryPanel();
        addCatalogItem(siteExtPanel, "RCC Column 9\"×12\"", "column", 0.75, 1.0);
        addCatalogItem(siteExtPanel, "Square Column 9\"×9\"", "column-sq", 0.75, 0.75);
        addCatalogItem(siteExtPanel, "Dog-Leg Stair 7'×10'", "stair-dogleg", 7.0, 10.0);
        addCatalogItem(siteExtPanel, "Straight Stair 3'-6\"×12'", "stair-straight", 3.5, 12.0);
        addCatalogItem(siteExtPanel, "Deciduous Tree (12'×12')", "tree-deciduous", 12.0, 12.0);
        addCatalogItem(siteExtPanel, "Evergreen Pine (8'×8')", "tree-pine", 8.0, 8.0);
        addCatalogItem(siteExtPanel, "Tropical Palm (10'×10')", "tree-palm", 10.0, 10.0);
        addCatalogItem(siteExtPanel, "Garden Shrub (4'×4')", "shrub", 4.0, 4.0);
        addCatalogItem(siteExtPanel, "Planter Box / Hedge (6'×2')", "planter-box", 6.0, 2.0);
        addCatalogItem(siteExtPanel, "Potted Patio Plant (2.5'×2.5')", "plant-pot", 2.5, 2.5);
        addCatalogItem(siteExtPanel, "Sedan Car (15'×6')", "car-sedan", 15.0, 6.0);
        addCatalogItem(siteExtPanel, "SUV / 4x4 (16'×6'-6\")", "car-suv", 16.0, 6.5);
        addCatalogItem(siteExtPanel, "Motorbike (7'×2'-6\")", "bike-motor", 7.0, 2.5);
        addCatalogItem(siteExtPanel, "Scooter / Vespa (6'×2')", "bike-scooter", 6.0, 2.0);
        addCatalogItem(siteExtPanel, "Bicycle (5'-6\"×1'-6\")", "bicycle", 5.5, 1.5);
        addCatalogItem(siteExtPanel, "2-Lane Road (24'×12')", "road-segment", 24.0, 12.0);
        addCatalogItem(siteExtPanel, "Car Parking Bay (18'×9')", "parking-bay", 18.0, 9.0);
        addCatalogItem(siteExtPanel, "Street Light Pole (3'×3')", "streetlight", 3.0, 3.0);
        addCatalogItem(siteExtPanel, "Garden Pathway (12'×3')", "pathway", 12.0, 3.0);
        addCatalogItem(siteExtPanel, "Bollard Light (1'-6\"×1'-6\")", "bollard-light", 1.5, 1.5);
        catalogContentCards.add(wrapInScroll(siteExtPanel), "SITE_EXT");

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBackground(new Color(15, 23, 42));
        centerPanel.add(catBar, BorderLayout.NORTH);
        centerPanel.add(catalogContentCards, BorderLayout.CENTER);
        sidebar.add(centerPanel, BorderLayout.CENTER);
        return sidebar;
    }

    private ModernToggle createCategoryTab(String title, String cardKey, CardLayout layout, JPanel container, ButtonGroup group, boolean sel) {
        ModernToggle t = new ModernToggle(title, sel, new Color(56, 189, 248), new Color(14, 116, 144), new Color(24, 33, 53));
        t.setFont(new Font("Segoe UI", Font.BOLD, 10));
        t.addActionListener(e -> layout.show(container, cardKey));
        group.add(t);
        return t;
    }

    private JPanel createCatalogCategoryPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(new Color(15, 23, 42));
        p.setBorder(new EmptyBorder(6, 6, 8, 6));
        return p;
    }

    private JScrollPane wrapInScroll(JPanel p) {
        JScrollPane sp = new JScrollPane(p);
        sp.setBorder(null);
        sp.getViewport().setBackground(new Color(15, 23, 42));
        sp.getVerticalScrollBar().setUnitIncrement(16);
        return sp;
    }

    public static void showModernDialog(Component parent, String title, String heading, String message) {
        Window win = (parent instanceof Window) ? (Window) parent : (parent != null ? SwingUtilities.getWindowAncestor(parent) : null);
        JDialog dlg = (win instanceof Frame) ? new JDialog((Frame) win, title, true) : (win instanceof Dialog ? new JDialog((Dialog) win, title, true) : new JDialog());
        dlg.setUndecorated(true);

        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(new Color(15, 23, 42)); // Dark Slate / Navy #0f172a
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(56, 189, 248), 1),
                new EmptyBorder(16, 20, 16, 20)));

        // Header
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JLabel titleLbl = new JLabel(heading != null ? heading : title);
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
        titleLbl.setForeground(new Color(56, 189, 248)); // Cyan
        top.add(titleLbl, BorderLayout.WEST);

        JLabel closeLbl = new JLabel(" \u2715 ");
        closeLbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
        closeLbl.setForeground(new Color(148, 163, 184));
        closeLbl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeLbl.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { dlg.dispose(); }
        });
        top.add(closeLbl, BorderLayout.EAST);
        p.add(top, BorderLayout.NORTH);

        // Body message
        JLabel msgLbl = new JLabel("<html><div style='width:340px; line-height:1.6; color:#e2e8f0; font-family:Segoe UI; font-size:11px;'>"
                + message.replace("\n", "<br>") + "</div></html>");
        msgLbl.setBorder(new EmptyBorder(14, 0, 16, 0));
        p.add(msgLbl, BorderLayout.CENTER);

        // Bottom Action
        JPanel bot = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        bot.setOpaque(false);
        ModernButton okBtn = new ModernButton("Got it", new Color(14, 116, 144), Color.WHITE, new Color(56, 189, 248));
        okBtn.setPreferredSize(new Dimension(85, 28));
        okBtn.addActionListener(e -> dlg.dispose());
        bot.add(okBtn);
        p.add(bot, BorderLayout.SOUTH);

        dlg.setContentPane(p);
        dlg.pack();
        dlg.setLocationRelativeTo(parent);
        dlg.setVisible(true);
    }

    private void addHatchPatternItem(JPanel container, String title, String patternType) {
        ModernButton btn = new ModernButton("▧ " + title, new Color(24, 33, 53), new Color(245, 158, 11), new Color(51, 65, 85));
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
        btn.setMaximumSize(new Dimension(252, 30));
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setToolTipText("Activate hatch tool for " + title + " (click room perimeter corners, double-click or Enter to apply)");
        btn.addActionListener(e -> {
            activeHatchPattern = patternType;
            currentTool = ToolMode.HATCH_POLYGON;
            selectToolButton(ToolMode.HATCH_POLYGON);
            viewportPanel.clearHatchPoly();
            viewportPanel.repaint();
            showModernDialog(this,
                    "HDE Studio Hatches - " + title,
                    "▧ HDE Studio Hatches Activated",
                    "<b>Pattern:</b> " + title + "\n\n"
                    + "• <b>Left-Click</b> perimeter corners around the room.\n"
                    + "• <b>Double-Click</b> or press <b>Enter</b> to close and apply hatch.\n"
                    + "• Press <b>Esc</b> to cancel.");
        });
        container.add(btn);
        container.add(Box.createVerticalStrut(3));
    }

    private void addCatalogItem(JPanel container, String title, String type, double wFt, double hFt) {
        ModernButton btn = new ModernButton("+ " + title, new Color(24, 33, 53), new Color(226, 232, 240), new Color(51, 65, 85));
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
        btn.setMaximumSize(new Dimension(252, 30));
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.addActionListener(e -> {
            saveUndoState();
            double cx = (-panX + viewportPanel.getWidth() / 2.0) / zoom;
            double cy = (-panY + viewportPanel.getHeight() / 2.0) / zoom;
            CADElement elem = new CADElement(type, title, snapCoord(cx), snapCoord(cy), wFt * FT_PX, hFt * FT_PX);
            elements.add(elem);
            clearSelection(); addToSelection(elem);
            currentTool = ToolMode.SELECT;
            selectToolButton(ToolMode.SELECT);
            checkAllCollisions(); updateProperties(); viewportPanel.repaint();
        });
        container.add(btn);
        container.add(Box.createVerticalStrut(3));
    }

    private void selectToolButton(ToolMode mode) {
        for (Enumeration<AbstractButton> buttons = toolButtonGroup.getElements(); buttons.hasMoreElements();) {
            AbstractButton b = buttons.nextElement();
            String t = b.getText();
            if (mode == ToolMode.SELECT && t.contains("Select")) b.setSelected(true);
            else if (mode == ToolMode.DRAW_WALL && t.contains("Draw Wall")) b.setSelected(true);
            else if (mode == ToolMode.ROOM_BOX && t.contains("Room Box")) b.setSelected(true);
            else if (mode == ToolMode.FLOOR_ZONE && t.contains("Floor / Tiles")) b.setSelected(true);
            else if (mode == ToolMode.HATCH_POLYGON && t.contains("Hatch Perimeter")) b.setSelected(true);
            else if (mode == ToolMode.MEASURE && t.contains("Measure")) b.setSelected(true);
            else if (mode == ToolMode.PAN && t.contains("Pan")) b.setSelected(true);
        }
    }

    // --- Inspector Sidebar ---

    private JPanel createInspectorSidebar() {
        JPanel inspector = new JPanel(new BorderLayout());
        inspector.setPreferredSize(new Dimension(270, 600));
        inspector.setBackground(new Color(15, 23, 42));
        inspector.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(30, 41, 59)));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(12, 14, 10, 14));
        JLabel title = new JLabel("INSPECTOR");
        title.setFont(new Font("Consolas", Font.BOLD, 11));
        title.setForeground(new Color(148, 163, 184));
        propTypeBadge = new JLabel("NONE");
        propTypeBadge.setFont(new Font("Consolas", Font.BOLD, 10));
        propTypeBadge.setForeground(new Color(56, 189, 248));
        propTypeBadge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(56, 189, 248, 120), 1, true),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)));
        header.add(title, BorderLayout.WEST);
        header.add(propTypeBadge, BorderLayout.EAST);
        inspector.add(header, BorderLayout.NORTH);

        JPanel body = new JPanel(new CardLayout());
        body.setOpaque(false);

        // Empty state
        propEmptyPanel = new JPanel(new GridBagLayout());
        propEmptyPanel.setOpaque(false);
        JLabel emptyLbl = new JLabel("<html><center><b style='color:#38bdf8'>Selection Mode</b><br><br>"
                + "<span style='color:#cbd5e1'>Click objects to select.<br>"
                + "Drag L→R for <b style='color:#60a5fa'>Window Select</b><br>"
                + "Drag R→L for <b style='color:#4ade80'>Crossing Select</b><br>"
                + "Hold <b>Shift</b> to add/remove</span><br><br>"
                + "<span style='color:#94a3b8'>• <b>R</b> = Rotate 90°<br>"
                + "• <b>F / V</b> = Flip H / V<br>"
                + "• <b>Ctrl+A</b> = Select All<br>"
                + "• <b>Ctrl+Z / Y</b> = Undo / Redo<br>"
                + "• <b>Del</b> = Delete</span></center></html>");
        propEmptyPanel.add(emptyLbl);

        // Multi-select state
        propMultiPanel = new JPanel();
        propMultiPanel.setLayout(new BoxLayout(propMultiPanel, BoxLayout.Y_AXIS));
        propMultiPanel.setOpaque(false);
        propMultiPanel.setBorder(new EmptyBorder(14, 14, 14, 14));
        multiSelectLabel = new JLabel("0 objects selected");
        multiSelectLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        multiSelectLabel.setForeground(new Color(56, 189, 248));
        multiSelectLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        propMultiPanel.add(multiSelectLabel);
        propMultiPanel.add(Box.createVerticalStrut(16));

        ModernButton rotAllBtn = new ModernButton("⟳ Rotate All 90°", new Color(24, 33, 53), Color.WHITE, new Color(51, 65, 85));
        rotAllBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        rotAllBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        rotAllBtn.addActionListener(e -> rotateSelected(90));
        propMultiPanel.add(rotAllBtn);
        propMultiPanel.add(Box.createVerticalStrut(6));

        JPanel multiOrderRow = new JPanel(new GridLayout(1, 2, 4, 4));
        multiOrderRow.setOpaque(false);
        multiOrderRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        multiOrderRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        ModernButton multiFrontBtn = new ModernButton("⤒ Bring to Front", new Color(24, 33, 53), Color.WHITE, new Color(51, 65, 85));
        ModernButton multiBackBtn = new ModernButton("⤓ Send to Back", new Color(24, 33, 53), Color.WHITE, new Color(51, 65, 85));
        multiFrontBtn.addActionListener(e -> bringToFront());
        multiBackBtn.addActionListener(e -> sendToBack());
        multiOrderRow.add(multiFrontBtn); multiOrderRow.add(multiBackBtn);
        propMultiPanel.add(multiOrderRow);
        propMultiPanel.add(Box.createVerticalStrut(6));

        ModernButton delAllBtn = new ModernButton("✕ Delete All Selected", new Color(127, 29, 29), new Color(254, 205, 211), new Color(244, 63, 94));
        delAllBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        delAllBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        delAllBtn.addActionListener(e -> deleteSelected());
        propMultiPanel.add(delAllBtn);
        propMultiPanel.add(Box.createVerticalGlue());

        // Single object properties
        propContentPanel = new JPanel();
        propContentPanel.setLayout(new BoxLayout(propContentPanel, BoxLayout.Y_AXIS));
        propContentPanel.setOpaque(false);
        propContentPanel.setBorder(new EmptyBorder(10, 14, 14, 14));

        propLockBanner = new JPanel(new BorderLayout(6, 4));
        propLockBanner.setBackground(new Color(69, 26, 3, 220));
        propLockBanner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(245, 158, 11), 1, true),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        propLockBanner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
        propLockBanner.setAlignmentX(Component.LEFT_ALIGNMENT);
        propLockBanner.setVisible(false);

        propLockLabel = new JLabel("🔒 Stage is Frozen (Locked)");
        propLockLabel.setFont(new Font("Segoe UI", Font.BOLD, 10));
        propLockLabel.setForeground(new Color(251, 191, 36));

        propUnfreezeBtn = new ModernButton("🔓 Unfreeze Stage to Edit", new Color(180, 83, 9), Color.WHITE, new Color(245, 158, 11));
        propUnfreezeBtn.setPreferredSize(new Dimension(140, 24));
        propUnfreezeBtn.setFont(new Font("Segoe UI", Font.BOLD, 10));

        propLockBanner.add(propLockLabel, BorderLayout.CENTER);
        propLockBanner.add(propUnfreezeBtn, BorderLayout.SOUTH);

        propContentPanel.add(propLockBanner);
        propContentPanel.add(Box.createVerticalStrut(4));

        propNameField = createStyledTextField();
        propWidthField = createStyledTextField();
        propHeightField = createStyledTextField();
        propRotSlider = new JSlider(0, 360, 0);
        propRotSlider.setOpaque(false);
        propRotSlider.setForeground(new Color(56, 189, 248));

        addFormField(propContentPanel, "LABEL:", propNameField);
        addFormField(propContentPanel, "WIDTH (FT):", propWidthField);
        addFormField(propContentPanel, "DEPTH (FT):", propHeightField);

        JLabel sizeHintLbl = new JLabel("<html><span style='color:#38bdf8'><b>📐 Custom Size:</b></span> <span style='color:#cbd5e1'>Edit Width or Depth to custom fit any room.</span></html>");
        sizeHintLbl.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        sizeHintLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        propContentPanel.add(sizeHintLbl);
        propContentPanel.add(Box.createVerticalStrut(8));

        JLabel rotLbl = new JLabel("ROTATION:");
        rotLbl.setFont(new Font("Consolas", Font.BOLD, 10));
        rotLbl.setForeground(new Color(148, 163, 184));
        rotLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        propContentPanel.add(rotLbl);
        propContentPanel.add(Box.createVerticalStrut(4));
        propRotSlider.setAlignmentX(Component.LEFT_ALIGNMENT);
        propRotSlider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        propContentPanel.add(propRotSlider);
        propContentPanel.add(Box.createVerticalStrut(10));

        JPanel actionRow = new JPanel(new GridLayout(1, 3, 4, 4));
        actionRow.setOpaque(false);
        actionRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        actionRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        ModernButton rot90Btn = new ModernButton("⟳ 90°", new Color(24, 33, 53), Color.WHITE, new Color(51, 65, 85));
        ModernButton flipHBtn = new ModernButton("⇄ Flip", new Color(24, 33, 53), Color.WHITE, new Color(51, 65, 85));
        ModernButton flipVBtn = new ModernButton("⇅ Flip", new Color(24, 33, 53), Color.WHITE, new Color(51, 65, 85));
        rot90Btn.addActionListener(e -> rotateSelected(90));
        flipHBtn.addActionListener(e -> flipSelectedH());
        flipVBtn.addActionListener(e -> flipSelectedV());
        actionRow.add(rot90Btn); actionRow.add(flipHBtn); actionRow.add(flipVBtn);
        propContentPanel.add(actionRow);
        propContentPanel.add(Box.createVerticalStrut(8));

        JLabel orderLbl = new JLabel("ARRANGE (Z-ORDER):");
        orderLbl.setFont(new Font("Consolas", Font.BOLD, 10));
        orderLbl.setForeground(new Color(148, 163, 184));
        orderLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        propContentPanel.add(orderLbl);
        propContentPanel.add(Box.createVerticalStrut(4));

        JPanel orderRow = new JPanel(new GridLayout(1, 4, 3, 3));
        orderRow.setOpaque(false);
        orderRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        orderRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        ModernButton frontBtn = new ModernButton("⤒ Front", new Color(24, 33, 53), Color.WHITE, new Color(51, 65, 85));
        ModernButton backBtn = new ModernButton("⤓ Back", new Color(24, 33, 53), Color.WHITE, new Color(51, 65, 85));
        ModernButton fwdBtn = new ModernButton("↑ Fwd", new Color(24, 33, 53), Color.WHITE, new Color(51, 65, 85));
        ModernButton bwdBtn = new ModernButton("↓ Bwd", new Color(24, 33, 53), Color.WHITE, new Color(51, 65, 85));
        frontBtn.setToolTipText("Bring to Front (Ctrl+Shift+])");
        backBtn.setToolTipText("Send to Back (Ctrl+Shift+[)");
        fwdBtn.setToolTipText("Bring Forward (Ctrl+])");
        bwdBtn.setToolTipText("Send Backward (Ctrl+[)");
        frontBtn.addActionListener(e -> bringToFront());
        backBtn.addActionListener(e -> sendToBack());
        fwdBtn.addActionListener(e -> bringForward());
        bwdBtn.addActionListener(e -> sendBackward());
        orderRow.add(frontBtn); orderRow.add(backBtn); orderRow.add(fwdBtn); orderRow.add(bwdBtn);
        propContentPanel.add(orderRow);
        propContentPanel.add(Box.createVerticalStrut(8));

        ModernButton dupBtn = new ModernButton("Duplicate (Ctrl+D)", new Color(24, 33, 53), new Color(56, 189, 248), new Color(14, 116, 144));
        dupBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        dupBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        dupBtn.addActionListener(e -> duplicateSelected());
        propContentPanel.add(dupBtn);
        propContentPanel.add(Box.createVerticalStrut(5));

        ModernButton deleteBtn = new ModernButton("Delete (Del)", new Color(127, 29, 29), new Color(254, 205, 211), new Color(244, 63, 94));
        deleteBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        deleteBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        deleteBtn.addActionListener(e -> deleteSelected());
        propContentPanel.add(deleteBtn);
        propContentPanel.add(Box.createVerticalGlue());

        body.add(propEmptyPanel, "EMPTY");
        body.add(propMultiPanel, "MULTI");
        body.add(propContentPanel, "CONTENT");
        body.add(createAgentInspectorPanel(), "AGENT");
        inspector.add(body, BorderLayout.CENTER);

        setupInspectorListeners();
        return inspector;
    }

    private JPanel createAgentInspectorPanel() {
        agentInspectorPanel = new JPanel();
        agentInspectorPanel.setLayout(new BoxLayout(agentInspectorPanel, BoxLayout.Y_AXIS));
        agentInspectorPanel.setOpaque(false);
        agentInspectorPanel.setBorder(new EmptyBorder(10, 12, 12, 12));

        // Score Card
        JPanel scoreCard = new JPanel(new BorderLayout(8, 0));
        scoreCard.setBackground(new Color(24, 33, 53));
        scoreCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(51, 65, 85), 1, true),
                new EmptyBorder(10, 10, 10, 10)));
        scoreCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
        scoreCard.setAlignmentX(Component.LEFT_ALIGNMENT);

        agentGradeLabel = new JLabel("A+");
        agentGradeLabel.setFont(new Font("Segoe UI", Font.BOLD, 26));
        agentGradeLabel.setForeground(new Color(52, 211, 153));
        agentGradeLabel.setHorizontalAlignment(SwingConstants.CENTER);
        agentGradeLabel.setPreferredSize(new Dimension(50, 44));
        scoreCard.add(agentGradeLabel, BorderLayout.WEST);

        JPanel scoreText = new JPanel();
        scoreText.setLayout(new BoxLayout(scoreText, BoxLayout.Y_AXIS));
        scoreText.setOpaque(false);

        agentScoreLabel = new JLabel("Plan Health: 100 / 100");
        agentScoreLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        agentScoreLabel.setForeground(Color.WHITE);

        agentSummaryLabel = new JLabel("0 Errors  \u00b7  0 Warnings");
        agentSummaryLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        agentSummaryLabel.setForeground(new Color(148, 163, 184));

        scoreText.add(agentScoreLabel);
        scoreText.add(Box.createVerticalStrut(3));
        scoreText.add(agentSummaryLabel);
        scoreCard.add(scoreText, BorderLayout.CENTER);

        agentInspectorPanel.add(scoreCard);
        agentInspectorPanel.add(Box.createVerticalStrut(10));

        // Action Row: Re-check plan button
        ModernButton recheckBtn = new ModernButton("⚡ Re-Analyze Plan Now", new Color(2, 132, 199), Color.WHITE, new Color(56, 189, 248));
        recheckBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        recheckBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        recheckBtn.addActionListener(e -> runArchitectAgentAudit());
        agentInspectorPanel.add(recheckBtn);
        agentInspectorPanel.add(Box.createVerticalStrut(10));

        JLabel issuesTitle = new JLabel("AUDIT FINDINGS & SUGGESTIONS:");
        issuesTitle.setFont(new Font("Consolas", Font.BOLD, 10));
        issuesTitle.setForeground(new Color(148, 163, 184));
        issuesTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        agentInspectorPanel.add(issuesTitle);
        agentInspectorPanel.add(Box.createVerticalStrut(6));

        // Scrollable issues list
        agentIssuesListPanel = new JPanel();
        agentIssuesListPanel.setLayout(new BoxLayout(agentIssuesListPanel, BoxLayout.Y_AXIS));
        agentIssuesListPanel.setOpaque(false);

        JScrollPane issuesScroll = new JScrollPane(agentIssuesListPanel);
        issuesScroll.setBorder(BorderFactory.createLineBorder(new Color(30, 41, 59), 1));
        issuesScroll.getViewport().setBackground(new Color(15, 23, 42));
        issuesScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        issuesScroll.getVerticalScrollBar().setUnitIncrement(12);

        agentInspectorPanel.add(issuesScroll);
        return agentInspectorPanel;
    }

    private JTextField createStyledTextField() {
        JTextField tf = new JTextField();
        tf.setFont(new Font("Consolas", Font.PLAIN, 12));
        tf.setBackground(new Color(24, 33, 53));
        tf.setForeground(Color.WHITE);
        tf.setCaretColor(new Color(56, 189, 248));
        tf.setAlignmentX(Component.LEFT_ALIGNMENT);
        tf.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        tf.setPreferredSize(new Dimension(230, 28));
        tf.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(51, 65, 85), 1, true),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        return tf;
    }

    private void addFormField(JPanel parent, String labelText, JComponent field) {
        JLabel lbl = new JLabel(labelText);
        lbl.setFont(new Font("Consolas", Font.BOLD, 10));
        lbl.setForeground(new Color(148, 163, 184));
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        parent.add(lbl); parent.add(Box.createVerticalStrut(3));
        parent.add(field); parent.add(Box.createVerticalStrut(8));
    }

    private void setupInspectorListeners() {
        DocumentListener dl = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { applyPropChange(); }
            public void removeUpdate(DocumentEvent e) { applyPropChange(); }
            public void changedUpdate(DocumentEvent e) { applyPropChange(); }
        };
        propNameField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { applyNameChange(); }
            public void removeUpdate(DocumentEvent e) { applyNameChange(); }
            public void changedUpdate(DocumentEvent e) { applyNameChange(); }
        });
        propWidthField.getDocument().addDocumentListener(dl);
        propHeightField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { applyHeightChange(); }
            public void removeUpdate(DocumentEvent e) { applyHeightChange(); }
            public void changedUpdate(DocumentEvent e) { applyHeightChange(); }
        });
        propRotSlider.addChangeListener(e -> {
            if (isUpdatingInspector) return;
            if (primarySelected instanceof CADElement) {
                ((CADElement) primarySelected).rotation = propRotSlider.getValue();
                checkAllCollisions(); viewportPanel.repaint();
            }
        });
    }

    private void applyNameChange() {
        if (isUpdatingInspector) return;
        if (primarySelected instanceof CADElement) {
            ((CADElement) primarySelected).name = propNameField.getText();
            viewportPanel.repaint();
        }
    }

    private void applyPropChange() {
        if (isUpdatingInspector) return;
        try {
            double ft = Double.parseDouble(propWidthField.getText());
            if (ft <= 0) return;
            if (primarySelected instanceof Wall) {
                Wall w = (Wall) primarySelected;
                double currentLen = w.getLength();
                if (currentLen > 0) {
                    double targetPx = ft * FT_PX;
                    double ux = (w.x2 - w.x1) / currentLen, uy = (w.y2 - w.y1) / currentLen;
                    w.x2 = w.x1 + ux * targetPx; w.y2 = w.y1 + uy * targetPx;
                    checkAllCollisions(); viewportPanel.repaint();
                }
            } else if (primarySelected instanceof CADElement) {
                ((CADElement) primarySelected).w = ft * FT_PX;
                checkAllCollisions(); viewportPanel.repaint();
            }
        } catch (NumberFormatException ignored) {}
    }

    private void applyHeightChange() {
        if (isUpdatingInspector) return;
        try {
            double ft = Double.parseDouble(propHeightField.getText());
            if (ft <= 0) return;
            if (primarySelected instanceof CADElement) {
                ((CADElement) primarySelected).h = ft * FT_PX;
                checkAllCollisions(); viewportPanel.repaint();
            }
        } catch (NumberFormatException ignored) {}
    }

    private void updateProperties() {
        isUpdatingInspector = true;
        try {
            CardLayout cl = (CardLayout) propContentPanel.getParent().getLayout();
            if (currentStage == ProjectStage.STAGE_8_AGENT) {
                cl.show(propContentPanel.getParent(), "AGENT");
                propTypeBadge.setText("ARCHITECT AGENT");
                return;
            }
            if (selectedObjects.isEmpty()) {
                cl.show(propContentPanel.getParent(), "EMPTY");
                propTypeBadge.setText("NONE");
                return;
            }
            if (selectedObjects.size() > 1) {
                cl.show(propContentPanel.getParent(), "MULTI");
                propTypeBadge.setText(selectedObjects.size() + " SEL");
                multiSelectLabel.setText(selectedObjects.size() + " objects selected");
                return;
            }
            cl.show(propContentPanel.getParent(), "CONTENT");
            boolean isLocked = primarySelected != null && isObjectLocked(primarySelected);
            ProjectStage objStage = primarySelected != null ? getObjectStage(primarySelected) : null;

            if (isLocked && objStage != null) {
                propLockBanner.setVisible(true);
                propLockLabel.setText("🔒 " + objStage.title + " is Frozen");
                for (java.awt.event.ActionListener al : propUnfreezeBtn.getActionListeners()) propUnfreezeBtn.removeActionListener(al);
                propUnfreezeBtn.addActionListener(e -> {
                    stageLocked.put(objStage, false);
                    updateStageStepperUI();
                    updateProperties();
                    viewportPanel.repaint();
                });
            } else {
                propLockBanner.setVisible(false);
            }

            if (primarySelected instanceof Wall) {
                Wall w = (Wall) primarySelected;
                propTypeBadge.setText(isLocked ? "🔒 " + (int)w.thicknessInches + "\" WALL (FROZEN)" : w.thicknessInches + "\" WALL");
                propTypeBadge.setForeground(isLocked ? new Color(251, 191, 36) : new Color(56, 189, 248));
                propNameField.setText("Wall (" + (int)w.thicknessInches + "\")");
                propWidthField.setText(String.format("%.2f", w.getLength() / FT_PX));
                propHeightField.setText(String.format("%.2f", w.thicknessInches / 12.0));
                propNameField.setEditable(!isLocked);
                propWidthField.setEditable(!isLocked);
                propHeightField.setEditable(!isLocked);
                propRotSlider.setEnabled(false);
            } else if (primarySelected instanceof CADDimension) {
                CADDimension d = (CADDimension) primarySelected;
                propTypeBadge.setText(isLocked ? "🔒 DIMENSION (FROZEN)" : "DIMENSION");
                propTypeBadge.setForeground(isLocked ? new Color(251, 191, 36) : new Color(56, 189, 248));
                propNameField.setText("Linear Dimension");
                double dist = Point2D.distance(d.x1, d.y1, d.x2, d.y2);
                propWidthField.setText(String.format("%.2f", dist / FT_PX));
                propHeightField.setText(String.format("%.2f", d.offset));
                propNameField.setEditable(false);
                propWidthField.setEditable(false);
                propHeightField.setEditable(false);
                propRotSlider.setEnabled(false);
            } else if (primarySelected instanceof CADElement) {
                CADElement elem = (CADElement) primarySelected;
                String typeStr = elem.type.toUpperCase();
                if (elem.type.startsWith("opening")) {
                    typeStr = "WALL OPENING";
                } else if (elem.type.startsWith("kitchen")) {
                    typeStr = "KITCHEN (CUSTOM)";
                } else if (elem.type.startsWith("floor-")) {
                    typeStr = "FLOOR FINISH";
                } else if (elem.type.startsWith("set-")) {
                    typeStr = "ROOM SET (CUSTOM)";
                }
                propTypeBadge.setText(isLocked ? "🔒 " + typeStr + " (FROZEN)" : typeStr);
                propTypeBadge.setForeground(isLocked ? new Color(251, 191, 36) : new Color(56, 189, 248));
                propNameField.setText(elem.name);
                propWidthField.setText(String.format("%.2f", elem.w / FT_PX));
                propHeightField.setText(String.format("%.2f", elem.h / FT_PX));
                propNameField.setEditable(!isLocked);
                propWidthField.setEditable(!isLocked);
                propHeightField.setEditable(!isLocked);
                propRotSlider.setEnabled(!isLocked);
                propRotSlider.setValue((int) Math.round(elem.rotation));
            }
        } finally { isUpdatingInspector = false; }
    }

    private JPanel createStatusBar() {
        JPanel status = new JPanel(new BorderLayout());
        status.setBackground(new Color(15, 23, 42));
        status.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(30, 41, 59)));
        status.setPreferredSize(new Dimension(1000, 32));

        // Left Status: Cursor coords, Selection count, Collision status
        JPanel leftStatus = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 3));
        leftStatus.setOpaque(false);

        statusCursorLabel = new JLabel(" CURSOR: 0'-0\", 0'-0\" ");
        statusCursorLabel.setFont(new Font("Consolas", Font.PLAIN, 11));
        statusCursorLabel.setForeground(new Color(148, 163, 184));

        statusSelectionLabel = new JLabel(" SEL: 0 ");
        statusSelectionLabel.setFont(new Font("Consolas", Font.BOLD, 11));
        statusSelectionLabel.setForeground(new Color(56, 189, 248));

        statusCollisionLabel = new JLabel(" CLEAR ");
        statusCollisionLabel.setFont(new Font("Consolas", Font.BOLD, 11));
        statusCollisionLabel.setForeground(new Color(52, 211, 153));

        leftStatus.add(statusCursorLabel);
        leftStatus.add(new JSeparator(JSeparator.VERTICAL));
        leftStatus.add(statusSelectionLabel);
        leftStatus.add(statusCollisionLabel);
        status.add(leftStatus, BorderLayout.WEST);

        // Right Status (AutoCAD Standard Drafting Status Tray)
        JPanel trayGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 3));
        trayGroup.setOpaque(false);

        gridBtn = new ModernToggle("Grid [F7]", true,
                new Color(56, 189, 248), new Color(14, 116, 144), new Color(15, 23, 42));
        gridBtn.setFont(new Font("Segoe UI", Font.BOLD, 10));
        gridBtn.setToolTipText("Grid Display (F7): Hide or show background drafting grid lines");
        gridBtn.addActionListener(e -> { showGrid = gridBtn.isSelected(); viewportPanel.repaint(); });

        orthoBtn = new ModernToggle("ORTHO [F8]", false,
                new Color(251, 191, 36), new Color(245, 158, 11), new Color(69, 26, 3));
        orthoBtn.setFont(new Font("Segoe UI", Font.BOLD, 10));
        orthoBtn.addActionListener(e -> { orthoMode = orthoBtn.isSelected(); viewportPanel.repaint(); });

        snapBtn = new ModernToggle("Snap [F3]", false,
                new Color(52, 211, 153), new Color(16, 185, 129), new Color(6, 78, 59));
        snapBtn.setFont(new Font("Segoe UI", Font.BOLD, 10));
        snapBtn.setToolTipText("Snap to 1' increments (Smooth by default)");
        snapBtn.addActionListener(e -> {
            snapToGrid = snapBtn.isSelected();
            snapStepFeet = snapToGrid ? 1.0 : 0.0;
            viewportPanel.repaint();
        });

        snapCursorBtn = new ModernToggle("Cursor [F9]", false,
                new Color(251, 191, 36), new Color(234, 179, 8), new Color(69, 26, 3));
        snapCursorBtn.setFont(new Font("Segoe UI", Font.BOLD, 10));
        snapCursorBtn.addActionListener(e -> { showSnapCursor = snapCursorBtn.isSelected(); viewportPanel.repaint(); });

        collisionBtn = new ModernToggle("Collision [F4]", true,
                new Color(244, 63, 94), new Color(225, 29, 72), new Color(76, 5, 25));
        collisionBtn.setFont(new Font("Segoe UI", Font.BOLD, 10));
        collisionBtn.addActionListener(e -> {
            collisionDetectionEnabled = collisionBtn.isSelected();
            checkAllCollisions(); viewportPanel.repaint();
        });

        ModernButton zoomOutBtn = new ModernButton("−", new Color(24, 33, 53), Color.WHITE, new Color(51, 65, 85));
        zoomOutBtn.setPreferredSize(new Dimension(26, 22));
        ModernButton zoomInBtn = new ModernButton("+", new Color(24, 33, 53), Color.WHITE, new Color(51, 65, 85));
        zoomInBtn.setPreferredSize(new Dimension(26, 22));
        ModernButton zoomResetBtn = new ModernButton("Fit", new Color(24, 33, 53), Color.WHITE, new Color(51, 65, 85));
        zoomResetBtn.setPreferredSize(new Dimension(36, 22));

        zoomOutBtn.addActionListener(e -> { zoom = Math.max(0.2, zoom / 1.2); viewportPanel.repaint(); });
        zoomInBtn.addActionListener(e -> { zoom = Math.min(4.0, zoom * 1.2); viewportPanel.repaint(); });
        zoomResetBtn.addActionListener(e -> { zoom = 1.0; panX = 140; panY = 120; viewportPanel.repaint(); });

        trayGroup.add(gridBtn);
        trayGroup.add(orthoBtn);
        trayGroup.add(snapBtn);
        trayGroup.add(snapCursorBtn);
        trayGroup.add(collisionBtn);
        trayGroup.add(new JSeparator(JSeparator.VERTICAL));
        trayGroup.add(zoomOutBtn);
        trayGroup.add(zoomInBtn);
        trayGroup.add(zoomResetBtn);

        status.add(trayGroup, BorderLayout.EAST);
        return status;
    }

    public static double parseDimensionFeet(String text) {
        if (text == null) return 0.0;
        text = text.trim();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "(\\d+(?:\\.\\d+)?)\\s*(?:'|ft|feet)?(?:\\s*-?\\s*(\\d+(?:\\.\\d+)?)\\s*(?:\"|in)?)?",
                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(text);
        if (m.find()) {
            double ft = Double.parseDouble(m.group(1));
            if (m.group(2) != null && !m.group(2).isEmpty()) {
                ft += Double.parseDouble(m.group(2)) / 12.0;
            }
            return ft;
        }
        return 0.0;
    }

    public static double parseRoomAreaFromText(String text) {
        if (text == null) return 0.0;
        String[] lines = text.contains("\n") ? text.split("\n") : (text.contains(" / ") ? text.split("\\s*/\\s*") : new String[]{text});
        for (String line : lines) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                    "([0-9]+(?:\\.[0-9]+)?(?:'|ft)?(?:\\s*-?\\s*[0-9]+(?:\\.[0-9]+)?\"?)?)\\s*[×xX*]\\s*([0-9]+(?:\\.[0-9]+)?(?:'|ft)?(?:\\s*-?\\s*[0-9]+(?:\\.[0-9]+)?\"?)?)",
                    java.util.regex.Pattern.CASE_INSENSITIVE).matcher(line);
            if (m.find()) {
                double w = parseDimensionFeet(m.group(1));
                double h = parseDimensionFeet(m.group(2));
                if (w > 0 && h > 0) {
                    return w * h;
                }
            }
        }
        return 0.0;
    }

    public double computeRoomAreaFromWalls(double cx, double cy) {
        if (walls.isEmpty()) return 0.0;
        double minLeft = -Double.MAX_VALUE;
        double minRight = Double.MAX_VALUE;
        double minTop = -Double.MAX_VALUE;
        double minBottom = Double.MAX_VALUE;

        for (Wall w : walls) {
            if (Math.abs(w.x1 - w.x2) < 5.0) {
                double wallX = (w.x1 + w.x2) / 2.0;
                double minY = Math.min(w.y1, w.y2) - 10;
                double maxY = Math.max(w.y1, w.y2) + 10;
                if (cy >= minY && cy <= maxY) {
                    if (wallX <= cx && wallX > minLeft) minLeft = wallX;
                    if (wallX >= cx && wallX < minRight) minRight = wallX;
                }
            }
            if (Math.abs(w.y1 - w.y2) < 5.0) {
                double wallY = (w.y1 + w.y2) / 2.0;
                double minX = Math.min(w.x1, w.x2) - 10;
                double maxX = Math.max(w.x1, w.x2) + 10;
                if (cx >= minX && cx <= maxX) {
                    if (wallY <= cy && wallY > minTop) minTop = wallY;
                    if (wallY >= cy && wallY < minBottom) minBottom = wallY;
                }
            }
        }

        if (minLeft > -Double.MAX_VALUE / 2 && minRight < Double.MAX_VALUE / 2
                && minTop > -Double.MAX_VALUE / 2 && minBottom < Double.MAX_VALUE / 2) {
            double wFt = (minRight - minLeft) / FT_PX;
            double hFt = (minBottom - minTop) / FT_PX;
            if (wFt >= 2.0 && hFt >= 2.0 && wFt <= 100.0 && hFt <= 100.0) {
                return wFt * hFt;
            }
        }
        return 0.0;
    }

    // --- Keyboard Shortcuts ---

    private void setupKeyboardShortcuts() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            if (e.getID() != KeyEvent.KEY_PRESSED) return false;
            boolean isTyping = (KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() instanceof javax.swing.text.JTextComponent);

            if (e.getKeyCode() == KeyEvent.VK_F7) {
                showGrid = !showGrid; if (gridBtn != null) gridBtn.setSelected(showGrid); viewportPanel.repaint(); return true;
            } else if (e.getKeyCode() == KeyEvent.VK_F8) {
                orthoMode = !orthoMode; orthoBtn.setSelected(orthoMode); viewportPanel.repaint(); return true;
            } else if (e.getKeyCode() == KeyEvent.VK_F3) {
                snapToGrid = !snapToGrid; snapBtn.setSelected(snapToGrid);
                snapStepFeet = snapToGrid ? 1.0 : 0.0;
                viewportPanel.repaint(); return true;
            } else if (e.getKeyCode() == KeyEvent.VK_F9) {
                showSnapCursor = !showSnapCursor; snapCursorBtn.setSelected(showSnapCursor); viewportPanel.repaint(); return true;
            } else if (e.getKeyCode() == KeyEvent.VK_F4) {
                collisionDetectionEnabled = !collisionDetectionEnabled; collisionBtn.setSelected(collisionDetectionEnabled);
                checkAllCollisions(); viewportPanel.repaint(); return true;
            }

            if (!isTyping) {
                if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_Z) { undo(); return true; }
                if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_Y) { redo(); return true; }
                if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_A) { selectAll(); return true; }
                if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_D) { duplicateSelected(); return true; }
                if (e.isControlDown() && e.isShiftDown() && e.getKeyCode() == KeyEvent.VK_CLOSE_BRACKET) { bringToFront(); return true; }
                if (e.isControlDown() && e.isShiftDown() && e.getKeyCode() == KeyEvent.VK_OPEN_BRACKET) { sendToBack(); return true; }
                if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_CLOSE_BRACKET) { bringForward(); return true; }
                if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_OPEN_BRACKET) { sendBackward(); return true; }

                if (e.getKeyCode() == KeyEvent.VK_DELETE || e.getKeyCode() == KeyEvent.VK_BACK_SPACE) { deleteSelected(); return true; }
                if (e.getKeyCode() == KeyEvent.VK_R) { rotateSelected(90); return true; }
                if (e.getKeyCode() == KeyEvent.VK_F) { flipSelectedH(); return true; }
                if (e.getKeyCode() == KeyEvent.VK_V) { flipSelectedV(); return true; }
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    if (viewportPanel.isSnipMode) {
                        viewportPanel.cancelSnipMode();
                        return true;
                    }
                    if (viewportPanel.isMeasuring()) {
                        viewportPanel.cancelMeasure();
                        currentTool = ToolMode.SELECT;
                        selectToolButton(ToolMode.SELECT);
                        return true;
                    }
                    if (currentTool == ToolMode.HATCH_POLYGON) {
                        viewportPanel.clearHatchPoly();
                        currentTool = ToolMode.SELECT;
                        selectToolButton(ToolMode.SELECT);
                    }
                    clearSelection(); updateProperties(); viewportPanel.repaint(); return true;
                }
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    if (currentTool == ToolMode.HATCH_POLYGON) {
                        viewportPanel.finishHatchPoly();
                        return true;
                    }
                }
                if (e.getKeyCode() == KeyEvent.VK_S && !e.isControlDown()) {
                    currentTool = ToolMode.SELECT; selectToolButton(ToolMode.SELECT); return true;
                }
                if (e.getKeyCode() == KeyEvent.VK_W && !e.isControlDown()) {
                    currentTool = ToolMode.DRAW_WALL; selectToolButton(ToolMode.DRAW_WALL); return true;
                }
                if (e.getKeyCode() == KeyEvent.VK_T && !e.isControlDown()) {
                    currentTool = ToolMode.FLOOR_ZONE; selectToolButton(ToolMode.FLOOR_ZONE); return true;
                }
                if (e.getKeyCode() == KeyEvent.VK_P && !e.isControlDown()) {
                    currentTool = ToolMode.HATCH_POLYGON; selectToolButton(ToolMode.HATCH_POLYGON); return true;
                }
                if (e.getKeyCode() == KeyEvent.VK_H) {
                    currentTool = ToolMode.PAN; selectToolButton(ToolMode.PAN); return true;
                }

                // Arrow key nudge for selected elements
                double step = e.isShiftDown() ? FT_PX * 0.5 : FT_PX / 12.0;
                double dx = 0, dy = 0;
                if (e.getKeyCode() == KeyEvent.VK_LEFT) dx = -step;
                else if (e.getKeyCode() == KeyEvent.VK_RIGHT) dx = step;
                else if (e.getKeyCode() == KeyEvent.VK_UP) dy = -step;
                else if (e.getKeyCode() == KeyEvent.VK_DOWN) dy = step;
                if (dx != 0 || dy != 0) {
                    for (Object obj : selectedObjects) {
                        if (obj instanceof CADElement) { ((CADElement) obj).translate(dx, dy); }
                        else if (obj instanceof Wall) { Wall ww = (Wall) obj; ww.x1 += dx; ww.y1 += dy; ww.x2 += dx; ww.y2 += dy; }
                    }
                    checkAllCollisions(); viewportPanel.repaint();
                    return true;
                }
            }
            return false;
        });
    }

    // --- Utility Methods ---

    public static String formatImperial(double pixels) {
        double feetDec = Math.abs(pixels) / FT_PX;
        int totalInches = (int) Math.round(feetDec * 12.0);
        int feet = totalInches / 12;
        int inches = totalInches % 12;
        return feet + "'-" + inches + "\"";
    }

    public double snapCoord(double val) {
        if (!snapToGrid || snapStepFeet <= 0.0) return val;
        double stepPx = snapStepFeet * FT_PX;
        return Math.round(val / stepPx) * stepPx;
    }

    // --- Selection System (AutoCAD-style) ---

    private void clearSelection() {
        selectedObjects.clear(); primarySelected = null;
        updateSelectionStatus();
    }

    private void addToSelection(Object obj) {
        selectedObjects.add(obj); primarySelected = obj;
        updateSelectionStatus();
    }

    private void removeFromSelection(Object obj) {
        selectedObjects.remove(obj);
        if (primarySelected == obj) primarySelected = selectedObjects.isEmpty() ? null : selectedObjects.iterator().next();
        updateSelectionStatus();
    }

    private void toggleSelection(Object obj) {
        if (selectedObjects.contains(obj)) removeFromSelection(obj);
        else addToSelection(obj);
    }

    private String getObjectName(Object obj) {
        if (obj instanceof Wall) {
            Wall w = (Wall) obj;
            return "Wall (" + (int)w.thicknessInches + "\" · " + formatImperial(w.getLength()) + ")";
        } else if (obj instanceof CADElement) {
            return ((CADElement) obj).name;
        } else if (obj instanceof CADDimension) {
            CADDimension d = (CADDimension) obj;
            return "Dimension (" + formatImperial(Point2D.distance(d.x1, d.y1, d.x2, d.y2)) + ")";
        }
        return "Object";
    }

    private boolean isSelected(Object obj) { return selectedObjects.contains(obj); }

    private void selectAll() {
        clearSelection();
        for (Wall w : walls) addToSelection(w);
        for (CADElement el : elements) addToSelection(el);
        for (CADDimension d : dimensions) addToSelection(d);
        updateProperties(); viewportPanel.repaint();
    }

    private void updateSelectionStatus() {
        if (statusSelectionLabel != null) {
            int n = selectedObjects.size();
            statusSelectionLabel.setText(" SEL: " + n + " ");
            statusSelectionLabel.setForeground(n > 0 ? new Color(56, 189, 248) : new Color(100, 116, 139));
        }
    }

    // Selection rectangle hit test
    private List<Object> hitTestRect(double x1, double y1, double x2, double y2, boolean mustFullyEnclose) {
        double minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
        double minY = Math.min(y1, y2), maxY = Math.max(y1, y2);
        Rectangle2D.Double rect = new Rectangle2D.Double(minX, minY, maxX - minX, maxY - minY);
        List<Object> result = new ArrayList<>();

        for (CADElement el : elements) {
            if (isObjectLocked(el)) continue;
            Rectangle2D.Double elBounds = new Rectangle2D.Double(el.x, el.y, el.w, el.h);
            if (mustFullyEnclose) { if (rect.contains(elBounds)) result.add(el); }
            else { if (rect.intersects(elBounds)) result.add(el); }
        }
        for (Wall w : walls) {
            if (isObjectLocked(w)) continue;
            double halfThick = ((w.thicknessInches / 12.0) * FT_PX) / 2.0;
            Rectangle2D.Double wallBounds = new Rectangle2D.Double(
                    Math.min(w.x1, w.x2) - halfThick, Math.min(w.y1, w.y2) - halfThick,
                    Math.abs(w.x2 - w.x1) + halfThick * 2.0, Math.abs(w.y2 - w.y1) + halfThick * 2.0);
            if (mustFullyEnclose) { if (rect.contains(wallBounds)) result.add(w); }
            else { if (rect.intersects(wallBounds)) result.add(w); }
        }
        for (CADDimension d : dimensions) {
            if (isObjectLocked(d)) continue;
            double dx = d.x2 - d.x1, dy = d.y2 - d.y1;
            double len = Math.hypot(dx, dy);
            double perpX = (len > 0) ? (-dy / len) * (d.offset / zoom) : 0;
            double perpY = (len > 0) ? (dx / len) * (d.offset / zoom) : 0;
            double d1x = d.x1 + perpX, d1y = d.y1 + perpY;
            double d2x = d.x2 + perpX, d2y = d.y2 + perpY;
            double dMinX = Math.min(Math.min(d.x1, d.x2), Math.min(d1x, d2x));
            double dMaxX = Math.max(Math.max(d.x1, d.x2), Math.max(d1x, d2x));
            double dMinY = Math.min(Math.min(d.y1, d.y2), Math.min(d1y, d2y));
            double dMaxY = Math.max(Math.max(d.y1, d.y2), Math.max(d1y, d2y));
            Rectangle2D.Double dimBounds = new Rectangle2D.Double(dMinX, dMinY, Math.max(4, dMaxX - dMinX), Math.max(4, dMaxY - dMinY));
            if (mustFullyEnclose) { if (rect.contains(dimBounds)) result.add(d); }
            else { if (rect.intersects(dimBounds)) result.add(d); }
        }
        return result;
    }

    // --- Undo / Redo ---

    private void saveUndoState() {
        undoStack.push(new AppState(walls, elements, dimensions));
        redoStack.clear();
        while (undoStack.size() > MAX_UNDO) ((ArrayDeque<AppState>) undoStack).removeLast();
        updateUndoButtons();
    }

    private void undo() {
        if (undoStack.isEmpty()) return;
        redoStack.push(new AppState(walls, elements, dimensions));
        restoreState(undoStack.pop());
        updateUndoButtons();
    }

    private void redo() {
        if (redoStack.isEmpty()) return;
        undoStack.push(new AppState(walls, elements, dimensions));
        restoreState(redoStack.pop());
        updateUndoButtons();
    }

    private void restoreState(AppState state) {
        walls.clear(); walls.addAll(state.walls);
        elements.clear(); elements.addAll(state.elements);
        dimensions.clear(); dimensions.addAll(state.dimensions);
        clearSelection();
        checkAllCollisions(); updateProperties(); viewportPanel.repaint();
    }

    private void updateUndoButtons() {
        if (undoBtn != null) undoBtn.setEnabled(!undoStack.isEmpty());
        if (redoBtn != null) redoBtn.setEnabled(!redoStack.isEmpty());
    }

    // --- Object Operations ---

    private void rotateSelected(double deg) {
        if (selectedObjects.isEmpty()) return;
        saveUndoState();
        for (Object obj : selectedObjects) {
            if (obj instanceof CADElement) {
                CADElement el = (CADElement) obj;
                el.rotation = (el.rotation + deg) % 360;
                if (el.rotation < 0) el.rotation += 360;
            }
        }
        if (primarySelected instanceof CADElement) {
            propRotSlider.setValue((int) ((CADElement) primarySelected).rotation);
        }
        checkAllCollisions(); viewportPanel.repaint();
    }

    private void flipSelectedH() {
        for (Object obj : selectedObjects)
            if (obj instanceof CADElement) ((CADElement) obj).flipH = !((CADElement) obj).flipH;
        viewportPanel.repaint();
    }

    private void flipSelectedV() {
        for (Object obj : selectedObjects)
            if (obj instanceof CADElement) ((CADElement) obj).flipV = !((CADElement) obj).flipV;
        viewportPanel.repaint();
    }

    private void duplicateSelected() {
        if (selectedObjects.isEmpty()) return;
        saveUndoState();
        List<Object> newObjs = new ArrayList<>();
        for (Object obj : selectedObjects) {
            if (obj instanceof CADElement) {
                CADElement copy = ((CADElement) obj).copy();
                elements.add(copy); newObjs.add(copy);
            } else if (obj instanceof Wall) {
                Wall w = (Wall) obj;
                Wall copy = new Wall(w.x1 + FT_PX, w.y1 + FT_PX, w.x2 + FT_PX, w.y2 + FT_PX, w.thicknessInches);
                walls.add(copy); newObjs.add(copy);
            }
        }
        clearSelection();
        for (Object o : newObjs) addToSelection(o);
        checkAllCollisions(); updateProperties(); viewportPanel.repaint();
    }

    private void deleteSelected() {
        if (selectedObjects.isEmpty()) return;
        List<Object> toDelete = new ArrayList<>();
        boolean hadLocked = false;
        for (Object obj : selectedObjects) {
            if (isObjectLocked(obj)) {
                hadLocked = true;
            } else {
                toDelete.add(obj);
            }
        }
        if (hadLocked && toDelete.isEmpty()) {
            if (statusCursorLabel != null) {
                statusCursorLabel.setText(" 🔒 CANNOT DELETE: Object is frozen in a locked stage. Unfreeze stage to delete. ");
            }
            return;
        }
        if (toDelete.isEmpty()) return;
        saveUndoState();
        for (Object obj : toDelete) {
            if (obj instanceof Wall) walls.remove(obj);
            else if (obj instanceof CADElement) elements.remove(obj);
            else if (obj instanceof CADDimension) dimensions.remove(obj);
        }
        clearSelection();
        checkAllCollisions(); updateProperties(); viewportPanel.repaint();
    }

    // --- Z-Order / Object Arrange Operations ---

    public void bringToFront() {
        if (selectedObjects.isEmpty()) return;
        saveUndoState();
        List<CADElement> selectedEls = new ArrayList<>();
        for (Object obj : selectedObjects) {
            if (obj instanceof CADElement) selectedEls.add((CADElement) obj);
        }
        if (selectedEls.isEmpty()) return;
        elements.removeAll(selectedEls);
        elements.addAll(selectedEls);
        viewportPanel.repaint();
    }

    public void sendToBack() {
        if (selectedObjects.isEmpty()) return;
        saveUndoState();
        List<CADElement> selectedEls = new ArrayList<>();
        for (Object obj : selectedObjects) {
            if (obj instanceof CADElement) selectedEls.add((CADElement) obj);
        }
        if (selectedEls.isEmpty()) return;
        elements.removeAll(selectedEls);
        int insertIdx = 0;
        for (int i = 0; i < elements.size(); i++) {
            if (elements.get(i).type.startsWith("floor-")) insertIdx = i + 1;
            else break;
        }
        boolean hasFloor = false;
        for (CADElement el : selectedEls) {
            if (el.type.startsWith("floor-")) { hasFloor = true; break; }
        }
        if (hasFloor) insertIdx = 0;
        elements.addAll(insertIdx, selectedEls);
        viewportPanel.repaint();
    }

    public void bringForward() {
        if (selectedObjects.isEmpty()) return;
        saveUndoState();
        for (int i = elements.size() - 2; i >= 0; i--) {
            CADElement el = elements.get(i);
            if (selectedObjects.contains(el)) {
                CADElement next = elements.get(i + 1);
                if (!selectedObjects.contains(next)) {
                    elements.set(i, next);
                    elements.set(i + 1, el);
                }
            }
        }
        viewportPanel.repaint();
    }

    public void sendBackward() {
        if (selectedObjects.isEmpty()) return;
        saveUndoState();
        for (int i = 1; i < elements.size(); i++) {
            CADElement el = elements.get(i);
            if (selectedObjects.contains(el)) {
                CADElement prev = elements.get(i - 1);
                if (!selectedObjects.contains(prev)) {
                    elements.set(i, prev);
                    elements.set(i - 1, el);
                }
            }
        }
        viewportPanel.repaint();
    }

    // --- Auto Dimension Operations ---

    private static class DimCandidate {
        double x1, y1, x2, y2;

        DimCandidate(double x1, double y1, double x2, double y2) {
            this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
            canonicalize();
        }

        void canonicalize() {
            if (x1 > x2 || (Math.abs(x1 - x2) < 0.5 && y1 > y2)) {
                double tx = x1; x1 = x2; x2 = tx;
                double ty = y1; y1 = y2; y2 = ty;
            }
        }

        double length() {
            return Math.hypot(x2 - x1, y2 - y1);
        }

        boolean isHorizontal() {
            return Math.abs(y2 - y1) <= Math.abs(x2 - x1);
        }

        boolean isVertical() {
            return Math.abs(x2 - x1) < Math.abs(y2 - y1);
        }

        boolean isCloseTo(DimCandidate o, double tol) {
            return (Point2D.distance(x1, y1, o.x1, o.y1) < tol && Point2D.distance(x2, y2, o.x2, o.y2) < tol);
        }
    }

    /**
     * Automatically generates architectural dimension lines without duplicates or collinear clashes.
     */
    public void autoGenerateDimensions() {
        saveUndoState();
        dimensions.clear();

        // 1. Collect candidate segments
        List<DimCandidate> candidates = new ArrayList<>();
        for (Wall w : walls) {
            if (w.getLength() >= FT_PX * 1.0) {
                candidates.add(new DimCandidate(w.x1, w.y1, w.x2, w.y2));
            }
        }

        if (candidates.isEmpty()) {
            viewportPanel.repaint();
            return;
        }

        // 2. Filter exact and near-identical duplicates
        List<DimCandidate> unique = new ArrayList<>();
        for (DimCandidate c : candidates) {
            boolean dup = false;
            for (DimCandidate u : unique) {
                if (c.isCloseTo(u, 10.0)) {
                    dup = true;
                    break;
                }
            }
            if (!dup) unique.add(c);
        }

        // 3. Compute layout bounding box to determine exterior vs interior
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (DimCandidate s : unique) {
            minX = Math.min(minX, Math.min(s.x1, s.x2));
            maxX = Math.max(maxX, Math.max(s.x1, s.x2));
            minY = Math.min(minY, Math.min(s.y1, s.y2));
            maxY = Math.max(maxY, Math.max(s.y1, s.y2));
        }
        double centerX = (minX + maxX) / 2.0;
        double centerY = (minY + maxY) / 2.0;
        double boundTol = 14.0; // wall boundary tolerance

        // 4. Split into Horizontal, Vertical, and Angled
        List<DimCandidate> horiz = new ArrayList<>();
        List<DimCandidate> vert = new ArrayList<>();
        List<DimCandidate> angled = new ArrayList<>();

        for (DimCandidate s : unique) {
            if (s.isHorizontal()) horiz.add(s);
            else if (s.isVertical()) vert.add(s);
            else angled.add(s);
        }

        // 5. Clean collinear overlapping segments (removes duplicate parallel strings)
        List<DimCandidate> cleanHoriz = filterCollinearOverlaps(horiz, true);
        List<DimCandidate> cleanVert = filterCollinearOverlaps(vert, false);

        double baseOffset = 26.0;

        // 6. Generate clean horizontal dimensions
        for (DimCandidate s : cleanHoriz) {
            double midY = (s.y1 + s.y2) / 2.0;
            double off;
            if (Math.abs(midY - minY) <= boundTol) {
                // Top exterior wall -> dimension ABOVE
                off = -baseOffset;
            } else if (Math.abs(midY - maxY) <= boundTol) {
                // Bottom exterior wall -> dimension BELOW
                off = baseOffset;
            } else {
                // Interior horizontal wall -> offset away from center
                off = (midY < centerY) ? -baseOffset : baseOffset;
            }
            dimensions.add(new CADDimension(s.x1, s.y1, s.x2, s.y2, off));
        }

        // 7. Generate clean vertical dimensions
        for (DimCandidate s : cleanVert) {
            double midX = (s.x1 + s.x2) / 2.0;
            double off;
            if (Math.abs(midX - minX) <= boundTol) {
                // Left exterior wall -> dimension LEFT (positive offset in CAD screen math)
                off = baseOffset;
            } else if (Math.abs(midX - maxX) <= boundTol) {
                // Right exterior wall -> dimension RIGHT (negative offset in CAD screen math)
                off = -baseOffset;
            } else {
                // Interior vertical wall -> offset away from center
                off = (midX < centerX) ? baseOffset : -baseOffset;
            }
            dimensions.add(new CADDimension(s.x1, s.y1, s.x2, s.y2, off));
        }

        // 8. Generate angled dimensions (if any)
        for (DimCandidate s : angled) {
            double midX = (s.x1 + s.x2) / 2.0, midY = (s.y1 + s.y2) / 2.0;
            double len = s.length();
            double dx = s.x2 - s.x1, dy = s.y2 - s.y1;
            double px = -dy / len, py = dx / len;
            double dot = px * (midX - centerX) + py * (midY - centerY);
            double off = (dot >= 0) ? baseOffset : -baseOffset;
            dimensions.add(new CADDimension(s.x1, s.y1, s.x2, s.y2, off));
        }

        viewportPanel.repaint();
    }

    private List<DimCandidate> filterCollinearOverlaps(List<DimCandidate> list, boolean isHorizontal) {
        List<DimCandidate> result = new ArrayList<>();
        // Sort by coordinate (Y for horizontal, X for vertical), then longest first
        list.sort((a, b) -> {
            double coordA = isHorizontal ? a.y1 : a.x1;
            double coordB = isHorizontal ? b.y1 : b.x1;
            if (Math.abs(coordA - coordB) > 6.0) {
                return Double.compare(coordA, coordB);
            }
            return Double.compare(b.length(), a.length());
        });

        for (DimCandidate s : list) {
            boolean keep = true;
            for (DimCandidate existing : result) {
                double coordS = isHorizontal ? s.y1 : s.x1;
                double coordE = isHorizontal ? existing.y1 : existing.x1;
                if (Math.abs(coordS - coordE) <= 8.0) {
                    double startS = isHorizontal ? s.x1 : s.y1;
                    double endS = isHorizontal ? s.x2 : s.y2;
                    double startE = isHorizontal ? existing.x1 : existing.y1;
                    double endE = isHorizontal ? existing.x2 : existing.y2;

                    double overlapStart = Math.max(startS, startE);
                    double overlapEnd = Math.min(endS, endE);
                    double overlap = overlapEnd - overlapStart;

                    if (overlap > 12.0) {
                        // If same length or existing substantially covers s
                        if (Math.abs(s.length() - existing.length()) < 16.0) {
                            keep = false;
                            break;
                        }
                        if (startS >= startE - 8.0 && endS <= endE + 8.0) {
                            keep = false;
                            break;
                        }
                    }
                }
            }
            if (keep) {
                result.add(s);
            }
        }
        return result;
    }


    // --- Wall Operations ---

    public void autoJoinAllWalls() {
        double threshold = (wallThicknessInches / 12.0) * FT_PX * 1.5;
        for (Wall w : walls) autoJoinWallEndpoints(w, threshold);
        checkAllCollisions();
        if (viewportPanel != null) viewportPanel.repaint();
    }

    public void autoJoinWallEndpoints(Wall wall, double threshold) {
        Point2D.Double p1 = findWeldPoint(wall, wall.x1, wall.y1, threshold);
        if (p1 != null) { wall.x1 = p1.x; wall.y1 = p1.y; }
        Point2D.Double p2 = findWeldPoint(wall, wall.x2, wall.y2, threshold);
        if (p2 != null) { wall.x2 = p2.x; wall.y2 = p2.y; }
    }

    private Point2D.Double findWeldPoint(Wall sourceWall, double x, double y, double threshold) {
        for (Wall other : walls) {
            if (other == sourceWall) continue;
            if (Point2D.distance(x, y, other.x1, other.y1) <= threshold) return new Point2D.Double(other.x1, other.y1);
            if (Point2D.distance(x, y, other.x2, other.y2) <= threshold) return new Point2D.Double(other.x2, other.y2);
        }
        for (Wall other : walls) {
            if (other == sourceWall) continue;
            double d = Line2D.ptSegDist(other.x1, other.y1, other.x2, other.y2, x, y);
            if (d <= threshold) {
                double dx = other.x2 - other.x1, dy = other.y2 - other.y1;
                double lenSq = dx * dx + dy * dy;
                if (lenSq > 0) {
                    double t = Math.max(0.0, Math.min(1.0, ((x - other.x1) * dx + (y - other.y1) * dy) / lenSq));
                    return new Point2D.Double(other.x1 + t * dx, other.y1 + t * dy);
                }
            }
        }
        return null;
    }

    // --- Collision Detection ---

    private boolean isPassableSurface(CADElement el) {
        return el.isOpening() || el.type.equals("tag") || el.type.equals("north-arrow")
                || el.type.equals("ceiling-fan") || el.type.equals("light-point")
                || el.type.startsWith("road") || el.type.equals("parking-bay")
                || el.type.equals("pathway");
    }

    private void checkAllCollisions() {
        int collisionCount = 0;
        for (CADElement el : elements) {
            el.hasCollision = false;
            if (!collisionDetectionEnabled || isPassableSurface(el)) continue;

            Area elArea = getElementFootprintArea(el);
            for (Wall w : walls) {
                Area wallArea = getWallArea(w);
                Area test = new Area(elArea); test.intersect(wallArea);
                if (!test.isEmpty()) { el.hasCollision = true; collisionCount++; break; }
            }
            if (!el.hasCollision) {
                for (CADElement other : elements) {
                    if (other == el || isPassableSurface(other)) continue;
                    Area otherArea = getElementFootprintArea(other);
                    Area test = new Area(elArea); test.intersect(otherArea);
                    if (!test.isEmpty()) { el.hasCollision = true; collisionCount++; break; }
                }
            }
        }
        if (statusCollisionLabel != null) {
            if (collisionCount > 0) {
                statusCollisionLabel.setText(" ⚠ " + collisionCount + " COLLISION ");
                statusCollisionLabel.setForeground(new Color(244, 63, 94));
            } else {
                statusCollisionLabel.setText(" CLEAR ");
                statusCollisionLabel.setForeground(new Color(52, 211, 153));
            }
        }
    }

    private Area getElementFootprintArea(CADElement el) {
        Rectangle2D.Double rect = new Rectangle2D.Double(-el.w / 2.0, -el.h / 2.0, el.w, el.h);
        AffineTransform at = new AffineTransform();
        at.translate(el.x + el.w / 2.0, el.y + el.h / 2.0);
        at.rotate(Math.toRadians(el.rotation));
        return new Area(at.createTransformedShape(rect));
    }

    private Area getWallArea(Wall w) {
        double thickPx = (w.thicknessInches / 12.0) * FT_PX;
        double dx = w.x2 - w.x1, dy = w.y2 - w.y1;
        double len = Math.hypot(dx, dy);
        if (len == 0) return new Area();
        double px = (-dy / len) * (thickPx / 2.0), py = (dx / len) * (thickPx / 2.0);
        Path2D.Double path = new Path2D.Double();
        path.moveTo(w.x1 + px, w.y1 + py); path.lineTo(w.x2 + px, w.y2 + py);
        path.lineTo(w.x2 - px, w.y2 - py); path.lineTo(w.x1 - px, w.y1 - py);
        path.closePath();
        return new Area(path);
    }


    // ========================================================================
    // =========================  CAD VIEWPORT PANEL  =========================
    // ========================================================================

    private class CADViewportPanel extends JPanel {

        private Point mouseScreen = new Point();
        private Point2D.Double mouseWorld = new Point2D.Double();
        private Point2D.Double drawStart = new Point2D.Double();
        private boolean isDrawing = false;
        private boolean isDragging = false;
        private boolean isRotating = false;
        private boolean isPanning = false;
        private Point panStartScreen = new Point();
        private Point2D.Double dragStartWorld = new Point2D.Double();
        private Point2D.Double lastDragWorld = new Point2D.Double();

        // AutoCAD Selection Rectangle
        private boolean isWindowSelecting = false;
        private Point2D.Double selRectStart = null;

        // Selection Cycling & Hover Feedback
        private Object hoveredObject = null;
        private Point lastClickScreen = null;
        private int cycleHitIndex = 0;

        // Wall grip editing
        private Wall gripWall = null;
        private boolean gripIsP1 = false;

        // AutoCAD Perimeter Hatch Polyline Tool
        private final List<Point2D.Double> currentHatchPoly = new ArrayList<>();

        public void clearHatchPoly() {
            currentHatchPoly.clear();
            repaint();
        }

        public void finishHatchPoly() {
            if (currentHatchPoly.size() >= 3) {
                saveUndoState();
                String label = "Hatch (" + activeHatchPattern.replace("floor-", "") + ")";
                CADElement hatchElem = new CADElement(activeHatchPattern, label, currentHatchPoly);
                elements.add(hatchElem);
                clearSelection();
                addToSelection(hatchElem);
                currentHatchPoly.clear();
                currentTool = ToolMode.SELECT;
                selectToolButton(ToolMode.SELECT);
                updateProperties();
                repaint();
            } else {
                currentHatchPoly.clear();
                repaint();
            }
        }

        // AutoCAD-style 3-Step Point-to-Point Dimensioning Tool
        private Point2D.Double dimP1 = null;
        private Point2D.Double dimP2 = null;
        private int dimStep = 0; // 0: wait for Pt 1, 1: wait for Pt 2, 2: wait for offset placement

        public boolean isMeasuring() {
            return currentTool == ToolMode.MEASURE && dimStep > 0;
        }

        public void initPointToPointDimension() {
            dimP1 = null;
            dimP2 = null;
            dimStep = 0;
            if (statusCursorLabel != null) {
                statusCursorLabel.setText(" 📏 DIMENSION: Click first extension point (Point 1) · ESC to cancel ");
            }
            repaint();
        }

        public void cancelMeasure() {
            dimP1 = null;
            dimP2 = null;
            dimStep = 0;
            updateStatusText();
            repaint();
        }

        // Snipping Tool Canvas Mode
        public boolean isSnipMode = false;
        private boolean isSnipDragging = false;
        private double snipAspect = 16.0 / 9.0;
        private Point snipStartScreen = null;
        private Point snipCurrentScreen = null;
        private java.util.function.Consumer<Rectangle2D.Double> snipCallback = null;

        public void startSnipMode(double aspect, java.util.function.Consumer<Rectangle2D.Double> callback) {
            this.isSnipMode = true;
            this.isSnipDragging = false;
            this.snipAspect = aspect;
            this.snipStartScreen = null;
            this.snipCurrentScreen = null;
            this.snipCallback = callback;
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            statusCursorLabel.setText(" \u2702 SNIP MODE: Drag a box on canvas (locked to " + (aspect > 1.0 ? "16:9" : "9:16") + ") \u00b7 ESC to cancel ");
            repaint();
        }

        public void cancelSnipMode() {
            this.isSnipMode = false;
            this.isSnipDragging = false;
            this.snipStartScreen = null;
            this.snipCurrentScreen = null;
            if (this.snipCallback != null) {
                java.util.function.Consumer<Rectangle2D.Double> cb = this.snipCallback;
                this.snipCallback = null;
                cb.accept(null);
            }
            setCursor(Cursor.getDefaultCursor());
            updateStatusText();
            repaint();
        }

        private Rectangle getConstrainedSnipScreenRect() {
            if (snipStartScreen == null || snipCurrentScreen == null) return null;
            int sx = snipStartScreen.x, sy = snipStartScreen.y;
            int cx = snipCurrentScreen.x, cy = snipCurrentScreen.y;
            int rawW = Math.abs(cx - sx);
            if (rawW < 4) return null;
            int constrainedH = (int) Math.round(rawW / snipAspect);
            int rx = (cx >= sx) ? sx : (sx - rawW);
            int ry = (cy >= sy) ? sy : (sy - constrainedH);
            return new Rectangle(rx, ry, rawW, constrainedH);
        }

        private final double ROT_STALK_LEN = 30.0;
        private final double ROT_HANDLE_RADIUS = 9.0;
        private final List<HUDButton> hudButtons = new ArrayList<>();

        private class HUDButton {
            Rectangle bounds; String text; Runnable action;
            HUDButton(Rectangle b, String t, Runnable a) { bounds = b; text = t; action = a; }
        }

        public CADViewportPanel() {
            setBackground(new Color(17, 24, 39));

            MouseAdapter adapter = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    requestFocusInWindow();
                    mouseScreen = e.getPoint();
                    mouseWorld = screenToWorld(mouseScreen.x, mouseScreen.y);

                    // Snip tool canvas mode
                    if (isSnipMode) {
                        if (SwingUtilities.isLeftMouseButton(e)) {
                            snipStartScreen = e.getPoint();
                            snipCurrentScreen = e.getPoint();
                            isSnipDragging = true;
                            repaint();
                        } else if (SwingUtilities.isRightMouseButton(e)) {
                            cancelSnipMode();
                        }
                        return;
                    }

                    // HUD buttons first
                    for (HUDButton btn : hudButtons) {
                        if (btn.bounds.contains(mouseScreen)) { btn.action.run(); return; }
                    }

                    // Pan (middle mouse, or Pan tool, or Shift+Right)
                    if (SwingUtilities.isMiddleMouseButton(e) || currentTool == ToolMode.PAN
                            || (e.isShiftDown() && SwingUtilities.isRightMouseButton(e))) {
                        isPanning = true; panStartScreen = e.getPoint(); return;
                    }

                    // AutoCAD Hatch Perimeter Tool (Polyline vertex placement)
                    if (currentTool == ToolMode.HATCH_POLYGON) {
                        if (SwingUtilities.isRightMouseButton(e) || e.getClickCount() >= 2) {
                            finishHatchPoly();
                            return;
                        }
                        Point2D.Double snapPt = getOsnapPoint(mouseWorld.x, mouseWorld.y);
                        Point2D.Double pt = (snapPt != null) ? snapPt : new Point2D.Double(snapCoord(mouseWorld.x), snapCoord(mouseWorld.y));

                        // If user clicks close to the first vertex (within 12px screen space), close the perimeter
                        if (currentHatchPoly.size() >= 3) {
                            Point firstScreen = worldToScreen(currentHatchPoly.get(0).x, currentHatchPoly.get(0).y);
                            if (firstScreen.distance(mouseScreen) <= 14) {
                                finishHatchPoly();
                                return;
                            }
                        }

                        currentHatchPoly.add(pt);
                        repaint();
                        return;
                    }

                    // AutoCAD-Style Point-to-Point Linear Dimension Tool
                    if (currentTool == ToolMode.MEASURE) {
                        Point2D.Double snapPt = getOsnapPoint(mouseWorld.x, mouseWorld.y);
                        Point2D.Double pt = (snapPt != null) ? snapPt : new Point2D.Double(snapCoord(mouseWorld.x), snapCoord(mouseWorld.y));

                        if (dimStep == 0) {
                            dimP1 = pt;
                            dimStep = 1;
                            if (statusCursorLabel != null) {
                                statusCursorLabel.setText(" 📏 DIMENSION: Click second extension point (Point 2) · ESC to cancel ");
                            }
                            repaint();
                            return;
                        } else if (dimStep == 1) {
                            if (dimP1.distance(pt) < 0.2 * FT_PX) return; // ignore accidental double-click at same point
                            dimP2 = pt;
                            dimStep = 2;
                            if (statusCursorLabel != null) {
                                statusCursorLabel.setText(" 📏 DIMENSION: Move mouse to set offset and click to place · ESC to cancel ");
                            }
                            repaint();
                            return;
                        } else if (dimStep == 2) {
                            Point sp1 = worldToScreen(dimP1.x, dimP1.y);
                            Point sp2 = worldToScreen(dimP2.x, dimP2.y);
                            double dx = sp2.x - sp1.x, dy = sp2.y - sp1.y;
                            double len = Math.hypot(dx, dy);
                            double offsetPx = 22.0;
                            if (len > 0) {
                                double nx = -dy / len, ny = dx / len;
                                offsetPx = (mouseScreen.x - sp1.x) * nx + (mouseScreen.y - sp1.y) * ny;
                                if (Math.abs(offsetPx) < 8) offsetPx = (offsetPx >= 0 ? 18.0 : -18.0);
                            }
                            saveUndoState();
                            CADDimension newDim = new CADDimension(dimP1.x, dimP1.y, dimP2.x, dimP2.y, offsetPx);
                            dimensions.add(newDim);
                            clearSelection();
                            addToSelection(newDim);
                            dimP1 = null; dimP2 = null; dimStep = 0;
                            currentTool = ToolMode.SELECT;
                            selectToolButton(ToolMode.SELECT);
                            updateProperties();
                            updateStatusText();
                            repaint();
                            return;
                        }
                    }

                    // Drawing tools (Wall, Room Box, Floor Zone)
                    if (currentTool == ToolMode.DRAW_WALL || currentTool == ToolMode.ROOM_BOX
                            || currentTool == ToolMode.FLOOR_ZONE) {
                        isDrawing = true;
                        Point2D.Double snapPt = getOsnapPoint(mouseWorld.x, mouseWorld.y);
                        drawStart = (snapPt != null) ? snapPt : new Point2D.Double(snapCoord(mouseWorld.x), snapCoord(mouseWorld.y));
                        return;
                    }

                    // SELECT tool
                    if (currentTool == ToolMode.SELECT) {

                        // Check rotation handle on primary selected element
                        if (primarySelected instanceof CADElement) {
                            CADElement el = (CADElement) primarySelected;
                            Point handlePt = getRotationHandleScreen(el);
                            if (handlePt != null && handlePt.distance(mouseScreen) <= ROT_HANDLE_RADIUS + 4) {
                                isRotating = true; return;
                            }
                        }

                        // Check wall endpoint grips
                        if (selectedObjects.size() == 1 && primarySelected instanceof Wall) {
                            Wall w = (Wall) primarySelected;
                            Point sp1 = worldToScreen(w.x1, w.y1);
                            Point sp2 = worldToScreen(w.x2, w.y2);
                            if (sp1.distance(mouseScreen) <= 10) {
                                if (!isObjectLocked(w)) {
                                    saveUndoState(); gripWall = w; gripIsP1 = true; return;
                                }
                            }
                            if (sp2.distance(mouseScreen) <= 10) {
                                if (!isObjectLocked(w)) {
                                    saveUndoState(); gripWall = w; gripIsP1 = false; return;
                                }
                            }
                        }

                        // Multi-candidate hit testing for selection & cycling
                        List<Object> candidates = findAllHits(mouseScreen, mouseWorld);
                        Object hit = null;

                        if (!candidates.isEmpty()) {
                            if (lastClickScreen != null && mouseScreen.distance(lastClickScreen) <= 8) {
                                // Clicked repeatedly at the same position -> cycle through overlapping candidates!
                                cycleHitIndex = (cycleHitIndex + 1) % candidates.size();
                            } else {
                                // First click -> pick the topmost unlocked candidate if available
                                cycleHitIndex = 0;
                                for (int ci = 0; ci < candidates.size(); ci++) {
                                    if (!isObjectLocked(candidates.get(ci))) {
                                        cycleHitIndex = ci;
                                        break;
                                    }
                                }
                            }
                            lastClickScreen = mouseScreen;
                            hit = candidates.get(cycleHitIndex);
                        } else {
                            lastClickScreen = null;
                            cycleHitIndex = 0;
                        }

                        if (hit != null) {
                            if (e.isShiftDown()) {
                                toggleSelection(hit);
                            } else {
                                if (isSelected(hit)) {
                                    // Already selected
                                } else {
                                    clearSelection();
                                    addToSelection(hit);
                                }
                            }

                            boolean isLocked = isObjectLocked(hit);
                            if (isLocked) {
                                if (statusCursorLabel != null) {
                                    ProjectStage st = getObjectStage(hit);
                                    statusCursorLabel.setText(" 🔒 SELECTED: " + getObjectName(hit) + " (FROZEN in " + (st != null ? st.title : "Locked Stage") + ") · Movement prevented ");
                                }
                            } else {
                                // Prepare for dragging unlocked objects
                                saveUndoState();
                                isDragging = true;
                                lastDragWorld = new Point2D.Double(mouseWorld.x, mouseWorld.y);
                                if (candidates.size() > 1 && statusCursorLabel != null) {
                                    statusCursorLabel.setText(" Selected: " + getObjectName(hit) + " · (" + (cycleHitIndex + 1) + "/" + candidates.size() + ") Click again to cycle overlapping objects ");
                                }
                            }
                        } else {
                            // Clicked on empty space - start selection rectangle
                            if (!e.isShiftDown()) clearSelection();
                            isWindowSelecting = true;
                            selRectStart = new Point2D.Double(mouseWorld.x, mouseWorld.y);
                        }
                        updateProperties(); repaint();
                    }
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    mouseScreen = e.getPoint();
                    if (isSnipMode) {
                        if (isSnipDragging) {
                            snipCurrentScreen = e.getPoint();
                            repaint();
                        }
                        return;
                    }
                    if (isPanning) {
                        panX += (mouseScreen.x - panStartScreen.x);
                        panY += (mouseScreen.y - panStartScreen.y);
                        panStartScreen = mouseScreen; repaint(); return;
                    }
                    mouseWorld = screenToWorld(mouseScreen.x, mouseScreen.y);
                    updateStatusText();

                    // Rotation handle dragging
                    if (isRotating && primarySelected instanceof CADElement) {
                        CADElement el = (CADElement) primarySelected;
                        Point centerScreen = worldToScreen(el.x + el.w / 2.0, el.y + el.h / 2.0);
                        double dx = mouseScreen.x - centerScreen.x, dy = mouseScreen.y - centerScreen.y;
                        double angle = Math.toDegrees(Math.atan2(dy, dx)) + 90.0;
                        if (angle < 0) angle += 360; if (angle >= 360) angle -= 360;
                        if (e.isShiftDown() || orthoMode) angle = Math.round(angle / 15.0) * 15.0;
                        el.rotation = angle;
                        propRotSlider.setValue((int) Math.round(angle));
                        checkAllCollisions(); repaint(); return;
                    }

                    // Wall grip editing
                    if (gripWall != null) {
                        double gx = snapCoord(mouseWorld.x), gy = snapCoord(mouseWorld.y);
                        if (orthoMode) {
                            if (gripIsP1) {
                                if (Math.abs(gx - gripWall.x2) < Math.abs(gy - gripWall.y2)) gx = gripWall.x2;
                                else gy = gripWall.y2;
                            } else {
                                if (Math.abs(gx - gripWall.x1) < Math.abs(gy - gripWall.y1)) gx = gripWall.x1;
                                else gy = gripWall.y1;
                            }
                        }
                        if (gripIsP1) { gripWall.x1 = gx; gripWall.y1 = gy; }
                        else { gripWall.x2 = gx; gripWall.y2 = gy; }
                        updateProperties(); checkAllCollisions(); repaint(); return;
                    }

                    // Selection rectangle dragging
                    if (isWindowSelecting) { repaint(); return; }

                    // Multi-object dragging
                    if (isDragging && !selectedObjects.isEmpty()) {
                        // Prevent movement if any selected object is frozen/locked
                        boolean anyLocked = false;
                        for (Object obj : selectedObjects) {
                            if (isObjectLocked(obj)) { anyLocked = true; break; }
                        }
                        if (anyLocked) {
                            if (statusCursorLabel != null) {
                                statusCursorLabel.setText(" 🔒 OBJECT IS FROZEN · Accidental movement prevented. Unfreeze stage in Properties to edit. ");
                            }
                            return;
                        }

                        double dx = mouseWorld.x - lastDragWorld.x;
                        double dy = mouseWorld.y - lastDragWorld.y;

                        // Snap the delta if grid enabled
                        if (snapToGrid && snapStepFeet > 0 && !e.isAltDown()) {
                            double objStep = (snapStepFeet > 1.0) ? 0.5 : snapStepFeet;
                            double stepPx = objStep * FT_PX;
                            // For smoother feel, snap accumulated position rather than delta
                            if (primarySelected instanceof CADElement) {
                                CADElement ref = (CADElement) primarySelected;
                                double targetX = ref.x + dx, targetY = ref.y + dy;
                                double snappedX = Math.round(targetX / stepPx) * stepPx;
                                double snappedY = Math.round(targetY / stepPx) * stepPx;
                                dx = snappedX - ref.x; dy = snappedY - ref.y;
                            } else if (primarySelected instanceof Wall) {
                                Wall ref = (Wall) primarySelected;
                                double targetX = ref.x1 + dx, targetY = ref.y1 + dy;
                                double snappedX = Math.round(targetX / stepPx) * stepPx;
                                double snappedY = Math.round(targetY / stepPx) * stepPx;
                                dx = snappedX - ref.x1; dy = snappedY - ref.y1;
                            }
                        }

                        // Special: fluid sliding & detachment for door/window openings
                        if (selectedObjects.size() == 1 && primarySelected instanceof CADElement
                                && ((CADElement) primarySelected).isOpening()) {
                            CADElement el = (CADElement) primarySelected;
                            if (e.isAltDown()) {
                                // Free movement mode when holding Alt
                                el.x += dx; el.y += dy;
                                lastDragWorld = new Point2D.Double(mouseWorld.x, mouseWorld.y);
                                checkAllCollisions(); updateProperties(); repaint(); return;
                            }

                            // Calculate projected center using mouseWorld directly for smooth sliding
                            Point2D.Double curCenter = new Point2D.Double(el.x + el.w / 2.0, el.y + el.h / 2.0);
                            double snapDist = Math.max(32.0 / zoom, 2.5 * FT_PX);
                            Wall closestWall = findClosestWall(mouseWorld, snapDist);
                            if (closestWall == null) {
                                closestWall = findClosestWall(curCenter, snapDist);
                            }

                            // If mouse is within snap distance of a wall, slide effortlessly along it
                            if (closestWall != null) {
                                double wallThickPx = (closestWall.thicknessInches / 12.0) * FT_PX;
                                double distToWall = Line2D.ptSegDist(closestWall.x1, closestWall.y1, closestWall.x2, closestWall.y2, mouseWorld.x, mouseWorld.y);
                                double detachThreshold = Math.max((wallThickPx / 2.0) + (36.0 / zoom), 3.5 * FT_PX);

                                if (distToWall <= detachThreshold) {
                                    // Slide smoothly along the wall
                                    snapElementToWall(el, closestWall, mouseWorld);
                                    lastDragWorld = new Point2D.Double(mouseWorld.x, mouseWorld.y);
                                    checkAllCollisions(); updateProperties(); repaint(); return;
                                }
                            }
                            // Mouse pulled beyond threshold -> detach and move freely
                        }

                        for (Object obj : selectedObjects) {
                            if (obj instanceof CADElement) {
                                ((CADElement) obj).translate(dx, dy);
                            } else if (obj instanceof Wall) {
                                Wall w = (Wall) obj;
                                w.x1 += dx; w.y1 += dy; w.x2 += dx; w.y2 += dy;
                            } else if (obj instanceof CADDimension) {
                                CADDimension d = (CADDimension) obj;
                                d.x1 += dx; d.y1 += dy; d.x2 += dx; d.y2 += dy;
                            }
                        }
                        lastDragWorld = new Point2D.Double(mouseWorld.x, mouseWorld.y);
                        checkAllCollisions(); updateProperties();
                    }
                    repaint();
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    mouseScreen = e.getPoint();
                    if (isSnipMode) {
                        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
                        repaint();
                        return;
                    }
                    mouseWorld = screenToWorld(mouseScreen.x, mouseScreen.y);
                    updateStatusText();

                    // Update cursor for rotation handle
                    if (primarySelected instanceof CADElement) {
                        Point handlePt = getRotationHandleScreen((CADElement) primarySelected);
                        if (handlePt != null && handlePt.distance(mouseScreen) <= ROT_HANDLE_RADIUS + 4) {
                            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                            repaint();
                            return;
                        }
                    }

                    // Hover feedback for selectable walls and objects
                    if (currentTool == ToolMode.SELECT || currentTool == ToolMode.DRAW_WALL) {
                        Object hit = hitTest(mouseWorld.x, mouseWorld.y);
                        if (hit != hoveredObject) {
                            hoveredObject = hit;
                            repaint();
                        }
                        if (hit != null) {
                            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                        } else {
                            setCursor(currentTool == ToolMode.DRAW_WALL ? Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR) : Cursor.getDefaultCursor());
                        }
                    } else {
                        if (hoveredObject != null) { hoveredObject = null; repaint(); }
                        setCursor(Cursor.getDefaultCursor());
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (isSnipMode) {
                        if (isSnipDragging) {
                            isSnipDragging = false;
                            isSnipMode = false;
                            setCursor(Cursor.getDefaultCursor());
                            Rectangle r = getConstrainedSnipScreenRect();
                            if (r != null && r.width >= 20 && r.height >= 20) {
                                Point2D.Double w1 = screenToWorld(r.x, r.y);
                                Point2D.Double w2 = screenToWorld(r.x + r.width, r.y + r.height);
                                double minWX = Math.min(w1.x, w2.x), maxWX = Math.max(w1.x, w2.x);
                                double minWY = Math.min(w1.y, w2.y), maxWY = Math.max(w1.y, w2.y);
                                Rectangle2D.Double cropWorld = new Rectangle2D.Double(minWX, minWY, maxWX - minWX, maxWY - minWY);
                                if (snipCallback != null) {
                                    java.util.function.Consumer<Rectangle2D.Double> cb = snipCallback;
                                    snipCallback = null;
                                    cb.accept(cropWorld);
                                }
                            } else {
                                cancelSnipMode();
                            }
                        }
                        repaint();
                        return;
                    }

                    // Finalize drawing
                    if (isDrawing) {
                        isDrawing = false;
                        Point2D.Double p1 = drawStart;
                        Point2D.Double p2 = getConstrainedEndPoint(p1, mouseWorld);

                        if (currentTool == ToolMode.DRAW_WALL) {
                            if (p1.distance(p2) >= FT_PX * 0.5) {
                                saveUndoState();
                                Wall wall = new Wall(p1.x, p1.y, p2.x, p2.y, wallThicknessInches);
                                autoJoinWallEndpoints(wall, (wallThicknessInches / 12.0) * FT_PX * 1.5);
                                walls.add(wall);
                                clearSelection(); addToSelection(wall);
                                checkAllCollisions(); updateProperties();
                            } else {
                                // User clicked on an existing wall or object while in DRAW_WALL mode
                                Object clicked = hitTest(mouseWorld.x, mouseWorld.y);
                                if (clicked != null) {
                                    clearSelection();
                                    addToSelection(clicked);
                                    currentTool = ToolMode.SELECT;
                                    selectToolButton(ToolMode.SELECT);
                                    updateProperties();
                                    if (statusCursorLabel != null) {
                                        statusCursorLabel.setText(" Selected: " + getObjectName(clicked) + " · Switched to Select Tool ");
                                    }
                                }
                            }
                        } else if (currentTool == ToolMode.ROOM_BOX) {
                            saveUndoState();
                            double minX = Math.min(p1.x, p2.x), maxX = Math.max(p1.x, p2.x);
                            double minY = Math.min(p1.y, p2.y), maxY = Math.max(p1.y, p2.y);
                            walls.add(new Wall(minX, minY, maxX, minY, wallThicknessInches));
                            walls.add(new Wall(maxX, minY, maxX, maxY, wallThicknessInches));
                            walls.add(new Wall(maxX, maxY, minX, maxY, wallThicknessInches));
                            walls.add(new Wall(minX, maxY, minX, minY, wallThicknessInches));
                            autoJoinAllWalls(); checkAllCollisions();
                        } else if (currentTool == ToolMode.FLOOR_ZONE) {
                            if (p1.distance(p2) >= FT_PX * 1.0) {
                                saveUndoState();
                                double minX = Math.min(p1.x, p2.x), maxX = Math.max(p1.x, p2.x);
                                double minY = Math.min(p1.y, p2.y), maxY = Math.max(p1.y, p2.y);
                                String pattern = (activeHatchPattern != null) ? activeHatchPattern : "floor-tiles-2x2";
                                String label = "Hatch (" + pattern.replace("floor-", "") + ")";
                                CADElement floor = new CADElement(pattern, label, minX, minY, maxX - minX, maxY - minY);
                                elements.add(floor);
                                clearSelection(); addToSelection(floor);
                                updateProperties();
                            } else {
                                Object clicked = hitTest(mouseWorld.x, mouseWorld.y);
                                if (clicked != null) {
                                    clearSelection();
                                    addToSelection(clicked);
                                    currentTool = ToolMode.SELECT;
                                    selectToolButton(ToolMode.SELECT);
                                    updateProperties();
                                }
                            }
                        }
                    }

                    // Finalize selection rectangle
                    if (isWindowSelecting) {
                        isWindowSelecting = false;
                        if (selRectStart != null) {
                            double rectMinDist = 4.0 / zoom;
                            if (Math.abs(mouseWorld.x - selRectStart.x) > rectMinDist || Math.abs(mouseWorld.y - selRectStart.y) > rectMinDist) {
                                boolean isWindowMode = mouseWorld.x > selRectStart.x; // L→R = window
                                List<Object> found = hitTestRect(selRectStart.x, selRectStart.y, mouseWorld.x, mouseWorld.y, isWindowMode);
                                if (!e.isShiftDown()) clearSelection();
                                for (Object obj : found) addToSelection(obj);
                            }
                        }
                        selRectStart = null;
                        updateProperties();
                    }

                    // Finalize wall drag - auto join
                    if (isDragging) {
                        for (Object obj : selectedObjects) {
                            if (obj instanceof Wall) {
                                autoJoinWallEndpoints((Wall) obj, (wallThicknessInches / 12.0) * FT_PX * 1.5);
                            }
                        }
                    }

                    // Finalize grip edit
                    if (gripWall != null) {
                        autoJoinWallEndpoints(gripWall, (wallThicknessInches / 12.0) * FT_PX * 1.5);
                    }

                    isDragging = false; isRotating = false; isPanning = false; gripWall = null;
                    repaint();
                }

                @Override
                public void mouseWheelMoved(MouseWheelEvent e) {
                    double factor = (e.getWheelRotation() < 0) ? 1.15 : 0.85;
                    double newZoom = Math.min(4.0, Math.max(0.15, zoom * factor));
                    panX = mouseScreen.x - (mouseScreen.x - panX) * (newZoom / zoom);
                    panY = mouseScreen.y - (mouseScreen.y - panY) * (newZoom / zoom);
                    zoom = newZoom; repaint();
                }
            };
            addMouseListener(adapter); addMouseMotionListener(adapter); addMouseWheelListener(adapter);
        }

        private Wall findClosestWall(Point2D.Double pt, double maxDist) {
            Wall best = null; double minDist = maxDist;
            for (Wall w : walls) {
                double d = Line2D.ptSegDist(w.x1, w.y1, w.x2, w.y2, pt.x, pt.y);
                if (d < minDist) { minDist = d; best = w; }
            }
            return best;
        }

        private void snapElementToWall(CADElement el, Wall w, Point2D.Double pt) {
            double dx = w.x2 - w.x1, dy = w.y2 - w.y1;
            double len = Math.hypot(dx, dy);
            if (len == 0) return;
            double ux = dx / len, uy = dy / len;
            double t = ((pt.x - w.x1) * ux + (pt.y - w.y1) * uy);
            double halfOpening = el.w / 2.0;
            t = Math.max(halfOpening, Math.min(len - halfOpening, t));
            double snappedCX = w.x1 + ux * t, snappedCY = w.y1 + uy * t;
            el.rotation = Math.toDegrees(Math.atan2(dy, dx));
            el.x = snappedCX - el.w / 2.0; el.y = snappedCY - el.h / 2.0;
        }

        private Point getRotationHandleScreen(CADElement el) {
            Point center = worldToScreen(el.x + el.w / 2.0, el.y + el.h / 2.0);
            double sh = el.h * zoom;
            double rad = Math.toRadians(el.rotation);
            double dist = sh / 2.0 + ROT_STALK_LEN;
            return new Point((int) Math.round(center.x + dist * Math.sin(rad)),
                    (int) Math.round(center.y - dist * Math.cos(rad)));
        }

        private void updateStatusText() {
            String snapDisplay = (!snapToGrid || snapStepFeet <= 0.0) ? "FREE" :
                    (snapStepFeet == 0.5) ? "6\"" : (int) snapStepFeet + "'";
            statusCursorLabel.setText(" " + formatImperial(mouseWorld.x) + ", " + formatImperial(mouseWorld.y)
                    + "  |  SNAP: " + snapDisplay + "  |  ORTHO: " + (orthoMode ? "ON" : "OFF") + " ");
        }

        public Point2D.Double screenToWorld(int sx, int sy) {
            return new Point2D.Double((sx - panX) / zoom, (sy - panY) / zoom);
        }

        public Point worldToScreen(double wx, double wy) {
            return new Point((int) Math.round(wx * zoom + panX), (int) Math.round(wy * zoom + panY));
        }

        private Point2D.Double getConstrainedEndPoint(Point2D.Double start, Point2D.Double current) {
            double x = snapCoord(current.x), y = snapCoord(current.y);
            if (orthoMode) {
                if (Math.abs(x - start.x) > Math.abs(y - start.y)) y = start.y; else x = start.x;
            }
            return new Point2D.Double(x, y);
        }

        private Point2D.Double getOsnapPoint(double wx, double wy) {
            if (!osnapEnabled) return null;
            double thresh = 18.0 / zoom;
            for (Wall w : walls) {
                if (Point2D.distance(wx, wy, w.x1, w.y1) < thresh) return new Point2D.Double(w.x1, w.y1);
                if (Point2D.distance(wx, wy, w.x2, w.y2) < thresh) return new Point2D.Double(w.x2, w.y2);
                double mx = (w.x1 + w.x2) / 2.0, my = (w.y1 + w.y2) / 2.0;
                if (Point2D.distance(wx, wy, mx, my) < thresh) return new Point2D.Double(mx, my);
            }
            return null;
        }

        private List<Object> findAllHits(Point mouseScr, Point2D.Double mouseWorld) {
            List<Object> hits = new ArrayList<>();
            double wx = mouseWorld.x, wy = mouseWorld.y;

            // Tier 1: Dimensions & Measure lines (topmost interactive layer)
            for (int i = dimensions.size() - 1; i >= 0; i--) {
                CADDimension d = dimensions.get(i);
                Point p1 = worldToScreen(d.x1, d.y1);
                Point p2 = worldToScreen(d.x2, d.y2);
                double dx = p2.x - p1.x, dy = p2.y - p1.y;
                double len = Math.hypot(dx, dy);
                if (len < 6) continue;
                double offset = d.offset;
                double perpX = (-dy / len) * offset, perpY = (dx / len) * offset;
                double d1x = p1.x + perpX, d1y = p1.y + perpY;
                double d2x = p2.x + perpX, d2y = p2.y + perpY;

                // Hit test dimension text label center
                double midX = (d1x + d2x) / 2.0, midY = (d1y + d2y) / 2.0;
                Rectangle2D textHit = new Rectangle2D.Double(midX - 35, midY - 14, 70, 28);
                if (textHit.contains(mouseScr.x, mouseScr.y)
                        || Line2D.ptSegDist(d1x, d1y, d2x, d2y, mouseScr.x, mouseScr.y) <= 16.0
                        || Line2D.ptSegDist(p1.x, p1.y, d1x, d1y, mouseScr.x, mouseScr.y) <= 12.0
                        || Line2D.ptSegDist(p2.x, p2.y, d2x, d2y, mouseScr.x, mouseScr.y) <= 12.0) {
                    hits.add(d);
                }
            }

            // Tier 2: Openings (Doors, Windows, Cased openings embedded in walls)
            for (int i = elements.size() - 1; i >= 0; i--) {
                CADElement el = elements.get(i);
                if (!el.isOpening()) continue;
                double cx = el.x + el.w / 2.0, cy = el.y + el.h / 2.0;
                double rad = Math.toRadians(-el.rotation);
                double cos = Math.cos(rad), sin = Math.sin(rad);
                double dx = wx - cx, dy = wy - cy;
                double lx = dx * cos - dy * sin, ly = dx * sin + dy * cos;
                double hw = el.w / 2.0, hh = Math.max(el.h / 2.0, 1.2 * FT_PX);
                if (el.type.startsWith("door")) hh = Math.max(hh, el.w / 2.0 + 10.0);
                double effPad = Math.max(12.0, 16.0 / zoom);
                if (Math.abs(lx) <= hw + effPad && Math.abs(ly) <= hh + effPad) {
                    hits.add(el);
                }
            }

            // Tier 3: Foreground CAD blocks & Furniture (excluding floor elements and room area tags)
            for (int i = elements.size() - 1; i >= 0; i--) {
                CADElement el = elements.get(i);
                if (el.isOpening() || el.type.startsWith("floor-") || el.type.equals("tag")
                        || el.type.equals("room-box") || el.type.equals("floor-zone")) continue;
                if (el.points != null && el.points.size() >= 3) {
                    Path2D.Double poly = el.getPolygonPath();
                    if (poly != null && poly.contains(wx, wy)) { hits.add(el); continue; }
                }
                double cx = el.x + el.w / 2.0, cy = el.y + el.h / 2.0;
                double rad = Math.toRadians(-el.rotation);
                double cos = Math.cos(rad), sin = Math.sin(rad);
                double dx = wx - cx, dy = wy - cy;
                double lx = dx * cos - dy * sin, ly = dx * sin + dy * cos;
                double hw = el.w / 2.0, hh = el.h / 2.0;
                double effPad = Math.max(8.0, 12.0 / zoom);
                if (Math.abs(lx) <= hw + effPad && Math.abs(ly) <= hh + effPad) {
                    hits.add(el);
                }
            }

            // Tier 4: Room Area Tags - Hit test on the visible centered label badge only!
            for (int i = elements.size() - 1; i >= 0; i--) {
                CADElement el = elements.get(i);
                if (!el.type.equals("tag")) continue;
                Point tagCenter = worldToScreen(el.x + el.w / 2.0, el.y + el.h / 2.0);
                if (Math.abs(mouseScr.x - tagCenter.x) <= 75 && Math.abs(mouseScr.y - tagCenter.y) <= 30) {
                    hits.add(el);
                }
            }

            // Tier 5: WALLS Hit Test (screen space with generous 14px pickbox tolerance + wall thickness)
            for (int i = walls.size() - 1; i >= 0; i--) {
                Wall w = walls.get(i);
                Point sp1 = worldToScreen(w.x1, w.y1);
                Point sp2 = worldToScreen(w.x2, w.y2);
                double distScreen = Line2D.ptSegDist(sp1.x, sp1.y, sp2.x, sp2.y, mouseScr.x, mouseScr.y);
                double visualHalfThick = Math.max(4.0, (w.thicknessInches / 12.0) * FT_PX * zoom / 2.0);
                if (distScreen <= visualHalfThick + 14.0) {
                    hits.add(w);
                }
            }

            // Tier 6: Floor Finishes & Background Area Hatches (Lowest interactive priority)
            for (int i = elements.size() - 1; i >= 0; i--) {
                CADElement el = elements.get(i);
                if (!el.type.startsWith("floor-") && !el.type.equals("room-box") && !el.type.equals("floor-zone")) continue;
                if (el.points != null && el.points.size() >= 3) {
                    Path2D.Double poly = el.getPolygonPath();
                    if (poly != null && poly.contains(wx, wy)) { hits.add(el); continue; }
                }
                double cx = el.x + el.w / 2.0, cy = el.y + el.h / 2.0;
                double rad = Math.toRadians(-el.rotation);
                double cos = Math.cos(rad), sin = Math.sin(rad);
                double dx = wx - cx, dy = wy - cy;
                double lx = dx * cos - dy * sin, ly = dx * sin + dy * cos;
                double hw = el.w / 2.0, hh = el.h / 2.0;
                if (Math.abs(lx) <= hw && Math.abs(ly) <= hh) {
                    hits.add(el);
                }
            }

            return hits;
        }

        private Object hitTest(double wx, double wy) {
            Point mouseScr = worldToScreen(wx, wy);
            List<Object> hits = findAllHits(mouseScr, new Point2D.Double(wx, wy));
            if (hits.isEmpty()) return null;

            // Prioritize unlocked objects over locked objects
            for (Object obj : hits) {
                if (!isObjectLocked(obj)) return obj;
            }
            // If only locked objects exist, return the first locked object
            return hits.get(0);
        }

        // ====================  PAINTING  ====================

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            hudButtons.clear();

            drawGrid(g2);
            drawFloorElements(g2);
            drawWallsWithCutThrough(g2);
            drawElements(g2);
            drawHoverHighlight(g2);
            drawDimensions(g2);
            drawDynamicDimensions(g2);
            drawSelectionRectangle(g2);
            drawActiveTools(g2);
            if (currentStage == ProjectStage.STAGE_8_AGENT) {
                drawArchitectAgentOverlay(g2);
            }
            if (isSnipMode) {
                drawSnipOverlay(g2);
            }
        }

        private void drawHoverHighlight(Graphics2D g2) {
            if (hoveredObject == null || isSelected(hoveredObject) || isDrawing || isDragging || isRotating) return;

            if (hoveredObject instanceof Wall) {
                Wall hw = (Wall) hoveredObject;
                Point p1 = worldToScreen(hw.x1, hw.y1);
                Point p2 = worldToScreen(hw.x2, hw.y2);
                float thickS = (float) (((hw.thicknessInches / 12.0) * FT_PX) * zoom);
                g2.setColor(new Color(56, 189, 248, 120));
                g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(createWallSegmentPath(p1, p2, thickS + 4));
            } else if (hoveredObject instanceof CADElement) {
                CADElement el = (CADElement) hoveredObject;
                if (el.points != null && el.points.size() >= 3) {
                    Path2D.Double polyScreen = new Path2D.Double();
                    Point p0 = worldToScreen(el.points.get(0).x, el.points.get(0).y);
                    polyScreen.moveTo(p0.x, p0.y);
                    for (int i = 1; i < el.points.size(); i++) {
                        Point pi = worldToScreen(el.points.get(i).x, el.points.get(i).y);
                        polyScreen.lineTo(pi.x, pi.y);
                    }
                    polyScreen.closePath();
                    g2.setColor(new Color(56, 189, 248, 150));
                    g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.draw(polyScreen);
                } else {
                    Point center = worldToScreen(el.x + el.w / 2.0, el.y + el.h / 2.0);
                    int sw = (int) Math.round(el.w * zoom);
                    int sh = (int) Math.round(el.h * zoom);
                    AffineTransform old = g2.getTransform();
                    g2.translate(center.x, center.y);
                    g2.rotate(Math.toRadians(el.rotation));
                    g2.setColor(new Color(56, 189, 248, 140));
                    g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{4, 3}, 0));
                    g2.drawRect(-sw / 2 - 2, -sh / 2 - 2, sw + 4, sh + 4);
                    g2.setTransform(old);
                }
            } else if (hoveredObject instanceof CADDimension) {
                CADDimension d = (CADDimension) hoveredObject;
                Point p1 = worldToScreen(d.x1, d.y1);
                Point p2 = worldToScreen(d.x2, d.y2);
                double dx = p2.x - p1.x, dy = p2.y - p1.y;
                double len = Math.hypot(dx, dy);
                if (len >= 6) {
                    double offset = d.offset;
                    double perpX = (-dy / len) * offset, perpY = (dx / len) * offset;
                    double d1x = p1.x + perpX, d1y = p1.y + perpY;
                    double d2x = p2.x + perpX, d2y = p2.y + perpY;
                    g2.setColor(new Color(56, 189, 248, 160));
                    g2.setStroke(new BasicStroke(1.8f));
                    g2.draw(new Line2D.Double(d1x, d1y, d2x, d2y));
                }
            }
        }

        private void drawSnipOverlay(Graphics2D g2) {
            int w = getWidth(), h = getHeight();
            Rectangle r = getConstrainedSnipScreenRect();
            if (r != null && isSnipDragging) {
                Area outer = new Area(new Rectangle(0, 0, w, h));
                outer.subtract(new Area(r));
                g2.setColor(new Color(0, 0, 0, 160));
                g2.fill(outer);

                // Cyan snip frame
                g2.setColor(new Color(56, 189, 248));
                g2.setStroke(new BasicStroke(2.0f));
                g2.draw(r);

                // Rule-of-thirds composition guides
                g2.setColor(new Color(56, 189, 248, 80));
                g2.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{4, 4}, 0));
                g2.drawLine(r.x + r.width / 3, r.y, r.x + r.width / 3, r.y + r.height);
                g2.drawLine(r.x + 2 * r.width / 3, r.y, r.x + 2 * r.width / 3, r.y + r.height);
                g2.drawLine(r.x, r.y + r.height / 3, r.x + r.width, r.y + r.height / 3);
                g2.drawLine(r.x, r.y + 2 * r.height / 3, r.x + r.width, r.y + 2 * r.height / 3);

                // Dimension & Aspect badge
                String badge = (snipAspect > 1.0 ? "16:9 Widescreen" : "9:16 Vertical") + "  \u00b7  " + r.width + " \u00d7 " + r.height + " px";
                g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
                FontMetrics fmb = g2.getFontMetrics();
                int bw = fmb.stringWidth(badge) + 16;
                int bh = fmb.getHeight() + 8;
                int bx = Math.max(8, r.x);
                int by = (r.y - bh - 6 >= 6) ? (r.y - 6) : (r.y + bh + 14);
                g2.setColor(new Color(15, 23, 42, 230));
                g2.fillRoundRect(bx, by - bh, bw, bh, 6, 6);
                g2.setColor(new Color(56, 189, 248));
                g2.drawRoundRect(bx, by - bh, bw, bh, 6, 6);
                g2.drawString(badge, bx + 8, by - 6);
            } else {
                // Dim screen slightly with guide banner at top
                g2.setColor(new Color(0, 0, 0, 70));
                g2.fillRect(0, 0, w, h);

                String msg = "\u2702 SNIP MODE: Drag a box on the plan (Locked to " + (snipAspect > 1.0 ? "16:9" : "9:16") + ")  \u00b7  Right-click or ESC to cancel";
                g2.setFont(new Font("Segoe UI", Font.BOLD, 13));
                FontMetrics fm = g2.getFontMetrics();
                int mw = fm.stringWidth(msg) + 24;
                int mh = fm.getHeight() + 10;
                int mx = (w - mw) / 2;
                g2.setColor(new Color(15, 23, 42, 230));
                g2.fillRoundRect(mx, 16, mw, mh, 8, 8);
                g2.setColor(new Color(56, 189, 248));
                g2.drawRoundRect(mx, 16, mw, mh, 8, 8);
                g2.drawString(msg, mx + 12, 16 + fm.getAscent() + 4);
            }
        }

        private void drawGrid(Graphics2D g2) {
            if (!showGrid) return;
            int w = getWidth(), h = getHeight();
            double stepFeet = (snapStepFeet > 0.0) ? snapStepFeet : 5.0;
            double gridStep = (stepFeet * FT_PX) * zoom;
            if (gridStep < 6) return;
            g2.setColor(new Color(30, 41, 59, 100));
            g2.setStroke(new BasicStroke(0.5f));
            double startX = panX % gridStep, startY = panY % gridStep;
            for (double x = startX; x < w; x += gridStep) g2.draw(new Line2D.Double(x, 0, x, h));
            for (double y = startY; y < h; y += gridStep) g2.draw(new Line2D.Double(0, y, w, y));
            double majorStep = (5.0 * FT_PX) * zoom;
            if (majorStep >= 16) {
                g2.setColor(new Color(51, 65, 85, 140));
                g2.setStroke(new BasicStroke(0.8f));
                double mSX = panX % majorStep, mSY = panY % majorStep;
                for (double x = mSX; x < w; x += majorStep) g2.draw(new Line2D.Double(x, 0, x, h));
                for (double y = mSY; y < h; y += majorStep) g2.draw(new Line2D.Double(0, y, w, y));
            }
        }

        // --- Wall Rendering with Cut-Through & Corner Union ---

        private void drawWallsWithCutThrough(Graphics2D g2) {
            Area combinedWallsArea = new Area();
            List<Line2D.Double> thresholdLines = new ArrayList<>();

            for (Wall w : walls) {
                double len = w.getLength();
                if (len <= 0) continue;
                double ux = (w.x2 - w.x1) / len, uy = (w.y2 - w.y1) / len;
                double wallThickPx = (w.thicknessInches / 12.0) * FT_PX;
                float thickScreen = (float) (wallThickPx * zoom);

                List<double[]> cutouts = new ArrayList<>();
                for (CADElement el : elements) {
                    if (!el.isOpening()) continue;
                    Point2D.Double ec = new Point2D.Double(el.x + el.w / 2.0, el.y + el.h / 2.0);
                    double d = Line2D.ptSegDist(w.x1, w.y1, w.x2, w.y2, ec.x, ec.y);
                    if (d <= (wallThickPx / 2.0) + (1.2 * FT_PX)) {
                        double tMid = (ec.x - w.x1) * ux + (ec.y - w.y1) * uy;
                        double half = el.w / 2.0;
                        double tStart = Math.max(0, tMid - half), tEnd = Math.min(len, tMid + half);
                        if (tEnd > tStart) cutouts.add(new double[]{tStart, tEnd, el.type.startsWith("door") ? 1 : 2});
                    }
                }

                cutouts.sort(Comparator.comparingDouble(a -> a[0]));
                List<double[]> merged = new ArrayList<>();
                for (double[] c : cutouts) {
                    if (merged.isEmpty()) merged.add(c);
                    else {
                        double[] last = merged.get(merged.size() - 1);
                        if (c[0] <= last[1]) last[1] = Math.max(last[1], c[1]);
                        else merged.add(c);
                    }
                }

                List<double[]> solidSegs = new ArrayList<>();
                double curT = 0;
                for (double[] c : merged) {
                    if (c[0] > curT) solidSegs.add(new double[]{curT, c[0]});
                    curT = Math.max(curT, c[1]);
                }
                if (curT < len) solidSegs.add(new double[]{curT, len});

                for (double[] seg : solidSegs) {
                    if (seg[1] - seg[0] < 1.0) continue;
                    Point pA = worldToScreen(w.x1 + ux * seg[0], w.y1 + uy * seg[0]);
                    Point pB = worldToScreen(w.x1 + ux * seg[1], w.y1 + uy * seg[1]);
                    combinedWallsArea.add(new Area(createWallSegmentPath(pA, pB, thickScreen)));
                }

                for (double[] c : merged) {
                    Point pA = worldToScreen(w.x1 + ux * c[0], w.y1 + uy * c[0]);
                    Point pB = worldToScreen(w.x1 + ux * c[1], w.y1 + uy * c[1]);
                    thresholdLines.add(new Line2D.Double(pA.x, pA.y, pB.x, pB.y));
                }
            }

            // Monolithic wall fill & architectural hatching
            if ("DIAGONAL_45".equals(wallHatchStyle)) {
                g2.setColor(new Color(24, 33, 53));
                g2.fill(combinedWallsArea);
                Shape oldClip = g2.getClip();
                g2.clip(combinedWallsArea);
                g2.setColor(new Color(148, 163, 184, 170));
                g2.setStroke(new BasicStroke(1.0f));
                Rectangle b = combinedWallsArea.getBounds();
                int step = (int) Math.max(6, 8 * zoom);
                for (int x = b.x - b.height; x < b.x + b.width; x += step) {
                    g2.drawLine(x, b.y + b.height, x + b.height, b.y);
                }
                g2.setClip(oldClip);
            } else if ("HOLLOW".equals(wallHatchStyle)) {
                g2.setColor(new Color(15, 23, 42));
                g2.fill(combinedWallsArea);
            } else {
                g2.setColor(new Color(51, 65, 85));
                g2.fill(combinedWallsArea);
            }

            // Monolithic outer border
            g2.setColor(new Color(203, 213, 225));
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER));
            g2.draw(combinedWallsArea);

            // Threshold lines in openings
            g2.setColor(new Color(71, 85, 105));
            g2.setStroke(new BasicStroke(1.0f));
            for (Line2D.Double t : thresholdLines) g2.draw(t);

            // Selected wall highlight + grips
            for (Object sel : selectedObjects) {
                if (sel instanceof Wall) {
                    Wall selW = (Wall) sel;
                    Point p1 = worldToScreen(selW.x1, selW.y1);
                    Point p2 = worldToScreen(selW.x2, selW.y2);
                    float thickS = (float) (((selW.thicknessInches / 12.0) * FT_PX) * zoom);
                    boolean isLocked = isObjectLocked(selW);
                    if (isLocked) {
                        g2.setColor(new Color(251, 191, 36)); // Amber for frozen
                        g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{6, 4}, 0));
                    } else {
                        g2.setColor(new Color(56, 189, 248)); // Bright Cyan for active
                        g2.setStroke(new BasicStroke(2.0f));
                    }
                    g2.draw(createWallSegmentPath(p1, p2, thickS));
                    String label = formatImperial(selW.getLength()) + " (" + (int)selW.thicknessInches + "\")" + (isLocked ? " 🔒 FROZEN" : "");
                    drawCADDimension(g2, p1.x, p1.y, p2.x, p2.y, label, 24);
                    drawGrip(g2, p1.x, p1.y, isLocked);
                    drawGrip(g2, p2.x, p2.y, isLocked);
                    drawGrip(g2, (p1.x + p2.x) / 2, (p1.y + p2.y) / 2, isLocked);
                }
            }
        }

        private void drawGrip(Graphics2D g2, int x, int y) {
            drawGrip(g2, x, y, false);
        }

        private void drawGrip(Graphics2D g2, int x, int y, boolean isLocked) {
            g2.setColor(isLocked ? new Color(245, 158, 11) : new Color(56, 189, 248));
            g2.fillRect(x - 4, y - 4, 8, 8);
            g2.setColor(Color.WHITE);
            g2.drawRect(x - 4, y - 4, 8, 8);
        }

        private Path2D.Double createWallSegmentPath(Point p1, Point p2, float thick) {
            double dx = p2.x - p1.x, dy = p2.y - p1.y;
            double len = Math.hypot(dx, dy);
            if (len == 0) return new Path2D.Double();
            double px = (-dy / len) * (thick / 2.0), py = (dx / len) * (thick / 2.0);
            double ex = (dx / len) * (thick / 2.0), ey = (dy / len) * (thick / 2.0);
            Path2D.Double path = new Path2D.Double();
            path.moveTo(p1.x - ex + px, p1.y - ey + py);
            path.lineTo(p2.x + ex + px, p2.y + ey + py);
            path.lineTo(p2.x + ex - px, p2.y + ey - py);
            path.lineTo(p1.x - ex - px, p1.y - ey - py);
            path.closePath(); return path;
        }

        // --- Floor Hatch & Tile Finishes Rendering (Bottom Architectural Layer) ---

        private void drawFloorElements(Graphics2D g2) {
            for (CADElement el : elements) {
                if (!el.type.startsWith("floor-")) continue;

                if (el.points != null && el.points.size() >= 3) {
                    // AutoCAD Polyline Perimeter Hatch
                    Path2D.Double polyScreen = new Path2D.Double();
                    Point p0 = worldToScreen(el.points.get(0).x, el.points.get(0).y);
                    polyScreen.moveTo(p0.x, p0.y);
                    for (int i = 1; i < el.points.size(); i++) {
                        Point pi = worldToScreen(el.points.get(i).x, el.points.get(i).y);
                        polyScreen.lineTo(pi.x, pi.y);
                    }
                    polyScreen.closePath();

                    // Clip to polygon perimeter
                    Shape oldClip = g2.getClip();
                    g2.clip(polyScreen);

                    Point origin = worldToScreen(el.x, el.y);
                    int sw = (int) Math.round(el.w * zoom);
                    int sh = (int) Math.round(el.h * zoom);

                    AffineTransform oldTx = g2.getTransform();
                    g2.translate(origin.x, origin.y);
                    renderFloorPattern(g2, el.type, sw, sh);
                    g2.setTransform(oldTx);
                    g2.setClip(oldClip);

                    // Polyline perimeter boundary
                    g2.setColor(new Color(148, 163, 184, 200));
                    g2.setStroke(new BasicStroke(1.2f));
                    g2.draw(polyScreen);

                    // Selection highlight along polyline boundary
                    if (isSelected(el)) {
                        g2.setColor(new Color(56, 189, 248));
                        g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{6, 4}, 0));
                        g2.draw(polyScreen);
                        for (Point2D.Double pt : el.points) {
                            Point sp = worldToScreen(pt.x, pt.y);
                            drawGrip(g2, sp.x, sp.y);
                        }
                    }
                } else {
                    // Rectangular floor finish
                    int sw = (int) Math.round(el.w * zoom);
                    int sh = (int) Math.round(el.h * zoom);

                    AffineTransform old = g2.getTransform();
                    Point center = worldToScreen(el.x + el.w / 2.0, el.y + el.h / 2.0);
                    g2.translate(center.x, center.y);
                    g2.rotate(Math.toRadians(el.rotation));
                    if (el.flipH) g2.scale(-1, 1);
                    if (el.flipV) g2.scale(1, -1);
                    g2.translate(-sw / 2, -sh / 2);

                    renderFloorPattern(g2, el.type, sw, sh);

                    // Selection highlight
                    if (isSelected(el)) {
                        g2.setColor(new Color(56, 189, 248));
                        g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{5, 4}, 0));
                        g2.drawRect(-2, -2, sw + 4, sh + 4);
                        drawGrip(g2, -2, -2);
                        drawGrip(g2, sw + 2, -2);
                        drawGrip(g2, -2, sh + 2);
                        drawGrip(g2, sw + 2, sh + 2);
                        drawGrip(g2, sw / 2, -2);
                        drawGrip(g2, sw / 2, sh + 2);
                        drawGrip(g2, -2, sh / 2);
                        drawGrip(g2, sw + 2, sh / 2);
                    }

                    g2.setTransform(old);
                }
            }
        }

        private void renderFloorPattern(Graphics2D g2, String type, int sw, int sh) {
            if (sw <= 0 || sh <= 0) return;
            // Floor tint
            if (type.equals("floor-wood-plank")) {
                g2.setColor(new Color(180, 83, 9, 25)); // warm amber wood tint
            } else if (type.equals("floor-mosaic")) {
                g2.setColor(new Color(14, 165, 233, 22)); // fresh aqua bathroom tint
            } else if (type.equals("floor-pavers")) {
                g2.setColor(new Color(120, 53, 15, 20)); // terracotta stone tint
            } else {
                g2.setColor(new Color(241, 245, 249, 18)); // ceramic white tint
            }
            g2.fillRect(0, 0, sw, sh);

            // Perimeter boundary of floor finish
            g2.setColor(new Color(100, 116, 139, 140));
            g2.setStroke(new BasicStroke(0.8f));
            g2.drawRect(0, 0, sw, sh);

            // Delicate architectural hairline for tile joints
            g2.setColor(new Color(148, 163, 184, 170));
            g2.setStroke(new BasicStroke(0.5f));

            if (type.equals("floor-tiles-2x2")) {
                double step = 2.0 * FT_PX * zoom;
                if (step >= 4) {
                    for (double x = step; x < sw; x += step) g2.draw(new Line2D.Double(x, 0, x, sh));
                    for (double y = step; y < sh; y += step) g2.draw(new Line2D.Double(0, y, sw, y));
                }
            } else if (type.equals("floor-tiles-1x1")) {
                double step = 1.0 * FT_PX * zoom;
                if (step >= 4) {
                    for (double x = step; x < sw; x += step) g2.draw(new Line2D.Double(x, 0, x, sh));
                    for (double y = step; y < sh; y += step) g2.draw(new Line2D.Double(0, y, sw, y));
                }
            } else if (type.equals("floor-tiles-2x4")) {
                double stepX = 2.0 * FT_PX * zoom;
                double stepY = 4.0 * FT_PX * zoom;
                if (stepX >= 4) {
                    for (double x = stepX; x < sw; x += stepX) g2.draw(new Line2D.Double(x, 0, x, sh));
                    for (double y = stepY; y < sh; y += stepY) g2.draw(new Line2D.Double(0, y, sw, y));
                }
            } else if (type.equals("floor-tiles-diagonal")) {
                double step = 2.0 * FT_PX * zoom;
                if (step >= 4) {
                    Shape oldClip = g2.getClip();
                    g2.clipRect(0, 0, sw, sh);
                    double diagMax = sw + sh;
                    for (double d = -sh; d < diagMax; d += step) {
                        g2.draw(new Line2D.Double(d, 0, d + sh, sh));
                        g2.draw(new Line2D.Double(d, sh, d + sh, 0));
                    }
                    g2.setClip(oldClip);
                }
            } else if (type.equals("floor-wood-plank")) {
                double plankW = 0.5 * FT_PX * zoom;
                double plankL = 3.5 * FT_PX * zoom;
                if (plankW >= 3) {
                    int row = 0;
                    for (double y = 0; y < sh; y += plankW) {
                        g2.draw(new Line2D.Double(0, y, sw, y));
                        double offset = (row % 3) * (plankL / 3.0);
                        for (double x = offset; x < sw; x += plankL) {
                            g2.draw(new Line2D.Double(x, y, x, Math.min(sh, y + plankW)));
                        }
                        row++;
                    }
                }
            } else if (type.equals("floor-mosaic")) {
                double step = 0.333 * FT_PX * zoom;
                if (step >= 3) {
                    for (double x = step; x < sw; x += step) g2.draw(new Line2D.Double(x, 0, x, sh));
                    for (double y = step; y < sh; y += step) g2.draw(new Line2D.Double(0, y, sw, y));
                }
            } else if (type.equals("floor-concrete")) {
                Random r = new Random(42);
                int dots = (int) ((sw * sh) / Math.max(1.0, 150.0 / (zoom * zoom + 0.1)));
                dots = Math.min(dots, 4000);
                for (int i = 0; i < dots; i++) {
                    int px = r.nextInt(Math.max(1, sw));
                    int py = r.nextInt(Math.max(1, sh));
                    g2.fillRect(px, py, 1, 1);
                }
            } else if (type.equals("floor-pavers")) {
                double paverH = 0.333 * FT_PX * zoom;
                double paverW = 0.666 * FT_PX * zoom;
                if (paverH >= 3) {
                    int row = 0;
                    for (double y = 0; y < sh; y += paverH) {
                        g2.draw(new Line2D.Double(0, y, sw, y));
                        double off = (row % 2 == 0) ? 0 : paverW / 2.0;
                        for (double x = off; x < sw; x += paverW) {
                            g2.draw(new Line2D.Double(x, y, x, Math.min(sh, y + paverH)));
                        }
                        row++;
                    }
                }
            }
        }

        // --- Element Rendering ---

        private void drawElements(Graphics2D g2) {
            for (CADElement el : elements) {
                if (el.type.startsWith("floor-")) continue; // Rendered on floor finishes layer
                int sw = (int) Math.round(el.w * zoom);
                int sh = (int) Math.round(el.h * zoom);

                AffineTransform old = g2.getTransform();
                Point center = worldToScreen(el.x + el.w / 2.0, el.y + el.h / 2.0);
                g2.translate(center.x, center.y);
                g2.rotate(Math.toRadians(el.rotation));
                if (el.flipH) g2.scale(-1, 1);
                if (el.flipV) g2.scale(1, -1);
                g2.translate(-sw / 2, -sh / 2);

                renderCADElement(g2, el, sw, sh);

                // Collision badge
                if (el.hasCollision) {
                    g2.setColor(new Color(244, 63, 94, 60));
                    g2.fillRect(-2, -2, sw + 4, sh + 4);
                    g2.setColor(new Color(244, 63, 94));
                    g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{4, 3}, 0));
                    g2.drawRect(-3, -3, sw + 6, sh + 6);
                    g2.setFont(new Font("Segoe UI", Font.BOLD, 9));
                    g2.drawString("COLLISION", 2, -5);
                }

                // Selection highlight
                if (isSelected(el)) {
                    boolean isLocked = isObjectLocked(el);
                    g2.setColor(isLocked ? new Color(251, 191, 36) : new Color(56, 189, 248));
                    g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{5, 4}, 0));
                    g2.drawRect(-4, -4, sw + 8, sh + 8);
                    drawGrip(g2, -4, -4, isLocked);
                    drawGrip(g2, sw + 4, -4, isLocked);
                    drawGrip(g2, -4, sh + 4, isLocked);
                    drawGrip(g2, sw + 4, sh + 4, isLocked);
                    drawGrip(g2, sw / 2, -4, isLocked);
                    drawGrip(g2, sw / 2, sh + 4, isLocked);
                    drawGrip(g2, -4, sh / 2, isLocked);
                    drawGrip(g2, sw + 4, sh / 2, isLocked);
                }

                g2.setTransform(old);
            }
        }


        // =====================================================================
        // ==================  HIGH-QUALITY CAD ELEMENT RENDERING ==============
        // =====================================================================

        private void renderCADElement(Graphics2D g2, CADElement el, int sw, int sh) {
            String type = el.type;

            // ─── CASED / FRAMELess WALL OPENING ─────────────────────────
            if (type.startsWith("opening")) {
                // Clear passage through wall without door leaf, frame, or swing arc
                // Render delicate architectural jamb return stop lines (casing return)
                g2.setColor(new Color(203, 213, 225));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawLine(0, 0, 0, sh);       // Left wall edge
                g2.drawLine(sw, 0, sw, sh);     // Right wall edge

                // Optional subtle architectural lintel indicator overhead (dashed)
                g2.setColor(new Color(56, 189, 248, 120));
                g2.setStroke(new BasicStroke(0.85f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{4, 3}, 0));
                g2.drawLine(0, sh / 2, sw, sh / 2);

            // ─── SINGLE DOOR ────────────────────────────────────────────
            } else if (type.equals("door")) {
                int jw = Math.max(3, (int)(2.5 * zoom));   // jamb width
                int leafLen = sw - jw * 2;                  // swing radius
                int leafThick = Math.max(3, (int)(2.0 * zoom));

                // Jamb posts (filled dark rectangles with white border)
                g2.setColor(new Color(148, 163, 184));
                g2.fillRect(0, 0, jw, sh);
                g2.fillRect(sw - jw, 0, jw, sh);
                g2.setColor(new Color(203, 213, 225));
                g2.drawRect(0, 0, jw, sh);
                g2.drawRect(sw - jw, 0, jw, sh);

                // Door leaf (thin rectangle at 90° open position)
                g2.setColor(new Color(56, 189, 248));
                g2.fillRect(jw, sh - leafLen, leafThick, leafLen);
                g2.setColor(new Color(203, 213, 225));
                g2.drawRect(jw, sh - leafLen, leafThick, leafLen);

                // Swing arc (dashed quarter-circle)
                g2.setColor(new Color(56, 189, 248, 160));
                g2.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{4, 3}, 0));
                g2.drawArc(jw - leafLen, sh - leafLen, leafLen * 2, leafLen * 2, 0, 90);

                // Door handle knob
                g2.setColor(new Color(251, 191, 36));
                int knobY = sh - leafLen + (int)(leafLen * 0.2);
                g2.fillOval(jw + leafThick - 1, knobY, 5, 5);

                // Threshold line
                g2.setColor(new Color(100, 116, 139));
                g2.setStroke(new BasicStroke(0.8f));
                g2.drawLine(jw, sh, sw - jw, sh);

            // ─── DOUBLE DOOR ────────────────────────────────────────────
            } else if (type.equals("door-double")) {
                int jw = Math.max(3, (int)(2.5 * zoom));
                int halfLeaf = (sw - jw * 2) / 2;
                int leafThick = Math.max(3, (int)(2.0 * zoom));

                // Jamb posts
                g2.setColor(new Color(148, 163, 184));
                g2.fillRect(0, 0, jw, sh);
                g2.fillRect(sw - jw, 0, jw, sh);
                g2.setColor(new Color(203, 213, 225));
                g2.drawRect(0, 0, jw, sh);
                g2.drawRect(sw - jw, 0, jw, sh);

                // Left leaf
                g2.setColor(new Color(56, 189, 248));
                g2.fillRect(jw, sh - halfLeaf, leafThick, halfLeaf);
                g2.setColor(new Color(203, 213, 225));
                g2.drawRect(jw, sh - halfLeaf, leafThick, halfLeaf);

                // Right leaf
                g2.setColor(new Color(56, 189, 248));
                g2.fillRect(sw - jw - leafThick, sh - halfLeaf, leafThick, halfLeaf);
                g2.setColor(new Color(203, 213, 225));
                g2.drawRect(sw - jw - leafThick, sh - halfLeaf, leafThick, halfLeaf);

                // Swing arcs (dashed)
                g2.setColor(new Color(56, 189, 248, 160));
                g2.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{4, 3}, 0));
                g2.drawArc(jw - halfLeaf, sh - halfLeaf, halfLeaf * 2, halfLeaf * 2, 0, 90);
                g2.drawArc(sw - jw - halfLeaf, sh - halfLeaf, halfLeaf * 2, halfLeaf * 2, 90, 90);

                // Door handles
                g2.setColor(new Color(251, 191, 36));
                g2.fillOval(jw + leafThick, sh - halfLeaf + (int)(halfLeaf * 0.2), 4, 4);
                g2.fillOval(sw - jw - leafThick - 4, sh - halfLeaf + (int)(halfLeaf * 0.2), 4, 4);

            // ─── SLIDING DOOR / SLIDING WINDOW ──────────────────────────
            } else if (type.equals("door-sliding") || type.equals("window-sliding")) {
                // Frame
                g2.setColor(new Color(30, 41, 59));
                g2.fillRect(0, 0, sw, sh);
                g2.setColor(new Color(148, 163, 184));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRect(0, 0, sw, sh);

                int mid = sw / 2;
                int trackH = sh / 2;

                // Two sliding panels
                g2.setColor(new Color(56, 189, 248));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRect(2, 2, mid + 8, trackH - 2);
                g2.drawRect(mid - 8, trackH, sw - mid + 6, trackH - 2);

                // Center divider
                g2.setColor(new Color(100, 116, 139));
                g2.setStroke(new BasicStroke(0.8f));
                g2.drawLine(mid, 0, mid, sh);

                // Direction arrow
                g2.setColor(new Color(251, 191, 36));
                g2.setStroke(new BasicStroke(1.5f));
                int ay = trackH / 2;
                g2.drawLine(mid - 14, ay, mid + 6, ay);
                g2.drawLine(mid + 6, ay, mid + 1, ay - 3);
                g2.drawLine(mid + 6, ay, mid + 1, ay + 3);

            // ─── POCKET DOOR ────────────────────────────────────────────
            } else if (type.equals("door-pocket")) {
                // Wall cavity (dashed outline)
                g2.setColor(new Color(71, 85, 105));
                g2.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{3, 3}, 0));
                g2.drawRect(0, 0, sw / 2, sh);

                // Visible door panel
                g2.setColor(new Color(56, 189, 248));
                g2.setStroke(new BasicStroke(2.0f));
                g2.drawLine(sw / 3, sh / 2, sw, sh / 2);

                // Jamb at opening edge
                g2.setColor(new Color(148, 163, 184));
                g2.fillRect(sw - 3, 0, 3, sh);

                // Handle
                g2.setColor(new Color(251, 191, 36));
                g2.fillOval(sw / 3 + 6, sh / 2 - 2, 4, 4);

            // ─── BIFOLD DOOR ────────────────────────────────────────────
            } else if (type.equals("door-bifold")) {
                int half = sw / 2;
                g2.setColor(new Color(56, 189, 248));
                g2.setStroke(new BasicStroke(2.0f));
                // Left pair of folding panels
                g2.drawLine(0, sh, half / 2, 0);
                g2.drawLine(half / 2, 0, half, sh);
                // Right pair
                g2.drawLine(half, sh, half + half / 2, 0);
                g2.drawLine(half + half / 2, 0, sw, sh);

                // Pivot points
                g2.setColor(new Color(251, 191, 36));
                g2.fillOval(-2, sh - 3, 5, 5);
                g2.fillOval(sw - 3, sh - 3, 5, 5);
                g2.fillOval(half - 2, sh - 3, 5, 5);

            // ─── CASEMENT / BAY / VENT WINDOW ───────────────────────────
            } else if (type.equals("window") || type.equals("window-bay") || type.equals("window-vent")) {
                // Outer wall sill projection
                int sill = Math.max(3, (int)(2.5 * zoom));
                g2.setColor(new Color(100, 116, 139));
                g2.fillRect(-sill, -sill, sw + sill * 2, sill);
                g2.setColor(new Color(203, 213, 225));
                g2.drawRect(-sill, -sill, sw + sill * 2, sill);

                // Glass pane background
                g2.setColor(new Color(15, 23, 42));
                g2.fillRect(0, 0, sw, sh);

                // Outer frame
                g2.setColor(new Color(203, 213, 225));
                g2.setStroke(new BasicStroke(1.4f));
                g2.drawRect(0, 0, sw, sh);

                // Inner frame (offset by 2px to show frame depth)
                g2.setColor(new Color(148, 163, 184));
                g2.setStroke(new BasicStroke(0.8f));
                g2.drawRect(3, 3, sw - 6, sh - 6);

                // Glass double-line (architectural convention: two parallel lines)
                g2.setColor(new Color(56, 189, 248, 180));
                g2.setStroke(new BasicStroke(0.8f));
                g2.drawLine(0, sh / 3, sw, sh / 3);
                g2.drawLine(0, sh / 3 + 2, sw, sh / 3 + 2);
                g2.drawLine(0, 2 * sh / 3, sw, 2 * sh / 3);
                g2.drawLine(0, 2 * sh / 3 + 2, sw, 2 * sh / 3 + 2);

                // Mullion vertical divider
                if (sw > 40) {
                    g2.setColor(new Color(203, 213, 225));
                    g2.setStroke(new BasicStroke(1.0f));
                    g2.drawLine(sw / 2, 0, sw / 2, sh);
                }

                // Ventilator louvers
                if (type.equals("window-vent")) {
                    g2.setColor(new Color(148, 163, 184, 160));
                    g2.setStroke(new BasicStroke(0.7f));
                    for (int x = 6; x < sw; x += 8) g2.drawLine(x, 3, x - 3, sh - 3);
                }

            // ─── KING / SINGLE BED ──────────────────────────────────────
            } else if (type.equals("bed-king") || type.equals("bed-single")) {
                // Mattress outline
                g2.setColor(new Color(30, 41, 59));
                g2.fillRoundRect(0, 0, sw, sh, 8, 8);
                g2.setColor(new Color(203, 213, 225));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(0, 0, sw, sh, 8, 8);

                // Headboard (thick filled bar at top)
                int hb = Math.max(6, sh / 7);
                g2.setColor(new Color(71, 85, 105));
                g2.fillRoundRect(0, 0, sw, hb, 4, 4);
                g2.setColor(new Color(203, 213, 225));
                g2.drawRoundRect(0, 0, sw, hb, 4, 4);

                // Headboard decorative lines
                g2.setColor(new Color(100, 116, 139));
                g2.drawLine(4, hb / 2, sw - 4, hb / 2);

                // Pillows
                int pillowH = Math.max(10, (int)(sh * 0.18));
                int pillowY = hb + 6;
                g2.setColor(new Color(226, 232, 240));
                if (type.equals("bed-king")) {
                    int pw = (sw - 18) / 2;
                    g2.fillRoundRect(6, pillowY, pw, pillowH, 8, 8);
                    g2.fillRoundRect(sw - pw - 6, pillowY, pw, pillowH, 8, 8);
                    g2.setColor(new Color(148, 163, 184));
                    g2.drawRoundRect(6, pillowY, pw, pillowH, 8, 8);
                    g2.drawRoundRect(sw - pw - 6, pillowY, pw, pillowH, 8, 8);
                } else {
                    int pw = sw - 16;
                    g2.fillRoundRect(8, pillowY, pw, pillowH, 8, 8);
                    g2.setColor(new Color(148, 163, 184));
                    g2.drawRoundRect(8, pillowY, pw, pillowH, 8, 8);
                }

                // Blanket fold line
                int foldY = pillowY + pillowH + Math.max(8, sh / 8);
                g2.setColor(new Color(100, 116, 139));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawLine(6, foldY, sw - 6, foldY);

                // Mattress stitching lines
                g2.setColor(new Color(51, 65, 85));
                g2.setStroke(new BasicStroke(0.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{2, 4}, 0));
                g2.drawLine(6, foldY + (sh - foldY) / 2, sw - 6, foldY + (sh - foldY) / 2);

            // ─── SOFA (3-SEAT / ARMCHAIR) ───────────────────────────────
            } else if (type.equals("sofa-3seat") || type.equals("sofa-armchair")) {
                // Body outline
                g2.setColor(new Color(30, 41, 59));
                g2.fillRoundRect(0, 0, sw, sh, 10, 10);

                int armW = Math.max(6, sw / 9);
                int backH = Math.max(6, sh / 4);

                // Back rest
                g2.setColor(new Color(51, 65, 85));
                g2.fillRoundRect(0, 0, sw, backH, 8, 8);
                g2.setColor(new Color(148, 163, 184));
                g2.drawRoundRect(0, 0, sw, backH, 8, 8);

                // Arm rests
                g2.setColor(new Color(51, 65, 85));
                g2.fillRoundRect(0, 0, armW, sh, 6, 6);
                g2.fillRoundRect(sw - armW, 0, armW, sh, 6, 6);
                g2.setColor(new Color(148, 163, 184));
                g2.drawRoundRect(0, 0, armW, sh, 6, 6);
                g2.drawRoundRect(sw - armW, 0, armW, sh, 6, 6);

                // Seat cushions with padding lines
                int seatW = sw - armW * 2;
                int seatH = sh - backH;
                int cushions = type.equals("sofa-3seat") ? 3 : 1;
                int cWidth = seatW / cushions;

                for (int c = 0; c < cushions; c++) {
                    int cx = armW + c * cWidth + 3;
                    int cy = backH + 3;
                    int cw = cWidth - 6;
                    int ch = seatH - 6;
                    g2.setColor(new Color(30, 41, 59));
                    g2.fillRoundRect(cx, cy, cw, ch, 6, 6);
                    g2.setColor(new Color(56, 189, 248, 140));
                    g2.setStroke(new BasicStroke(1.0f));
                    g2.drawRoundRect(cx, cy, cw, ch, 6, 6);
                    // Padding stitch
                    g2.setColor(new Color(56, 189, 248, 80));
                    g2.setStroke(new BasicStroke(0.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{2, 3}, 0));
                    g2.drawRoundRect(cx + 3, cy + 3, cw - 6, ch - 6, 4, 4);
                }

                // Outline
                g2.setColor(new Color(203, 213, 225));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(0, 0, sw, sh, 10, 10);

            // ─── DINING SET ─────────────────────────────────────────────
            } else if (type.equals("dining-set")) {
                int chairD = Math.max(6, sh / 6);
                int chairW = Math.max(10, sw / 4);
                int tblY = chairD + 4;
                int tblH = sh - (chairD + 4) * 2;

                // Table
                g2.setColor(new Color(30, 41, 59));
                g2.fillRoundRect(6, tblY, sw - 12, tblH, 6, 6);
                g2.setColor(new Color(203, 213, 225));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(6, tblY, sw - 12, tblH, 6, 6);

                // Table centerline
                g2.setColor(new Color(56, 189, 248, 80));
                g2.setStroke(new BasicStroke(0.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{3, 3}, 0));
                g2.drawLine(sw / 2, tblY + 4, sw / 2, tblY + tblH - 4);

                // Chairs (top and bottom rows)
                g2.setColor(new Color(71, 85, 105));
                g2.setStroke(new BasicStroke(1.0f));
                int spacing = (sw - 16) / 3;
                for (int i = 0; i < 3; i++) {
                    int cx = 8 + i * spacing;
                    // Top chairs
                    g2.setColor(new Color(30, 41, 59));
                    g2.fillRoundRect(cx, 0, chairW, chairD, 4, 4);
                    g2.setColor(new Color(148, 163, 184));
                    g2.drawRoundRect(cx, 0, chairW, chairD, 4, 4);
                    // Bottom chairs
                    g2.setColor(new Color(30, 41, 59));
                    g2.fillRoundRect(cx, sh - chairD, chairW, chairD, 4, 4);
                    g2.setColor(new Color(148, 163, 184));
                    g2.drawRoundRect(cx, sh - chairD, chairW, chairD, 4, 4);
                }

            // ─── DESK ───────────────────────────────────────────────────
            } else if (type.equals("desk")) {
                // Desktop surface
                g2.setColor(new Color(30, 41, 59));
                g2.fillRoundRect(0, 0, sw, sh, 4, 4);
                g2.setColor(new Color(203, 213, 225));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(0, 0, sw, sh, 4, 4);

                // Monitor
                int mw = Math.max(14, sw / 3);
                int mh = Math.max(4, sh / 6);
                g2.setColor(new Color(56, 189, 248));
                g2.drawRect((sw - mw) / 2, 4, mw, mh);
                // Monitor stand
                g2.drawLine(sw / 2, 4 + mh, sw / 2, 4 + mh + 4);
                g2.drawLine(sw / 2 - 4, 4 + mh + 4, sw / 2 + 4, 4 + mh + 4);

                // Keyboard
                int kw = mw - 6, kh = Math.max(3, mh / 2);
                g2.setColor(new Color(100, 116, 139));
                g2.drawRoundRect((sw - kw) / 2, 4 + mh + 8, kw, kh, 2, 2);

                // Chair circle
                int chairR = Math.min(14, sh / 4);
                g2.setColor(new Color(71, 85, 105));
                g2.drawOval(sw / 2 - chairR, sh + 4, chairR * 2, chairR * 2);
                g2.drawLine(sw / 2, sh + 4, sw / 2, sh + 4 + chairR * 2);
                g2.drawLine(sw / 2 - chairR, sh + 4 + chairR, sw / 2 + chairR, sh + 4 + chairR);

            // ─── WARDROBE ───────────────────────────────────────────────
            } else if (type.equals("wardrobe")) {
                g2.setColor(new Color(30, 41, 59));
                g2.fillRect(0, 0, sw, sh);
                g2.setColor(new Color(203, 213, 225));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRect(0, 0, sw, sh);

                // Center divider
                g2.drawLine(sw / 2, 0, sw / 2, sh);

                // Diagonal convention lines (shows it's a closet)
                g2.setColor(new Color(71, 85, 105));
                g2.setStroke(new BasicStroke(0.8f));
                g2.drawLine(0, 0, sw / 2, sh);
                g2.drawLine(sw / 2, sh, sw, 0);

                // Handles
                g2.setColor(new Color(56, 189, 248));
                g2.fillRect(sw / 2 - 6, sh / 2 - 1, 4, 2);
                g2.fillRect(sw / 2 + 2, sh / 2 - 1, 4, 2);

            // ─── TV UNIT ────────────────────────────────────────────────
            } else if (type.equals("tv-unit")) {
                // Console body
                g2.setColor(new Color(30, 41, 59));
                g2.fillRoundRect(0, 0, sw, sh, 4, 4);
                g2.setColor(new Color(203, 213, 225));
                g2.setStroke(new BasicStroke(1.0f));
                g2.drawRoundRect(0, 0, sw, sh, 4, 4);

                // TV screen
                int tvW = Math.max(20, (int)(sw * 0.6));
                int tvH = Math.max(4, sh / 3);
                g2.setColor(new Color(56, 189, 248, 120));
                g2.fillRect((sw - tvW) / 2, 2, tvW, tvH);
                g2.setColor(new Color(56, 189, 248));
                g2.drawRect((sw - tvW) / 2, 2, tvW, tvH);

            // ─── BOOKSHELF ──────────────────────────────────────────────
            } else if (type.equals("bookshelf")) {
                g2.setColor(new Color(30, 41, 59));
                g2.fillRect(0, 0, sw, sh);
                g2.setColor(new Color(203, 213, 225));
                g2.setStroke(new BasicStroke(1.0f));
                g2.drawRect(0, 0, sw, sh);

                // Shelf dividers
                int shelves = 4;
                int spacing = sw / shelves;
                g2.setColor(new Color(100, 116, 139));
                for (int i = 1; i < shelves; i++) g2.drawLine(i * spacing, 0, i * spacing, sh);

            // ─── TOILET WC ──────────────────────────────────────────────
            } else if (type.equals("toilet")) {
                int tankH = Math.max(8, sh / 4);

                // Tank (cistern)
                g2.setColor(new Color(226, 232, 240));
                g2.fillRoundRect(1, 1, sw - 2, tankH, 4, 4);
                g2.setColor(new Color(100, 116, 139));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(1, 1, sw - 2, tankH, 4, 4);

                // Flush button
                g2.setColor(new Color(56, 189, 248));
                g2.fillRoundRect(sw / 2 - 5, 3, 10, tankH / 2, 3, 3);

                // Bowl outer
                int bowlW = (int)(sw * 0.9);
                int bowlH = sh - tankH + 2;
                int bx = (sw - bowlW) / 2;
                g2.setColor(new Color(241, 245, 249));
                g2.fillOval(bx, tankH - 2, bowlW, bowlH);
                g2.setColor(new Color(100, 116, 139));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawOval(bx, tankH - 2, bowlW, bowlH);

                // Seat rim (inner oval)
                g2.setColor(new Color(203, 213, 225));
                g2.setStroke(new BasicStroke(1.0f));
                int rimOff = Math.max(3, bowlW / 6);
                g2.drawOval(bx + rimOff, tankH + rimOff - 2, bowlW - rimOff * 2, bowlH - rimOff * 2);

                // Water line
                g2.setColor(new Color(56, 189, 248, 100));
                g2.fillOval(bx + bowlW / 3, tankH + bowlH / 3, bowlW / 3, bowlH / 3);

            // ─── WASH BASIN ─────────────────────────────────────────────
            } else if (type.equals("basin")) {
                // Counter/vanity
                g2.setColor(new Color(30, 41, 59));
                g2.fillRoundRect(0, 0, sw, sh, 4, 4);
                g2.setColor(new Color(203, 213, 225));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(0, 0, sw, sh, 4, 4);

                // Basin bowl (oval)
                int pad = Math.max(4, sw / 8);
                g2.setColor(new Color(241, 245, 249));
                g2.fillOval(pad, pad, sw - pad * 2, sh - pad * 2);
                g2.setColor(new Color(100, 116, 139));
                g2.drawOval(pad, pad, sw - pad * 2, sh - pad * 2);

                // Drain
                g2.setColor(new Color(56, 189, 248));
                g2.fillOval(sw / 2 - 3, sh / 2 - 3, 6, 6);

                // Faucet line
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawLine(sw / 2, pad - 2, sw / 2, pad + 6);
                g2.drawLine(sw / 2 - 4, pad - 2, sw / 2 + 4, pad - 2);

            // ─── BATHTUB ────────────────────────────────────────────────
            } else if (type.equals("bathtub")) {
                // Outer rim
                g2.setColor(new Color(30, 41, 59));
                g2.fillRoundRect(0, 0, sw, sh, 6, 6);
                g2.setColor(new Color(203, 213, 225));
                g2.setStroke(new BasicStroke(1.4f));
                g2.drawRoundRect(0, 0, sw, sh, 6, 6);

                // Inner tub basin
                int pad = Math.max(4, (int)(sw * 0.06));
                g2.setColor(new Color(241, 245, 249));
                g2.fillRoundRect(pad, pad, sw - pad * 2, sh - pad * 2, 16, 16);
                g2.setColor(new Color(100, 116, 139));
                g2.drawRoundRect(pad, pad, sw - pad * 2, sh - pad * 2, 16, 16);

                // Drain
                g2.setColor(new Color(56, 189, 248));
                g2.drawOval(sw - pad - 10, sh / 2 - 4, 8, 8);

                // Faucet assembly at one end
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawLine(pad + 4, sh / 2 - 6, pad + 4, sh / 2 + 6);
                g2.drawOval(pad + 1, sh / 2 - 8, 6, 6);
                g2.drawOval(pad + 1, sh / 2 + 3, 6, 6);

            // ─── SHOWER STALL ───────────────────────────────────────────
            } else if (type.equals("shower")) {
                // Stall outline
                g2.setColor(new Color(30, 41, 59));
                g2.fillRect(0, 0, sw, sh);
                g2.setColor(new Color(56, 189, 248));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRect(0, 0, sw, sh);

                // Floor drain (center circles)
                g2.drawOval(sw / 2 - 6, sh / 2 - 6, 12, 12);
                g2.drawOval(sw / 2 - 3, sh / 2 - 3, 6, 6);

                // Drain cross
                g2.setStroke(new BasicStroke(0.8f));
                g2.drawLine(sw / 2 - 6, sh / 2, sw / 2 + 6, sh / 2);
                g2.drawLine(sw / 2, sh / 2 - 6, sw / 2, sh / 2 + 6);

                // Glass door arc (corner quarter-circle)
                g2.setColor(new Color(56, 189, 248, 140));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawArc(-2, -2, 24, 24, 270, 90);

                // Shower head indicator
                g2.setColor(new Color(148, 163, 184));
                g2.fillOval(sw - 10, 4, 8, 8);

            // ─── L-SHAPE KITCHEN ────────────────────────────────────────
            } else if (type.equals("kitchen-l")) {
                int d = Math.max(14, (int) Math.min(sw * 0.35, Math.min(sh * 0.35, (2.0 * FT_PX) * zoom)));

                Path2D.Double lCounter = new Path2D.Double();
                lCounter.moveTo(0, 0);
                lCounter.lineTo(sw, 0);
                lCounter.lineTo(sw, d);
                lCounter.lineTo(d, d);
                lCounter.lineTo(d, sh);
                lCounter.lineTo(0, sh);
                lCounter.closePath();

                g2.setColor(new Color(30, 41, 59));
                g2.fill(lCounter);
                g2.setColor(new Color(203, 213, 225));
                g2.setStroke(new BasicStroke(1.4f));
                g2.draw(lCounter);

                // Backsplash border
                g2.setColor(new Color(71, 85, 105));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawLine(1, 2, sw - 1, 2);
                g2.drawLine(2, 1, 2, sh - 1);

                // Corner 45° miter line
                g2.setColor(new Color(100, 116, 139, 140));
                g2.setStroke(new BasicStroke(0.8f));
                g2.drawLine(0, 0, d, d);

                // Overhead wall cabinets projection (dashed)
                int ohD = (int)(d * 0.55);
                g2.setColor(new Color(56, 189, 248, 140));
                g2.setStroke(new BasicStroke(0.8f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{4, 3}, 0));
                g2.drawLine(ohD, ohD, sw, ohD);
                g2.drawLine(ohD, ohD, ohD, sh);

                // 4-burner cooktop on top wing
                int stoveW = Math.max(18, Math.min(sw / 4, d * 2));
                int stoveH = (int)(d * 0.8);
                int stoveX = d + (sw - d - stoveW) / 2;
                int stoveY = (d - stoveH) / 2;
                drawCooktop4Burner(g2, stoveX, stoveY, stoveW, stoveH);

                // Double undermount sink on left wing
                int sinkW = (int)(d * 0.8);
                int sinkH = Math.max(18, Math.min(sh / 4, d * 2));
                int sinkX = (d - sinkW) / 2;
                int sinkY = d + (sh - d - sinkH) / 2;
                drawUndermountSink(g2, sinkX, sinkY, sinkW, sinkH);

                // Module cabinet divisions
                g2.setColor(new Color(100, 116, 139, 120));
                g2.setStroke(new BasicStroke(0.7f));
                g2.drawLine(sw - (sw - d) / 3, 0, sw - (sw - d) / 3, d);
                g2.drawLine(0, sh - (sh - d) / 3, d, sh - (sh - d) / 3);

                // Label
                g2.setColor(new Color(148, 163, 184));
                g2.setFont(new Font("Consolas", Font.BOLD, Math.max(9, (int)(10 * zoom))));
                g2.drawString("L-KITCHEN", d + 8, d + 16);

            // ─── U-SHAPE KITCHEN ────────────────────────────────────────
            } else if (type.equals("kitchen-u")) {
                int d = Math.max(14, (int) Math.min(sw / 3.2, Math.min(sh / 2.8, (2.0 * FT_PX) * zoom)));

                Path2D.Double uCounter = new Path2D.Double();
                uCounter.moveTo(0, 0);
                uCounter.lineTo(sw, 0);
                uCounter.lineTo(sw, sh);
                uCounter.lineTo(sw - d, sh);
                uCounter.lineTo(sw - d, d);
                uCounter.lineTo(d, d);
                uCounter.lineTo(d, sh);
                uCounter.lineTo(0, sh);
                uCounter.closePath();

                g2.setColor(new Color(30, 41, 59));
                g2.fill(uCounter);
                g2.setColor(new Color(203, 213, 225));
                g2.setStroke(new BasicStroke(1.4f));
                g2.draw(uCounter);

                // Backsplash
                g2.setColor(new Color(71, 85, 105));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawLine(2, 2, sw - 2, 2);
                g2.drawLine(2, 2, 2, sh - 2);
                g2.drawLine(sw - 2, 2, sw - 2, sh - 2);

                // Corner miters
                g2.setColor(new Color(100, 116, 139, 140));
                g2.setStroke(new BasicStroke(0.8f));
                g2.drawLine(0, 0, d, d);
                g2.drawLine(sw, 0, sw - d, d);

                // Overhead wall cabinets projection
                int ohD = (int)(d * 0.55);
                g2.setColor(new Color(56, 189, 248, 140));
                g2.setStroke(new BasicStroke(0.8f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{4, 3}, 0));
                g2.drawLine(ohD, ohD, sw - ohD, ohD);
                g2.drawLine(ohD, ohD, ohD, sh);
                g2.drawLine(sw - ohD, ohD, sw - ohD, sh);

                // Top center sink
                int sinkW = Math.max(22, Math.min(sw / 3, d * 2));
                int sinkH = (int)(d * 0.8);
                drawUndermountSink(g2, (sw - sinkW) / 2, (d - sinkH) / 2, sinkW, sinkH);

                // Left stove
                int stoveW = (int)(d * 0.8);
                int stoveH = Math.max(18, Math.min(sh / 3, d * 2));
                drawCooktop4Burner(g2, (d - stoveW) / 2, d + (sh - d - stoveH) / 2, stoveW, stoveH);

                // Right prep counter
                int prepW = (int)(d * 0.8);
                int prepH = Math.max(16, (sh - d) / 2);
                int prepX = sw - d + (d - prepW) / 2;
                int prepY = d + (sh - d - prepH) / 2;
                g2.setColor(new Color(15, 23, 42));
                g2.fillRoundRect(prepX, prepY, prepW, prepH, 3, 3);
                g2.setColor(new Color(245, 158, 11, 160));
                g2.drawRoundRect(prepX, prepY, prepW, prepH, 3, 3);
                for (int l = 4; l < prepH; l += 5) g2.drawLine(prepX + 2, prepY + l, prepX + prepW - 2, prepY + l);

                // Walking aisle label
                g2.setColor(new Color(148, 163, 184));
                g2.setFont(new Font("Consolas", Font.BOLD, Math.max(9, (int)(10 * zoom))));
                g2.drawString("U-KITCHEN", sw / 2 - 24, sh - 12);

            // ─── PARALLEL / GALLEY KITCHEN ──────────────────────────────
            } else if (type.equals("kitchen-parallel")) {
                int d = Math.max(14, (int) Math.min(sh / 3.0, (2.0 * FT_PX) * zoom));

                // Top Counter Run
                g2.setColor(new Color(30, 41, 59));
                g2.fillRoundRect(0, 0, sw, d, 3, 3);
                g2.setColor(new Color(203, 213, 225));
                g2.setStroke(new BasicStroke(1.4f));
                g2.drawRoundRect(0, 0, sw, d, 3, 3);

                // Bottom Counter Run
                g2.setColor(new Color(30, 41, 59));
                g2.fillRoundRect(0, sh - d, sw, d, 3, 3);
                g2.setColor(new Color(203, 213, 225));
                g2.setStroke(new BasicStroke(1.4f));
                g2.drawRoundRect(0, sh - d, sw, d, 3, 3);

                // Overhead cabinet dashed lines
                int ohD = (int)(d * 0.55);
                g2.setColor(new Color(56, 189, 248, 140));
                g2.setStroke(new BasicStroke(0.8f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{4, 3}, 0));
                g2.drawLine(0, ohD, sw, ohD);
                g2.drawLine(0, sh - ohD, sw, sh - ohD);

                // Cooktop on Top Bank
                int stoveW = Math.max(20, Math.min(sw / 3, d * 2));
                int stoveH = (int)(d * 0.8);
                drawCooktop4Burner(g2, sw / 4 - stoveW / 2, (d - stoveH) / 2, stoveW, stoveH);

                // Tall Pantry / Oven Tower at Top Right
                int tw = (int)(sw / 4);
                g2.setColor(new Color(15, 23, 42));
                g2.fillRect(sw - tw, 0, tw, d);
                g2.setColor(new Color(203, 213, 225));
                g2.drawRect(sw - tw, 0, tw, d);
                g2.drawLine(sw - tw, 0, sw, d);
                g2.drawLine(sw - tw, d, sw, 0);

                // Double Sink on Bottom Bank
                int sinkW = Math.max(22, Math.min(sw / 3, d * 2));
                int sinkH = (int)(d * 0.8);
                drawUndermountSink(g2, sw / 2 - sinkW / 2, sh - d + (d - sinkH) / 2, sinkW, sinkH);

                // Walkway label
                g2.setColor(new Color(56, 189, 248, 160));
                g2.setFont(new Font("Consolas", Font.BOLD, Math.max(8, (int)(9 * zoom))));
                g2.drawString("PARALLEL AISLE (3'-0\" CLEAR)", sw / 2 - 55, sh / 2 + 4);

            // ─── ISLAND KITCHEN ─────────────────────────────────────────
            } else if (type.equals("kitchen-island")) {
                int d = Math.max(14, (int) Math.min(sh * 0.28, (2.0 * FT_PX) * zoom));

                // Rear Wall Counter
                g2.setColor(new Color(30, 41, 59));
                g2.fillRoundRect(0, 0, sw, d, 4, 4);
                g2.setColor(new Color(203, 213, 225));
                g2.setStroke(new BasicStroke(1.4f));
                g2.drawRoundRect(0, 0, sw, d, 4, 4);

                // Cooktop on Rear Counter
                int stoveW = Math.max(20, sw / 4);
                int stoveH = (int)(d * 0.8);
                drawCooktop4Burner(g2, sw / 4 - stoveW / 2, (d - stoveH) / 2, stoveW, stoveH);

                // Sink on Rear Counter
                int sinkW = Math.max(22, sw / 4);
                int sinkH = (int)(d * 0.8);
                drawUndermountSink(g2, 3 * sw / 4 - sinkW / 2, (d - stoveH) / 2, sinkW, sinkH);

                // Overhead cabinet line
                int ohD = (int)(d * 0.55);
                g2.setColor(new Color(56, 189, 248, 140));
                g2.setStroke(new BasicStroke(0.8f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{4, 3}, 0));
                g2.drawLine(0, ohD, sw, ohD);

                // Central Island Table
                int iw = (int)(sw * 0.65);
                int ih = Math.max(18, (int)((sh - d) * 0.45));
                int ix = (sw - iw) / 2;
                int iy = d + Math.max(14, (int)((sh - d - ih) * 0.35));

                g2.setColor(new Color(24, 33, 53));
                g2.fillRoundRect(ix, iy, iw, ih, 6, 6);
                g2.setColor(new Color(56, 189, 248));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(ix, iy, iw, ih, 6, 6);
                g2.setColor(new Color(100, 116, 139));
                g2.setStroke(new BasicStroke(0.8f));
                g2.drawRoundRect(ix + 3, iy + 3, iw - 6, ih - 6, 4, 4);

                // Island prep sink
                int prepSinkW = Math.max(12, iw / 4);
                int prepSinkH = ih - 8;
                drawUndermountSink(g2, ix + iw / 4 - prepSinkW / 2, iy + 4, prepSinkW, prepSinkH);

                // Overhang breakfast bar line
                g2.setColor(new Color(56, 189, 248, 120));
                g2.setStroke(new BasicStroke(0.8f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{3, 3}, 0));
                g2.drawLine(ix, iy + ih - 4, ix + iw, iy + ih - 4);

                // 3 Bar Stools
                int stoolR = Math.max(6, Math.min(10, (int)(8 * zoom)));
                int stoolY = Math.min(sh - stoolR - 2, iy + ih + stoolR + 4);
                for (int s = 0; s < 3; s++) {
                    int stoolX = ix + (s + 1) * (iw / 4);
                    drawBarStool(g2, stoolX, stoolY, stoolR);
                }

                g2.setColor(new Color(148, 163, 184));
                g2.setFont(new Font("Consolas", Font.BOLD, Math.max(8, (int)(9 * zoom))));
                g2.drawString("ISLAND + BREAKFAST BAR", ix + 8, iy + ih / 2 + 3);

            // ─── PENINSULA / G-SHAPE KITCHEN ────────────────────────────
            } else if (type.equals("kitchen-peninsula")) {
                int d = Math.max(14, (int) Math.min(sw * 0.3, Math.min(sh * 0.3, (2.0 * FT_PX) * zoom)));
                int penLen = (int)(sw * 0.7);

                Path2D.Double penPath = new Path2D.Double();
                penPath.moveTo(0, 0);
                penPath.lineTo(sw, 0);
                penPath.lineTo(sw, d);
                penPath.lineTo(d, d);
                penPath.lineTo(d, sh - d);
                penPath.lineTo(penLen, sh - d);
                penPath.lineTo(penLen, sh);
                penPath.lineTo(0, sh);
                penPath.closePath();

                g2.setColor(new Color(30, 41, 59));
                g2.fill(penPath);
                g2.setColor(new Color(203, 213, 225));
                g2.setStroke(new BasicStroke(1.4f));
                g2.draw(penPath);

                // Cooktop on Top Run
                int stoveW = Math.max(18, sw / 3);
                int stoveH = (int)(d * 0.8);
                drawCooktop4Burner(g2, d + 8, (d - stoveH) / 2, stoveW, stoveH);

                // Double Sink on Left Run
                int sinkW = (int)(d * 0.8);
                int sinkH = Math.max(18, (sh - d * 2) / 2);
                drawUndermountSink(g2, (d - sinkW) / 2, d + 6, sinkW, sinkH);

                // Breakfast bar stools
                int stoolR = Math.max(5, Math.min(9, (int)(7 * zoom)));
                for (int s = 0; s < 2; s++) {
                    int sx = d + 12 + s * (penLen - d - 24);
                    int sy = Math.max(d + stoolR + 2, sh - d - stoolR - 4);
                    drawBarStool(g2, sx, sy, stoolR);
                }

                g2.setColor(new Color(148, 163, 184));
                g2.setFont(new Font("Consolas", Font.BOLD, Math.max(8, (int)(9 * zoom))));
                g2.drawString("PENINSULA", d + 8, sh - d / 2 + 3);

            // ─── STRAIGHT / LINEAR KITCHEN ──────────────────────────────
            } else if (type.equals("kitchen-linear")) {
                g2.setColor(new Color(30, 41, 59));
                g2.fillRoundRect(0, 0, sw, sh, 4, 4);
                g2.setColor(new Color(203, 213, 225));
                g2.setStroke(new BasicStroke(1.4f));
                g2.drawRoundRect(0, 0, sw, sh, 4, 4);

                // Backsplash
                g2.setColor(new Color(71, 85, 105));
                g2.drawLine(2, 2, sw - 2, 2);

                int ohD = Math.max(6, sh / 2);
                g2.setColor(new Color(56, 189, 248, 140));
                g2.setStroke(new BasicStroke(0.8f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{4, 3}, 0));
                g2.drawLine(0, ohD, sw, ohD);

                int secW = sw / 4;
                // Sec 1: Tall Fridge Unit
                g2.setColor(new Color(15, 23, 42));
                g2.fillRect(2, 2, secW - 2, sh - 4);
                g2.setColor(new Color(203, 213, 225));
                g2.drawRect(2, 2, secW - 2, sh - 4);
                g2.setColor(new Color(56, 189, 248));
                g2.drawLine(secW - 6, 4, secW - 6, sh - 4);

                // Sec 2: 4-Burner Cooktop
                int stoveW = (int)(secW * 0.85);
                int stoveH = (int)(sh * 0.8);
                drawCooktop4Burner(g2, secW + (secW - stoveW) / 2, (sh - stoveH) / 2, stoveW, stoveH);

                // Sec 3: Prep counter
                g2.setColor(new Color(100, 116, 139, 120));
                g2.drawRoundRect(secW * 2 + 4, 4, secW - 8, sh - 8, 3, 3);

                // Sec 4: Double Sink
                int sinkW = (int)(secW * 0.85);
                int sinkH = (int)(sh * 0.8);
                drawUndermountSink(g2, secW * 3 + (secW - sinkW) / 2, (sh - sinkH) / 2, sinkW, sinkH);

            // ─── STANDALONE KITCHEN ISLAND UNIT ─────────────────────────
            } else if (type.equals("kitchen-island-unit")) {
                g2.setColor(new Color(24, 33, 53));
                g2.fillRoundRect(0, 0, sw, sh, 6, 6);
                g2.setColor(new Color(56, 189, 248));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(0, 0, sw, sh, 6, 6);

                g2.setColor(new Color(100, 116, 139));
                g2.setStroke(new BasicStroke(0.8f));
                g2.drawRoundRect(3, 3, sw - 6, sh - 6, 4, 4);

                int sinkW = Math.max(14, sw / 3);
                int sinkH = Math.max(10, sh / 2);
                drawUndermountSink(g2, 8, (sh - sinkH) / 2, sinkW, sinkH);

                int dwX = sw / 2;
                g2.setColor(new Color(100, 116, 139, 140));
                g2.drawLine(dwX, 4, dwX, sh - 4);
                g2.drawLine(dwX, sh / 2, sw - 6, sh / 2);

                int stoolR = Math.max(6, Math.min(10, (int)(8 * zoom)));
                int numStools = Math.max(2, sw / 36);
                for (int s = 0; s < numStools; s++) {
                    int sx = (s + 1) * (sw / (numStools + 1));
                    drawBarStool(g2, sx, sh + stoolR + 2, stoolR);
                }

            // ─── BREAKFAST BAR + STOOLS ─────────────────────────────────
            } else if (type.equals("kitchen-bar")) {
                g2.setColor(new Color(30, 41, 59));
                g2.fillRoundRect(0, 0, sw, sh, 4, 4);
                g2.setColor(new Color(203, 213, 225));
                g2.setStroke(new BasicStroke(1.4f));
                g2.drawRoundRect(0, 0, sw, sh, 4, 4);

                g2.setColor(new Color(56, 189, 248));
                g2.setFont(new Font("Consolas", Font.BOLD, Math.max(8, (int)(9 * zoom))));
                g2.drawString("BREAKFAST BAR", 8, sh / 2 + 3);

                int stoolR = Math.max(6, (int)(8 * zoom));
                for (int s = 0; s < 3; s++) {
                    drawBarStool(g2, (s + 1) * (sw / 4), sh + stoolR + 2, stoolR);
                }

            // ─── STUDIO KITCHENETTE ──────────────────────────────────────
            } else if (type.equals("kitchen-compact")) {
                g2.setColor(new Color(30, 41, 59));
                g2.fillRoundRect(0, 0, sw, sh, 4, 4);
                g2.setColor(new Color(203, 213, 225));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(0, 0, sw, sh, 4, 4);

                int hobW = sw / 3, hobH = (int)(sh * 0.8);
                g2.setColor(new Color(15, 23, 42));
                g2.fillRect(4, 3, hobW, hobH);
                g2.setColor(new Color(245, 158, 11));
                drawBurnerGrate(g2, 4 + hobW / 4, 3 + hobH / 2, hobH / 2);
                drawBurnerGrate(g2, 4 + 3 * hobW / 4, 3 + hobH / 2, hobH / 2);

                int sinkW = sw / 3, sinkH = (int)(sh * 0.8);
                int sx = sw - sinkW - 4;
                g2.setColor(new Color(226, 232, 240));
                g2.drawRoundRect(sx, 3, sinkW, sinkH, 3, 3);
                g2.setColor(new Color(56, 189, 248));
                g2.fillOval(sx + sinkW / 2 - 2, 3 + sinkH / 2 - 2, 4, 4);

            // ─── BUILT-IN OVEN / MICROWAVE TOWER ────────────────────────
            } else if (type.equals("kitchen-oven")) {
                g2.setColor(new Color(15, 23, 42));
                g2.fillRoundRect(0, 0, sw, sh, 4, 4);
                g2.setColor(new Color(203, 213, 225));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(0, 0, sw, sh, 4, 4);

                g2.setColor(new Color(30, 41, 59));
                g2.fillRect(4, 4, sw - 8, sh / 3);
                g2.setColor(new Color(56, 189, 248));
                g2.drawRect(4, 4, sw - 8, sh / 3);
                g2.setColor(new Color(30, 41, 59));
                g2.fillRect(4, sh / 3 + 6, sw - 8, sh / 2);
                g2.setColor(new Color(245, 158, 11));
                g2.drawRect(4, sh / 3 + 6, sw - 8, sh / 2);
                g2.setColor(new Color(203, 213, 225));
                g2.drawLine(8, sh / 3 + 10, sw - 8, sh / 3 + 10);

            // ─── DISHWASHER ─────────────────────────────────────────────
            } else if (type.equals("kitchen-dishwasher")) {
                g2.setColor(new Color(30, 41, 59));
                g2.fillRoundRect(0, 0, sw, sh, 4, 4);
                g2.setColor(new Color(203, 213, 225));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(0, 0, sw, sh, 4, 4);

                g2.setColor(new Color(15, 23, 42));
                g2.fillRect(2, 2, sw - 4, sh / 4);
                g2.setColor(new Color(56, 189, 248));
                g2.fillOval(6, sh / 8, 4, 4);
                g2.fillOval(14, sh / 8, 4, 4);
                g2.fillOval(22, sh / 8, 4, 4);

                g2.setColor(new Color(203, 213, 225));
                g2.drawRoundRect(sw / 4, sh / 3, sw / 2, 4, 2, 2);

            // ─── KITCHEN SINK ───────────────────────────────────────────
            } else if (type.equals("kitchen-sink")) {
                // Counter
                g2.setColor(new Color(30, 41, 59));
                g2.fillRoundRect(0, 0, sw, sh, 4, 4);
                g2.setColor(new Color(203, 213, 225));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(0, 0, sw, sh, 4, 4);

                // Double basins
                int bw = (sw - 14) / 2;
                g2.setColor(new Color(226, 232, 240));
                g2.setStroke(new BasicStroke(1.0f));
                g2.drawRoundRect(4, 4, bw, sh - 8, 4, 4);
                g2.drawRoundRect(sw - bw - 4, 4, bw, sh - 8, 4, 4);

                // Drains
                g2.setColor(new Color(56, 189, 248));
                g2.fillOval(4 + bw / 2 - 3, sh / 2 - 3, 6, 6);
                g2.fillOval(sw - bw - 4 + bw / 2 - 3, sh / 2 - 3, 6, 6);

                // Faucet gooseneck
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawLine(sw / 2, 2, sw / 2, sh / 3);
                g2.drawArc(sw / 2 - 4, 0, 8, sh / 3, 0, 180);

            // ─── STOVE ──────────────────────────────────────────────────
            } else if (type.equals("stove")) {
                g2.setColor(new Color(15, 23, 42));
                g2.fillRoundRect(0, 0, sw, sh, 6, 6);
                g2.setColor(new Color(203, 213, 225));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(0, 0, sw, sh, 6, 6);

                int bx1 = sw / 4, bx2 = 3 * sw / 4;
                int by1 = sh / 4, by2 = 3 * sh / 4;
                int br = Math.max(8, Math.min(sw, sh) / 5);

                g2.setColor(new Color(245, 158, 11));
                drawBurnerGrate(g2, bx1, by1, br);
                drawBurnerGrate(g2, bx2, by1, br);
                drawBurnerGrate(g2, bx1, by2, br);
                drawBurnerGrate(g2, bx2, by2, br);

                // Knob indicators
                g2.setColor(new Color(148, 163, 184));
                g2.fillOval(2, sh - 6, 4, 4);
                g2.fillOval(10, sh - 6, 4, 4);
                g2.fillOval(18, sh - 6, 4, 4);
                g2.fillOval(26, sh - 6, 4, 4);

            // ─── REFRIGERATOR ───────────────────────────────────────────
            } else if (type.equals("fridge")) {
                g2.setColor(new Color(30, 41, 59));
                g2.fillRoundRect(0, 0, sw, sh, 4, 4);
                g2.setColor(new Color(203, 213, 225));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(0, 0, sw, sh, 4, 4);

                // Freezer/fridge divider
                int divY = sh / 3;
                g2.setColor(new Color(56, 189, 248));
                g2.drawLine(0, divY, sw, divY);

                // Handles
                g2.setStroke(new BasicStroke(2.0f));
                g2.drawLine(sw - 5, 4, sw - 5, divY - 4);
                g2.drawLine(sw - 5, divY + 4, sw - 5, sh - 4);

            // ─── WASHING MACHINE ────────────────────────────────────────
            } else if (type.equals("washer")) {
                g2.setColor(new Color(30, 41, 59));
                g2.fillRoundRect(0, 0, sw, sh, 6, 6);
                g2.setColor(new Color(203, 213, 225));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(0, 0, sw, sh, 6, 6);

                // Drum circle
                int drumR = Math.min(sw, sh) / 3;
                g2.setColor(new Color(56, 189, 248));
                g2.drawOval(sw / 2 - drumR, sh / 2 - drumR, drumR * 2, drumR * 2);
                g2.drawOval(sw / 2 - drumR + 4, sh / 2 - drumR + 4, drumR * 2 - 8, drumR * 2 - 8);

                // Control panel
                g2.setColor(new Color(100, 116, 139));
                g2.drawLine(4, sh / 6, sw - 4, sh / 6);
                g2.fillOval(sw / 2 - 3, sh / 12 - 3, 6, 6);

            // ─── RCC / SQUARE COLUMN ────────────────────────────────────
            } else if (type.startsWith("column")) {
                g2.setColor(new Color(71, 85, 105));
                g2.fillRect(0, 0, sw, sh);
                g2.setColor(new Color(203, 213, 225));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRect(0, 0, sw, sh);
                // Cross hatching (structural convention)
                g2.setColor(new Color(56, 189, 248));
                g2.setStroke(new BasicStroke(1.0f));
                g2.drawLine(0, 0, sw, sh);
                g2.drawLine(0, sh, sw, 0);

            // ─── NORTH ARROW ────────────────────────────────────────────
            } else if (type.equals("north-arrow")) {
                int r = Math.min(sw, sh) / 2;
                int cx = sw / 2, cy = sh / 2;

                // Outer circle
                g2.setColor(new Color(148, 163, 184));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawOval(cx - r + 4, cy - r + 4, (r - 4) * 2, (r - 4) * 2);

                int topY = cy - r + 6;
                int botY = cy + (int)(r * 0.25);
                int wingY = cy + (int)(r * 0.55);
                int wingW = (int)(r * 0.35);

                Polygon needleL = new Polygon(
                        new int[]{cx, cx - wingW, cx},
                        new int[]{topY, wingY, botY}, 3);
                Polygon needleR = new Polygon(
                        new int[]{cx, cx + wingW, cx},
                        new int[]{topY, wingY, botY}, 3);

                g2.setColor(new Color(56, 189, 248)); // Cyan accent on left
                g2.fillPolygon(needleL);
                g2.setColor(Color.WHITE); // White on right
                g2.fillPolygon(needleR);

                g2.setColor(new Color(203, 213, 225));
                g2.setStroke(new BasicStroke(1.0f));
                g2.drawPolygon(needleL);
                g2.drawPolygon(needleR);
                g2.drawLine(cx, topY, cx, botY);

                // "N" label
                g2.setColor(new Color(56, 189, 248));
                g2.setFont(new Font("Segoe UI", Font.BOLD, Math.max(11, (int)(14 * zoom))));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString("N", cx - fm.stringWidth("N") / 2, topY - 2);

            // ─── STAIRCASE ──────────────────────────────────────────────
            } else if (type.startsWith("stair")) {
                g2.setColor(new Color(11, 19, 36));
                g2.fillRect(0, 0, sw, sh);
                g2.setColor(new Color(148, 163, 184));
                g2.setStroke(new BasicStroke(1.0f));
                g2.drawRect(0, 0, sw, sh);

                // Treads
                int treads = type.equals("stair-dogleg") ? 8 : 10;
                for (int t = 1; t < treads; t++) {
                    int ty = (sh / treads) * t;
                    g2.drawLine(0, ty, sw, ty);
                }

                // UP arrow
                g2.setColor(new Color(245, 158, 11));
                g2.setFont(new Font("Segoe UI", Font.BOLD, Math.max(9, (int)(10 * zoom))));
                g2.drawString("UP \u2191", sw / 2 - 12, sh - 6);

                // Halfway line for dog-leg
                if (type.equals("stair-dogleg")) {
                    g2.setColor(new Color(56, 189, 248));
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawLine(0, sh / 2, sw, sh / 2);
                }

            // ─── ROOM AREA TAG ──────────────────────────────────────────
            } else if (type.equals("tag")) {
                String[] rawLines = el.name.contains("\n") ? el.name.split("\n") : (el.name.contains(" / ") ? el.name.split("\\s*/\\s*") : new String[]{el.name});
                List<String> displayLines = new ArrayList<>();
                boolean hasAreaLine = false;
                for (String l : rawLines) {
                    String trimmed = l.trim();
                    if (!trimmed.isEmpty()) {
                        displayLines.add(trimmed);
                        String up = trimmed.toUpperCase();
                        if (up.contains("SQFT") || up.contains("SQ.FT") || up.contains("SQ FT") || up.contains("SQ. FT")) {
                            hasAreaLine = true;
                        }
                    }
                }
                if (!hasAreaLine) {
                    double roomSqFt = parseRoomAreaFromText(el.name);
                    if (roomSqFt <= 0) {
                        roomSqFt = computeRoomAreaFromWalls(el.x + el.w / 2.0, el.y + el.h / 2.0);
                    }
                    if (roomSqFt > 0) {
                        displayLines.add(String.format("%.0f SQ.FT.", roomSqFt));
                    }
                }
                if (displayLines.isEmpty()) {
                    displayLines.add("ROOM");
                }

                float titleSize = (float) Math.max(11, 12 * zoom);
                float subSize   = (float) Math.max(9.5, 10.5 * zoom);
                Font titleFont = new Font("Segoe UI", Font.BOLD, (int) titleSize);
                Font subFont   = new Font("Consolas", Font.PLAIN, (int) subSize);
                Font areaFont  = new Font("Consolas", Font.BOLD, (int) subSize);

                int maxTextW = 0;
                int lineH = (int) (titleSize * 1.35);
                int totalTextH = displayLines.size() * lineH;

                for (int i = 0; i < displayLines.size(); i++) {
                    g2.setFont(i == 0 ? titleFont : subFont);
                    FontMetrics fm = g2.getFontMetrics();
                    maxTextW = Math.max(maxTextW, fm.stringWidth(displayLines.get(i)));
                }

                int padX = 8, padY = 5;
                int maskW = maxTextW + padX * 2;
                int maskH = totalTextH + padY * 2;
                int maskX = (sw - maskW) / 2;
                int maskY = (sh - maskH) / 2;

                // AutoCAD-style subtle background mask so lines underneath don't clash (minimal padding)
                g2.setColor(new Color(15, 23, 42, 215));
                g2.fillRoundRect(maskX, maskY, maskW, maskH, 6, 6);
                g2.setColor(new Color(56, 189, 248, 80));
                g2.setStroke(new BasicStroke(0.8f));
                g2.drawRoundRect(maskX, maskY, maskW, maskH, 6, 6);

                // Draw each text line centered
                int curY = maskY + padY + (int)(titleSize * 0.95);
                for (int i = 0; i < displayLines.size(); i++) {
                    String line = displayLines.get(i);
                    boolean isTitle = (i == 0);
                    boolean isArea = (!hasAreaLine && i == displayLines.size() - 1);

                    g2.setFont(isTitle ? titleFont : (isArea ? areaFont : subFont));
                    FontMetrics fm = g2.getFontMetrics();
                    int lx = (sw - fm.stringWidth(line)) / 2;

                    if (isTitle) {
                        g2.setColor(Color.WHITE);
                    } else if (isArea) {
                        g2.setColor(new Color(56, 189, 248)); // Cyan area highlight
                    } else {
                        g2.setColor(new Color(203, 213, 225)); // Light slate dimensions
                    }
                    g2.drawString(line, lx, curY);
                    curY += lineH;
                }

            // ─── CEILING FAN ────────────────────────────────────────────
            } else if (type.equals("ceiling-fan")) {
                int cx = sw / 2, cy = sh / 2;
                int r = Math.min(sw, sh) / 2 - 2;

                // Outer circle
                g2.setColor(new Color(100, 116, 139));
                g2.setStroke(new BasicStroke(1.0f));
                g2.drawOval(cx - r, cy - r, r * 2, r * 2);

                // Fan blades (4 blades)
                g2.setColor(new Color(56, 189, 248, 140));
                g2.setStroke(new BasicStroke(1.5f));
                for (int b = 0; b < 4; b++) {
                    double angle = Math.toRadians(b * 90 + 30);
                    int bx = (int)(cx + r * 0.85 * Math.cos(angle));
                    int by = (int)(cy + r * 0.85 * Math.sin(angle));
                    g2.drawLine(cx, cy, bx, by);
                    // Blade shape
                    double perp = angle + Math.PI / 2;
                    int pw = (int)(r * 0.2);
                    g2.drawLine(bx + (int)(pw * Math.cos(perp)), by + (int)(pw * Math.sin(perp)),
                               bx - (int)(pw * Math.cos(perp)), by - (int)(pw * Math.sin(perp)));
                }

                // Center hub
                g2.setColor(new Color(56, 189, 248));
                g2.fillOval(cx - 4, cy - 4, 8, 8);

            // ─── AC UNIT ────────────────────────────────────────────────
            } else if (type.equals("ac-unit")) {
                g2.setColor(new Color(30, 41, 59));
                g2.fillRoundRect(0, 0, sw, sh, 4, 4);
                g2.setColor(new Color(203, 213, 225));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(0, 0, sw, sh, 4, 4);

                // Louver lines
                g2.setColor(new Color(56, 189, 248, 140));
                g2.setStroke(new BasicStroke(0.8f));
                int louvers = 4;
                for (int i = 1; i <= louvers; i++) {
                    int ly = (sh / (louvers + 1)) * i;
                    g2.drawLine(4, ly, sw - 4, ly);
                }

                // "AC" label
                g2.setColor(new Color(56, 189, 248));
                g2.setFont(new Font("Consolas", Font.BOLD, Math.max(8, (int)(9 * zoom))));
                g2.drawString("AC", 4, sh / 2 + 3);

            // ─── LIGHT POINT ────────────────────────────────────────────
            } else if (type.equals("light-point")) {
                int cx = sw / 2, cy = sh / 2;
                int r = Math.min(sw, sh) / 3;
                g2.setColor(new Color(251, 191, 36, 100));
                g2.fillOval(cx - r, cy - r, r * 2, r * 2);
                g2.setColor(new Color(251, 191, 36));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawOval(cx - r, cy - r, r * 2, r * 2);
                // Cross
                g2.drawLine(cx - r, cy, cx + r, cy);
                g2.drawLine(cx, cy - r, cx, cy + r);

            // ─── SWITCHBOARD ────────────────────────────────────────────
            } else if (type.equals("switchboard")) {
                g2.setColor(new Color(30, 41, 59));
                g2.fillRect(0, 0, sw, sh);
                g2.setColor(new Color(148, 163, 184));
                g2.setStroke(new BasicStroke(1.0f));
                g2.drawRect(0, 0, sw, sh);
                g2.setColor(new Color(56, 189, 248));
                g2.fillRect(sw / 4 - 2, sh / 4, 4, sh / 2);
                g2.fillRect(3 * sw / 4 - 2, sh / 4, 4, sh / 2);

            // ─── DECIDUOUS CANOPY TREE ──────────────────────────────────
            } else if (type.equals("tree-deciduous")) {
                int cx = sw / 2, cy = sh / 2;
                int r = Math.min(sw, sh) / 2 - 4;
                if (r < 6) r = 6;

                g2.setColor(new Color(16, 185, 129, 45));
                g2.fillOval(cx - r, cy - r, r * 2, r * 2);

                g2.setColor(new Color(52, 211, 153));
                g2.setStroke(new BasicStroke(1.2f));
                int lobes = 12;
                int lobeR = (int)(r * 0.3);
                for (int i = 0; i < lobes; i++) {
                    double angle = (2 * Math.PI / lobes) * i;
                    int lx = (int)(cx + (r - lobeR * 0.5) * Math.cos(angle));
                    int ly = (int)(cy + (r - lobeR * 0.5) * Math.sin(angle));
                    g2.drawArc(lx - lobeR, ly - lobeR, lobeR * 2, lobeR * 2, (int) Math.toDegrees(angle) - 45, 180);
                }

                int trR = Math.max(3, r / 6);
                g2.setColor(new Color(5, 150, 105));
                g2.fillOval(cx - trR, cy - trR, trR * 2, trR * 2);
                g2.setColor(new Color(167, 243, 208));
                g2.drawOval(cx - trR, cy - trR, trR * 2, trR * 2);
                g2.fillOval(cx - 1, cy - 1, 3, 3);

                g2.setColor(new Color(52, 211, 153, 140));
                g2.setStroke(new BasicStroke(0.8f));
                for (int i = 0; i < 8; i++) {
                    double angle = (2 * Math.PI / 8) * i;
                    int bx = (int)(cx + (r * 0.8) * Math.cos(angle));
                    int by = (int)(cy + (r * 0.8) * Math.sin(angle));
                    g2.drawLine(cx, cy, bx, by);
                }

            // ─── EVERGREEN PINE TREE ────────────────────────────────────
            } else if (type.equals("tree-pine")) {
                int cx = sw / 2, cy = sh / 2;
                int r = Math.min(sw, sh) / 2 - 4;
                if (r < 6) r = 6;

                g2.setColor(new Color(4, 120, 87));
                g2.fillOval(cx - 4, cy - 4, 8, 8);
                g2.setColor(new Color(52, 211, 153));
                g2.drawOval(cx - 4, cy - 4, 8, 8);

                g2.setStroke(new BasicStroke(1.2f));
                int needles = 16;
                for (int i = 0; i < needles; i++) {
                    double angle = (2 * Math.PI / needles) * i;
                    double len = (i % 2 == 0) ? r : r * 0.7;
                    int nx = (int)(cx + len * Math.cos(angle));
                    int ny = (int)(cy + len * Math.sin(angle));
                    g2.drawLine(cx, cy, nx, ny);
                    double sideAng1 = angle + 0.3;
                    double sideAng2 = angle - 0.3;
                    int sx1 = (int)(cx + (len * 0.6) * Math.cos(sideAng1));
                    int sy1 = (int)(cy + (len * 0.6) * Math.sin(sideAng1));
                    int sx2 = (int)(cx + (len * 0.6) * Math.cos(sideAng2));
                    int sy2 = (int)(cy + (len * 0.6) * Math.sin(sideAng2));
                    g2.drawLine((int)(cx + (len * 0.5) * Math.cos(angle)), (int)(cy + (len * 0.5) * Math.sin(angle)), sx1, sy1);
                    g2.drawLine((int)(cx + (len * 0.5) * Math.cos(angle)), (int)(cy + (len * 0.5) * Math.sin(angle)), sx2, sy2);
                }

            // ─── TROPICAL PALM TREE ─────────────────────────────────────
            } else if (type.equals("tree-palm")) {
                int cx = sw / 2, cy = sh / 2;
                int r = Math.min(sw, sh) / 2 - 3;
                if (r < 6) r = 6;

                g2.setColor(new Color(6, 78, 59));
                g2.fillOval(cx - 5, cy - 5, 10, 10);
                g2.setColor(new Color(52, 211, 153));
                g2.drawOval(cx - 5, cy - 5, 10, 10);

                int fronds = 8;
                g2.setStroke(new BasicStroke(1.4f));
                for (int i = 0; i < fronds; i++) {
                    double angle = (2 * Math.PI / fronds) * i;
                    double bendAngle = angle + 0.35;
                    int midX = (int)(cx + (r * 0.6) * Math.cos(bendAngle));
                    int midY = (int)(cy + (r * 0.6) * Math.sin(bendAngle));
                    int tipX = (int)(cx + r * Math.cos(angle));
                    int tipY = (int)(cy + r * Math.sin(angle));

                    Path2D.Double frond = new Path2D.Double();
                    frond.moveTo(cx, cy);
                    frond.quadTo(midX, midY, tipX, tipY);
                    g2.draw(frond);

                    for (double t = 0.3; t <= 0.9; t += 0.2) {
                        int px = (int)((1 - t) * (1 - t) * cx + 2 * (1 - t) * t * midX + t * t * tipX);
                        int py = (int)((1 - t) * (1 - t) * cy + 2 * (1 - t) * t * midY + t * t * tipY);
                        double perp = angle + Math.PI / 2;
                        int lfLen = (int)(r * 0.18);
                        g2.drawLine(px, py, px + (int)(lfLen * Math.cos(perp)), py + (int)(lfLen * Math.sin(perp)));
                        g2.drawLine(px, py, px - (int)(lfLen * Math.cos(perp)), py - (int)(lfLen * Math.sin(perp)));
                    }
                }

            // ─── SHRUB / BUSH ───────────────────────────────────────────
            } else if (type.equals("shrub")) {
                int cx = sw / 2, cy = sh / 2;
                int r = Math.min(sw, sh) / 2 - 3;
                g2.setColor(new Color(16, 185, 129, 50));
                g2.fillOval(cx - r, cy - r, r * 2, r * 2);

                g2.setColor(new Color(52, 211, 153));
                g2.setStroke(new BasicStroke(1.2f));
                int clusters = 8;
                int clR = (int)(r * 0.45);
                for (int i = 0; i < clusters; i++) {
                    double angle = (2 * Math.PI / clusters) * i;
                    int x = (int)(cx + (r * 0.6) * Math.cos(angle));
                    int y = (int)(cy + (r * 0.6) * Math.sin(angle));
                    g2.drawOval(x - clR, y - clR, clR * 2, clR * 2);
                }

            // ─── PLANTER BOX / HEDGE ────────────────────────────────────
            } else if (type.equals("planter-box")) {
                g2.setColor(new Color(30, 41, 59));
                g2.fillRoundRect(0, 0, sw, sh, 4, 4);
                g2.setColor(new Color(148, 163, 184));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(0, 0, sw, sh, 4, 4);

                int numShrubs = Math.max(2, sw / 16);
                int rad = Math.min(sh / 2 - 2, sw / (numShrubs * 2));
                g2.setColor(new Color(16, 185, 129, 120));
                for (int i = 0; i < numShrubs; i++) {
                    int px = (i + 1) * (sw / (numShrubs + 1));
                    g2.fillOval(px - rad, sh / 2 - rad, rad * 2, rad * 2);
                    g2.setColor(new Color(52, 211, 153));
                    g2.drawOval(px - rad, sh / 2 - rad, rad * 2, rad * 2);
                }

            // ─── POTTED PATIO PLANT ─────────────────────────────────────
            } else if (type.equals("plant-pot")) {
                int cx = sw / 2, cy = sh / 2;
                int r = Math.min(sw, sh) / 2 - 2;

                g2.setColor(new Color(120, 53, 15));
                g2.fillOval(cx - r / 2, cy - r / 2, r, r);
                g2.setColor(new Color(245, 158, 11));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawOval(cx - r / 2, cy - r / 2, r, r);

                g2.setColor(new Color(52, 211, 153));
                for (int l = 0; l < 6; l++) {
                    double angle = (2 * Math.PI / 6) * l;
                    int tipX = (int)(cx + r * Math.cos(angle));
                    int tipY = (int)(cy + r * Math.sin(angle));
                    g2.drawLine(cx, cy, tipX, tipY);
                    g2.drawOval(tipX - 3, tipY - 3, 6, 6);
                }

            // ─── SEDAN CAR (Top-Down CAD View) ──────────────────────────
            } else if (type.equals("car-sedan")) {
                Path2D.Double carBody = new Path2D.Double();
                int noseR = Math.max(4, sh / 6);
                carBody.moveTo(noseR * 2, 0);
                carBody.lineTo(sw - noseR * 2, 0);
                carBody.quadTo(sw, 0, sw, noseR * 2);
                carBody.lineTo(sw, sh - noseR * 2);
                carBody.quadTo(sw, sh, sw - noseR * 2, sh);
                carBody.lineTo(noseR * 2, sh);
                carBody.quadTo(0, sh, 0, sh - noseR * 2);
                carBody.lineTo(0, noseR * 2);
                carBody.quadTo(0, 0, noseR * 2, 0);
                carBody.closePath();

                g2.setColor(new Color(30, 41, 59));
                g2.fill(carBody);
                g2.setColor(new Color(203, 213, 225));
                g2.setStroke(new BasicStroke(1.4f));
                g2.draw(carBody);

                int hoodLen = (int)(sw * 0.25);
                int[] wsX = {hoodLen, (int)(sw * 0.4), (int)(sw * 0.4), hoodLen};
                int[] wsY = {3, 6, sh - 6, sh - 3};
                g2.setColor(new Color(56, 189, 248, 140));
                g2.fillPolygon(wsX, wsY, 4);
                g2.setColor(new Color(56, 189, 248));
                g2.setStroke(new BasicStroke(1.0f));
                g2.drawPolygon(wsX, wsY, 4);

                int[] rwsX = {(int)(sw * 0.65), (int)(sw * 0.78), (int)(sw * 0.78), (int)(sw * 0.65)};
                int[] rwsY = {6, 3, sh - 3, sh - 6};
                g2.setColor(new Color(56, 189, 248, 140));
                g2.fillPolygon(rwsX, rwsY, 4);
                g2.drawPolygon(rwsX, rwsY, 4);

                g2.setColor(new Color(203, 213, 225));
                g2.setStroke(new BasicStroke(0.8f));
                g2.drawRoundRect((int)(sw * 0.38), 5, (int)(sw * 0.28), sh - 10, 4, 4);

                int mirrorW = Math.max(3, (int)(sw * 0.04));
                int mirrorH = Math.max(4, (int)(sh * 0.18));
                g2.setColor(new Color(203, 213, 225));
                g2.fillRoundRect(hoodLen + 2, -mirrorH + 1, mirrorW, mirrorH, 2, 2);
                g2.fillRoundRect(hoodLen + 2, sh - 1, mirrorW, mirrorH, 2, 2);

                g2.setColor(new Color(251, 191, 36));
                g2.fillRect(1, 2, 4, 6);
                g2.fillRect(1, sh - 8, 4, 6);

                g2.setColor(new Color(244, 63, 94));
                g2.fillRect(sw - 5, 2, 4, 6);
                g2.fillRect(sw - 5, sh - 8, 4, 6);

            // ─── SUV / 4x4 (Top-Down CAD View) ──────────────────────────
            } else if (type.equals("car-suv")) {
                g2.setColor(new Color(24, 33, 53));
                g2.fillRoundRect(0, 0, sw, sh, 8, 8);
                g2.setColor(new Color(203, 213, 225));
                g2.setStroke(new BasicStroke(1.4f));
                g2.drawRoundRect(0, 0, sw, sh, 8, 8);

                g2.setColor(new Color(100, 116, 139));
                g2.setStroke(new BasicStroke(2.0f));
                g2.drawLine(2, 6, 2, sh - 6);

                int wsStart = (int)(sw * 0.24);
                int wsEnd = (int)(sw * 0.38);
                int[] wsX = {wsStart, wsEnd, wsEnd, wsStart};
                int[] wsY = {3, 5, sh - 5, sh - 3};
                g2.setColor(new Color(56, 189, 248, 140));
                g2.fillPolygon(wsX, wsY, 4);
                g2.setColor(new Color(56, 189, 248));
                g2.setStroke(new BasicStroke(1.0f));
                g2.drawPolygon(wsX, wsY, 4);

                g2.setColor(new Color(56, 189, 248, 60));
                g2.fillRoundRect((int)(sw * 0.42), 6, (int)(sw * 0.32), sh - 12, 4, 4);
                g2.setColor(new Color(56, 189, 248));
                g2.drawRoundRect((int)(sw * 0.42), 6, (int)(sw * 0.32), sh - 12, 4, 4);

                g2.setColor(new Color(148, 163, 184));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawLine((int)(sw * 0.35), 4, (int)(sw * 0.85), 4);
                g2.drawLine((int)(sw * 0.35), sh - 4, (int)(sw * 0.85), sh - 4);

                int rwsStart = (int)(sw * 0.82);
                g2.setColor(new Color(56, 189, 248, 140));
                g2.fillRect(rwsStart, 4, (int)(sw * 0.1), sh - 8);

                g2.setColor(new Color(203, 213, 225));
                g2.fillRoundRect(wsStart + 2, -4, 4, 5, 2, 2);
                g2.fillRoundRect(wsStart + 2, sh - 1, 4, 5, 2, 2);

                g2.setColor(new Color(251, 191, 36));
                g2.fillRect(0, 3, 4, 8);
                g2.fillRect(0, sh - 11, 4, 8);
                g2.setColor(new Color(244, 63, 94));
                g2.fillRect(sw - 4, 3, 4, 8);
                g2.fillRect(sw - 4, sh - 11, 4, 8);

            // ─── MOTORCYCLE (Top-Down CAD View) ─────────────────────────
            } else if (type.equals("bike-motor")) {
                int midY = sh / 2;
                int frontW = Math.max(4, (int)(sw * 0.2));
                int rearW = Math.max(4, (int)(sw * 0.22));

                g2.setColor(new Color(15, 23, 42));
                g2.fillRect(2, midY - 2, frontW, 4);
                g2.setColor(new Color(148, 163, 184));
                g2.drawRect(2, midY - 2, frontW, 4);

                int hbX = 2 + frontW;
                g2.setColor(new Color(203, 213, 225));
                g2.setStroke(new BasicStroke(2.0f));
                g2.drawLine(hbX, 2, hbX, sh - 2);
                g2.setColor(new Color(56, 189, 248));
                g2.fillOval(hbX - 2, 0, 4, 4);
                g2.fillOval(hbX - 2, sh - 4, 4, 4);

                int tankW = (int)(sw * 0.28);
                int tankH = (int)(sh * 0.7);
                g2.setColor(new Color(2, 132, 199));
                g2.fillOval(hbX + 2, midY - tankH / 2, tankW, tankH);
                g2.setColor(new Color(56, 189, 248));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawOval(hbX + 2, midY - tankH / 2, tankW, tankH);
                g2.setColor(Color.WHITE);
                g2.fillOval(hbX + tankW / 2, midY - 2, 4, 4);

                int seatX = hbX + tankW;
                int seatW = (int)(sw * 0.3);
                g2.setColor(new Color(30, 41, 59));
                g2.fillRoundRect(seatX, midY - (int)(sh * 0.25), seatW, (int)(sh * 0.5), 4, 4);
                g2.setColor(new Color(148, 163, 184));
                g2.drawRoundRect(seatX, midY - (int)(sh * 0.25), seatW, (int)(sh * 0.5), 4, 4);

                int rwX = seatX + seatW - 2;
                g2.setColor(new Color(15, 23, 42));
                g2.fillRect(rwX, midY - 3, rearW, 6);
                g2.setColor(new Color(148, 163, 184));
                g2.drawRect(rwX, midY - 3, rearW, 6);

                g2.setColor(new Color(203, 213, 225));
                g2.fillRect(hbX + tankW / 2, sh - 4, (int)(sw * 0.45), 3);

            // ─── SCOOTER / VESPA (Top-Down CAD View) ────────────────────
            } else if (type.equals("bike-scooter")) {
                int midY = sh / 2;

                g2.setColor(new Color(30, 41, 59));
                g2.fillRoundRect(2, midY - 4, (int)(sw * 0.25), 8, 4, 4);
                g2.setColor(new Color(56, 189, 248));
                g2.drawRoundRect(2, midY - 4, (int)(sw * 0.25), 8, 4, 4);

                int hbX = (int)(sw * 0.2);
                g2.setColor(new Color(203, 213, 225));
                g2.setStroke(new BasicStroke(2.0f));
                g2.drawLine(hbX, 3, hbX, sh - 3);

                int fbX = (int)(sw * 0.28);
                int fbW = (int)(sw * 0.25);
                g2.setColor(new Color(15, 23, 42));
                g2.fillRect(fbX, 4, fbW, sh - 8);
                g2.setColor(new Color(71, 85, 105));
                g2.drawRect(fbX, 4, fbW, sh - 8);

                int sX = fbX + fbW;
                int sW = sw - sX - 4;
                g2.setColor(new Color(51, 65, 85));
                g2.fillRoundRect(sX, midY - (int)(sh * 0.38), sW, (int)(sh * 0.76), 8, 8);
                g2.setColor(new Color(203, 213, 225));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(sX, midY - (int)(sh * 0.38), sW, (int)(sh * 0.76), 8, 8);

            // ─── BICYCLE (Top-Down CAD View) ────────────────────────────
            } else if (type.equals("bicycle")) {
                int midY = sh / 2;
                g2.setColor(new Color(56, 189, 248));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawLine((int)(sw * 0.15), midY, (int)(sw * 0.85), midY);

                g2.setColor(new Color(148, 163, 184));
                g2.drawLine(2, midY, (int)(sw * 0.22), midY);
                g2.drawLine((int)(sw * 0.75), midY, sw - 2, midY);

                int hbX = (int)(sw * 0.25);
                g2.setColor(new Color(203, 213, 225));
                g2.setStroke(new BasicStroke(2.0f));
                g2.drawLine(hbX, 2, hbX, sh - 2);

                int pdX = (int)(sw * 0.5);
                g2.drawLine(pdX, midY - 6, pdX, midY + 6);
                g2.fillRect(pdX - 2, midY - 7, 4, 2);
                g2.fillRect(pdX - 2, midY + 5, 4, 2);

                int sadX = (int)(sw * 0.58);
                g2.setColor(new Color(15, 23, 42));
                Polygon sad = new Polygon(new int[]{sadX - 3, sadX + 6, sadX - 3}, new int[]{midY - 4, midY, midY + 4}, 3);
                g2.fillPolygon(sad);
                g2.setColor(new Color(203, 213, 225));
                g2.drawPolygon(sad);

            // ─── 2-LANE ROAD SEGMENT ────────────────────────────────────
            } else if (type.equals("road-segment")) {
                g2.setColor(new Color(15, 23, 42));
                g2.fillRect(0, 0, sw, sh);

                int curbH = Math.max(3, (int)(sh * 0.08));
                g2.setColor(new Color(71, 85, 105));
                g2.fillRect(0, 0, sw, curbH);
                g2.fillRect(0, sh - curbH, sw, curbH);
                g2.setColor(new Color(148, 163, 184));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRect(0, 0, sw, curbH);
                g2.drawRect(0, sh - curbH, sw, curbH);

                g2.setColor(new Color(226, 232, 240));
                g2.setStroke(new BasicStroke(1.0f));
                g2.drawLine(0, curbH + 2, sw, curbH + 2);
                g2.drawLine(0, sh - curbH - 2, sw, sh - curbH - 2);

                g2.setColor(new Color(251, 191, 36));
                g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{12, 10}, 0));
                g2.drawLine(0, sh / 2, sw, sh / 2);

                g2.setColor(new Color(226, 232, 240, 140));
                g2.setStroke(new BasicStroke(1.5f));
                int arrowX = sw / 2;
                int topLaneY = (curbH + sh / 2) / 2;
                int botLaneY = (sh / 2 + sh - curbH) / 2;
                g2.drawLine(arrowX - 12, topLaneY, arrowX + 12, topLaneY);
                g2.drawLine(arrowX + 12, topLaneY, arrowX + 6, topLaneY - 4);
                g2.drawLine(arrowX + 12, topLaneY, arrowX + 6, topLaneY + 4);
                g2.drawLine(arrowX + 12, botLaneY, arrowX - 12, botLaneY);
                g2.drawLine(arrowX - 12, botLaneY, arrowX - 6, botLaneY - 4);
                g2.drawLine(arrowX - 12, botLaneY, arrowX - 6, botLaneY + 4);

            // ─── CAR PARKING BAY ────────────────────────────────────────
            } else if (type.equals("parking-bay")) {
                g2.setColor(new Color(15, 23, 42));
                g2.fillRect(0, 0, sw, sh);

                g2.setColor(new Color(255, 255, 255));
                g2.setStroke(new BasicStroke(2.0f));
                g2.drawLine(0, 0, sw, 0);
                g2.drawLine(0, sh, sw, sh);
                g2.drawLine(0, 0, 0, sh);

                int wbX = (int)(sw * 0.12);
                int wbH = (int)(sh * 0.7);
                g2.setColor(new Color(245, 158, 11));
                g2.fillRoundRect(wbX, (sh - wbH) / 2, 6, wbH, 2, 2);
                g2.setColor(Color.WHITE);
                g2.drawRoundRect(wbX, (sh - wbH) / 2, 6, wbH, 2, 2);

                g2.setColor(new Color(56, 189, 248, 160));
                g2.setFont(new Font("Segoe UI", Font.BOLD, Math.max(12, (int)(16 * zoom))));
                g2.drawString("P", sw / 2 - 4, sh / 2 + 6);

            // ─── STREET LIGHT POLE ──────────────────────────────────────
            } else if (type.equals("streetlight")) {
                int cx = sw / 2, cy = sh / 2;

                g2.setColor(new Color(251, 191, 36, 40));
                g2.fillOval(0, 0, sw, sh);
                g2.setColor(new Color(251, 191, 36, 120));
                g2.setStroke(new BasicStroke(0.8f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{4, 4}, 0));
                g2.drawOval(0, 0, sw, sh);

                g2.setColor(new Color(30, 41, 59));
                g2.fillRect(cx - 5, cy - 5, 10, 10);
                g2.setColor(new Color(203, 213, 225));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRect(cx - 5, cy - 5, 10, 10);

                g2.setStroke(new BasicStroke(2.0f));
                g2.drawLine(cx, cy - 10, cx, cy + 10);

                g2.setColor(new Color(251, 191, 36));
                g2.fillOval(cx - 3, cy - 12, 6, 4);
                g2.fillOval(cx - 3, cy + 8, 6, 4);

            // ─── GARDEN PATHWAY PAVERS ──────────────────────────────────
            } else if (type.equals("pathway")) {
                g2.setColor(new Color(15, 23, 42));
                g2.fillRect(0, 0, sw, sh);
                g2.setColor(new Color(71, 85, 105));
                g2.drawRect(0, 0, sw, sh);

                int pLen = Math.max(14, sw / 6);
                int pH = sh / 2 - 2;
                g2.setColor(new Color(51, 65, 85));
                for (int x = 2; x < sw; x += pLen + 2) {
                    g2.fillRoundRect(x, 2, pLen, pH, 3, 3);
                    g2.fillRoundRect(x - pLen / 2, sh / 2 + 1, pLen, pH, 3, 3);
                    g2.setColor(new Color(148, 163, 184));
                    g2.drawRoundRect(x, 2, pLen, pH, 3, 3);
                    g2.drawRoundRect(x - pLen / 2, sh / 2 + 1, pLen, pH, 3, 3);
                    g2.setColor(new Color(51, 65, 85));
                }

            // ─── BOLLARD LIGHT ──────────────────────────────────────────
            } else if (type.equals("bollard-light")) {
                int cx = sw / 2, cy = sh / 2;
                int r = Math.min(sw, sh) / 2;

                g2.setColor(new Color(251, 191, 36, 45));
                g2.fillOval(0, 0, sw, sh);

                g2.setColor(new Color(30, 41, 59));
                g2.fillOval(cx - r / 2, cy - r / 2, r, r);
                g2.setColor(new Color(203, 213, 225));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawOval(cx - r / 2, cy - r / 2, r, r);

                g2.setColor(new Color(251, 191, 36));
                g2.drawLine(cx - r / 2, cy, cx + r / 2, cy);
                g2.drawLine(cx, cy - r / 2, cx, cy + r / 2);

            // ─── MASTER BEDROOM SUITE (16'×14') ─────────────────────────
            } else if (type.equals("set-bedroom-master")) {
                // Perimeter soft floor / room boundary (dashed)
                g2.setColor(new Color(30, 41, 59, 120));
                g2.fillRoundRect(0, 0, sw, sh, 6, 6);
                g2.setColor(new Color(56, 189, 248, 80));
                g2.setStroke(new BasicStroke(0.8f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{5, 3}, 0));
                g2.drawRoundRect(0, 0, sw, sh, 6, 6);

                // Top: 6-Door Wardrobe Run
                int wardH = Math.max(16, (int)(sh * 0.16));
                g2.setColor(new Color(30, 41, 59));
                g2.fillRect(4, 4, sw - 8, wardH);
                g2.setColor(new Color(203, 213, 225));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRect(4, 4, sw - 8, wardH);
                int doors = 6;
                for (int d = 1; d < doors; d++) {
                    int dx = 4 + d * (sw - 8) / doors;
                    g2.setColor(new Color(100, 116, 139));
                    g2.drawLine(dx, 4, dx, 4 + wardH);
                }
                g2.setColor(new Color(56, 189, 248, 140));
                g2.setFont(new Font("Consolas", Font.BOLD, Math.max(8, (int)(8 * zoom))));
                g2.drawString("FULL WARDROBE RUN", 10, 4 + wardH - 4);

                // Center King Bed with dual nightstands
                int bedW = Math.max(40, (int)(sw * 0.42));
                int bedH = Math.max(48, (int)(sh * 0.52));
                int bedX = (sw - bedW) / 2;
                int bedY = wardH + 14;

                // Nightstands (Left & Right)
                int standW = Math.max(14, (int)(sw * 0.13));
                int standH = Math.max(14, (int)(sh * 0.14));
                int leftStandX = bedX - standW - 6;
                int rightStandX = bedX + bedW + 6;
                g2.setColor(new Color(24, 33, 53));
                g2.fillRoundRect(leftStandX, bedY, standW, standH, 4, 4);
                g2.fillRoundRect(rightStandX, bedY, standW, standH, 4, 4);
                g2.setColor(new Color(203, 213, 225));
                g2.setStroke(new BasicStroke(1.0f));
                g2.drawRoundRect(leftStandX, bedY, standW, standH, 4, 4);
                g2.drawRoundRect(rightStandX, bedY, standW, standH, 4, 4);
                // Lamps
                g2.setColor(new Color(251, 191, 36));
                g2.drawOval(leftStandX + standW / 2 - 3, bedY + standH / 2 - 3, 6, 6);
                g2.drawOval(rightStandX + standW / 2 - 3, bedY + standH / 2 - 3, 6, 6);

                // King Bed Body
                g2.setColor(new Color(30, 41, 59));
                g2.fillRoundRect(bedX, bedY, bedW, bedH, 8, 8);
                g2.setColor(new Color(203, 213, 225));
                g2.setStroke(new BasicStroke(1.4f));
                g2.drawRoundRect(bedX, bedY, bedW, bedH, 8, 8);

                // Headboard
                int hbH = Math.max(8, bedH / 7);
                g2.setColor(new Color(71, 85, 105));
                g2.fillRoundRect(bedX, bedY, bedW, hbH, 4, 4);
                g2.setColor(new Color(203, 213, 225));
                g2.drawRoundRect(bedX, bedY, bedW, hbH, 4, 4);

                // Dual Pillows
                int pw = (bedW - 14) / 2;
                int ph = Math.max(8, (int)(bedH * 0.18));
                int py = bedY + hbH + 4;
                g2.setColor(new Color(226, 232, 240));
                g2.fillRoundRect(bedX + 4, py, pw, ph, 6, 6);
                g2.fillRoundRect(bedX + bedW - pw - 4, py, pw, ph, 6, 6);
                g2.setColor(new Color(148, 163, 184));
                g2.drawRoundRect(bedX + 4, py, pw, ph, 6, 6);
                g2.drawRoundRect(bedX + bedW - pw - 4, py, pw, ph, 6, 6);

                // Duvet throw fold
                int throwY = py + ph + 8;
                g2.setColor(new Color(100, 116, 139));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawLine(bedX + 4, throwY, bedX + bedW - 4, throwY);

                // Bottom Left: Dressing table / vanity with stool
                int dressW = Math.max(26, (int)(sw * 0.26));
                int dressH = Math.max(14, (int)(sh * 0.15));
                int dressY = sh - dressH - 6;
                g2.setColor(new Color(24, 33, 53));
                g2.fillRoundRect(6, dressY, dressW, dressH, 4, 4);
                g2.setColor(new Color(203, 213, 225));
                g2.drawRoundRect(6, dressY, dressW, dressH, 4, 4);
                // Mirror line
                g2.setColor(new Color(56, 189, 248));
                g2.drawLine(10, dressY + 3, 6 + dressW - 4, dressY + 3);
                // Stool
                drawBarStool(g2, 6 + dressW / 2, dressY - 6, Math.max(4, Math.min(7, (int)(6 * zoom))));

                // Bottom Right: Lounge Armchair
                int chairW = Math.max(22, (int)(sw * 0.22));
                int chairH = Math.max(20, (int)(sh * 0.2));
                int chairX = sw - chairW - 8;
                int chairY = sh - chairH - 6;
                g2.setColor(new Color(30, 41, 59));
                g2.fillRoundRect(chairX, chairY, chairW, chairH, 6, 6);
                g2.setColor(new Color(148, 163, 184));
                g2.drawRoundRect(chairX, chairY, chairW, chairH, 6, 6);
                g2.setColor(new Color(56, 189, 248, 100));
                g2.drawRoundRect(chairX + 3, chairY + 3, chairW - 6, chairH - 6, 4, 4);

                // Suite Label
                g2.setColor(new Color(56, 189, 248));
                g2.setFont(new Font("Consolas", Font.BOLD, Math.max(9, (int)(10 * zoom))));
                g2.drawString("MASTER SUITE", bedX + 4, bedY + bedH + 16);

            // ─── DOUBLE BEDROOM SET (14'×12') ───────────────────────────
            } else if (type.equals("set-bedroom-double")) {
                g2.setColor(new Color(30, 41, 59, 120));
                g2.fillRoundRect(0, 0, sw, sh, 6, 6);
                g2.setColor(new Color(56, 189, 248, 80));
                g2.setStroke(new BasicStroke(0.8f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{5, 3}, 0));
                g2.drawRoundRect(0, 0, sw, sh, 6, 6);

                // Top Wardrobe (4-door closet)
                int wardH = Math.max(14, (int)(sh * 0.17));
                g2.setColor(new Color(30, 41, 59));
                g2.fillRect(4, 4, sw - 8, wardH);
                g2.setColor(new Color(203, 213, 225));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRect(4, 4, sw - 8, wardH);
                for (int d = 1; d < 4; d++) {
                    int dx = 4 + d * (sw - 8) / 4;
                    g2.drawLine(dx, 4, dx, 4 + wardH);
                }

                // Queen Bed
                int bedW = Math.max(36, (int)(sw * 0.44));
                int bedH = Math.max(42, (int)(sh * 0.54));
                int bedX = 8 + Math.max(12, (int)(sw * 0.12));
                int bedY = wardH + 12;

                // Bedside table
                int standW = Math.max(12, (int)(sw * 0.12));
                g2.setColor(new Color(24, 33, 53));
                g2.fillRoundRect(bedX - standW - 4, bedY, standW, standW, 4, 4);
                g2.setColor(new Color(203, 213, 225));
                g2.drawRoundRect(bedX - standW - 4, bedY, standW, standW, 4, 4);

                // Bed outline & headboard
                g2.setColor(new Color(30, 41, 59));
                g2.fillRoundRect(bedX, bedY, bedW, bedH, 8, 8);
                g2.setColor(new Color(203, 213, 225));
                g2.drawRoundRect(bedX, bedY, bedW, bedH, 8, 8);
                int hbH = Math.max(6, bedH / 8);
                g2.setColor(new Color(71, 85, 105));
                g2.fillRect(bedX, bedY, bedW, hbH);
                g2.drawRect(bedX, bedY, bedW, hbH);

                // Pillows
                int pw = (bedW - 12) / 2;
                int ph = Math.max(8, (int)(bedH * 0.18));
                g2.setColor(new Color(226, 232, 240));
                g2.fillRoundRect(bedX + 3, bedY + hbH + 3, pw, ph, 4, 4);
                g2.fillRoundRect(bedX + bedW - pw - 3, bedY + hbH + 3, pw, ph, 4, 4);

                // Right: Study desk + chair
                int deskW = Math.max(22, (int)(sw * 0.28));
                int deskH = Math.max(16, (int)(sh * 0.22));
                int deskX = sw - deskW - 6;
                int deskY = bedY + 8;
                g2.setColor(new Color(24, 33, 53));
                g2.fillRoundRect(deskX, deskY, deskW, deskH, 4, 4);
                g2.setColor(new Color(203, 213, 225));
                g2.drawRoundRect(deskX, deskY, deskW, deskH, 4, 4);
                g2.setColor(new Color(56, 189, 248));
                g2.drawRect(deskX + 4, deskY + 3, deskW - 8, deskH / 4);
                // Chair
                drawBarStool(g2, deskX + deskW / 2, deskY + deskH + 8, Math.max(5, (int)(6 * zoom)));

                g2.setColor(new Color(148, 163, 184));
                g2.setFont(new Font("Consolas", Font.BOLD, Math.max(8, (int)(9 * zoom))));
                g2.drawString("DOUBLE BEDROOM", bedX, sh - 8);

            // ─── COMPACT GUEST BED (11'×10') ────────────────────────────
            } else if (type.equals("set-bedroom-compact")) {
                g2.setColor(new Color(30, 41, 59, 120));
                g2.fillRoundRect(0, 0, sw, sh, 6, 6);
                g2.setColor(new Color(56, 189, 248, 80));
                g2.setStroke(new BasicStroke(0.8f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{5, 3}, 0));
                g2.drawRoundRect(0, 0, sw, sh, 6, 6);

                // Double Bed against corner
                int bedW = Math.max(34, (int)(sw * 0.52));
                int bedH = Math.max(42, (int)(sh * 0.65));
                int bedX = 6;
                int bedY = 6;
                g2.setColor(new Color(30, 41, 59));
                g2.fillRoundRect(bedX, bedY, bedW, bedH, 6, 6);
                g2.setColor(new Color(203, 213, 225));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(bedX, bedY, bedW, bedH, 6, 6);
                // Headboard
                int hbH = Math.max(6, bedH / 8);
                g2.setColor(new Color(71, 85, 105));
                g2.fillRect(bedX, bedY, bedW, hbH);
                g2.setColor(new Color(226, 232, 240));
                g2.fillRoundRect(bedX + 4, bedY + hbH + 2, bedW - 8, Math.max(8, bedH / 5), 4, 4);

                // Nightstand next to bed
                int standW = Math.max(10, (int)(sw * 0.16));
                g2.setColor(new Color(24, 33, 53));
                g2.fillRoundRect(bedX + bedW + 4, bedY, standW, standW, 3, 3);
                g2.setColor(new Color(203, 213, 225));
                g2.drawRoundRect(bedX + bedW + 4, bedY, standW, standW, 3, 3);

                // Wardrobe opposite bed
                int wardW = Math.max(16, (int)(sw * 0.35));
                int wardH = Math.max(14, (int)(sh * 0.22));
                int wardX = sw - wardW - 6;
                int wardY = sh - wardH - 6;
                g2.setColor(new Color(30, 41, 59));
                g2.fillRect(wardX, wardY, wardW, wardH);
                g2.setColor(new Color(203, 213, 225));
                g2.drawRect(wardX, wardY, wardW, wardH);
                g2.drawLine(wardX + wardW / 2, wardY, wardX + wardW / 2, wardY + wardH);

                g2.setColor(new Color(148, 163, 184));
                g2.setFont(new Font("Consolas", Font.BOLD, Math.max(8, (int)(8 * zoom))));
                g2.drawString("COMPACT BED", bedX + 4, sh - 8);

            // ─── TWIN KIDS BEDROOM (14'×11') ────────────────────────────
            } else if (type.equals("set-bedroom-twin")) {
                g2.setColor(new Color(30, 41, 59, 120));
                g2.fillRoundRect(0, 0, sw, sh, 6, 6);
                g2.setColor(new Color(56, 189, 248, 80));
                g2.setStroke(new BasicStroke(0.8f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{5, 3}, 0));
                g2.drawRoundRect(0, 0, sw, sh, 6, 6);

                int bedW = Math.max(24, (int)(sw * 0.28));
                int bedH = Math.max(38, (int)(sh * 0.58));
                int bed1X = 8;
                int bed2X = sw - bedW - 8;
                int bedY = 6;

                // Bed 1 (Left)
                g2.setColor(new Color(30, 41, 59));
                g2.fillRoundRect(bed1X, bedY, bedW, bedH, 6, 6);
                g2.setColor(new Color(203, 213, 225));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(bed1X, bedY, bedW, bedH, 6, 6);
                g2.setColor(new Color(71, 85, 105));
                g2.fillRect(bed1X, bedY, bedW, bedH / 8);
                g2.setColor(new Color(226, 232, 240));
                g2.fillRoundRect(bed1X + 3, bedY + bedH / 8 + 3, bedW - 6, bedH / 5, 4, 4);

                // Bed 2 (Right)
                g2.setColor(new Color(30, 41, 59));
                g2.fillRoundRect(bed2X, bedY, bedW, bedH, 6, 6);
                g2.setColor(new Color(203, 213, 225));
                g2.drawRoundRect(bed2X, bedY, bedW, bedH, 6, 6);
                g2.setColor(new Color(71, 85, 105));
                g2.fillRect(bed2X, bedY, bedW, bedH / 8);
                g2.setColor(new Color(226, 232, 240));
                g2.fillRoundRect(bed2X + 3, bedY + bedH / 8 + 3, bedW - 6, bedH / 5, 4, 4);

                // Shared Center Nightstand
                int standW = Math.max(12, (int)(sw * 0.16));
                int standX = (sw - standW) / 2;
                g2.setColor(new Color(24, 33, 53));
                g2.fillRoundRect(standX, bedY, standW, standW, 4, 4);
                g2.setColor(new Color(203, 213, 225));
                g2.drawRoundRect(standX, bedY, standW, standW, 4, 4);
                g2.setColor(new Color(251, 191, 36));
                g2.fillOval(standX + standW / 2 - 3, bedY + standW / 2 - 3, 6, 6);

                // Bottom: Dual study desk run
                int deskH = Math.max(14, (int)(sh * 0.2));
                int deskY = sh - deskH - 6;
                g2.setColor(new Color(24, 33, 53));
                g2.fillRoundRect(8, deskY, sw - 16, deskH, 4, 4);
                g2.setColor(new Color(203, 213, 225));
                g2.drawRoundRect(8, deskY, sw - 16, deskH, 4, 4);
                g2.drawLine(sw / 2, deskY, sw / 2, deskY + deskH);

                g2.setColor(new Color(148, 163, 184));
                g2.setFont(new Font("Consolas", Font.BOLD, Math.max(8, (int)(9 * zoom))));
                g2.drawString("TWIN ROOM", sw / 2 - 24, bedY + bedH / 2);

            // ─── MASTER BATH SUITE (10'×8') ─────────────────────────────
            } else if (type.equals("set-bath-master")) {
                g2.setColor(new Color(15, 23, 42));
                g2.fillRoundRect(0, 0, sw, sh, 6, 6);
                g2.setColor(new Color(56, 189, 248, 120));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(0, 0, sw, sh, 6, 6);

                // Left: Walk-in Shower Stall with Glass Door & Drain
                int showerW = Math.max(26, (int)(sw * 0.36));
                int showerH = Math.max(26, (int)(sh * 0.5));
                g2.setColor(new Color(24, 33, 53));
                g2.fillRect(4, 4, showerW, showerH);
                g2.setColor(new Color(56, 189, 248));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRect(4, 4, showerW, showerH);
                // Glass door swing
                g2.drawArc(4, 4 + showerH - 16, 20, 20, 0, 90);
                // Floor drain
                g2.drawOval(4 + showerW / 2 - 4, 4 + showerH / 2 - 4, 8, 8);

                // Bottom Left: Bathtub
                int tubW = showerW;
                int tubH = sh - showerH - 12;
                int tubY = showerH + 8;
                g2.setColor(new Color(30, 41, 59));
                g2.fillRoundRect(4, tubY, tubW, tubH, 6, 6);
                g2.setColor(new Color(203, 213, 225));
                g2.drawRoundRect(4, tubY, tubW, tubH, 6, 6);
                g2.setColor(new Color(241, 245, 249));
                g2.fillRoundRect(7, tubY + 3, tubW - 6, tubH - 6, 10, 10);
                g2.setColor(new Color(100, 116, 139));
                g2.drawRoundRect(7, tubY + 3, tubW - 6, tubH - 6, 10, 10);

                // Top Right: Double Floating Vanity Sink
                int vanW = sw - showerW - 12;
                int vanH = Math.max(16, (int)(sh * 0.28));
                int vanX = showerW + 8;
                g2.setColor(new Color(30, 41, 59));
                g2.fillRoundRect(vanX, 4, vanW, vanH, 4, 4);
                g2.setColor(new Color(203, 213, 225));
                g2.drawRoundRect(vanX, 4, vanW, vanH, 4, 4);
                // Two basins
                int bW = (vanW - 14) / 2;
                g2.setColor(new Color(241, 245, 249));
                g2.fillOval(vanX + 4, 7, bW, vanH - 8);
                g2.fillOval(vanX + vanW - bW - 4, 7, bW, vanH - 8);
                g2.setColor(new Color(100, 116, 139));
                g2.drawOval(vanX + 4, 7, bW, vanH - 8);
                g2.drawOval(vanX + vanW - bW - 4, 7, bW, vanH - 8);

                // Bottom Right: Toilet WC in alcove
                int wcW = Math.max(14, (int)(sw * 0.2));
                int wcH = Math.max(20, (int)(sh * 0.36));
                int wcX = sw - wcW - 10;
                int wcY = sh - wcH - 6;
                // Tank
                g2.setColor(new Color(226, 232, 240));
                g2.fillRoundRect(wcX, wcY, wcW, wcH / 3, 3, 3);
                g2.setColor(new Color(100, 116, 139));
                g2.drawRoundRect(wcX, wcY, wcW, wcH / 3, 3, 3);
                // Bowl
                g2.setColor(new Color(241, 245, 249));
                g2.fillOval(wcX, wcY + wcH / 4, wcW, (int)(wcH * 0.75));
                g2.setColor(new Color(100, 116, 139));
                g2.drawOval(wcX, wcY + wcH / 4, wcW, (int)(wcH * 0.75));

                g2.setColor(new Color(56, 189, 248));
                g2.setFont(new Font("Consolas", Font.BOLD, Math.max(8, (int)(9 * zoom))));
                g2.drawString("MASTER BATH", vanX + 4, vanH + 18);

            // ─── STANDARD 3-FIXTURE BATH (8'×5') ────────────────────────
            } else if (type.equals("set-bath-standard")) {
                g2.setColor(new Color(15, 23, 42));
                g2.fillRoundRect(0, 0, sw, sh, 4, 4);
                g2.setColor(new Color(56, 189, 248, 120));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(0, 0, sw, sh, 4, 4);

                // Right: Tub/Shower Combo (5' length along right wall)
                int tubW = Math.max(22, (int)(sw * 0.34));
                g2.setColor(new Color(30, 41, 59));
                g2.fillRoundRect(sw - tubW - 4, 4, tubW, sh - 8, 4, 4);
                g2.setColor(new Color(203, 213, 225));
                g2.drawRoundRect(sw - tubW - 4, 4, tubW, sh - 8, 4, 4);
                g2.setColor(new Color(241, 245, 249));
                g2.fillRoundRect(sw - tubW - 2, 6, tubW - 4, sh - 12, 10, 10);
                g2.setColor(new Color(100, 116, 139));
                g2.drawRoundRect(sw - tubW - 2, 6, tubW - 4, sh - 12, 10, 10);
                // Shower curtain rod line
                g2.setColor(new Color(56, 189, 248));
                g2.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{3, 2}, 0));
                g2.drawLine(sw - tubW - 4, 4, sw - tubW - 4, sh - 4);
                g2.setStroke(new BasicStroke(1.0f));

                // Left: Vanity Wash Basin
                int vanW = Math.max(16, (int)(sw * 0.28));
                int vanH = Math.max(14, (int)(sh * 0.4));
                g2.setColor(new Color(30, 41, 59));
                g2.fillRoundRect(4, 4, vanW, vanH, 4, 4);
                g2.setColor(new Color(203, 213, 225));
                g2.drawRoundRect(4, 4, vanW, vanH, 4, 4);
                g2.setColor(new Color(241, 245, 249));
                g2.fillOval(6, 6, vanW - 4, vanH - 6);
                g2.setColor(new Color(100, 116, 139));
                g2.drawOval(6, 6, vanW - 4, vanH - 6);

                // Middle: Toilet WC
                int wcW = Math.max(12, (int)(sw * 0.22));
                int wcH = Math.max(18, (int)(sh * 0.55));
                int wcX = 4 + vanW + 6;
                int wcY = 4;
                g2.setColor(new Color(226, 232, 240));
                g2.fillRoundRect(wcX, wcY, wcW, wcH / 3, 3, 3);
                g2.setColor(new Color(100, 116, 139));
                g2.drawRoundRect(wcX, wcY, wcW, wcH / 3, 3, 3);
                g2.setColor(new Color(241, 245, 249));
                g2.fillOval(wcX, wcY + wcH / 4, wcW, (int)(wcH * 0.75));
                g2.setColor(new Color(100, 116, 139));
                g2.drawOval(wcX, wcY + wcH / 4, wcW, (int)(wcH * 0.75));

                g2.setColor(new Color(148, 163, 184));
                g2.setFont(new Font("Consolas", Font.BOLD, Math.max(8, (int)(8 * zoom))));
                g2.drawString("FULL BATH (8'×5')", 6, sh - 6);

            // ─── POWDER ROOM / HALF BATH (5'×5') ────────────────────────
            } else if (type.equals("set-bath-powder")) {
                g2.setColor(new Color(15, 23, 42));
                g2.fillRoundRect(0, 0, sw, sh, 4, 4);
                g2.setColor(new Color(56, 189, 248, 120));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(0, 0, sw, sh, 4, 4);

                // Top: Vanity Basin
                int vanW = sw - 12;
                int vanH = Math.max(14, (int)(sh * 0.38));
                g2.setColor(new Color(30, 41, 59));
                g2.fillRoundRect(6, 6, vanW, vanH, 4, 4);
                g2.setColor(new Color(203, 213, 225));
                g2.drawRoundRect(6, 6, vanW, vanH, 4, 4);
                g2.setColor(new Color(241, 245, 249));
                g2.fillOval(10, 8, vanW - 8, vanH - 6);
                g2.setColor(new Color(100, 116, 139));
                g2.drawOval(10, 8, vanW - 8, vanH - 6);

                // Bottom: Toilet WC
                int wcW = Math.max(14, (int)(sw * 0.42));
                int wcH = Math.max(18, (int)(sh * 0.48));
                int wcX = (sw - wcW) / 2;
                int wcY = sh - wcH - 6;
                g2.setColor(new Color(226, 232, 240));
                g2.fillRoundRect(wcX, wcY, wcW, wcH / 3, 3, 3);
                g2.setColor(new Color(100, 116, 139));
                g2.drawRoundRect(wcX, wcY, wcW, wcH / 3, 3, 3);
                g2.setColor(new Color(241, 245, 249));
                g2.fillOval(wcX, wcY + wcH / 4, wcW, (int)(wcH * 0.75));
                g2.setColor(new Color(100, 116, 139));
                g2.drawOval(wcX, wcY + wcH / 4, wcW, (int)(wcH * 0.75));

                g2.setColor(new Color(148, 163, 184));
                g2.setFont(new Font("Consolas", Font.BOLD, Math.max(7, (int)(8 * zoom))));
                g2.drawString("POWDER", 8, sh / 2 + 3);

            // ─── SPA LUXURY BATH (12'×10') ──────────────────────────────
            } else if (type.equals("set-bath-luxury")) {
                g2.setColor(new Color(15, 23, 42));
                g2.fillRoundRect(0, 0, sw, sh, 6, 6);
                g2.setColor(new Color(56, 189, 248, 140));
                g2.setStroke(new BasicStroke(1.4f));
                g2.drawRoundRect(0, 0, sw, sh, 6, 6);

                // Center Freestanding Oval Tub
                int tubW = Math.max(34, (int)(sw * 0.42));
                int tubH = Math.max(22, (int)(sh * 0.32));
                int tubX = (sw - tubW) / 2;
                int tubY = (sh - tubH) / 2 + 4;
                g2.setColor(new Color(241, 245, 249));
                g2.fillOval(tubX, tubY, tubW, tubH);
                g2.setColor(new Color(203, 213, 225));
                g2.setStroke(new BasicStroke(1.4f));
                g2.drawOval(tubX, tubY, tubW, tubH);
                g2.setColor(new Color(56, 189, 248, 100));
                g2.fillOval(tubX + 4, tubY + 3, tubW - 8, tubH - 6);

                // Top: Double floating vanity
                int vanH = Math.max(14, (int)(sh * 0.22));
                g2.setColor(new Color(30, 41, 59));
                g2.fillRoundRect(6, 6, sw - 12, vanH, 4, 4);
                g2.setColor(new Color(203, 213, 225));
                g2.drawRoundRect(6, 6, sw - 12, vanH, 4, 4);
                int bW = (sw - 28) / 2;
                g2.setColor(new Color(241, 245, 249));
                g2.fillOval(10, 8, bW, vanH - 6);
                g2.fillOval(sw - bW - 10, 8, bW, vanH - 6);
                g2.setColor(new Color(100, 116, 139));
                g2.drawOval(10, 8, bW, vanH - 6);
                g2.drawOval(sw - bW - 10, 8, bW, vanH - 6);

                // Bottom Left: Walk-in rain shower
                int shwW = Math.max(24, (int)(sw * 0.34));
                int shwH = Math.max(22, (int)(sh * 0.34));
                g2.setColor(new Color(24, 33, 53));
                g2.fillRect(6, sh - shwH - 6, shwW, shwH);
                g2.setColor(new Color(56, 189, 248));
                g2.drawRect(6, sh - shwH - 6, shwW, shwH);
                g2.drawOval(6 + shwW / 2 - 4, sh - shwH / 2 - 6 - 4, 8, 8);

                // Bottom Right: Enclosed WC cubicle
                int wcCubeW = Math.max(22, (int)(sw * 0.32));
                int wcCubeH = shwH;
                int wcCubeX = sw - wcCubeW - 6;
                int wcCubeY = sh - wcCubeH - 6;
                g2.setColor(new Color(30, 41, 59));
                g2.fillRect(wcCubeX, wcCubeY, wcCubeW, wcCubeH);
                g2.setColor(new Color(100, 116, 139));
                g2.drawRect(wcCubeX, wcCubeY, wcCubeW, wcCubeH);
                // Toilet in cubicle
                int tw = wcCubeW / 2;
                g2.setColor(new Color(241, 245, 249));
                g2.fillOval(wcCubeX + (wcCubeW - tw) / 2, wcCubeY + wcCubeH / 4, tw, (int)(wcCubeH * 0.65));
                g2.setColor(new Color(100, 116, 139));
                g2.drawOval(wcCubeX + (wcCubeW - tw) / 2, wcCubeY + wcCubeH / 4, tw, (int)(wcCubeH * 0.65));

                g2.setColor(new Color(56, 189, 248));
                g2.setFont(new Font("Consolas", Font.BOLD, Math.max(8, (int)(10 * zoom))));
                g2.drawString("SPA LUXURY BATH", tubX + 4, tubY + tubH + 12);

            // ─── LIVING ROOM SUITE (16'×12') ────────────────────────────
            } else if (type.equals("set-living-full")) {
                // Room carpet rug boundary
                g2.setColor(new Color(30, 41, 59, 100));
                g2.fillRoundRect(0, 0, sw, sh, 6, 6);
                g2.setColor(new Color(56, 189, 248, 80));
                g2.setStroke(new BasicStroke(0.8f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{5, 3}, 0));
                g2.drawRoundRect(0, 0, sw, sh, 6, 6);

                // Top: TV Media Console Unit
                int tvW = Math.max(36, (int)(sw * 0.55));
                int tvH = Math.max(10, (int)(sh * 0.12));
                int tvX = (sw - tvW) / 2;
                g2.setColor(new Color(24, 33, 53));
                g2.fillRoundRect(tvX, 4, tvW, tvH, 4, 4);
                g2.setColor(new Color(203, 213, 225));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(tvX, 4, tvW, tvH, 4, 4);
                // TV Screen
                int scrW = (int)(tvW * 0.7);
                g2.setColor(new Color(56, 189, 248, 140));
                g2.fillRect(tvX + (tvW - scrW) / 2, 2, scrW, tvH / 2);
                g2.setColor(new Color(56, 189, 248));
                g2.drawRect(tvX + (tvW - scrW) / 2, 2, scrW, tvH / 2);

                // Center Area Rug
                int rugW = Math.max(48, (int)(sw * 0.65));
                int rugH = Math.max(36, (int)(sh * 0.48));
                int rugX = (sw - rugW) / 2;
                int rugY = tvH + 14;
                g2.setColor(new Color(15, 23, 42));
                g2.fillRoundRect(rugX, rugY, rugW, rugH, 6, 6);
                g2.setColor(new Color(100, 116, 139, 140));
                g2.setStroke(new BasicStroke(0.8f));
                g2.drawRoundRect(rugX, rugY, rugW, rugH, 6, 6);
                // Coffee Table in center
                int tblW = Math.max(22, (int)(rugW * 0.42));
                int tblH = Math.max(14, (int)(rugH * 0.35));
                int tblX = rugX + (rugW - tblW) / 2;
                int tblY = rugY + (rugH - tblH) / 2;
                g2.setColor(new Color(30, 41, 59));
                g2.fillRoundRect(tblX, tblY, tblW, tblH, 4, 4);
                g2.setColor(new Color(203, 213, 225));
                g2.drawRoundRect(tblX, tblY, tblW, tblH, 4, 4);

                // Bottom: 3-Seater Main Sofa
                int sofaW = Math.max(46, (int)(sw * 0.65));
                int sofaH = Math.max(18, (int)(sh * 0.24));
                int sofaX = (sw - sofaW) / 2;
                int sofaY = sh - sofaH - 6;
                g2.setColor(new Color(30, 41, 59));
                g2.fillRoundRect(sofaX, sofaY, sofaW, sofaH, 8, 8);
                g2.setColor(new Color(203, 213, 225));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(sofaX, sofaY, sofaW, sofaH, 8, 8);
                // 3 Cushions
                int cW = (sofaW - 12) / 3;
                for (int c = 0; c < 3; c++) {
                    g2.setColor(new Color(56, 189, 248, 120));
                    g2.drawRoundRect(sofaX + 4 + c * cW, sofaY + 4, cW - 2, sofaH - 8, 4, 4);
                }

                // Left & Right: Accent Armchairs
                int armW = Math.max(16, (int)(sw * 0.16));
                int armH = Math.max(18, (int)(sh * 0.22));
                // Left
                g2.setColor(new Color(30, 41, 59));
                g2.fillRoundRect(6, rugY + 6, armW, armH, 6, 6);
                g2.setColor(new Color(148, 163, 184));
                g2.drawRoundRect(6, rugY + 6, armW, armH, 6, 6);
                // Right
                g2.setColor(new Color(30, 41, 59));
                g2.fillRoundRect(sw - armW - 6, rugY + 6, armW, armH, 6, 6);
                g2.setColor(new Color(148, 163, 184));
                g2.drawRoundRect(sw - armW - 6, rugY + 6, armW, armH, 6, 6);

                g2.setColor(new Color(56, 189, 248));
                g2.setFont(new Font("Consolas", Font.BOLD, Math.max(8, (int)(9 * zoom))));
                g2.drawString("LIVING ROOM SUITE", sofaX + 6, sofaY - 4);

            // ─── DINING ROOM SET (12'×10') ──────────────────────────────
            } else if (type.equals("set-dining-full")) {
                g2.setColor(new Color(30, 41, 59, 100));
                g2.fillRoundRect(0, 0, sw, sh, 6, 6);
                g2.setColor(new Color(56, 189, 248, 80));
                g2.setStroke(new BasicStroke(0.8f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{5, 3}, 0));
                g2.drawRoundRect(0, 0, sw, sh, 6, 6);

                // Top: Sideboard / Credenza Buffet
                int buffW = Math.max(36, (int)(sw * 0.6));
                int buffH = Math.max(10, (int)(sh * 0.14));
                int buffX = (sw - buffW) / 2;
                g2.setColor(new Color(24, 33, 53));
                g2.fillRoundRect(buffX, 4, buffW, buffH, 4, 4);
                g2.setColor(new Color(203, 213, 225));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(buffX, 4, buffW, buffH, 4, 4);
                g2.drawLine(buffX + buffW / 3, 4, buffX + buffW / 3, 4 + buffH);
                g2.drawLine(buffX + 2 * buffW / 3, 4, buffX + 2 * buffW / 3, 4 + buffH);

                // Center Dining Table (6/8 Seater)
                int tblW = Math.max(40, (int)(sw * 0.58));
                int tblH = Math.max(26, (int)(sh * 0.42));
                int tblX = (sw - tblW) / 2;
                int tblY = (sh - tblH) / 2 + 6;
                g2.setColor(new Color(30, 41, 59));
                g2.fillRoundRect(tblX, tblY, tblW, tblH, 8, 8);
                g2.setColor(new Color(203, 213, 225));
                g2.setStroke(new BasicStroke(1.4f));
                g2.drawRoundRect(tblX, tblY, tblW, tblH, 8, 8);
                // Center inlay runner
                g2.setColor(new Color(56, 189, 248, 90));
                g2.drawRoundRect(tblX + 6, tblY + 4, tblW - 12, tblH - 8, 4, 4);

                // Chairs Top & Bottom
                int chairW = Math.max(10, tblW / 4);
                int chairD = Math.max(8, (int)(sh * 0.12));
                for (int i = 0; i < 3; i++) {
                    int cx = tblX + 4 + i * (tblW - 8) / 3;
                    // Top chairs
                    g2.setColor(new Color(24, 33, 53));
                    g2.fillRoundRect(cx, tblY - chairD - 2, chairW, chairD, 3, 3);
                    g2.setColor(new Color(148, 163, 184));
                    g2.drawRoundRect(cx, tblY - chairD - 2, chairW, chairD, 3, 3);
                    // Bottom chairs
                    g2.setColor(new Color(24, 33, 53));
                    g2.fillRoundRect(cx, tblY + tblH + 2, chairW, chairD, 3, 3);
                    g2.setColor(new Color(148, 163, 184));
                    g2.drawRoundRect(cx, tblY + tblH + 2, chairW, chairD, 3, 3);
                }

                // Captain chairs (Left & Right ends)
                int capW = Math.max(8, (int)(sw * 0.08));
                int capH = Math.max(14, tblH / 2);
                g2.setColor(new Color(24, 33, 53));
                g2.fillRoundRect(tblX - capW - 2, tblY + (tblH - capH) / 2, capW, capH, 3, 3);
                g2.fillRoundRect(tblX + tblW + 2, tblY + (tblH - capH) / 2, capW, capH, 3, 3);
                g2.setColor(new Color(148, 163, 184));
                g2.drawRoundRect(tblX - capW - 2, tblY + (tblH - capH) / 2, capW, capH, 3, 3);
                g2.drawRoundRect(tblX + tblW + 2, tblY + (tblH - capH) / 2, capW, capH, 3, 3);

                g2.setColor(new Color(56, 189, 248));
                g2.setFont(new Font("Consolas", Font.BOLD, Math.max(8, (int)(9 * zoom))));
                g2.drawString("DINING SUITE", tblX + 8, tblY + tblH / 2 + 3);
            }
        }

        private void drawBurnerGrate(Graphics2D g2, int cx, int cy, int r) {
            // Outer ring
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawOval(cx - r / 2, cy - r / 2, r, r);
            // Inner ring
            g2.drawOval(cx - r / 4, cy - r / 4, r / 2, r / 2);
            // Grate cross
            g2.setStroke(new BasicStroke(0.8f));
            g2.drawLine(cx - r / 2, cy, cx + r / 2, cy);
            g2.drawLine(cx, cy - r / 2, cx, cy + r / 2);
        }

        private void drawBarStool(Graphics2D g2, int cx, int cy, int r) {
            // Chrome base ring
            g2.setColor(new Color(100, 116, 139));
            g2.setStroke(new BasicStroke(1.0f));
            g2.drawOval(cx - r, cy - r, r * 2, r * 2);
            // Seat cushion
            g2.setColor(new Color(30, 41, 59));
            g2.fillOval(cx - r + 2, cy - r + 2, (r - 2) * 2, (r - 2) * 2);
            g2.setColor(new Color(56, 189, 248));
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawOval(cx - r + 2, cy - r + 2, (r - 2) * 2, (r - 2) * 2);
            // Center tuft / cross
            g2.fillOval(cx - 2, cy - 2, 4, 4);
        }

        private void drawUndermountSink(Graphics2D g2, int x, int y, int w, int h) {
            g2.setColor(new Color(15, 23, 42));
            g2.fillRoundRect(x, y, w, h, 4, 4);
            g2.setColor(new Color(100, 116, 139));
            g2.setStroke(new BasicStroke(1.0f));
            g2.drawRoundRect(x, y, w, h, 4, 4);

            int bw = Math.max(4, (w - 6) / 2);
            g2.setColor(new Color(226, 232, 240));
            g2.setStroke(new BasicStroke(1.0f));
            g2.drawRoundRect(x + 2, y + 2, bw, Math.max(4, h - 4), 3, 3);
            g2.drawRoundRect(x + w - bw - 2, y + 2, bw, Math.max(4, h - 4), 3, 3);

            g2.setColor(new Color(56, 189, 248));
            g2.fillOval(x + 2 + bw / 2 - 2, y + h / 2 - 2, 4, 4);
            g2.fillOval(x + w - bw - 2 + bw / 2 - 2, y + h / 2 - 2, 4, 4);

            g2.setStroke(new BasicStroke(1.5f));
            g2.drawLine(x + w / 2, y - 2, x + w / 2, y + 6);
            g2.drawArc(x + w / 2 - 3, y - 3, 6, 6, 0, 180);
        }

        private void drawCooktop4Burner(Graphics2D g2, int x, int y, int w, int h) {
            g2.setColor(new Color(15, 23, 42));
            g2.fillRoundRect(x, y, w, h, 4, 4);
            g2.setColor(new Color(203, 213, 225));
            g2.setStroke(new BasicStroke(1.0f));
            g2.drawRoundRect(x, y, w, h, 4, 4);

            int b1x = x + w / 4, b2x = x + 3 * w / 4;
            int b1y = y + h / 4, b2y = y + 3 * h / 4;
            int br = Math.max(5, Math.min(w, h) / 4);

            g2.setColor(new Color(245, 158, 11));
            drawBurnerGrate(g2, b1x, b1y, br);
            drawBurnerGrate(g2, b2x, b1y, br);
            drawBurnerGrate(g2, b1x, b2y, br);
            drawBurnerGrate(g2, b2x, b2y, br);

            g2.setColor(new Color(148, 163, 184));
            for (int k = 0; k < 4; k++) {
                g2.fillOval(x + 4 + k * (w - 8) / 3, y + h - 4, 3, 3);
            }
        }

        // --- Dimensions ---

        private void drawDimensions(Graphics2D g2) {
            for (CADDimension d : dimensions) {
                Point p1 = worldToScreen(d.x1, d.y1);
                Point p2 = worldToScreen(d.x2, d.y2);
                boolean isSel = isSelected(d);
                drawCADDimension(g2, p1.x, p1.y, p2.x, p2.y,
                        formatImperial(Point2D.distance(d.x1, d.y1, d.x2, d.y2)), (int) Math.round(d.offset), isSel);
            }
        }

        private void drawCADDimension(Graphics2D g2, int p1x, int p1y, int p2x, int p2y, String text, int offset) {
            drawCADDimension(g2, p1x, p1y, p2x, p2y, text, offset, false);
        }

        private void drawCADDimension(Graphics2D g2, int p1x, int p1y, int p2x, int p2y, String text, int offset, boolean isSel) {
            double dx = p2x - p1x, dy = p2y - p1y;
            double len = Math.hypot(dx, dy);
            if (len < 6) return;
            double perpX = (-dy / len) * offset, perpY = (dx / len) * offset;
            double d1x = p1x + perpX, d1y = p1y + perpY;
            double d2x = p2x + perpX, d2y = p2y + perpY;

            double extX = (offset != 0) ? (perpX / offset) * 3.5 : 0;
            double extY = (offset != 0) ? (perpY / offset) * 3.5 : 0;

            Color dimColor = isSel ? new Color(56, 189, 248) : new Color(56, 189, 248, 175);
            g2.setColor(dimColor);
            g2.setStroke(isSel ? new BasicStroke(1.5f) : new BasicStroke(0.65f));
            g2.draw(new Line2D.Double(p1x, p1y, d1x + extX, d1y + extY));
            g2.draw(new Line2D.Double(p2x, p2y, d2x + extX, d2y + extY));
            g2.draw(new Line2D.Double(d1x, d1y, d2x, d2y));

            double tick = 3.5;
            g2.setStroke(new BasicStroke(isSel ? 1.6f : 0.85f));
            g2.draw(new Line2D.Double(d1x - tick, d1y - tick, d1x + tick, d1y + tick));
            g2.draw(new Line2D.Double(d2x - tick, d2y - tick, d2x + tick, d2y + tick));

            if (isSel) {
                // Grips on dimension points and text center
                drawGrip(g2, (int) Math.round(d1x), (int) Math.round(d1y));
                drawGrip(g2, (int) Math.round(d2x), (int) Math.round(d2y));
                drawGrip(g2, p1x, p1y);
                drawGrip(g2, p2x, p2y);
            }

            g2.setFont(new Font("Consolas", Font.BOLD, 10));
            FontMetrics fm = g2.getFontMetrics();
            int tw = fm.stringWidth(text);
            int midX = (int)((d1x + d2x) / 2), midY = (int)((d1y + d2y) / 2);
            g2.setColor(new Color(15, 23, 42));
            g2.fillRect(midX - tw / 2 - 4, midY - 7, tw + 8, 14);
            g2.setColor(isSel ? new Color(251, 191, 36) : new Color(56, 189, 248));
            g2.drawRect(midX - tw / 2 - 4, midY - 7, tw + 8, 14);
            g2.drawString(text, midX - tw / 2, midY + 4);
            if (isSel) {
                drawGrip(g2, midX, midY);
            }
        }

        // --- Dynamic Dimensions (Live Spacing & Clearance) ---

        private void drawDynamicDimensions(Graphics2D g2) {
            if (!(primarySelected instanceof CADElement) || selectedObjects.size() != 1) return;
            CADElement el = (CADElement) primarySelected;
            if (el.isOpening()) return;

            double elX1 = el.x, elX2 = el.x + el.w;
            double elY1 = el.y, elY2 = el.y + el.h;
            double elCX = el.x + el.w / 2.0, elCY = el.y + el.h / 2.0;

            // 1. Find closest walls in 4 orthogonal directions
            double minLeft = Double.MAX_VALUE, leftWallX = 0;
            double minRight = Double.MAX_VALUE, rightWallX = 0;
            double minTop = Double.MAX_VALUE, topWallY = 0;
            double minBottom = Double.MAX_VALUE, bottomWallY = 0;

            for (Wall w : walls) {
                double wxMin = Math.min(w.x1, w.x2), wxMax = Math.max(w.x1, w.x2);
                double wyMin = Math.min(w.y1, w.y2), wyMax = Math.max(w.y1, w.y2);
                boolean isVert = Math.abs(w.x2 - w.x1) < Math.abs(w.y2 - w.y1);

                if (isVert) {
                    double wx = (w.x1 + w.x2) / 2.0;
                    if (elCY >= wyMin - 1.5 * FT_PX && elCY <= wyMax + 1.5 * FT_PX) {
                        if (wx <= elX1 && (elX1 - wx) < minLeft) {
                            minLeft = elX1 - wx;
                            leftWallX = wx;
                        } else if (wx >= elX2 && (wx - elX2) < minRight) {
                            minRight = wx - elX2;
                            rightWallX = wx;
                        }
                    }
                } else {
                    double wy = (w.y1 + w.y2) / 2.0;
                    if (elCX >= wxMin - 1.5 * FT_PX && elCX <= wxMax + 1.5 * FT_PX) {
                        if (wy <= elY1 && (elY1 - wy) < minTop) {
                            minTop = elY1 - wy;
                            topWallY = wy;
                        } else if (wy >= elY2 && (wy - elY2) < minBottom) {
                            minBottom = wy - elY2;
                            bottomWallY = wy;
                        }
                    }
                }
            }

            Color wallDimColor = new Color(56, 189, 248); // Cyan
            double maxSearchDist = 40.0 * FT_PX;

            // Left Wall Dimension
            if (minLeft < maxSearchDist) {
                Point pWall = worldToScreen(leftWallX, elCY);
                Point pEl = worldToScreen(elX1, elCY);
                drawDynamicDimLine(g2, pWall.x, pWall.y, pEl.x, pEl.y, formatImperial(minLeft), wallDimColor);
            }

            // Right Wall Dimension
            if (minRight < maxSearchDist) {
                Point pEl = worldToScreen(elX2, elCY);
                Point pWall = worldToScreen(rightWallX, elCY);
                drawDynamicDimLine(g2, pEl.x, pEl.y, pWall.x, pWall.y, formatImperial(minRight), wallDimColor);
            }

            // Top Wall Dimension
            if (minTop < maxSearchDist) {
                Point pWall = worldToScreen(elCX, topWallY);
                Point pEl = worldToScreen(elCX, elY1);
                drawDynamicDimLine(g2, pWall.x, pWall.y, pEl.x, pEl.y, formatImperial(minTop), wallDimColor);
            }

            // Bottom Wall Dimension
            if (minBottom < maxSearchDist) {
                Point pEl = worldToScreen(elCX, elY2);
                Point pWall = worldToScreen(elCX, bottomWallY);
                drawDynamicDimLine(g2, pEl.x, pEl.y, pWall.x, pWall.y, formatImperial(minBottom), wallDimColor);
            }

            // 2. Find closest neighboring interior object/element
            CADElement closestOther = null;
            double minOtherDist = Double.MAX_VALUE;
            for (CADElement other : elements) {
                if (other == el || other.isOpening()) continue;
                double ocx = other.x + other.w / 2.0;
                double ocy = other.y + other.h / 2.0;
                double d = Point2D.distance(elCX, elCY, ocx, ocy);
                if (d < minOtherDist && d < 25.0 * FT_PX) {
                    minOtherDist = d;
                    closestOther = other;
                }
            }

            if (closestOther != null) {
                double oX1 = closestOther.x, oX2 = closestOther.x + closestOther.w;
                double oY1 = closestOther.y, oY2 = closestOther.y + closestOther.h;

                double nearX1 = Math.max(elX1, Math.min(elX2, (oX1 + oX2) / 2.0));
                double nearY1 = Math.max(elY1, Math.min(elY2, (oY1 + oY2) / 2.0));
                double nearX2 = Math.max(oX1, Math.min(oX2, elCX));
                double nearY2 = Math.max(oY1, Math.min(oY2, elCY));

                double clearDist = Point2D.distance(nearX1, nearY1, nearX2, nearY2);
                if (clearDist > 4.0) {
                    Point p1 = worldToScreen(nearX1, nearY1);
                    Point p2 = worldToScreen(nearX2, nearY2);
                    drawDynamicDimLine(g2, p1.x, p1.y, p2.x, p2.y, "GAP: " + formatImperial(clearDist), new Color(245, 158, 11));
                }
            }
        }

        private void drawDynamicDimLine(Graphics2D g2, int x1, int y1, int x2, int y2, String label, Color color) {
            double dist = Math.hypot(x2 - x1, y2 - y1);
            if (dist < 8) return;

            g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 210));
            g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{4, 3}, 0));
            g2.drawLine(x1, y1, x2, y2);

            double dx = x2 - x1, dy = y2 - y1;
            double len = Math.hypot(dx, dy);
            double px = (-dy / len) * 4.0, py = (dx / len) * 4.0;
            g2.setStroke(new BasicStroke(1.4f));
            g2.drawLine((int) Math.round(x1 - px), (int) Math.round(y1 - py), (int) Math.round(x1 + px), (int) Math.round(y1 + py));
            g2.drawLine((int) Math.round(x2 - px), (int) Math.round(y2 - py), (int) Math.round(x2 + px), (int) Math.round(y2 + py));

            int midX = (x1 + x2) / 2;
            int midY = (y1 + y2) / 2;
            g2.setFont(new Font("Consolas", Font.BOLD, 10));
            FontMetrics fm = g2.getFontMetrics();
            int tw = fm.stringWidth(label);
            g2.setColor(new Color(15, 23, 42, 235));
            g2.fillRoundRect(midX - tw / 2 - 4, midY - 7, tw + 8, 14, 4, 4);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(1.0f));
            g2.drawRoundRect(midX - tw / 2 - 4, midY - 7, tw + 8, 14, 4, 4);
            g2.drawString(label, midX - tw / 2, midY + 4);
        }

        // --- Selection Rectangle (AutoCAD Window/Crossing) ---

        private void drawSelectionRectangle(Graphics2D g2) {
            if (!isWindowSelecting || selRectStart == null) return;

            Point sp1 = worldToScreen(selRectStart.x, selRectStart.y);
            Point sp2 = mouseScreen;

            int rx = Math.min(sp1.x, sp2.x), ry = Math.min(sp1.y, sp2.y);
            int rw = Math.abs(sp2.x - sp1.x), rh = Math.abs(sp2.y - sp1.y);

            boolean isWindowMode = mouseWorld.x > selRectStart.x; // L→R = window select

            if (isWindowMode) {
                // Window select: solid blue border, light blue fill
                g2.setColor(new Color(59, 130, 246, 30));
                g2.fillRect(rx, ry, rw, rh);
                g2.setColor(new Color(59, 130, 246, 180));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRect(rx, ry, rw, rh);
            } else {
                // Crossing select: dashed green border, light green fill
                g2.setColor(new Color(74, 222, 128, 25));
                g2.fillRect(rx, ry, rw, rh);
                g2.setColor(new Color(74, 222, 128, 200));
                g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{6, 4}, 0));
                g2.drawRect(rx, ry, rw, rh);
            }

            // Selection count preview
            List<Object> preview = hitTestRect(selRectStart.x, selRectStart.y, mouseWorld.x, mouseWorld.y, isWindowMode);
            if (!preview.isEmpty()) {
                String label = preview.size() + " object" + (preview.size() > 1 ? "s" : "");
                g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
                FontMetrics fm = g2.getFontMetrics();
                int lw = fm.stringWidth(label) + 10;
                g2.setColor(new Color(15, 23, 42, 200));
                g2.fillRoundRect(sp2.x + 12, sp2.y - 10, lw, 18, 6, 6);
                g2.setColor(isWindowMode ? new Color(96, 165, 250) : new Color(74, 222, 128));
                g2.drawString(label, sp2.x + 17, sp2.y + 3);
            }
        }

        // --- Active Tools & HUD ---

        private void drawActiveTools(Graphics2D g2) {
            // Wall drawing preview
            if (isDrawing && currentTool == ToolMode.DRAW_WALL) {
                Point p1 = worldToScreen(drawStart.x, drawStart.y);
                Point2D.Double p2World = getConstrainedEndPoint(drawStart, mouseWorld);
                Point p2 = worldToScreen(p2World.x, p2World.y);
                float thickPx = (float) ((wallThicknessInches / 12.0) * FT_PX * zoom);
                g2.setColor(new Color(56, 189, 248, 160));
                g2.setStroke(new BasicStroke(thickPx));
                g2.drawLine(p1.x, p1.y, p2.x, p2.y);
                drawCADDimension(g2, p1.x, p1.y, p2.x, p2.y, formatImperial(drawStart.distance(p2World)), 24);
            }


            // Room box preview
            if (isDrawing && currentTool == ToolMode.ROOM_BOX) {
                Point p1 = worldToScreen(drawStart.x, drawStart.y);
                Point2D.Double p2World = getConstrainedEndPoint(drawStart, mouseWorld);
                Point p2 = worldToScreen(p2World.x, p2World.y);
                int rx = Math.min(p1.x, p2.x), ry = Math.min(p1.y, p2.y);
                int rw = Math.abs(p2.x - p1.x), rh = Math.abs(p2.y - p1.y);
                g2.setColor(new Color(56, 189, 248, 60));
                g2.fillRect(rx, ry, rw, rh);
                g2.setColor(new Color(56, 189, 248, 180));
                g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{6, 4}, 0));
                g2.drawRect(rx, ry, rw, rh);
                // Dimensions
                drawCADDimension(g2, rx, ry, rx + rw, ry, formatImperial(Math.abs(p2World.x - drawStart.x)), -18);
                drawCADDimension(g2, rx + rw, ry, rx + rw, ry + rh, formatImperial(Math.abs(p2World.y - drawStart.y)), 18);
            }

            // Floor Zone / Tile Finish preview
            if (isDrawing && currentTool == ToolMode.FLOOR_ZONE) {
                Point p1 = worldToScreen(drawStart.x, drawStart.y);
                Point2D.Double p2World = getConstrainedEndPoint(drawStart, mouseWorld);
                Point p2 = worldToScreen(p2World.x, p2World.y);
                int rx = Math.min(p1.x, p2.x), ry = Math.min(p1.y, p2.y);
                int rw = Math.abs(p2.x - p1.x), rh = Math.abs(p2.y - p1.y);
                g2.setColor(new Color(245, 158, 11, 40));
                g2.fillRect(rx, ry, rw, rh);
                g2.setColor(new Color(245, 158, 11, 200));
                g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{4, 4}, 0));
                g2.drawRect(rx, ry, rw, rh);
                // Tile grid indicator inside drag
                if (rw > 20 && rh > 20) {
                    g2.setColor(new Color(245, 158, 11, 80));
                    g2.setStroke(new BasicStroke(0.8f));
                    int step = (int) Math.max(12, (2.0 * FT_PX) * zoom);
                    for (int x = rx + step; x < rx + rw; x += step) g2.drawLine(x, ry, x, ry + rh);
                    for (int y = ry + step; y < ry + rh; y += step) g2.drawLine(rx, y, rx + rw, y);
                }
                // Dimensions & Badge
                drawCADDimension(g2, rx, ry, rx + rw, ry, formatImperial(Math.abs(p2World.x - drawStart.x)), -18);
                drawCADDimension(g2, rx + rw, ry, rx + rw, ry + rh, formatImperial(Math.abs(p2World.y - drawStart.y)), 18);
                g2.setColor(new Color(15, 23, 42, 220));
                g2.fillRoundRect(rx + 8, ry + 8, 110, 20, 6, 6);
                g2.setColor(new Color(245, 158, 11));
                g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
                g2.drawString("\u25a6 FLOOR / TILES", rx + 14, ry + 22);
            }

            // AutoCAD Hatch Perimeter Tool (Live Polyline Preview)
            if (currentTool == ToolMode.HATCH_POLYGON) {
                Point2D.Double snapPt = getOsnapPoint(mouseWorld.x, mouseWorld.y);
                Point2D.Double curPt = (snapPt != null) ? snapPt : new Point2D.Double(snapCoord(mouseWorld.x), snapCoord(mouseWorld.y));
                Point curScreen = worldToScreen(curPt.x, curPt.y);

                if (!currentHatchPoly.isEmpty()) {
                    Path2D.Double poly = new Path2D.Double();
                    Point first = worldToScreen(currentHatchPoly.get(0).x, currentHatchPoly.get(0).y);
                    poly.moveTo(first.x, first.y);

                    for (int i = 1; i < currentHatchPoly.size(); i++) {
                        Point pi = worldToScreen(currentHatchPoly.get(i).x, currentHatchPoly.get(i).y);
                        poly.lineTo(pi.x, pi.y);
                    }

                    // Line to cursor
                    poly.lineTo(curScreen.x, curScreen.y);

                    // Translucent fill preview if at least 2 vertices exist
                    if (currentHatchPoly.size() >= 2) {
                        Path2D.Double fillPoly = (Path2D.Double) poly.clone();
                        fillPoly.closePath();
                        g2.setColor(new Color(245, 158, 11, 45));
                        g2.fill(fillPoly);
                    }

                    // Polyline stroke
                    g2.setColor(new Color(245, 158, 11, 220));
                    g2.setStroke(new BasicStroke(1.8f));
                    g2.draw(poly);

                    // Closing guide line back to first vertex (dashed)
                    g2.setColor(new Color(245, 158, 11, 140));
                    g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{4, 4}, 0));
                    g2.drawLine(curScreen.x, curScreen.y, first.x, first.y);

                    // Vertex handles
                    for (int i = 0; i < currentHatchPoly.size(); i++) {
                        Point pi = worldToScreen(currentHatchPoly.get(i).x, currentHatchPoly.get(i).y);
                        g2.setColor(i == 0 ? new Color(52, 211, 153) : new Color(245, 158, 11));
                        g2.fillRect(pi.x - 4, pi.y - 4, 8, 8);
                        g2.setColor(Color.WHITE);
                        g2.drawRect(pi.x - 4, pi.y - 4, 8, 8);
                    }
                }

                // Cursor reticle and HUD instruction badge
                g2.setColor(new Color(245, 158, 11));
                g2.drawOval(curScreen.x - 6, curScreen.y - 6, 12, 12);
                g2.drawLine(curScreen.x - 9, curScreen.y, curScreen.x + 9, curScreen.y);
                g2.drawLine(curScreen.x, curScreen.y - 9, curScreen.x, curScreen.y + 9);

                String badge = (currentHatchPoly.isEmpty())
                        ? "HATCH: Click 1st corner of room perimeter"
                        : "HATCH (" + currentHatchPoly.size() + " pts): Click next corner \u00b7 Dbl-Click / Enter to finish \u00b7 Esc to cancel";
                g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
                FontMetrics fm = g2.getFontMetrics();
                int bw = fm.stringWidth(badge) + 16;
                g2.setColor(new Color(15, 23, 42, 230));
                g2.fillRoundRect(curScreen.x + 14, curScreen.y - 12, bw, 22, 6, 6);
                g2.setColor(new Color(245, 158, 11));
                g2.drawRoundRect(curScreen.x + 14, curScreen.y - 12, bw, 22, 6, 6);
                g2.drawString(badge, curScreen.x + 22, curScreen.y + 3);
            }

            // AutoCAD-Style Point-to-Point Linear Dimension Live Preview
            if (currentTool == ToolMode.MEASURE) {
                Point2D.Double snapPt = getOsnapPoint(mouseWorld.x, mouseWorld.y);
                Point2D.Double curPt = (snapPt != null) ? snapPt : new Point2D.Double(snapCoord(mouseWorld.x), snapCoord(mouseWorld.y));
                Point curScreen = worldToScreen(curPt.x, curPt.y);

                if (dimStep == 0) {
                    g2.setColor(new Color(56, 189, 248));
                    g2.drawOval(curScreen.x - 6, curScreen.y - 6, 12, 12);
                    g2.drawLine(curScreen.x - 9, curScreen.y, curScreen.x + 9, curScreen.y);
                    g2.drawLine(curScreen.x, curScreen.y - 9, curScreen.x, curScreen.y + 9);

                    String badge = "DIMENSION: Click first extension point (Point 1)";
                    g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
                    FontMetrics fm = g2.getFontMetrics();
                    int bw = fm.stringWidth(badge) + 16;
                    g2.setColor(new Color(15, 23, 42, 230));
                    g2.fillRoundRect(curScreen.x + 14, curScreen.y - 12, bw, 22, 6, 6);
                    g2.setColor(new Color(56, 189, 248));
                    g2.drawRoundRect(curScreen.x + 14, curScreen.y - 12, bw, 22, 6, 6);
                    g2.drawString(badge, curScreen.x + 22, curScreen.y + 3);
                } else if (dimStep == 1 && dimP1 != null) {
                    Point sp1 = worldToScreen(dimP1.x, dimP1.y);
                    g2.setColor(new Color(56, 189, 248, 200));
                    g2.setStroke(new BasicStroke(0.75f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{4, 4}, 0));
                    g2.drawLine(sp1.x, sp1.y, curScreen.x, curScreen.y);
                    drawGrip(g2, sp1.x, sp1.y);
                    drawGrip(g2, curScreen.x, curScreen.y);
                    drawCADDimension(g2, sp1.x, sp1.y, curScreen.x, curScreen.y, formatImperial(dimP1.distance(curPt)), 18);

                    String badge = "DIMENSION (" + formatImperial(dimP1.distance(curPt)) + "): Click second point (Point 2)";
                    g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
                    FontMetrics fm = g2.getFontMetrics();
                    int bw = fm.stringWidth(badge) + 16;
                    g2.setColor(new Color(15, 23, 42, 230));
                    g2.fillRoundRect(curScreen.x + 14, curScreen.y - 12, bw, 22, 6, 6);
                    g2.setColor(new Color(56, 189, 248));
                    g2.drawRoundRect(curScreen.x + 14, curScreen.y - 12, bw, 22, 6, 6);
                    g2.drawString(badge, curScreen.x + 22, curScreen.y + 3);
                } else if (dimStep == 2 && dimP1 != null && dimP2 != null) {
                    Point sp1 = worldToScreen(dimP1.x, dimP1.y);
                    Point sp2 = worldToScreen(dimP2.x, dimP2.y);
                    double dx = sp2.x - sp1.x, dy = sp2.y - sp1.y;
                    double len = Math.hypot(dx, dy);
                    double offsetPx = 22.0;
                    if (len > 0) {
                        double nx = -dy / len, ny = dx / len;
                        offsetPx = (curScreen.x - sp1.x) * nx + (curScreen.y - sp1.y) * ny;
                        if (Math.abs(offsetPx) < 8) offsetPx = (offsetPx >= 0 ? 18.0 : -18.0);
                    }
                    drawCADDimension(g2, sp1.x, sp1.y, sp2.x, sp2.y, formatImperial(dimP1.distance(dimP2)), (int) Math.round(offsetPx));

                    String badge = "DIMENSION: Move mouse to set offset distance and click to place";
                    g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
                    FontMetrics fm = g2.getFontMetrics();
                    int bw = fm.stringWidth(badge) + 16;
                    g2.setColor(new Color(15, 23, 42, 230));
                    g2.fillRoundRect(curScreen.x + 14, curScreen.y - 12, bw, 22, 6, 6);
                    g2.setColor(new Color(56, 189, 248));
                    g2.drawRoundRect(curScreen.x + 14, curScreen.y - 12, bw, 22, 6, 6);
                    g2.drawString(badge, curScreen.x + 22, curScreen.y + 3);
                }
            }

            // Object snap indicator
            if (osnapEnabled) {
                Point2D.Double osnapPt = getOsnapPoint(mouseWorld.x, mouseWorld.y);
                if (osnapPt != null) {
                    Point sp = worldToScreen(osnapPt.x, osnapPt.y);
                    g2.setColor(new Color(52, 211, 153));
                    g2.setStroke(new BasicStroke(1.8f));
                    g2.drawRect(sp.x - 5, sp.y - 5, 10, 10);
                }
            }

            // Snap cursor reticle
            if (showSnapCursor && snapToGrid && snapStepFeet > 0.0) {
                double sx = snapCoord(mouseWorld.x), sy = snapCoord(mouseWorld.y);
                Point ret = worldToScreen(sx, sy);
                g2.setColor(new Color(245, 158, 11, 180));
                g2.setStroke(new BasicStroke(1.2f));
                int r = 7;
                g2.drawPolygon(new int[]{ret.x, ret.x + r, ret.x, ret.x - r},
                        new int[]{ret.y - r, ret.y, ret.y + r, ret.y}, 4);
                g2.fillRect(ret.x - 1, ret.y - 1, 3, 3);
            }

            // Rotation handle & HUD for primary selected element
            if (primarySelected instanceof CADElement && selectedObjects.size() == 1) {
                drawRotateHandleAndHUD(g2, (CADElement) primarySelected);
            }
        }

        private void drawRotateHandleAndHUD(Graphics2D g2, CADElement el) {
            Point center = worldToScreen(el.x + el.w / 2.0, el.y + el.h / 2.0);
            Point handle = getRotationHandleScreen(el);
            if (handle == null) return;

            // Stalk line
            g2.setColor(new Color(56, 189, 248, 140));
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawLine(center.x, center.y, handle.x, handle.y);

            // Handle knob
            int r = (int) ROT_HANDLE_RADIUS;
            g2.setColor(new Color(30, 41, 59));
            g2.fillOval(handle.x - r, handle.y - r, r * 2, r * 2);
            g2.setColor(new Color(56, 189, 248));
            g2.setStroke(new BasicStroke(2.0f));
            g2.drawOval(handle.x - r, handle.y - r, r * 2, r * 2);
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Segoe UI Symbol", Font.BOLD, 10));
            g2.drawString("\u21bb", handle.x - 4, handle.y + 4);

            // Live angle readout during rotation
            if (isRotating) {
                String angStr = String.format("%.0f\u00b0", el.rotation);
                g2.setFont(new Font("Consolas", Font.BOLD, 11));
                FontMetrics fm = g2.getFontMetrics();
                int tw = fm.stringWidth(angStr) + 10;
                g2.setColor(new Color(15, 23, 42, 220));
                g2.fillRoundRect(handle.x + 14, handle.y - 10, tw, 20, 6, 6);
                g2.setColor(new Color(56, 189, 248));
                g2.drawRoundRect(handle.x + 14, handle.y - 10, tw, 20, 6, 6);
                g2.drawString(angStr, handle.x + 19, handle.y + 4);
            }

            // Floating action HUD
            int hudX = handle.x - 90, hudY = handle.y - 32;
            g2.setColor(new Color(15, 23, 42, 220));
            g2.fillRoundRect(hudX - 6, hudY - 4, 190, 26, 10, 10);
            g2.setColor(new Color(56, 189, 248, 120));
            g2.setStroke(new BasicStroke(0.8f));
            g2.drawRoundRect(hudX - 6, hudY - 4, 190, 26, 10, 10);

            addHUDButton(g2, hudX, hudY, 38, 18, "\u27f3 90\u00b0", () -> rotateSelected(90));
            addHUDButton(g2, hudX + 42, hudY, 40, 18, "\u21c4 Flip", HousePlanCADApp.this::flipSelectedH);
            addHUDButton(g2, hudX + 86, hudY, 44, 18, "\u21c5 Flip", HousePlanCADApp.this::flipSelectedV);
            addHUDButton(g2, hudX + 134, hudY, 38, 18, "\u2715 Del", HousePlanCADApp.this::deleteSelected);
        }

        private void drawArchitectAgentOverlay(Graphics2D g2) {
            if (currentAuditReport == null) return;

            // Draw issue markers and callout tags on canvas
            for (AuditIssue issue : currentAuditReport.issues) {
                Point sp = worldToScreen(issue.markerX, issue.markerY);
                Color c = (issue.severity == AuditIssue.Severity.ERROR) ? new Color(244, 63, 94) :
                          (issue.severity == AuditIssue.Severity.WARNING) ? new Color(245, 158, 11) : new Color(56, 189, 248);

                // Pulsing hazard beacon
                g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 45));
                g2.fillOval(sp.x - 18, sp.y - 18, 36, 36);
                g2.setColor(c);
                g2.setStroke(new BasicStroke(1.8f));
                g2.drawOval(sp.x - 12, sp.y - 12, 24, 24);
                g2.fillOval(sp.x - 4, sp.y - 4, 8, 8);

                // Callout badge
                String tag = (issue.severity == AuditIssue.Severity.ERROR ? "✕ " : "⚠ ") + issue.title;
                g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
                FontMetrics fm = g2.getFontMetrics();
                int tw = fm.stringWidth(tag) + 12;
                int bx = sp.x + 14, by = sp.y - 12;
                g2.setColor(new Color(15, 23, 42, 225));
                g2.fillRoundRect(bx, by - 12, tw, 18, 6, 6);
                g2.setColor(c);
                g2.drawRoundRect(bx, by - 12, tw, 18, 6, 6);
                g2.drawString(tag, bx + 6, by + 2);
            }

            // Top-left persistent Health Banner in viewport
            int scrW = getWidth();
            int bannerW = 280, bannerH = 34;
            int bx = 16, by = 16;
            g2.setColor(new Color(15, 23, 42, 230));
            g2.fillRoundRect(bx, by, bannerW, bannerH, 8, 8);
            g2.setColor(currentAuditReport.errorCount > 0 ? new Color(244, 63, 94) : new Color(52, 211, 153));
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRoundRect(bx, by, bannerW, bannerH, 8, 8);

            g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
            g2.setColor(Color.WHITE);
            g2.drawString("Architect Health: " + currentAuditReport.score + "/100 (" + currentAuditReport.grade + ")", bx + 12, by + 16);
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            g2.setColor(new Color(148, 163, 184));
            g2.drawString(currentAuditReport.errorCount + " Errors \u00b7 " + currentAuditReport.warningCount + " Warnings \u00b7 Auto-fixes in Inspector", bx + 12, by + 28);
        }

        private void addHUDButton(Graphics2D g2, int x, int y, int w, int h, String text, Runnable action) {
            Rectangle rect = new Rectangle(x, y, w, h);
            hudButtons.add(new HUDButton(rect, text, action));
            boolean hovered = rect.contains(mouseScreen);
            g2.setColor(hovered ? new Color(2, 132, 199) : new Color(30, 41, 59));
            g2.fillRoundRect(x, y, w, h, 6, 6);
            g2.setColor(hovered ? Color.WHITE : new Color(203, 213, 225));
            g2.setStroke(new BasicStroke(0.8f));
            g2.drawRoundRect(x, y, w, h, 6, 6);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, x + (w - fm.stringWidth(text)) / 2, y + 13);
        }
    } // end CADViewportPanel

    // =====================================================================
    // ====================  STAGE 8: ARCHITECT AGENT  =====================
    // =====================================================================

    public void runArchitectAgentAudit() {
        ArchitectAuditReport rep = new ArchitectAuditReport();

        // ═══════════════════════════════════════════════════════════════════
        // SECTION 1: STRUCTURAL INTEGRITY & DEFECTS
        // ═══════════════════════════════════════════════════════════════════

        // Check 1.1: Unclosed Wall Corners / Floating Wall Ends
        if (walls.size() >= 3) {
            for (Wall w : walls) {
                boolean p1Connected = false, p2Connected = false;
                for (Wall other : walls) {
                    if (other == w) continue;
                    if (Line2D.ptSegDist(other.x1, other.y1, other.x2, other.y2, w.x1, w.y1) < 1.0 * FT_PX) p1Connected = true;
                    if (Line2D.ptSegDist(other.x1, other.y1, other.x2, other.y2, w.x2, w.y2) < 1.0 * FT_PX) p2Connected = true;
                }
                if (!p1Connected || !p2Connected) {
                    double mx = !p1Connected ? w.x1 : w.x2;
                    double my = !p1Connected ? w.y1 : w.y2;
                    rep.issues.add(new AuditIssue(
                            AuditIssue.Category.STRUCTURAL,
                            AuditIssue.Severity.WARNING,
                            "Unclosed Wall Perimeter / Open End",
                            "Wall endpoint does not meet an intersecting corner, risking unclosed room enclosure.",
                            "Extend or join wall endpoint to snap to adjacent wall.",
                            w, mx, my,
                            () -> { autoJoinWallEndpoints(w, 2.0 * FT_PX); viewportPanel.repaint(); runArchitectAgentAudit(); }
                    ));
                    break;
                }
            }
        }

        // Check 1.2: Long Unbraced Wall Spans (> 18 ft) Lacking RCC Columns
        for (Wall w : walls) {
            double len = w.getLength();
            if (len > 18.0 * FT_PX) {
                boolean hasColumn = false;
                for (CADElement el : elements) {
                    if (el.type.startsWith("column")) {
                        double d = Line2D.ptSegDist(w.x1, w.y1, w.x2, w.y2, el.x + el.w / 2.0, el.y + el.h / 2.0);
                        if (d < 5.0 * FT_PX) { hasColumn = true; break; }
                    }
                }
                if (!hasColumn) {
                    rep.issues.add(new AuditIssue(
                            AuditIssue.Category.STRUCTURAL,
                            AuditIssue.Severity.WARNING,
                            "Long Unbraced Wall Span (" + formatImperial(len) + ")",
                            "Span exceeds 18'-0\". Structural code (IS 456 / NBC) recommends RCC column support.",
                            "Place a 9\"×12\" RCC Column near the midpoint of this wall span.",
                            w, (w.x1 + w.x2) / 2.0, (w.y1 + w.y2) / 2.0,
                            () -> {
                                CADElement col = new CADElement("column", "RCC Column 9\"×12\"",
                                        (w.x1 + w.x2) / 2.0 - 0.375 * FT_PX, (w.y1 + w.y2) / 2.0 - 0.5 * FT_PX, 0.75 * FT_PX, 1.0 * FT_PX);
                                elements.add(col);
                                viewportPanel.repaint();
                                runArchitectAgentAudit();
                            }
                    ));
                    break;
                }
            }
        }

        // ═══════════════════════════════════════════════════════════════════
        // SECTION 2: INTERIOR & HUMAN CIRCULATION CLEARANCE
        // ═══════════════════════════════════════════════════════════════════

        // Check 2.1: Door Swing Clearance & Obstacle Detection
        for (CADElement el : elements) {
            if (el.type.startsWith("door")) {
                double swingR = el.w;
                double hingeX = el.x + (el.flipH ? el.w : 0);
                double hingeY = el.y + el.h / 2.0;

                for (CADElement obs : elements) {
                    if (obs == el || obs.isOpening() || obs.type.startsWith("floor-") || obs.type.equals("tag") || obs.type.startsWith("north")) continue;
                    Rectangle2D.Double obsBounds = new Rectangle2D.Double(obs.x, obs.y, obs.w, obs.h);
                    double dist = Point2D.distance(hingeX, hingeY, obs.x + obs.w / 2.0, obs.y + obs.h / 2.0);
                    if (dist < swingR + Math.min(obs.w, obs.h) / 2.0 && obsBounds.intersects(el.x - 10, el.y - 10, el.w + 20, el.h + 20)) {
                        rep.issues.add(new AuditIssue(
                                AuditIssue.Category.INTERIOR,
                                AuditIssue.Severity.ERROR,
                                "Door Swing Blocked",
                                "Door '" + el.name + "' swings into " + obs.name + " causing obstruction.",
                                "Flip door swing direction or move obstacle away from opening path.",
                                el, el.x + el.w / 2.0, el.y + el.h / 2.0,
                                () -> { el.flipH = !el.flipH; viewportPanel.repaint(); runArchitectAgentAudit(); }
                        ));
                    }
                }
            }
        }

        // Check 2.2: Free Human Movement Walkways (< 2'-0" clearance)
        for (CADElement el : elements) {
            if (el.type.startsWith("bed-") || el.type.startsWith("sofa-") || el.type.startsWith("dining-")) {
                double leftDist = Double.MAX_VALUE;
                for (Wall w : walls) {
                    if (Math.abs(w.x1 - w.x2) < 2) {
                        double wx = w.x1;
                        if (wx < el.x && Math.max(w.y1, w.y2) >= el.y && Math.min(w.y1, w.y2) <= el.y + el.h) {
                            leftDist = Math.min(leftDist, el.x - wx);
                        }
                    }
                }
                if (leftDist < 1.4 * FT_PX && leftDist > 0.05 * FT_PX) {
                    rep.issues.add(new AuditIssue(
                            AuditIssue.Category.INTERIOR,
                            AuditIssue.Severity.WARNING,
                            "Tight Walkway (" + formatImperial(leftDist) + ")",
                            "Object '" + el.name + "' has less than 2'-0\" walkway clearance. Restricts free human passage.",
                            "Shift position towards center for comfortable human circulation.",
                            el, el.x, el.y + el.h / 2.0,
                            () -> { el.x += 1.0 * FT_PX; viewportPanel.repaint(); runArchitectAgentAudit(); }
                    ));
                    break;
                }
            }
        }

        // Check 2.3: Bedroom Natural Light & Ventilation Window (IBC / NBC)
        boolean hasBed = false;
        CADElement firstBed = null;
        for (CADElement el : elements) {
            if (el.type.startsWith("bed-") || el.name.toLowerCase().contains("bedroom")) {
                hasBed = true;
                if (firstBed == null) firstBed = el;
            }
        }
        if (hasBed) {
            boolean hasWindowNearBed = false;
            for (CADElement w : elements) {
                if (w.isWindow()) {
                    if (firstBed != null && Point2D.distance(firstBed.x, firstBed.y, w.x, w.y) < 20.0 * FT_PX) {
                        hasWindowNearBed = true; break;
                    }
                }
            }
            if (!hasWindowNearBed) {
                final CADElement bedRef = firstBed;
                rep.issues.add(new AuditIssue(
                        AuditIssue.Category.INTERIOR,
                        AuditIssue.Severity.ERROR,
                        "Bedroom Lacks Window",
                        "Habitable bedroom requires natural light and fresh air ventilation window.",
                        "Add a Casement or Sliding Window to the exterior wall adjacent to the bed.",
                        bedRef, bedRef.x + bedRef.w / 2.0, bedRef.y - 20,
                        () -> {
                            Wall closest = null; double minD = Double.MAX_VALUE;
                            for (Wall w : walls) {
                                double d = Line2D.ptSegDist(w.x1, w.y1, w.x2, w.y2, bedRef.x, bedRef.y);
                                if (d < minD) { minD = d; closest = w; }
                            }
                            if (closest != null) {
                                CADElement win = new CADElement("window", "Window 4'-0\"", (closest.x1 + closest.x2) / 2.0 - 2.0 * FT_PX,
                                        (closest.y1 + closest.y2) / 2.0 - 0.375 * FT_PX, 4.0 * FT_PX, 0.75 * FT_PX);
                                elements.add(win);
                                viewportPanel.repaint();
                                runArchitectAgentAudit();
                            }
                        }
                ));
            }
        }

        // Check 2.4: Bathroom Exhaust / Ventilation
        for (CADElement el : elements) {
            if (el.type.equals("toilet") || el.type.startsWith("set-bath")) {
                boolean hasVent = false;
                for (CADElement w : elements) {
                    if (w.type.equals("window-vent") || w.isWindow()) {
                        if (Point2D.distance(el.x, el.y, w.x, w.y) < 14.0 * FT_PX) {
                            hasVent = true; break;
                        }
                    }
                }
                if (!hasVent) {
                    final CADElement bathRef = el;
                    rep.issues.add(new AuditIssue(
                            AuditIssue.Category.INTERIOR,
                            AuditIssue.Severity.WARNING,
                            "Bathroom Missing Exhaust / Vent",
                            "Sanitary bathroom requires exterior ventilation louver/window (NBC 2'-0\" Vent).",
                            "Install a 2' ventilation window on the external wall.",
                            bathRef, bathRef.x + bathRef.w / 2.0, bathRef.y,
                            () -> {
                                Wall closest = null; double minD = Double.MAX_VALUE;
                                for (Wall w : walls) {
                                    double d = Line2D.ptSegDist(w.x1, w.y1, w.x2, w.y2, bathRef.x, bathRef.y);
                                    if (d < minD) { minD = d; closest = w; }
                                }
                                if (closest != null) {
                                    CADElement vent = new CADElement("window-vent", "Toilet Vent 2'-0\"", (closest.x1 + closest.x2) / 2.0 - 1.0 * FT_PX,
                                            (closest.y1 + closest.y2) / 2.0 - 0.375 * FT_PX, 2.0 * FT_PX, 0.75 * FT_PX);
                                    elements.add(vent);
                                    viewportPanel.repaint();
                                    runArchitectAgentAudit();
                                }
                            }
                    ));
                    break;
                }
            }
        }

        // Check 2.5: Room Minimum Area Standard Check (Correctly parsed from text or enclosed walls)
        for (CADElement el : elements) {
            if ("tag".equals(el.type)) {
                double sqft = parseRoomAreaFromText(el.name);
                if (sqft <= 0) {
                    sqft = computeRoomAreaFromWalls(el.x + el.w / 2.0, el.y + el.h / 2.0);
                }
                if (sqft <= 0) {
                    sqft = (el.w / FT_PX) * (el.h / FT_PX);
                }
                String nameLower = el.name.toLowerCase();
                if (nameLower.contains("bed") && sqft < 100.0) {
                    rep.issues.add(new AuditIssue(
                            AuditIssue.Category.INTERIOR,
                            AuditIssue.Severity.WARNING,
                            "Substandard Bedroom Area (" + Math.round(sqft) + " sqft)",
                            "Standard bedroom requires at least 100 sqft (NBC min 9.5 m²). Currently parsed at " + Math.round(sqft) + " sqft.",
                            "Expand perimeter walls or re-tag zone as study or guest room.",
                            el, el.x + el.w / 2.0, el.y + el.h / 2.0, null
                    ));
                } else if (nameLower.contains("bath") && sqft < 30.0) {
                    rep.issues.add(new AuditIssue(
                            AuditIssue.Category.INTERIOR,
                            AuditIssue.Severity.WARNING,
                            "Compact Bath Area (" + Math.round(sqft) + " sqft)",
                            "3-fixture bathroom typically requires minimum 35–40 sqft. Currently parsed at " + Math.round(sqft) + " sqft.",
                            "Enlarge bathroom boundary walls to accommodate standard fixtures.",
                            el, el.x + el.w / 2.0, el.y + el.h / 2.0, null
                    ));
                }
            }
        }

        // ═══════════════════════════════════════════════════════════════════
        // SECTION 3: ELECTRICAL & LIGHTING ISSUES
        // ═══════════════════════════════════════════════════════════════════

        // Check 3.1: Habitable Rooms Ceiling Fan Check
        boolean hasHabitable = false;
        CADElement firstHabitable = null;
        for (CADElement el : elements) {
            if (el.type.startsWith("bed-") || el.type.startsWith("sofa-") || el.type.startsWith("set-")) {
                hasHabitable = true;
                if (firstHabitable == null) firstHabitable = el;
            }
        }
        if (hasHabitable) {
            boolean hasFan = false;
            for (CADElement el : elements) {
                if (el.type.contains("fan")) { hasFan = true; break; }
            }
            if (!hasFan) {
                final CADElement target = firstHabitable;
                rep.issues.add(new AuditIssue(
                        AuditIssue.Category.ELECTRICAL,
                        AuditIssue.Severity.WARNING,
                        "Habitable Room Missing Ceiling Fan",
                        "Bedrooms and living spaces require ceiling fan points for air circulation.",
                        "Insert a 4'×4' Ceiling Fan in the center of the habitable room.",
                        target, target.x + target.w / 2.0, target.y - 20,
                        () -> {
                            CADElement fan = new CADElement("ceiling-fan", "Ceiling Fan 4'×4'", target.x + target.w / 2.0 - 2.0 * FT_PX, target.y - 4.0 * FT_PX, 4.0 * FT_PX, 4.0 * FT_PX);
                            elements.add(fan);
                            viewportPanel.repaint();
                            runArchitectAgentAudit();
                        }
                ));
            }
        }

        // Check 3.2: Artificial Light Points Check
        if (!walls.isEmpty()) {
            boolean hasLight = false;
            for (CADElement el : elements) {
                if (el.type.equals("light-point") || el.type.startsWith("light")) { hasLight = true; break; }
            }
            if (!hasLight) {
                rep.issues.add(new AuditIssue(
                        AuditIssue.Category.ELECTRICAL,
                        AuditIssue.Severity.WARNING,
                        "Missing Artificial Light Points",
                        "Every room requires electrical ceiling/wall light point fixtures.",
                        "Add Light Points from Stage 3 Electrical palette.",
                        null, 200, 200,
                        () -> {
                            if (!elements.isEmpty()) {
                                CADElement ref = elements.get(0);
                                CADElement lp = new CADElement("light-point", "Light Point 1'×1'", ref.x, ref.y - 20, 1.0 * FT_PX, 1.0 * FT_PX);
                                elements.add(lp);
                            }
                            viewportPanel.repaint();
                            runArchitectAgentAudit();
                        }
                ));
            }
        }

        // Check 3.3: Entrance Light Switchboard Check
        if (!elements.isEmpty()) {
            boolean hasDoor = false;
            CADElement firstDoor = null;
            for (CADElement el : elements) {
                if (el.type.startsWith("door")) { hasDoor = true; if (firstDoor == null) firstDoor = el; }
            }
            if (hasDoor) {
                boolean hasSwitch = false;
                for (CADElement el : elements) {
                    if (el.type.startsWith("switch")) { hasSwitch = true; break; }
                }
                if (!hasSwitch) {
                    final CADElement dRef = firstDoor;
                    rep.issues.add(new AuditIssue(
                            AuditIssue.Category.ELECTRICAL,
                            AuditIssue.Severity.INFO,
                            "Entrance Switchboard Missing",
                            "Standard electrical codes recommend a gang switchboard within 2' of room entrance doors.",
                            "Place a switchboard adjacent to the room entrance.",
                            dRef, dRef.x + dRef.w + 10, dRef.y,
                            () -> {
                                CADElement sb = new CADElement("switchboard", "Switch Board", dRef.x + dRef.w + 0.5 * FT_PX, dRef.y, 1.0 * FT_PX, 0.5 * FT_PX);
                                elements.add(sb);
                                viewportPanel.repaint();
                                runArchitectAgentAudit();
                            }
                    ));
                }
            }
        }

        // ═══════════════════════════════════════════════════════════════════
        // SECTION 4: FLOORING & FINISHES ERRORS
        // ═══════════════════════════════════════════════════════════════════

        // Check 4.1: Missing Floor Tile Finishes
        if (!walls.isEmpty()) {
            boolean hasFloor = false;
            for (CADElement el : elements) {
                if (el.type.startsWith("floor-")) { hasFloor = true; break; }
            }
            if (!hasFloor) {
                rep.issues.add(new AuditIssue(
                        AuditIssue.Category.FLOORING,
                        AuditIssue.Severity.WARNING,
                        "Missing Flooring / Tile Finishes",
                        "Interior rooms do not have floor tile hatching or surface finish zones applied.",
                        "Use Stage 5 Flooring palette to apply vitrified tiles or wooden planks.",
                        null, 250, 250,
                        () -> {
                            double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
                            double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
                            for (Wall w : walls) {
                                minX = Math.min(minX, Math.min(w.x1, w.x2));
                                maxX = Math.max(maxX, Math.max(w.x1, w.x2));
                                minY = Math.min(minY, Math.min(w.y1, w.y2));
                                maxY = Math.max(maxY, Math.max(w.y1, w.y2));
                            }
                            if (maxX > minX && maxY > minY) {
                                CADElement fl = new CADElement("floor-tiles-2x2", "Hatch (tiles-2x2)", minX, minY, maxX - minX, maxY - minY);
                                elements.add(0, fl);
                                viewportPanel.repaint();
                                runArchitectAgentAudit();
                            }
                        }
                ));
            }
        }

        // ═══════════════════════════════════════════════════════════════════
        // SECTION 5: DIMENSIONS & SPACING ANNOTATION DEFECTS
        // ═══════════════════════════════════════════════════════════════════

        // Check 5.1: Missing Room Area Tags
        if (!walls.isEmpty()) {
            boolean hasTag = false;
            for (CADElement el : elements) {
                if ("tag".equals(el.type)) { hasTag = true; break; }
            }
            if (!hasTag) {
                rep.issues.add(new AuditIssue(
                        AuditIssue.Category.DIMENSIONS,
                        AuditIssue.Severity.WARNING,
                        "Missing Room Area & Name Tags",
                        "Rooms lack identification labels and calculated square footage tags.",
                        "Insert Room Area Tags from Stage 7 Dimensions & Tags library.",
                        null, 220, 220,
                        () -> {
                            CADElement tag = new CADElement("tag", "ROOM AREA\n14'-0\" \u00d7 12'-0\"", 200, 200, 14.0 * FT_PX, 4.0 * FT_PX);
                            elements.add(tag);
                            viewportPanel.repaint();
                            runArchitectAgentAudit();
                        }
                ));
            }
        }

        // Check 5.2: North Arrow Orientation
        boolean hasNorth = false;
        for (CADElement el : elements) {
            if (el.type.startsWith("north")) { hasNorth = true; break; }
        }
        if (!hasNorth) {
            rep.issues.add(new AuditIssue(
                    AuditIssue.Category.DIMENSIONS,
                    AuditIssue.Severity.INFO,
                    "North Arrow Missing",
                    "Architectural submission drawings require a North Arrow for sun orientation.",
                    "Place a North Arrow symbol in the site/title corner.",
                    null, 180, 180,
                    () -> {
                        CADElement na = new CADElement("north-arrow", "North Arrow", 140, 140, 3.0 * FT_PX, 3.0 * FT_PX);
                        elements.add(na);
                        viewportPanel.repaint();
                        runArchitectAgentAudit();
                    }
            ));
        }

        // Check 5.3: Linear Wall Dimensions Missing
        if (!walls.isEmpty() && dimensions.isEmpty()) {
            rep.issues.add(new AuditIssue(
                    AuditIssue.Category.DIMENSIONS,
                    AuditIssue.Severity.WARNING,
                    "Linear Dimensions Missing",
                    "Working drawing requires exterior and interior linear wall dimensions.",
                    "Use Point-to-Point Dimension tool or 1-Click Auto-Dimension.",
                    null, 150, 150,
                    () -> { autoGenerateDimensions(); viewportPanel.repaint(); runArchitectAgentAudit(); }
            ));
        }

        // Calculate score & grade
        rep.errorCount = 0; rep.warningCount = 0; rep.infoCount = 0;
        for (AuditIssue iss : rep.issues) {
            if (iss.severity == AuditIssue.Severity.ERROR) rep.errorCount++;
            else if (iss.severity == AuditIssue.Severity.WARNING) rep.warningCount++;
            else rep.infoCount++;
        }
        int penalty = rep.errorCount * 20 + rep.warningCount * 8 + rep.infoCount * 3;
        rep.score = Math.max(25, 100 - penalty);
        if (rep.score >= 95) rep.grade = "A+";
        else if (rep.score >= 88) rep.grade = "A";
        else if (rep.score >= 78) rep.grade = "B";
        else if (rep.score >= 65) rep.grade = "C";
        else rep.grade = "Needs Work";

        currentAuditReport = rep;
        updateAgentInspectorUI();
        if (viewportPanel != null) viewportPanel.repaint();
    }

    private void updateAgentInspectorUI() {
        if (agentScoreLabel == null || currentAuditReport == null) return;

        agentScoreLabel.setText("Plan Health: " + currentAuditReport.score + " / 100");
        agentGradeLabel.setText(currentAuditReport.grade);
        agentGradeLabel.setForeground(currentAuditReport.score >= 88 ? new Color(52, 211, 153) :
                (currentAuditReport.score >= 70 ? new Color(251, 191, 36) : new Color(244, 63, 94)));

        agentSummaryLabel.setText(currentAuditReport.errorCount + " Errors  \u00b7  "
                + currentAuditReport.warningCount + " Warnings  \u00b7  "
                + currentAuditReport.infoCount + " Notices");

        if (agentIssuesListPanel != null) {
            agentIssuesListPanel.removeAll();

            // Render 5 Distinct Audit Category Sections:
            // 1. Structural Integrity
            // 2. Interior & Clearance
            // 3. Electrical & Lighting
            // 4. Flooring & Finishes
            // 5. Dimensions & Spacing

            AuditIssue.Category[] categories = AuditIssue.Category.values();
            for (AuditIssue.Category cat : categories) {
                List<AuditIssue> catIssues = new ArrayList<>();
                for (AuditIssue iss : currentAuditReport.issues) {
                    if (iss.category == cat) catIssues.add(iss);
                }

                boolean isClean = catIssues.isEmpty();
                JPanel catHeader = new JPanel(new BorderLayout(6, 0));
                catHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
                catHeader.setAlignmentX(Component.LEFT_ALIGNMENT);

                if (isClean) {
                    catHeader.setBackground(new Color(16, 44, 38)); // dark emerald
                    catHeader.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(new Color(52, 211, 153, 140), 1, true),
                            new EmptyBorder(4, 8, 4, 8)));

                    JLabel tickLbl = new JLabel("✔  " + cat.title.toUpperCase());
                    tickLbl.setFont(new Font("Segoe UI", Font.BOLD, 10));
                    tickLbl.setForeground(new Color(52, 211, 153));
                    catHeader.add(tickLbl, BorderLayout.WEST);

                    JLabel statusLbl = new JLabel("PASS");
                    statusLbl.setFont(new Font("Consolas", Font.BOLD, 10));
                    statusLbl.setForeground(new Color(52, 211, 153));
                    catHeader.add(statusLbl, BorderLayout.EAST);
                    agentIssuesListPanel.add(catHeader);
                    agentIssuesListPanel.add(Box.createVerticalStrut(4));
                } else {
                    boolean hasError = catIssues.stream().anyMatch(i -> i.severity == AuditIssue.Severity.ERROR);
                    Color alertColor = hasError ? new Color(244, 63, 94) : new Color(245, 158, 11);
                    Color bgAlert = hasError ? new Color(69, 10, 25) : new Color(69, 39, 10);

                    catHeader.setBackground(bgAlert);
                    catHeader.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(alertColor, 1, true),
                            new EmptyBorder(4, 8, 4, 8)));

                    JLabel alertLbl = new JLabel("✕  " + cat.title.toUpperCase());
                    alertLbl.setFont(new Font("Segoe UI", Font.BOLD, 10));
                    alertLbl.setForeground(alertColor);
                    catHeader.add(alertLbl, BorderLayout.WEST);

                    JLabel countLbl = new JLabel(catIssues.size() + " DEFECT" + (catIssues.size() > 1 ? "S" : ""));
                    countLbl.setFont(new Font("Consolas", Font.BOLD, 10));
                    countLbl.setForeground(alertColor);
                    catHeader.add(countLbl, BorderLayout.EAST);
                    agentIssuesListPanel.add(catHeader);
                    agentIssuesListPanel.add(Box.createVerticalStrut(3));

                    // List issues under this category
                    for (AuditIssue issue : catIssues) {
                        JPanel item = new JPanel();
                        item.setLayout(new BoxLayout(item, BoxLayout.Y_AXIS));
                        item.setBackground(new Color(24, 33, 53));
                        Color bColor = (issue.severity == AuditIssue.Severity.ERROR) ? new Color(244, 63, 94) :
                                (issue.severity == AuditIssue.Severity.WARNING) ? new Color(245, 158, 11) : new Color(56, 189, 248);
                        item.setBorder(BorderFactory.createCompoundBorder(
                                BorderFactory.createMatteBorder(0, 3, 0, 0, bColor),
                                new EmptyBorder(5, 8, 5, 8)));
                        item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 115));
                        item.setAlignmentX(Component.LEFT_ALIGNMENT);

                        JLabel title = new JLabel((issue.severity == AuditIssue.Severity.ERROR ? "✕ " : "⚠ ") + issue.title);
                        title.setFont(new Font("Segoe UI", Font.BOLD, 10));
                        title.setForeground(bColor);
                        title.setAlignmentX(Component.LEFT_ALIGNMENT);
                        item.add(title);
                        item.add(Box.createVerticalStrut(2));

                        JLabel msg = new JLabel("<html><span style='color:#cbd5e1'>" + issue.message + "</span><br><i style='color:#94a3b8'>" + issue.recommendation + "</i></html>");
                        msg.setFont(new Font("Segoe UI", Font.PLAIN, 9));
                        msg.setAlignmentX(Component.LEFT_ALIGNMENT);
                        item.add(msg);

                        if (issue.autoFixAction != null) {
                            item.add(Box.createVerticalStrut(4));
                            ModernButton fixBtn = new ModernButton("⚡ 1-Click Auto-Fix", new Color(13, 148, 136), Color.WHITE, new Color(45, 212, 191));
                            fixBtn.setFont(new Font("Segoe UI", Font.BOLD, 9));
                            fixBtn.setPreferredSize(new Dimension(130, 20));
                            fixBtn.setMaximumSize(new Dimension(130, 20));
                            fixBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
                            fixBtn.addActionListener(e -> issue.autoFixAction.run());
                            item.add(fixBtn);
                        }

                        agentIssuesListPanel.add(item);
                        agentIssuesListPanel.add(Box.createVerticalStrut(4));
                    }
                }
                agentIssuesListPanel.add(Box.createVerticalStrut(4));
            }

            agentIssuesListPanel.revalidate();
            agentIssuesListPanel.repaint();
        }
    }

    // =====================================================================
    // =========================  UTILITY METHODS  =========================
    // =====================================================================

    private void clearCanvas() {
        saveUndoState();
        walls.clear(); elements.clear(); dimensions.clear();
        clearSelection();
        checkAllCollisions(); updateProperties(); viewportPanel.repaint();
    }

    // --- Preset Wall Plans with Dimensions (No Interior Furniture) ---

    private void loadPreset30x40() {
        clearCanvas();
        double ox = 120.0, oy = 120.0;
        double pw = 30.0 * FT_PX, ph = 40.0 * FT_PX;

        // Exterior Perimeter Walls (9" Brickwork)
        walls.add(new Wall(ox, oy, ox + pw, oy, 9.0));
        walls.add(new Wall(ox + pw, oy, ox + pw, oy + ph, 9.0));
        walls.add(new Wall(ox + pw, oy + ph, ox, oy + ph, 9.0));
        walls.add(new Wall(ox, oy + ph, ox, oy, 9.0));

        // Interior Partitions (6" and 4.5" Walls)
        double divY = oy + 22.0 * FT_PX;
        walls.add(new Wall(ox, divY, ox + pw, divY, 6.0)); // Living / Bedroom divider
        double bedDivX = ox + 17.0 * FT_PX;
        walls.add(new Wall(bedDivX, divY, bedDivX, oy + ph, 6.0)); // Bedroom / Bath-Kitchen divider
        double bathY = oy + 31.0 * FT_PX;
        walls.add(new Wall(bedDivX, bathY, ox + pw, bathY, 4.5)); // Kitchen / Bath divider

        // Doors & Openings with Wall Cut-Through
        elements.add(new CADElement("door", "Main Entry 3'-6\"", ox + 8.0 * FT_PX, oy - 0.375 * FT_PX, 3.5 * FT_PX, 0.75 * FT_PX));
        elements.add(new CADElement("door", "Bed Door 3'-0\"", bedDivX - 3.5 * FT_PX, divY - 0.25 * FT_PX, 3.0 * FT_PX, 0.5 * FT_PX));
        elements.add(new CADElement("door", "Kitchen Door 3'-0\"", ox + pw - 3.5 * FT_PX, divY - 0.25 * FT_PX, 3.0 * FT_PX, 0.5 * FT_PX));
        elements.add(new CADElement("door", "Bath Door 2'-6\"", bedDivX + 0.5 * FT_PX, bathY - 0.2 * FT_PX, 2.5 * FT_PX, 0.375 * FT_PX));

        // Windows
        elements.add(new CADElement("window", "Living Window 5'", ox + 16.0 * FT_PX, oy - 0.375 * FT_PX, 5.0 * FT_PX, 0.75 * FT_PX));
        elements.add(new CADElement("window", "Bed Window 4'", ox + 4.0 * FT_PX, oy + ph - 0.375 * FT_PX, 4.0 * FT_PX, 0.75 * FT_PX));
        elements.add(new CADElement("window", "Kitchen Window 3'", ox + pw - 0.375 * FT_PX, divY + 4.0 * FT_PX, 3.0 * FT_PX, 0.75 * FT_PX));
        elements.add(new CADElement("window", "Bath Ventilator 2'", ox + pw - 0.375 * FT_PX, bathY + 4.0 * FT_PX, 2.0 * FT_PX, 0.75 * FT_PX));

        // Room Labels & Area Identifiers
        elements.add(new CADElement("tag", "LIVING / DINING\n30'-0\" \u00d7 22'-0\"", ox + 8.0 * FT_PX, oy + 9.0 * FT_PX, 14.0 * FT_PX, 4.0 * FT_PX));
        elements.add(new CADElement("tag", "MASTER BEDROOM\n17'-0\" \u00d7 18'-0\"", ox + 3.0 * FT_PX, divY + 8.0 * FT_PX, 11.0 * FT_PX, 4.0 * FT_PX));
        elements.add(new CADElement("tag", "KITCHEN\n13'-0\" \u00d7 9'-0\"", bedDivX + 2.0 * FT_PX, divY + 4.0 * FT_PX, 9.0 * FT_PX, 3.0 * FT_PX));
        elements.add(new CADElement("tag", "BATHROOM\n13'-0\" \u00d7 9'-0\"", bedDivX + 2.0 * FT_PX, bathY + 4.0 * FT_PX, 9.0 * FT_PX, 3.0 * FT_PX));
        elements.add(new CADElement("north-arrow", "North", ox + pw + 3.0 * FT_PX, oy + 2.0 * FT_PX, 3.0 * FT_PX, 3.0 * FT_PX));

        // Complete Architectural Dimensions
        dimensions.add(new CADDimension(ox, oy, ox + pw, oy, -30.0)); // 30' Overall Top
        dimensions.add(new CADDimension(ox + pw, oy, ox + pw, oy + ph, -30.0)); // 40' Overall Right
        dimensions.add(new CADDimension(ox, oy, ox, divY, 26.0)); // 22' Living Height
        dimensions.add(new CADDimension(ox, divY, ox, oy + ph, 26.0)); // 18' Bedroom Height
        dimensions.add(new CADDimension(ox, oy + ph, bedDivX, oy + ph, 26.0)); // 17' Bedroom Width
        dimensions.add(new CADDimension(bedDivX, oy + ph, ox + pw, oy + ph, 26.0)); // 13' Bath/Kitchen Width

        autoJoinAllWalls(); checkAllCollisions(); viewportPanel.repaint();
    }

    private void loadPreset25x40() {
        clearCanvas();
        double ox = 120.0, oy = 120.0;
        double pw = 25.0 * FT_PX, ph = 40.0 * FT_PX;

        // Exterior Perimeter Walls (9")
        walls.add(new Wall(ox, oy, ox + pw, oy, 9.0));
        walls.add(new Wall(ox + pw, oy, ox + pw, oy + ph, 9.0));
        walls.add(new Wall(ox + pw, oy + ph, ox, oy + ph, 9.0));
        walls.add(new Wall(ox, oy + ph, ox, oy, 9.0));

        // Interior Partitions (6" and 4.5")
        double midY1 = oy + 16.0 * FT_PX;
        double midY2 = oy + 28.0 * FT_PX;
        double splitX = ox + 14.0 * FT_PX;

        walls.add(new Wall(ox, midY1, ox + pw, midY1, 6.0)); // Hall to Bed 1 / Kitchen
        walls.add(new Wall(ox, midY2, ox + pw, midY2, 6.0)); // Bed 1 to Bed 2
        walls.add(new Wall(splitX, midY1, splitX, oy + ph, 4.5)); // Bed divider to Kitchen/Toilet

        // Doors
        elements.add(new CADElement("door", "Main Door 3'-6\"", ox + 6.0 * FT_PX, oy - 0.375 * FT_PX, 3.5 * FT_PX, 0.75 * FT_PX));
        elements.add(new CADElement("door", "Bed 1 Door 3'-0\"", ox + 2.0 * FT_PX, midY1 - 0.25 * FT_PX, 3.0 * FT_PX, 0.5 * FT_PX));
        elements.add(new CADElement("door", "Kitchen Door 3'-0\"", splitX + 2.0 * FT_PX, midY1 - 0.25 * FT_PX, 3.0 * FT_PX, 0.5 * FT_PX));
        elements.add(new CADElement("door", "Bed 2 Door 3'-0\"", ox + 2.0 * FT_PX, midY2 - 0.25 * FT_PX, 3.0 * FT_PX, 0.5 * FT_PX));
        elements.add(new CADElement("door", "Bath Door 2'-6\"", splitX + 2.0 * FT_PX, midY2 - 0.2 * FT_PX, 2.5 * FT_PX, 0.375 * FT_PX));

        // Windows
        elements.add(new CADElement("window", "Hall Window 5'", ox + 15.0 * FT_PX, oy - 0.375 * FT_PX, 5.0 * FT_PX, 0.75 * FT_PX));
        elements.add(new CADElement("window", "Bed 1 Window 4'", ox - 0.375 * FT_PX, midY1 + 4.0 * FT_PX, 4.0 * FT_PX, 0.75 * FT_PX));
        elements.add(new CADElement("window", "Bed 2 Window 4'", ox + 4.0 * FT_PX, oy + ph - 0.375 * FT_PX, 4.0 * FT_PX, 0.75 * FT_PX));
        elements.add(new CADElement("window", "Kitchen Window 3'", ox + pw - 0.375 * FT_PX, midY1 + 4.0 * FT_PX, 3.0 * FT_PX, 0.75 * FT_PX));

        // Tags
        elements.add(new CADElement("tag", "LIVING HALL\n25'-0\" \u00d7 16'-0\"", ox + 6.0 * FT_PX, oy + 6.0 * FT_PX, 13.0 * FT_PX, 4.0 * FT_PX));
        elements.add(new CADElement("tag", "BEDROOM 01\n14'-0\" \u00d7 12'-0\"", ox + 2.0 * FT_PX, midY1 + 5.0 * FT_PX, 10.0 * FT_PX, 3.0 * FT_PX));
        elements.add(new CADElement("tag", "KITCHEN\n11'-0\" \u00d7 12'-0\"", splitX + 1.5 * FT_PX, midY1 + 5.0 * FT_PX, 8.0 * FT_PX, 3.0 * FT_PX));
        elements.add(new CADElement("tag", "BEDROOM 02\n14'-0\" \u00d7 12'-0\"", ox + 2.0 * FT_PX, midY2 + 5.0 * FT_PX, 10.0 * FT_PX, 3.0 * FT_PX));
        elements.add(new CADElement("tag", "TOILET\n11'-0\" \u00d7 12'-0\"", splitX + 1.5 * FT_PX, midY2 + 5.0 * FT_PX, 8.0 * FT_PX, 3.0 * FT_PX));
        elements.add(new CADElement("north-arrow", "North", ox + pw + 3.0 * FT_PX, oy + 2.0 * FT_PX, 3.0 * FT_PX, 3.0 * FT_PX));

        // Dimensions
        dimensions.add(new CADDimension(ox, oy, ox + pw, oy, -30.0)); // 25' Overall Top
        dimensions.add(new CADDimension(ox + pw, oy, ox + pw, oy + ph, -30.0)); // 40' Overall Right
        dimensions.add(new CADDimension(ox, oy, ox, midY1, 26.0)); // 16' Hall
        dimensions.add(new CADDimension(ox, midY1, ox, midY2, 26.0)); // 12' Bed 1
        dimensions.add(new CADDimension(ox, midY2, ox, oy + ph, 26.0)); // 12' Bed 2
        dimensions.add(new CADDimension(ox, oy + ph, splitX, oy + ph, 26.0)); // 14' Bed Width
        dimensions.add(new CADDimension(splitX, oy + ph, ox + pw, oy + ph, 26.0)); // 11' Bath Width

        autoJoinAllWalls(); checkAllCollisions(); viewportPanel.repaint();
    }

    private void loadPreset20x30() {
        clearCanvas();
        double ox = 120.0, oy = 120.0;
        double pw = 20.0 * FT_PX, ph = 30.0 * FT_PX;

        // Exterior Perimeter Walls (9")
        walls.add(new Wall(ox, oy, ox + pw, oy, 9.0));
        walls.add(new Wall(ox + pw, oy, ox + pw, oy + ph, 9.0));
        walls.add(new Wall(ox + pw, oy + ph, ox, oy + ph, 9.0));
        walls.add(new Wall(ox, oy + ph, ox, oy, 9.0));

        // Interior Partition (4.5")
        double bathY = oy + 20.0 * FT_PX;
        double bathX = ox + 12.0 * FT_PX;
        walls.add(new Wall(ox, bathY, ox + pw, bathY, 4.5)); // Studio divider
        walls.add(new Wall(bathX, bathY, bathX, oy + ph, 4.5)); // Bath / Kitchenette divider

        // Openings
        elements.add(new CADElement("door", "Studio Entry 3'-0\"", ox + 4.0 * FT_PX, oy - 0.375 * FT_PX, 3.0 * FT_PX, 0.75 * FT_PX));
        elements.add(new CADElement("door", "Bath Door 2'-6\"", bathX + 1.0 * FT_PX, bathY - 0.2 * FT_PX, 2.5 * FT_PX, 0.375 * FT_PX));
        elements.add(new CADElement("door", "Kitchenette Opening", ox + 4.0 * FT_PX, bathY - 0.25 * FT_PX, 3.0 * FT_PX, 0.5 * FT_PX));

        // Windows
        elements.add(new CADElement("window", "Studio Window 4'", ox + 12.0 * FT_PX, oy - 0.375 * FT_PX, 4.0 * FT_PX, 0.75 * FT_PX));
        elements.add(new CADElement("window", "Kitchen Window 3'", ox - 0.375 * FT_PX, bathY + 4.0 * FT_PX, 3.0 * FT_PX, 0.75 * FT_PX));
        elements.add(new CADElement("window", "Bath Ventilator 2'", ox + pw - 0.375 * FT_PX, bathY + 4.0 * FT_PX, 2.0 * FT_PX, 0.75 * FT_PX));

        // Tags
        elements.add(new CADElement("tag", "STUDIO ROOM\n20'-0\" \u00d7 20'-0\"", ox + 4.0 * FT_PX, oy + 8.0 * FT_PX, 12.0 * FT_PX, 4.0 * FT_PX));
        elements.add(new CADElement("tag", "KITCHENETTE\n12'-0\" \u00d7 10'-0\"", ox + 1.5 * FT_PX, bathY + 4.0 * FT_PX, 9.0 * FT_PX, 3.0 * FT_PX));
        elements.add(new CADElement("tag", "BATHROOM\n8'-0\" \u00d7 10'-0\"", bathX + 1.0 * FT_PX, bathY + 4.0 * FT_PX, 6.0 * FT_PX, 3.0 * FT_PX));
        elements.add(new CADElement("north-arrow", "North", ox + pw + 3.0 * FT_PX, oy + 2.0 * FT_PX, 3.0 * FT_PX, 3.0 * FT_PX));

        // Dimensions
        dimensions.add(new CADDimension(ox, oy, ox + pw, oy, -30.0)); // 20' Overall Top
        dimensions.add(new CADDimension(ox + pw, oy, ox + pw, oy + ph, -30.0)); // 30' Overall Right
        dimensions.add(new CADDimension(ox, oy, ox, bathY, 26.0)); // 20' Studio Height
        dimensions.add(new CADDimension(ox, bathY, ox, oy + ph, 26.0)); // 10' Service Height
        dimensions.add(new CADDimension(ox, oy + ph, bathX, oy + ph, 26.0)); // 12' Kitchenette Width
        dimensions.add(new CADDimension(bathX, oy + ph, ox + pw, oy + ph, 26.0)); // 8' Bath Width

        autoJoinAllWalls(); checkAllCollisions(); viewportPanel.repaint();
    }

    // =====================================================================
    // ==============  ARCHITECTURAL EXPORT ENGINE (PNG & PDF)  ============
    // =====================================================================

    /**
     * Dialog for professional architectural drawing export in multiple aspect ratios,
     * monochrome working drawing style or color presentation style, and PDF or PNG format.
     */
    public void showExportDialog() {
        JDialog dlg = new JDialog(this, "Export PDF — HDE Architectural CAD Studio", true);
        dlg.setLayout(new BorderLayout());
        dlg.getContentPane().setBackground(new Color(15, 23, 42));

        // Main Container: Left Settings + Right Live Preview
        JPanel mainContainer = new JPanel(new BorderLayout(16, 0));
        mainContainer.setOpaque(false);
        mainContainer.setBorder(new EmptyBorder(16, 20, 16, 20));

        // ─── LEFT: SETTINGS FORM ───
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        form.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(30, 41, 59)),
                new EmptyBorder(14, 16, 14, 16)));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 5, 6, 5);

        // Header
        JLabel titleLbl = new JLabel("EXPORT PDF");
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 15));
        titleLbl.setForeground(Color.WHITE);
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        form.add(titleLbl, gbc);
        gbc.gridwidth = 1;

        // 1. Paper Size
        gbc.gridx = 0; gbc.gridy = 1;
        JLabel paperLbl = new JLabel("Paper Size:");
        paperLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        paperLbl.setForeground(new Color(203, 213, 225));
        form.add(paperLbl, gbc);

        JComboBox<String> paperCombo = new JComboBox<>(new String[]{
                "A4 (210 \u00d7 297 mm)",
                "A3 (297 \u00d7 420 mm)"
        });
        styleComboBox(paperCombo);
        gbc.gridx = 1;
        form.add(paperCombo, gbc);

        // 2. Orientation
        gbc.gridx = 0; gbc.gridy = 2;
        JLabel orientLbl = new JLabel("Orientation:");
        orientLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        orientLbl.setForeground(new Color(203, 213, 225));
        form.add(orientLbl, gbc);

        JComboBox<String> orientCombo = new JComboBox<>(new String[]{
                "Landscape",
                "Portrait"
        });
        styleComboBox(orientCombo);
        gbc.gridx = 1;
        form.add(orientCombo, gbc);

        // 3. Plot Scale: Fit to paper
        gbc.gridx = 0; gbc.gridy = 3;
        JLabel scaleLbl = new JLabel("Plot Scale:");
        scaleLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        scaleLbl.setForeground(new Color(203, 213, 225));
        form.add(scaleLbl, gbc);

        JCheckBox fitScaleBox = new JCheckBox("Fit to paper", true);
        fitScaleBox.setFont(new Font("Segoe UI", Font.BOLD, 12));
        fitScaleBox.setForeground(new Color(56, 189, 248));
        fitScaleBox.setOpaque(false);
        fitScaleBox.setSelected(true);
        fitScaleBox.setEnabled(false);
        gbc.gridx = 1;
        form.add(fitScaleBox, gbc);

        // 4. Drawing Style
        gbc.gridx = 0; gbc.gridy = 4;
        JLabel styleLbl = new JLabel("Drawing Style:");
        styleLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        styleLbl.setForeground(new Color(203, 213, 225));
        form.add(styleLbl, gbc);

        JComboBox<String> styleCombo = new JComboBox<>(new String[]{
                "Monochrome (Black Lines on White)",
                "Color (Architectural Presentation)"
        });
        styleComboBox(styleCombo);
        gbc.gridx = 1;
        form.add(styleCombo, gbc);

        // 5. Wall Hatch
        gbc.gridx = 0; gbc.gridy = 5;
        JLabel hatchLbl = new JLabel("Wall Hatch:");
        hatchLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        hatchLbl.setForeground(new Color(203, 213, 225));
        form.add(hatchLbl, gbc);

        JComboBox<String> hatchCombo = new JComboBox<>(new String[]{
                "45\u00b0 Diagonal (ANSI31)",
                "Solid Black Poch\u00e9",
                "Hollow"
        });
        if ("SOLID".equals(wallHatchStyle)) hatchCombo.setSelectedIndex(1);
        else if ("HOLLOW".equals(wallHatchStyle)) hatchCombo.setSelectedIndex(2);
        else hatchCombo.setSelectedIndex(0);
        styleComboBox(hatchCombo);
        gbc.gridx = 1;
        form.add(hatchCombo, gbc);

        // ─── RIGHT: LIVE PREVIEW ───
        JPanel previewContainer = new JPanel(new BorderLayout(0, 8));
        previewContainer.setOpaque(false);
        previewContainer.setPreferredSize(new Dimension(380, 420));
        previewContainer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(30, 41, 59)),
                new EmptyBorder(12, 14, 12, 14)));

        JLabel prevTitle = new JLabel("\u25c8 LIVE SHEET PREVIEW");
        prevTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        prevTitle.setForeground(new Color(56, 189, 248));
        previewContainer.add(prevTitle, BorderLayout.NORTH);

        final BufferedImage[] cachedThumb = new BufferedImage[1];
        JPanel previewCanvas = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                int pw = getWidth(), ph = getHeight();
                g2.setColor(new Color(11, 17, 32));
                g2.fillRect(0, 0, pw, ph);
                if (cachedThumb[0] != null) {
                    BufferedImage thumb = cachedThumb[0];
                    double sc = Math.min((double)(pw - 24) / thumb.getWidth(), (double)(ph - 24) / thumb.getHeight());
                    int dw = (int)(thumb.getWidth() * sc), dh = (int)(thumb.getHeight() * sc);
                    int dx = (pw - dw) / 2, dy = (ph - dh) / 2;
                    g2.setColor(new Color(0, 0, 0, 90));
                    g2.fillRoundRect(dx + 4, dy + 4, dw, dh, 4, 4);
                    g2.drawImage(thumb, dx, dy, dw, dh, null);
                    g2.setColor(new Color(71, 85, 105));
                    g2.setStroke(new BasicStroke(1.2f));
                    g2.drawRect(dx, dy, dw, dh);
                }
            }
        };
        previewCanvas.setBackground(new Color(11, 17, 32));
        previewCanvas.setBorder(BorderFactory.createLineBorder(new Color(30, 41, 59)));
        previewContainer.add(previewCanvas, BorderLayout.CENTER);

        JLabel previewFooter = new JLabel("", JLabel.CENTER);
        previewFooter.setFont(new Font("Consolas", Font.PLAIN, 10));
        previewFooter.setForeground(new Color(100, 116, 139));
        previewContainer.add(previewFooter, BorderLayout.SOUTH);

        // Helper: get pixel dims for current paper+orientation selection
        // A4 @300DPI: 2480×3508, A3 @300DPI: 3508×4960
        Runnable updatePreview = () -> {
            boolean isA3 = (paperCombo.getSelectedIndex() == 1);
            boolean isLandscape = (orientCombo.getSelectedIndex() == 0);
            int baseW, baseH;
            if (isA3) { baseW = 3508; baseH = 4960; }
            else       { baseW = 2480; baseH = 3508; }
            int targetW = isLandscape ? Math.max(baseW, baseH) : Math.min(baseW, baseH);
            int targetH = isLandscape ? Math.min(baseW, baseH) : Math.max(baseW, baseH);

            boolean isMono = (styleCombo.getSelectedIndex() == 0);
            String hStyle = hatchCombo.getSelectedIndex() == 0 ? "DIAGONAL_45" :
                           (hatchCombo.getSelectedIndex() == 1 ? "SOLID" : "HOLLOW");

            // Generate scaled-down preview
            double aspect = (double) targetW / targetH;
            int prevW, prevH;
            if (aspect >= 1.0) { prevW = 640; prevH = (int)(640 / aspect); }
            else               { prevH = 640; prevW = (int)(640 * aspect); }

            cachedThumb[0] = renderArchitecturalDrawing(prevW, prevH, isMono, false, hStyle);
            String paperName = isA3 ? "A3" : "A4";
            String orientName = isLandscape ? "Landscape" : "Portrait";
            previewFooter.setText(paperName + " " + orientName + " | " + targetW + " \u00d7 " + targetH + " px @ 300 DPI | Fit to Paper");
            previewCanvas.repaint();
        };

        paperCombo.addActionListener(e -> updatePreview.run());
        orientCombo.addActionListener(e -> updatePreview.run());
        styleCombo.addActionListener(e -> updatePreview.run());
        hatchCombo.addActionListener(e -> updatePreview.run());

        updatePreview.run();

        mainContainer.add(form, BorderLayout.CENTER);
        mainContainer.add(previewContainer, BorderLayout.EAST);
        dlg.add(mainContainer, BorderLayout.CENTER);

        // ─── BUTTONS ───
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 14));
        btnPanel.setOpaque(false);
        btnPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(30, 41, 59)));

        ModernButton cancelBtn = new ModernButton("Cancel", new Color(30, 41, 59), new Color(203, 213, 225), new Color(71, 85, 105));
        cancelBtn.addActionListener(e -> dlg.dispose());

        ModernButton doExportBtn = new ModernButton("\u2913 Export PDF", new Color(2, 132, 199), Color.WHITE, new Color(56, 189, 248));
        doExportBtn.setPreferredSize(new Dimension(130, 32));
        doExportBtn.addActionListener(e -> {
            boolean isA3 = (paperCombo.getSelectedIndex() == 1);
            boolean isLandscape = (orientCombo.getSelectedIndex() == 0);
            int baseW, baseH;
            if (isA3) { baseW = 3508; baseH = 4960; }
            else       { baseW = 2480; baseH = 3508; }
            int targetW = isLandscape ? Math.max(baseW, baseH) : Math.min(baseW, baseH);
            int targetH = isLandscape ? Math.min(baseW, baseH) : Math.max(baseW, baseH);

            boolean isMono = (styleCombo.getSelectedIndex() == 0);
            String hatch = hatchCombo.getSelectedIndex() == 0 ? "DIAGONAL_45" :
                          (hatchCombo.getSelectedIndex() == 1 ? "SOLID" : "HOLLOW");

            String paperName = isA3 ? "A3" : "A4";
            String orientName = isLandscape ? "Landscape" : "Portrait";
            String styleTag = isMono ? "Mono" : "Color";
            String defName = "HDE_Plan_" + paperName + "_" + orientName + "_" + styleTag + ".pdf";

            dlg.dispose();

            JFileChooser fc = new JFileChooser();
            fc.setSelectedFile(new File(defName));
            if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                File dest = fc.getSelectedFile();
                if (!dest.getName().toLowerCase().endsWith(".pdf")) {
                    dest = new File(dest.getParentFile(), dest.getName() + ".pdf");
                }
                try {
                    BufferedImage img = renderArchitecturalDrawing(targetW, targetH, isMono, false, hatch);
                    writeImageToPdf(img, dest);
                    JOptionPane.showMessageDialog(this,
                            "PDF exported successfully:\n" + dest.getAbsolutePath() +
                            "\n\nPaper: " + paperName + " " + orientName +
                            "\nSize: " + targetW + " \u00d7 " + targetH + " px (300 DPI)" +
                            "\nScale: Fit to Paper",
                            "Export Complete", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this,
                            "Export failed: " + ex.getMessage(),
                            "Export Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        btnPanel.add(cancelBtn);
        btnPanel.add(doExportBtn);
        dlg.add(btnPanel, BorderLayout.SOUTH);

        dlg.pack();
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    // =====================================================================
    // =============  ARCHITECTURAL SNIPPING TOOL (9:16 & 16:9)  ============
    // =====================================================================

    /**
     * Architectural Snipping Tool for exporting floor plans in social media & video formats:
     * - 16:9 Landscape (YouTube, LinkedIn, Presentations, Desktop)
     * - 9:16 Vertical (Instagram Reels, YouTube Shorts, TikTok, Mobile Stories)
     * Supports Full Floor Plan framing or interactive Canvas Drag-Snip, with PNG and JPG export and Clipboard Copy.
     */
    public void showSnippingDialog() {
        JDialog dlg = new JDialog(this, "\u2702 Snipping Tool \u2014 9:16 & 16:9 Format", true);
        dlg.setLayout(new BorderLayout());
        dlg.getContentPane().setBackground(new Color(15, 23, 42));

        final boolean[] isLandscape16x9 = new boolean[]{ true };
        final Rectangle2D.Double[] activeCropWorld = new Rectangle2D.Double[]{ null };
        final BufferedImage[] cachedThumb = new BufferedImage[1];

        JPanel mainContainer = new JPanel(new BorderLayout(18, 0));
        mainContainer.setOpaque(false);
        mainContainer.setBorder(new EmptyBorder(16, 20, 16, 20));

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        form.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(30, 41, 59)),
                new EmptyBorder(14, 16, 14, 16)));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 6, 6, 6);

        // Header Title
        JLabel titleLbl = new JLabel("\u2702 ARCHITECTURAL SNIPPING TOOL");
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 15));
        titleLbl.setForeground(Color.WHITE);
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        form.add(titleLbl, gbc);

        JLabel subtitleLbl = new JLabel("Export 16:9 (YouTube/Posts) or 9:16 (Reels/Shorts/Stories) in PNG/JPG");
        subtitleLbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        subtitleLbl.setForeground(new Color(148, 163, 184));
        gbc.gridy = 1;
        form.add(subtitleLbl, gbc);
        gbc.gridwidth = 1;

        // 1. Aspect Ratio
        gbc.gridx = 0; gbc.gridy = 2;
        JLabel ratioLbl = new JLabel("Aspect Ratio:");
        ratioLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        ratioLbl.setForeground(new Color(203, 213, 225));
        form.add(ratioLbl, gbc);

        JPanel ratioPanel = new JPanel(new GridLayout(1, 2, 8, 0));
        ratioPanel.setOpaque(false);
        ModernToggle btn16x9 = new ModernToggle("\u25a0 16:9 Landscape (YouTube)", true);
        ModernToggle btn9x16 = new ModernToggle("\u25af 9:16 Vertical (Reels/Shorts)", false);
        ButtonGroup ratioGroup = new ButtonGroup();
        ratioGroup.add(btn16x9); ratioGroup.add(btn9x16);
        ratioPanel.add(btn16x9); ratioPanel.add(btn9x16);
        gbc.gridx = 1;
        form.add(ratioPanel, gbc);

        // 2. Framing Area
        gbc.gridx = 0; gbc.gridy = 3;
        JLabel framingLbl = new JLabel("Snip Area:");
        framingLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        framingLbl.setForeground(new Color(203, 213, 225));
        form.add(framingLbl, gbc);

        JPanel frameActionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        frameActionPanel.setOpaque(false);
        JLabel cropStatusLbl = new JLabel("Full Floor Plan (Auto)");
        cropStatusLbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        cropStatusLbl.setForeground(new Color(56, 189, 248));

        ModernButton dragSnipBtn = new ModernButton("\u2702 Select on Canvas", new Color(13, 148, 136), Color.WHITE, new Color(45, 212, 191));
        dragSnipBtn.setPreferredSize(new Dimension(140, 26));

        ModernButton resetCropBtn = new ModernButton("Reset Full", new Color(30, 41, 59), new Color(203, 213, 225), new Color(71, 85, 105));
        resetCropBtn.setPreferredSize(new Dimension(90, 26));
        resetCropBtn.setVisible(false);

        frameActionPanel.add(cropStatusLbl);
        frameActionPanel.add(dragSnipBtn);
        frameActionPanel.add(resetCropBtn);
        gbc.gridx = 1;
        form.add(frameActionPanel, gbc);

        // 3. Resolution
        gbc.gridx = 0; gbc.gridy = 4;
        JLabel resLbl = new JLabel("Resolution:");
        resLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        resLbl.setForeground(new Color(203, 213, 225));
        form.add(resLbl, gbc);

        JComboBox<String> resCombo = new JComboBox<>(new String[]{
                "1080p Full HD (1920 \u00d7 1080 / 1080 \u00d7 1920)",
                "4K Ultra HD (3840 \u00d7 2160 / 2160 \u00d7 3840)",
                "720p Standard HD (1280 \u00d7 720 / 720 \u00d7 1280)"
        });
        styleComboBox(resCombo);
        gbc.gridx = 1;
        form.add(resCombo, gbc);

        // 4. File Format
        gbc.gridx = 0; gbc.gridy = 5;
        JLabel formatLbl = new JLabel("Image Format:");
        formatLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        formatLbl.setForeground(new Color(203, 213, 225));
        form.add(formatLbl, gbc);

        JComboBox<String> formatCombo = new JComboBox<>(new String[]{
                "PNG (.png) \u2014 Lossless Sharp Lines",
                "JPEG (.jpg) \u2014 Web Ready (95% Quality)"
        });
        styleComboBox(formatCombo);
        gbc.gridx = 1;
        form.add(formatCombo, gbc);

        // 5. Drawing Style
        gbc.gridx = 0; gbc.gridy = 6;
        JLabel styleLbl = new JLabel("Drawing Style:");
        styleLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        styleLbl.setForeground(new Color(203, 213, 225));
        form.add(styleLbl, gbc);

        JComboBox<String> styleCombo = new JComboBox<>(new String[]{
                "Monochrome (Black Lines on White)",
                "Color (Architectural Presentation)"
        });
        styleComboBox(styleCombo);
        gbc.gridx = 1;
        form.add(styleCombo, gbc);

        // 6. Wall Hatch
        gbc.gridx = 0; gbc.gridy = 7;
        JLabel hatchLbl = new JLabel("Wall Hatch:");
        hatchLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        hatchLbl.setForeground(new Color(203, 213, 225));
        form.add(hatchLbl, gbc);

        JComboBox<String> hatchCombo = new JComboBox<>(new String[]{
                "45\u00b0 Diagonal (ANSI31 Masonry)",
                "Solid Black Poch\u00e9",
                "Hollow"
        });
        styleComboBox(hatchCombo);
        gbc.gridx = 1;
        form.add(hatchCombo, gbc);

        // 7. Title Bar / Disclaimer Checkbox
        gbc.gridx = 0; gbc.gridy = 8; gbc.gridwidth = 2;
        JCheckBox bannerCheck = new JCheckBox("Include HDE Studio Title Bar (Plan Size, Area & Disclaimer)", true);
        bannerCheck.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        bannerCheck.setForeground(new Color(203, 213, 225));
        bannerCheck.setOpaque(false);
        form.add(bannerCheck, gbc);
        gbc.gridwidth = 1;

        // ─── RIGHT: LIVE ASPECT RATIO VIEWPORT PREVIEW ───
        JPanel previewContainer = new JPanel(new BorderLayout(0, 8));
        previewContainer.setOpaque(false);
        previewContainer.setPreferredSize(new Dimension(420, 460));
        previewContainer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(30, 41, 59)),
                new EmptyBorder(12, 14, 12, 14)));

        JLabel prevHeader = new JLabel("\u25c8 LIVE ASPECT RATIO PREVIEW");
        prevHeader.setFont(new Font("Segoe UI", Font.BOLD, 12));
        prevHeader.setForeground(new Color(56, 189, 248));
        previewContainer.add(prevHeader, BorderLayout.NORTH);

        JPanel previewCanvas = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                int pw = getWidth(), ph = getHeight();
                g2.setColor(new Color(11, 17, 32));
                g2.fillRect(0, 0, pw, ph);
                if (cachedThumb[0] != null) {
                    BufferedImage thumb = cachedThumb[0];
                    double sc = Math.min((double)(pw - 20) / thumb.getWidth(), (double)(ph - 20) / thumb.getHeight());
                    int dw = (int)(thumb.getWidth() * sc), dh = (int)(thumb.getHeight() * sc);
                    int dx = (pw - dw) / 2, dy = (ph - dh) / 2;
                    g2.setColor(new Color(0, 0, 0, 100));
                    g2.fillRoundRect(dx + 4, dy + 4, dw, dh, 4, 4);
                    g2.drawImage(thumb, dx, dy, dw, dh, null);
                    g2.setColor(new Color(71, 85, 105));
                    g2.setStroke(new BasicStroke(1.2f));
                    g2.drawRect(dx, dy, dw, dh);
                }
            }
        };
        previewCanvas.setBackground(new Color(11, 17, 32));
        previewCanvas.setBorder(BorderFactory.createLineBorder(new Color(30, 41, 59)));
        previewContainer.add(previewCanvas, BorderLayout.CENTER);

        JLabel previewFooter = new JLabel("", JLabel.CENTER);
        previewFooter.setFont(new Font("Consolas", Font.PLAIN, 11));
        previewFooter.setForeground(new Color(148, 163, 184));
        previewContainer.add(previewFooter, BorderLayout.SOUTH);

        Runnable updatePreview = () -> {
            boolean is16x9 = isLandscape16x9[0];
            int resIdx = resCombo.getSelectedIndex();
            int baseW, baseH;
            if (resIdx == 1) {
                baseW = is16x9 ? 3840 : 2160;
                baseH = is16x9 ? 2160 : 3840;
            } else if (resIdx == 2) {
                baseW = is16x9 ? 1280 : 720;
                baseH = is16x9 ? 720 : 1280;
            } else {
                baseW = is16x9 ? 1920 : 1080;
                baseH = is16x9 ? 1080 : 1920;
            }

            boolean isMono = (styleCombo.getSelectedIndex() == 0);
            boolean showBanner = bannerCheck.isSelected();
            String hStyle = hatchCombo.getSelectedIndex() == 0 ? "DIAGONAL_45" :
                           (hatchCombo.getSelectedIndex() == 1 ? "SOLID" : "HOLLOW");

            double aspect = (double) baseW / baseH;
            int prevW, prevH;
            if (aspect >= 1.0) { prevW = 540; prevH = (int)(540 / aspect); }
            else               { prevH = 540; prevW = (int)(540 * aspect); }

            cachedThumb[0] = renderArchitecturalDrawing(prevW, prevH, isMono, !showBanner, hStyle, activeCropWorld[0]);
            String ratioTag = is16x9 ? "16:9 Landscape" : "9:16 Vertical";
            String cropTag = (activeCropWorld[0] != null) ? "Custom Region" : "Full Plan";
            previewFooter.setText(ratioTag + "  |  " + baseW + " \u00d7 " + baseH + " px  |  " + cropTag);
            previewCanvas.repaint();
        };

        btn16x9.addActionListener(e -> {
            isLandscape16x9[0] = true;
            btn16x9.setSelected(true);
            btn9x16.setSelected(false);
            if (activeCropWorld[0] != null) {
                activeCropWorld[0] = null;
                cropStatusLbl.setText("Full Floor Plan (Auto)");
                resetCropBtn.setVisible(false);
            }
            updatePreview.run();
        });

        btn9x16.addActionListener(e -> {
            isLandscape16x9[0] = false;
            btn9x16.setSelected(true);
            btn16x9.setSelected(false);
            if (activeCropWorld[0] != null) {
                activeCropWorld[0] = null;
                cropStatusLbl.setText("Full Floor Plan (Auto)");
                resetCropBtn.setVisible(false);
            }
            updatePreview.run();
        });

        dragSnipBtn.addActionListener(e -> {
            double aspectVal = isLandscape16x9[0] ? (16.0 / 9.0) : (9.0 / 16.0);
            dlg.setVisible(false);
            viewportPanel.startSnipMode(aspectVal, cropWorld -> {
                if (cropWorld != null) {
                    activeCropWorld[0] = cropWorld;
                    cropStatusLbl.setText("Custom: " + Math.round(cropWorld.width / FT_PX) + "' \u00d7 " + Math.round(cropWorld.height / FT_PX) + "'");
                    resetCropBtn.setVisible(true);
                }
                dlg.setVisible(true);
                updatePreview.run();
            });
        });

        resetCropBtn.addActionListener(e -> {
            activeCropWorld[0] = null;
            cropStatusLbl.setText("Full Floor Plan (Auto)");
            resetCropBtn.setVisible(false);
            updatePreview.run();
        });

        resCombo.addActionListener(e -> updatePreview.run());
        formatCombo.addActionListener(e -> updatePreview.run());
        styleCombo.addActionListener(e -> updatePreview.run());
        hatchCombo.addActionListener(e -> updatePreview.run());
        bannerCheck.addActionListener(e -> updatePreview.run());

        updatePreview.run();

        mainContainer.add(form, BorderLayout.CENTER);
        mainContainer.add(previewContainer, BorderLayout.EAST);
        dlg.add(mainContainer, BorderLayout.CENTER);

        // ─── BUTTONS ───
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 14));
        btnPanel.setOpaque(false);
        btnPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(30, 41, 59)));

        ModernButton cancelBtn = new ModernButton("Cancel", new Color(30, 41, 59), new Color(203, 213, 225), new Color(71, 85, 105));
        cancelBtn.addActionListener(e -> dlg.dispose());

        ModernButton copyBtn = new ModernButton("\uD83D\uDCCB Copy to Clipboard", new Color(30, 41, 59), new Color(203, 213, 225), new Color(71, 85, 105));
        copyBtn.setPreferredSize(new Dimension(150, 32));
        copyBtn.addActionListener(e -> {
            boolean is16x9 = isLandscape16x9[0];
            int resIdx = resCombo.getSelectedIndex();
            int baseW = (resIdx == 1) ? (is16x9 ? 3840 : 2160) : (resIdx == 2 ? (is16x9 ? 1280 : 720) : (is16x9 ? 1920 : 1080));
            int baseH = (resIdx == 1) ? (is16x9 ? 2160 : 3840) : (resIdx == 2 ? (is16x9 ? 720 : 1280) : (is16x9 ? 1080 : 1920));
            boolean isMono = (styleCombo.getSelectedIndex() == 0);
            boolean showBanner = bannerCheck.isSelected();
            String hStyle = hatchCombo.getSelectedIndex() == 0 ? "DIAGONAL_45" : (hatchCombo.getSelectedIndex() == 1 ? "SOLID" : "HOLLOW");

            BufferedImage fullImg = renderArchitecturalDrawing(baseW, baseH, isMono, !showBanner, hStyle, activeCropWorld[0]);
            copyImageToClipboard(fullImg);
            JOptionPane.showMessageDialog(dlg,
                    "Image copied to clipboard!\n\n" +
                    "Ratio: " + (is16x9 ? "16:9 Landscape" : "9:16 Vertical") + "\n" +
                    "Dimensions: " + baseW + " \u00d7 " + baseH + " px\n\n" +
                    "You can now paste it directly into social media, chat, or presentations (Ctrl+V).",
                    "Copied to Clipboard", JOptionPane.INFORMATION_MESSAGE);
        });

        ModernButton saveBtn = new ModernButton("\uD83D\uDCBE Save Image...", new Color(13, 148, 136), Color.WHITE, new Color(45, 212, 191));
        saveBtn.setPreferredSize(new Dimension(140, 32));
        saveBtn.addActionListener(e -> {
            boolean is16x9 = isLandscape16x9[0];
            int resIdx = resCombo.getSelectedIndex();
            int baseW = (resIdx == 1) ? (is16x9 ? 3840 : 2160) : (resIdx == 2 ? (is16x9 ? 1280 : 720) : (is16x9 ? 1920 : 1080));
            int baseH = (resIdx == 1) ? (is16x9 ? 2160 : 3840) : (resIdx == 2 ? (is16x9 ? 720 : 1280) : (is16x9 ? 1080 : 1920));
            boolean isMono = (styleCombo.getSelectedIndex() == 0);
            boolean showBanner = bannerCheck.isSelected();
            String hStyle = hatchCombo.getSelectedIndex() == 0 ? "DIAGONAL_45" : (hatchCombo.getSelectedIndex() == 1 ? "SOLID" : "HOLLOW");
            boolean isPng = (formatCombo.getSelectedIndex() == 0);
            String ext = isPng ? "png" : "jpg";

            String ratioTag = is16x9 ? "16x9" : "9x16";
            String styleTag = isMono ? "Mono" : "Color";
            String defName = "HDE_Snip_" + ratioTag + "_" + baseW + "x" + baseH + "_" + styleTag + "." + ext;

            JFileChooser fc = new JFileChooser();
            fc.setSelectedFile(new File(defName));
            if (fc.showSaveDialog(dlg) == JFileChooser.APPROVE_OPTION) {
                File dest = fc.getSelectedFile();
                if (!dest.getName().toLowerCase().endsWith("." + ext)) {
                    dest = new File(dest.getParentFile(), dest.getName() + "." + ext);
                }
                try {
                    BufferedImage fullImg = renderArchitecturalDrawing(baseW, baseH, isMono, !showBanner, hStyle, activeCropWorld[0]);
                    saveImageToFile(fullImg, dest, ext);
                    JOptionPane.showMessageDialog(dlg,
                            "Image saved successfully:\n" + dest.getAbsolutePath() +
                            "\n\nRatio: " + (is16x9 ? "16:9 Landscape" : "9:16 Vertical") +
                            "\nResolution: " + baseW + " \u00d7 " + baseH + " px (" + ext.toUpperCase() + ")",
                            "Snip Saved", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(dlg,
                            "Failed to save image: " + ex.getMessage(),
                            "Save Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        btnPanel.add(cancelBtn);
        btnPanel.add(copyBtn);
        btnPanel.add(saveBtn);
        dlg.add(btnPanel, BorderLayout.SOUTH);

        dlg.pack();
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    private void saveImageToFile(BufferedImage img, File file, String format) throws IOException {
        if ("jpg".equalsIgnoreCase(format) || "jpeg".equalsIgnoreCase(format)) {
            BufferedImage rgbImg = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgbImg.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, img.getWidth(), img.getHeight());
            g.drawImage(img, 0, 0, null);
            g.dispose();

            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
            if (writers.hasNext()) {
                ImageWriter writer = writers.next();
                ImageWriteParam param = writer.getDefaultWriteParam();
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(0.95f);
                try (ImageOutputStream ios = ImageIO.createImageOutputStream(file)) {
                    writer.setOutput(ios);
                    writer.write(null, new IIOImage(rgbImg, null, null), param);
                }
                writer.dispose();
            } else {
                ImageIO.write(rgbImg, "jpeg", file);
            }
        } else {
            ImageIO.write(img, "png", file);
        }
    }

    public void copyImageToClipboard(BufferedImage img) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new Transferable() {
            @Override
            public DataFlavor[] getTransferDataFlavors() {
                return new DataFlavor[]{ DataFlavor.imageFlavor };
            }
            @Override
            public boolean isDataFlavorSupported(DataFlavor flavor) {
                return DataFlavor.imageFlavor.equals(flavor);
            }
            @Override
            public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
                if (DataFlavor.imageFlavor.equals(flavor)) return img;
                throw new UnsupportedFlavorException(flavor);
            }
        }, null);
    }


    /**
     * Professional Architectural Drawing Renderer.
     * Supports:
     * 1) Social Media Clean Mode: Maximizes floor plan bounding box to fill ~92% of the canvas with slim margins,
     *    omits bulky contractor title table, and places an ultra-crisp corner watermark badge.
     * 2) Working Drawing Sheet Mode: Double border frame with full contractor title block table.
     * 3) Architectural Wall Hatching: ANSI31 45° diagonal masonry hatching, solid poché, or hollow.
     * 4) Floor / Tile Patterns: Real architectural flooring hatches rendered below wall cut lines.
     */
    public BufferedImage renderArchitecturalDrawing(int targetW, int targetH, boolean isMonochrome, boolean isSocialClean, String hatchStyle) {
        return renderArchitecturalDrawing(targetW, targetH, isMonochrome, isSocialClean, hatchStyle, null);
    }

    public BufferedImage renderArchitecturalDrawing(int targetW, int targetH, boolean isMonochrome, boolean isSocialClean, String hatchStyle, Rectangle2D.Double customCrop) {
        BufferedImage img = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // 1. Pure White Architectural Drawing Paper
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, targetW, targetH);

        // Margins & Framing
        double baseDim = Math.min(targetW, targetH);
        double padOuter, padInner;
        double titleBlockHeight = 0;
        double titleBlockY = targetH;

        if (isSocialClean) {
            padOuter = baseDim * 0.018;
            padInner = baseDim * 0.024;
            g2.setColor(isMonochrome ? new Color(180, 180, 180) : new Color(203, 213, 225));
            g2.setStroke(new BasicStroke(1.0f));
            g2.draw(new Rectangle2D.Double(padInner, padInner, targetW - 2 * padInner, targetH - 2 * padInner));
        } else {
            // Standard AutoCAD border & slim, perfectly proportional title block strip
            padOuter = baseDim * 0.018;
            padInner = baseDim * 0.026;
            titleBlockHeight = Math.max(82.0, baseDim * 0.080);
            titleBlockY = targetH - padInner - titleBlockHeight;

            g2.setColor(isMonochrome ? Color.BLACK : new Color(30, 41, 59));
            g2.setStroke(new BasicStroke(1.0f));
            g2.draw(new Rectangle2D.Double(padOuter, padOuter, targetW - 2 * padOuter, targetH - 2 * padOuter));
            g2.setStroke(new BasicStroke(2.0f));
            g2.draw(new Rectangle2D.Double(padInner, padInner, targetW - 2 * padInner, targetH - 2 * padInner));
        }

        // 2. Compute Drawing Bounding Box & Architectural Footprint
        double rawMinX = Double.MAX_VALUE, rawMaxX = -Double.MAX_VALUE;
        double rawMinY = Double.MAX_VALUE, rawMaxY = -Double.MAX_VALUE;

        if (customCrop != null) {
            rawMinX = customCrop.x;
            rawMaxX = customCrop.x + customCrop.width;
            rawMinY = customCrop.y;
            rawMaxY = customCrop.y + customCrop.height;
        } else {
            for (Wall w : walls) {
                rawMinX = Math.min(rawMinX, Math.min(w.x1, w.x2)); rawMaxX = Math.max(rawMaxX, Math.max(w.x1, w.x2));
                rawMinY = Math.min(rawMinY, Math.min(w.y1, w.y2)); rawMaxY = Math.max(rawMaxY, Math.max(w.y1, w.y2));
            }
            for (CADElement el : elements) {
                if (el.type.startsWith("floor-") || el.type.equals("room") || el.type.equals("wall")) {
                    rawMinX = Math.min(rawMinX, el.x); rawMaxX = Math.max(rawMaxX, el.x + el.w);
                    rawMinY = Math.min(rawMinY, el.y); rawMaxY = Math.max(rawMinY, el.y + el.h);
                }
            }
        }

        double minX = rawMinX, maxX = rawMaxX;
        double minY = rawMinY, maxY = rawMaxY;

        if (customCrop == null) {
            for (CADElement el : elements) {
                minX = Math.min(minX, el.x); maxX = Math.max(maxX, el.x + el.w);
                minY = Math.min(minY, el.y); maxY = Math.max(maxY, el.y + el.h);
            }
            for (CADDimension d : dimensions) {
                minX = Math.min(minX, Math.min(d.x1, d.x2)); maxX = Math.max(maxX, Math.max(d.x1, d.x2));
                minY = Math.min(minY, Math.min(d.y1, d.y2)); maxY = Math.max(maxY, Math.max(d.y1, d.y2));
            }
        }

        if (walls.isEmpty() && elements.isEmpty() && customCrop == null) {
            minX = 0; maxX = 800; minY = 0; maxY = 600;
            rawMinX = 0; rawMaxX = 800; rawMinY = 0; rawMaxY = 600;
        }

        // Calculate building footprint size in feet and area in sq ft
        double footprintW_Px = (rawMaxX > rawMinX && rawMaxX < Double.MAX_VALUE / 2) ? (rawMaxX - rawMinX) : (30.0 * FT_PX);
        double footprintH_Px = (rawMaxY > rawMinY && rawMaxY < Double.MAX_VALUE / 2) ? (rawMaxY - rawMinY) : (40.0 * FT_PX);
        int planWidthFt = Math.max(1, (int) Math.round(footprintW_Px / FT_PX));
        int planDepthFt = Math.max(1, (int) Math.round(footprintH_Px / FT_PX));
        long totalAreaSqFt = (long) planWidthFt * (long) planDepthFt;

        String planSizeText = planWidthFt + "' \u00d7 " + planDepthFt + "' FT";
        String totalAreaText = String.format("%,d SQ.FT.", totalAreaSqFt);

        double modelMargin = (customCrop != null) ? (0.5 * FT_PX) : (isSocialClean ? (1.5 * FT_PX) : (2.5 * FT_PX));
        minX -= modelMargin; maxX += modelMargin;
        minY -= modelMargin; maxY += modelMargin;

        double boxW = maxX - minX;
        double boxH = maxY - minY;
        if (boxW <= 0) boxW = 800;
        if (boxH <= 0) boxH = 600;

        double availDrawW, availDrawH;
        if (isSocialClean) {
            availDrawW = targetW - 2.0 * padInner - 16.0;
            availDrawH = targetH - 2.0 * padInner - 16.0;
        } else {
            availDrawW = targetW - 2.0 * padInner - 20.0;
            availDrawH = (titleBlockY - padInner) - 20.0;
        }

        double scale = Math.min(availDrawW / boxW, availDrawH / boxH);

        // Center perfectly within drawing frame
        double drawX, drawY;
        if (isSocialClean) {
            drawX = padInner + 8.0 + (availDrawW - boxW * scale) / 2.0 - minX * scale;
            drawY = padInner + 8.0 + (availDrawH - boxH * scale) / 2.0 - minY * scale;
        } else {
            drawX = padInner + 12.0 + (availDrawW - boxW * scale) / 2.0 - minX * scale;
            drawY = padInner + 12.0 + (availDrawH - boxH * scale) / 2.0 - minY * scale;
        }

        // 3. Render Architectural Plan in Scaled Space
        AffineTransform origTx = g2.getTransform();
        g2.translate(drawX, drawY);
        g2.scale(scale, scale);

        // --- Layer 0: Floor Finishes & Tile Hatches ---
        for (CADElement el : elements) {
            if (!el.type.startsWith("floor-")) continue;

            Shape oldClip = g2.getClip();
            Path2D.Double poly = (el.points != null && el.points.size() >= 3) ? el.getPolygonPath() : null;

            if (poly != null) {
                g2.clip(poly);
            }

            AffineTransform fTx = g2.getTransform();
            g2.translate(el.x, el.y);
            if (poly == null) {
                g2.rotate(Math.toRadians(el.rotation), el.w / 2.0, el.h / 2.0);
            }

            // Floor boundary & light fill
            if (!isMonochrome) {
                if (el.type.equals("floor-wood-plank")) g2.setColor(new Color(254, 243, 199, 140));
                else if (el.type.equals("floor-mosaic")) g2.setColor(new Color(224, 242, 254, 140));
                else if (el.type.equals("floor-pavers")) g2.setColor(new Color(254, 215, 170, 140));
                else g2.setColor(new Color(248, 250, 252, 180));
                g2.fill(new Rectangle2D.Double(0, 0, el.w, el.h));
            }

            g2.setColor(isMonochrome ? new Color(190, 190, 190) : new Color(148, 163, 184));
            g2.setStroke(new BasicStroke((float) (0.8 / scale)));
            if (poly == null) {
                g2.draw(new Rectangle2D.Double(0, 0, el.w, el.h));
            }

            // Tile joints & hatching patterns
            if (el.type.equals("floor-tiles-2x2") || el.type.equals("floor-tile-2x2")) {
                double step = 2.0 * FT_PX;
                for (double x = step; x < el.w; x += step) g2.draw(new Line2D.Double(x, 0, x, el.h));
                for (double y = step; y < el.h; y += step) g2.draw(new Line2D.Double(0, y, el.w, y));
            } else if (el.type.equals("floor-tiles-1x1") || el.type.equals("floor-tile-1x1")) {
                double step = 1.0 * FT_PX;
                for (double x = step; x < el.w; x += step) g2.draw(new Line2D.Double(x, 0, x, el.h));
                for (double y = step; y < el.h; y += step) g2.draw(new Line2D.Double(0, y, el.w, y));
            } else if (el.type.equals("floor-tiles-2x4") || el.type.equals("floor-marble-2x4")) {
                double stepX = 2.0 * FT_PX, stepY = 4.0 * FT_PX;
                for (double x = stepX; x < el.w; x += stepX) g2.draw(new Line2D.Double(x, 0, x, el.h));
                for (double y = stepY; y < el.h; y += stepY) g2.draw(new Line2D.Double(0, y, el.w, y));
            } else if (el.type.equals("floor-tiles-diagonal")) {
                double step = 2.0 * FT_PX;
                double diagMax = el.w + el.h;
                for (double d = -el.h; d < diagMax; d += step) {
                    g2.draw(new Line2D.Double(d, 0, d + el.h, el.h));
                    g2.draw(new Line2D.Double(d, el.h, d + el.h, 0));
                }
            } else if (el.type.equals("floor-wood-plank")) {
                double plankW = 0.5 * FT_PX, plankL = 3.5 * FT_PX;
                int row = 0;
                for (double y = 0; y < el.h; y += plankW) {
                    g2.draw(new Line2D.Double(0, y, el.w, y));
                    double offset = (row % 3) * (plankL / 3.0);
                    for (double x = offset; x < el.w; x += plankL) {
                        g2.draw(new Line2D.Double(x, y, x, Math.min(el.h, y + plankW)));
                    }
                    row++;
                }
            } else if (el.type.equals("floor-mosaic")) {
                double step = 0.333 * FT_PX;
                for (double x = step; x < el.w; x += step) g2.draw(new Line2D.Double(x, 0, x, el.h));
                for (double y = step; y < el.h; y += step) g2.draw(new Line2D.Double(0, y, el.w, y));
            } else if (el.type.equals("floor-concrete")) {
                Random r = new Random(42);
                int dots = (int) ((el.w * el.h) / (150.0));
                dots = Math.min(dots, 4000);
                for (int i = 0; i < dots; i++) {
                    int px = r.nextInt((int) Math.max(1, el.w));
                    int py = r.nextInt((int) Math.max(1, el.h));
                    g2.fillRect(px, py, 1, 1);
                }
            } else if (el.type.equals("floor-pavers")) {
                double pH = 0.333 * FT_PX, pW = 0.666 * FT_PX;
                int row = 0;
                for (double y = 0; y < el.h; y += pH) {
                    g2.draw(new Line2D.Double(0, y, el.w, y));
                    double off = (row % 2 == 0) ? 0 : pW / 2.0;
                    for (double x = off; x < el.w; x += pW) {
                        g2.draw(new Line2D.Double(x, y, x, Math.min(el.h, y + pH)));
                    }
                    row++;
                }
            }
            g2.setTransform(fTx);
            g2.setClip(oldClip);

            // Draw crisp perimeter polyline boundary
            if (poly != null) {
                g2.setColor(isMonochrome ? new Color(120, 120, 120) : new Color(100, 116, 139));
                g2.setStroke(new BasicStroke((float) (1.0 / scale)));
                g2.draw(poly);
            }
        }

        // --- Layer 1: Walls (Monolithic Cut Lines & 45° ANSI31 Hatching) ---
        Area combinedWallsArea = new Area();
        List<Line2D.Double> thresholdLines = new ArrayList<>();

        for (Wall w : walls) {
            double len = w.getLength();
            if (len <= 0) continue;
            double ux = (w.x2 - w.x1) / len, uy = (w.y2 - w.y1) / len;
            double wallThickPx = (w.thicknessInches / 12.0) * FT_PX;

            List<double[]> cutouts = new ArrayList<>();
            for (CADElement el : elements) {
                if (!el.isOpening()) continue;
                Point2D.Double ec = new Point2D.Double(el.x + el.w / 2.0, el.y + el.h / 2.0);
                double d = Line2D.ptSegDist(w.x1, w.y1, w.x2, w.y2, ec.x, ec.y);
                if (d <= (wallThickPx / 2.0) + (1.2 * FT_PX)) {
                    double tMid = (ec.x - w.x1) * ux + (ec.y - w.y1) * uy;
                    double half = el.w / 2.0;
                    double tStart = Math.max(0, tMid - half), tEnd = Math.min(len, tMid + half);
                    if (tEnd > tStart) cutouts.add(new double[]{tStart, tEnd});
                }
            }

            cutouts.sort(Comparator.comparingDouble(a -> a[0]));
            List<double[]> merged = new ArrayList<>();
            for (double[] c : cutouts) {
                if (merged.isEmpty()) merged.add(c);
                else {
                    double[] last = merged.get(merged.size() - 1);
                    if (c[0] <= last[1]) last[1] = Math.max(last[1], c[1]);
                    else merged.add(c);
                }
            }

            List<double[]> solidSegs = new ArrayList<>();
            double curT = 0;
            for (double[] c : merged) {
                if (c[0] > curT) solidSegs.add(new double[]{curT, c[0]});
                curT = Math.max(curT, c[1]);
            }
            if (curT < len) solidSegs.add(new double[]{curT, len});

            double hThick = wallThickPx / 2.0;
            double nx = -uy, ny = ux;

            for (double[] seg : solidSegs) {
                if (seg[1] - seg[0] < 1.0) continue;
                double sx1 = w.x1 + ux * seg[0], sy1 = w.y1 + uy * seg[0];
                double sx2 = w.x1 + ux * seg[1], sy2 = w.y1 + uy * seg[1];

                Path2D.Double wp = new Path2D.Double();
                wp.moveTo(sx1 + nx * hThick, sy1 + ny * hThick);
                wp.lineTo(sx2 + nx * hThick, sy2 + ny * hThick);
                wp.lineTo(sx2 - nx * hThick, sy2 - ny * hThick);
                wp.lineTo(sx1 - nx * hThick, sy1 - ny * hThick);
                wp.closePath();
                combinedWallsArea.add(new Area(wp));
            }

            for (double[] c : merged) {
                double cx1 = w.x1 + ux * c[0], cy1 = w.y1 + uy * c[0];
                double cx2 = w.x1 + ux * c[1], cy2 = w.y1 + uy * c[1];
                thresholdLines.add(new Line2D.Double(cx1, cy1, cx2, cy2));
            }
        }

        // Draw Wall Fills & 45° ANSI31 diagonal hatching
        String activeHatch = (hatchStyle != null) ? hatchStyle : wallHatchStyle;
        if ("DIAGONAL_45".equals(activeHatch)) {
            g2.setColor(Color.WHITE);
            g2.fill(combinedWallsArea);

            Shape oldClip = g2.getClip();
            g2.clip(combinedWallsArea);
            g2.setColor(isMonochrome ? Color.BLACK : new Color(30, 41, 59));
            g2.setStroke(new BasicStroke((float) (0.9 / scale)));
            Rectangle2D b = combinedWallsArea.getBounds2D();
            double step = Math.max(4.0, 6.0 / scale);
            for (double x = b.getMinX() - b.getHeight(); x < b.getMaxX(); x += step) {
                g2.draw(new Line2D.Double(x, b.getMaxY(), x + b.getHeight(), b.getMinY()));
            }
            g2.setClip(oldClip);
        } else if ("SOLID".equals(activeHatch)) {
            g2.setColor(isMonochrome ? Color.BLACK : new Color(24, 33, 53));
            g2.fill(combinedWallsArea);
        } else {
            g2.setColor(Color.WHITE);
            g2.fill(combinedWallsArea);
        }

        // Heavy cut contour line (2.4pt)
        g2.setColor(isMonochrome ? Color.BLACK : new Color(15, 23, 42));
        g2.setStroke(new BasicStroke((float) (2.4 / scale), BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
        g2.draw(combinedWallsArea);

        // Threshold hairline in openings
        g2.setColor(isMonochrome ? new Color(100, 100, 100) : new Color(148, 163, 184));
        g2.setStroke(new BasicStroke((float) (1.0 / scale)));
        for (Line2D.Double t : thresholdLines) g2.draw(t);

        // --- Layer 2: Openings (Doors & Windows Standard 2D AutoCAD Symbols) ---
        for (CADElement el : elements) {
            if (el.isOpening()) {
                int ew = (int) Math.round(el.w);
                int eh = (int) Math.round(el.h);
                AffineTransform elTx = g2.getTransform();
                g2.translate(el.x + el.w / 2.0, el.y + el.h / 2.0);
                g2.rotate(Math.toRadians(el.rotation));
                if (el.flipH) g2.scale(-1, 1);
                if (el.flipV) g2.scale(1, -1);
                g2.translate(-ew / 2.0, -eh / 2.0);

                renderAutoCADBlock(g2, el, ew, eh, isMonochrome, scale);

                g2.setTransform(elTx);
            }
        }

        // --- Layer 2.5: All Inserted Elements (Standard AutoCAD 2D Drafting Blocks) ---
        for (CADElement el : elements) {
            if (el.isOpening() || el.type.startsWith("floor-") || "tag".equals(el.type) || "north-arrow".equals(el.type)) {
                continue;
            }
            int ew = (int) Math.round(el.w);
            int eh = (int) Math.round(el.h);
            AffineTransform elTx = g2.getTransform();
            g2.translate(el.x + el.w / 2.0, el.y + el.h / 2.0);
            g2.rotate(Math.toRadians(el.rotation));
            if (el.flipH) g2.scale(-1, 1);
            if (el.flipV) g2.scale(1, -1);
            g2.translate(-ew / 2.0, -eh / 2.0);

            renderAutoCADBlock(g2, el, ew, eh, isMonochrome, scale);

            g2.setTransform(elTx);
        }

        // --- Layer 3: Architectural Dimensions ---
        for (CADDimension d : dimensions) {
            double dx = d.x2 - d.x1, dy = d.y2 - d.y1;
            double len = Math.hypot(dx, dy);
            if (len <= 0) continue;
            double ux = dx / len, uy = dy / len;
            double nx = -uy, ny = ux;

            double p1x = d.x1 + nx * d.offset, p1y = d.y1 + ny * d.offset;
            double p2x = d.x2 + nx * d.offset, p2y = d.y2 + ny * d.offset;

            g2.setColor(isMonochrome ? Color.BLACK : new Color(51, 65, 85));
            g2.setStroke(new BasicStroke((float) (0.65 / scale)));

            // Extension witness lines
            g2.draw(new Line2D.Double(d.x1, d.y1, p1x + nx * (3.0 / scale), p1y + ny * (3.0 / scale)));
            g2.draw(new Line2D.Double(d.x2, d.y2, p2x + nx * (3.0 / scale), p2y + ny * (3.0 / scale)));

            // Dimension line
            g2.draw(new Line2D.Double(p1x, p1y, p2x, p2y));

            // 45-degree architectural slash ticks
            double tickSize = 4.0 / scale;
            g2.setStroke(new BasicStroke((float) (0.9 / scale)));
            g2.draw(new Line2D.Double(p1x - tickSize, p1y + tickSize, p1x + tickSize, p1y - tickSize));
            g2.draw(new Line2D.Double(p2x - tickSize, p2y + tickSize, p2x + tickSize, p2y - tickSize));

            // Dimension text badge
            String dimText = formatImperial(len);
            float fontSize = (float) Math.max(10.0, 11.5 / scale);
            g2.setFont(new Font("Consolas", Font.BOLD, (int) fontSize));
            FontMetrics fm = g2.getFontMetrics();
            int strW = fm.stringWidth(dimText);
            double midX = (p1x + p2x) / 2.0;
            double midY = (p1y + p2y) / 2.0;

            AffineTransform dimTx = g2.getTransform();
            g2.translate(midX, midY);
            double angle = Math.toDegrees(Math.atan2(dy, dx));
            if (angle > 90 || angle < -90) angle += 180;
            g2.rotate(Math.toRadians(angle));

            g2.setColor(Color.WHITE);
            g2.fillRect(-strW / 2 - 3, -fm.getAscent() / 2 - 2, strW + 6, fm.getAscent() + 4);
            g2.setColor(isMonochrome ? Color.BLACK : new Color(15, 23, 42));
            g2.drawString(dimText, -strW / 2, fm.getAscent() / 2 - 2);

            g2.setTransform(dimTx);
        }

        // --- Layer 4: Room Tags & North Arrow (Standard AutoCAD Architectural Style) ---
        for (CADElement el : elements) {
            if ("tag".equals(el.type)) {
                String[] rawLines = el.name.contains("\n") ? el.name.split("\n") : (el.name.contains(" / ") ? el.name.split("\\s*/\\s*") : new String[]{el.name});
                List<String> displayLines = new ArrayList<>();
                boolean hasAreaLine = false;
                for (String l : rawLines) {
                    String trimmed = l.trim();
                    if (!trimmed.isEmpty()) {
                        displayLines.add(trimmed);
                        String up = trimmed.toUpperCase();
                        if (up.contains("SQFT") || up.contains("SQ.FT") || up.contains("SQ FT") || up.contains("SQ. FT")) {
                            hasAreaLine = true;
                        }
                    }
                }
                if (!hasAreaLine) {
                    double roomSqFt = parseRoomAreaFromText(el.name);
                    if (roomSqFt <= 0) {
                        roomSqFt = computeRoomAreaFromWalls(el.x + el.w / 2.0, el.y + el.h / 2.0);
                    }
                    if (roomSqFt > 0) {
                        displayLines.add(String.format("%.0f SQ.FT.", roomSqFt));
                    }
                }
                if (displayLines.isEmpty()) {
                    displayLines.add("ROOM");
                }

                float tagFontSize = (float) Math.max(9.5, 11.0 / scale);
                Font titleFont = new Font("Consolas", Font.BOLD, (int) tagFontSize);
                Font dimFont   = new Font("Consolas", Font.PLAIN, (int) (tagFontSize * 0.92));
                Font areaFont  = new Font("Consolas", Font.BOLD, (int) (tagFontSize * 0.95));

                g2.setFont(titleFont);
                FontMetrics fm = g2.getFontMetrics();
                double lineH = fm.getHeight() * 1.12;
                double totalH = displayLines.size() * lineH;
                double startY = el.y + (el.h - totalH) / 2.0 + fm.getAscent();

                for (int i = 0; i < displayLines.size(); i++) {
                    String line = displayLines.get(i);
                    boolean isTitle = (i == 0);
                    boolean isArea = (!hasAreaLine && i == displayLines.size() - 1);
                    g2.setFont(isTitle ? titleFont : (isArea ? areaFont : dimFont));
                    FontMetrics fmL = g2.getFontMetrics();
                    int lw = fmL.stringWidth(line);
                    double lx = el.x + (el.w - lw) / 2.0;

                    // Clean AutoCAD text background mask (tight hairline box)
                    g2.setColor(Color.WHITE);
                    g2.fillRect((int) lx - 3, (int) (startY + i * lineH - fmL.getAscent()), lw + 6, fmL.getHeight());

                    if (isMonochrome) {
                        g2.setColor(Color.BLACK);
                    } else {
                        g2.setColor(isTitle ? new Color(15, 23, 42) : (isArea ? new Color(2, 132, 199) : new Color(51, 65, 85)));
                    }
                    g2.drawString(line, (float) lx, (float) (startY + i * lineH));
                }
            } else if ("north-arrow".equals(el.type)) {
                double cx = el.x + el.w / 2.0, cy = el.y + el.h / 2.0, r = el.w / 2.0;
                g2.setColor(isMonochrome ? Color.BLACK : new Color(15, 23, 42));
                g2.setStroke(new BasicStroke((float) (1.2 / scale)));
                g2.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));

                // Left wing (solid filled dark)
                Path2D.Double arrowL = new Path2D.Double();
                arrowL.moveTo(cx, cy - r * 0.88);
                arrowL.lineTo(cx - r * 0.35, cy + r * 0.55);
                arrowL.lineTo(cx, cy + r * 0.20);
                arrowL.closePath();
                g2.setColor(isMonochrome ? Color.BLACK : new Color(15, 23, 42));
                g2.fill(arrowL);

                // Right wing (white fill + outlined)
                Path2D.Double arrowR = new Path2D.Double();
                arrowR.moveTo(cx, cy - r * 0.88);
                arrowR.lineTo(cx + r * 0.35, cy + r * 0.55);
                arrowR.lineTo(cx, cy + r * 0.20);
                arrowR.closePath();
                g2.setColor(Color.WHITE);
                g2.fill(arrowR);
                g2.setColor(isMonochrome ? Color.BLACK : new Color(15, 23, 42));
                g2.draw(arrowR);

                // Center dividing meridian line
                g2.draw(new Line2D.Double(cx, cy - r * 0.88, cx, cy + r * 0.20));

                // Compass Cardinal "N"
                g2.setFont(new Font("Consolas", Font.BOLD, (int) (r * 0.65)));
                FontMetrics fmN = g2.getFontMetrics();
                g2.drawString("N", (float) (cx - fmN.stringWidth("N") / 2.0), (float) (cy - r * 1.05));
            }
        }

        g2.setTransform(origTx);

        // 4. Branding & Title Treatment
        if (isSocialClean) {
            // Sleek architectural brand watermark in bottom-right corner (leaves maximum space for plan)
            String brandTitle = "HDE STUDIO";
            String brandSub = "PLAN: " + planSizeText + "  |  " + totalAreaText;
            int brandFontSize = Math.max(14, (int) (baseDim * 0.013));
            int subFontSize   = Math.max(10, (int) (baseDim * 0.0085));
            g2.setFont(new Font("Segoe UI", Font.BOLD, brandFontSize));
            FontMetrics fmB = g2.getFontMetrics();
            g2.setFont(new Font("Consolas", Font.PLAIN, subFontSize));
            FontMetrics fmS = g2.getFontMetrics();
            int bW = Math.max(fmB.stringWidth(brandTitle), fmS.stringWidth(brandSub));
            float bx = (float) (targetW - padInner - bW - 20);
            float by = (float) (targetH - padInner - 28);

            g2.setColor(new Color(255, 255, 255, 235));
            g2.fillRoundRect((int) bx - 10, (int) by - fmB.getAscent() - 6, bW + 20, fmB.getHeight() + fmS.getHeight() + 10, 8, 8);
            g2.setColor(isMonochrome ? new Color(180, 180, 180) : new Color(203, 213, 225));
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRoundRect((int) bx - 10, (int) by - fmB.getAscent() - 6, bW + 20, fmB.getHeight() + fmS.getHeight() + 10, 8, 8);

            g2.setFont(new Font("Segoe UI", Font.BOLD, brandFontSize));
            g2.setColor(isMonochrome ? Color.BLACK : new Color(2, 132, 199));
            g2.drawString(brandTitle, bx, by);

            g2.setFont(new Font("Consolas", Font.PLAIN, subFontSize));
            g2.setColor(isMonochrome ? new Color(70, 70, 70) : new Color(71, 85, 105));
            g2.drawString(brandSub, bx, by + fmB.getHeight() + 2);

        } else {
            // Sleek AutoCAD Title Block & Legal Information Bar (Clearly legible, proportional fonts)
            double tbX = padInner;
            double tbW = targetW - 2.0 * padInner;
            g2.setColor(Color.WHITE);
            g2.fill(new Rectangle2D.Double(tbX, titleBlockY, tbW, titleBlockHeight));

            g2.setColor(isMonochrome ? Color.BLACK : new Color(30, 41, 59));
            g2.setStroke(new BasicStroke(1.5f));
            g2.draw(new Rectangle2D.Double(tbX, titleBlockY, tbW, titleBlockHeight));

            double midY = titleBlockY + titleBlockHeight * 0.50;
            g2.setStroke(new BasicStroke(0.8f));
            g2.draw(new Line2D.Double(tbX, midY, tbX + tbW, midY));

            // Fonts: Clearly legible across both previews and high-res A4/A3 prints
            int brandFontSize = Math.max(16, (int) (baseDim * 0.018));
            int infoFontSize  = Math.max(13, (int) (baseDim * 0.013));
            int discFontSize  = Math.max(12, (int) (baseDim * 0.011));

            Font brandFont = new Font("Segoe UI", Font.BOLD, brandFontSize);
            Font infoFont  = new Font("Segoe UI", Font.BOLD, infoFontSize);
            Font discFont  = new Font("Segoe UI", Font.BOLD, discFontSize);

            // Row 1 (Top Section): Firm Brand (Left) + Plan Size, Area, Scale, Sheet (Right)
            g2.setFont(brandFont);
            FontMetrics fmBrand = g2.getFontMetrics();
            float r1Baseline = (float) (titleBlockY + (titleBlockHeight * 0.50 + fmBrand.getAscent() - fmBrand.getDescent()) / 2.0);

            g2.setColor(isMonochrome ? Color.BLACK : new Color(2, 132, 199));
            g2.drawString("HDE STUDIO", (float) (tbX + 16), r1Baseline);

            // Row 1 Right: Plan Size | Total Area | Scale | Sheet
            g2.setFont(infoFont);
            FontMetrics fmInfo = g2.getFontMetrics();
            double ratio = (double) targetW / targetH;
            String sheetTag;
            if (Math.abs(ratio - (16.0 / 9.0)) < 0.05) {
                sheetTag = "16:9 (" + targetW + "\u00d7" + targetH + ")";
            } else if (Math.abs(ratio - (9.0 / 16.0)) < 0.05) {
                sheetTag = "9:16 (" + targetW + "\u00d7" + targetH + ")";
            } else {
                boolean isLandscape = targetW >= targetH;
                boolean isA3 = Math.max(targetW, targetH) >= 4000;
                sheetTag = (isA3 ? "A3" : "A4") + " " + (isLandscape ? "LANDSCAPE" : "PORTRAIT");
            }

            String specStr = "PLAN SIZE: " + planSizeText + "   |   TOTAL AREA: " + totalAreaText +
                             "   |   SCALE: FIT TO PAPER   |   SHEET: " + sheetTag;
            float specW = fmInfo.stringWidth(specStr);
            float specX = (float) (tbX + tbW - specW - 16);
            float brandEnd = (float) (tbX + 16 + fmBrand.stringWidth("HDE STUDIO") + 24);

            g2.setColor(isMonochrome ? Color.BLACK : new Color(51, 65, 85));
            if (specX > brandEnd) {
                g2.drawString(specStr, specX, r1Baseline);
            } else {
                String compactSpec = "PLAN: " + planSizeText + " (" + totalAreaText + ")   |   " + sheetTag;
                float cW = fmInfo.stringWidth(compactSpec);
                float cX = (float) (tbX + tbW - cW - 16);
                g2.drawString(compactSpec, Math.max(brandEnd, cX), r1Baseline);
            }

            // Row 2 (Bottom Section): Disclaimer Notice
            g2.setFont(discFont);
            FontMetrics fmDisc = g2.getFontMetrics();
            float r2Baseline = (float) (midY + (titleBlockHeight * 0.50 + fmDisc.getAscent() - fmDisc.getDescent()) / 2.0);

            String disclaimer = "DISCLAIMER: This plan is created in HDE Studio planner and this plan serves as concept design only, you may generate complete architectural floor plan, use only after Licensed architectural approval only.";

            g2.setColor(isMonochrome ? Color.BLACK : new Color(30, 41, 59));
            float discW = fmDisc.stringWidth(disclaimer);
            if (discW + 32 <= tbW) {
                g2.drawString(disclaimer, (float) (tbX + 16), r2Baseline);
            } else {
                String p1 = "DISCLAIMER: This plan is created in HDE Studio planner and this plan serves as concept design only,";
                String p2 = "you may generate complete architectural floor plan, use only after Licensed architectural approval only.";
                float row2H = (float) (titleBlockHeight * 0.50);
                float line1Y = (float) (midY + row2H * 0.35 + fmDisc.getAscent() / 2.0);
                float line2Y = (float) (midY + row2H * 0.78 + fmDisc.getAscent() / 2.0);
                g2.drawString(p1, (float) (tbX + 16), line1Y);
                g2.drawString(p2, (float) (tbX + 16), line2Y);
            }
        }

        g2.dispose();
        return img;
    }

    /**
     * Pure Java Standard Library PDF 1.4 Vector Container Writer.
     * Generates a fully compliant, self-contained PDF 1.4 file with high-definition DCTDecode raster image.
     */
    private void writeImageToPdf(BufferedImage img, File pdfFile) throws IOException {
        boolean isLandscape = img.getWidth() >= img.getHeight();
        boolean isA3 = Math.max(img.getWidth(), img.getHeight()) >= 4000;
        int pagePtW = isA3 ? (isLandscape ? 1191 : 842) : (isLandscape ? 842 : 595);
        int pagePtH = isA3 ? (isLandscape ? 842 : 1191) : (isLandscape ? 595 : 842);
        writeImageToPdf(img, pdfFile, pagePtW, pagePtH);
    }

    private void writeImageToPdf(BufferedImage img, File pdfFile, int pagePtW, int pagePtH) throws IOException {
        ByteArrayOutputStream imgBaos = new ByteArrayOutputStream();
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (writers.hasNext()) {
            ImageWriter writer = writers.next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.95f);
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(imgBaos)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(img, null, null), param);
            }
            writer.dispose();
        } else {
            ImageIO.write(img, "jpeg", imgBaos);
        }
        byte[] imgBytes = imgBaos.toByteArray();

        int imgW = img.getWidth();
        int imgH = img.getHeight();

        String obj1 = "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n";
        String obj2 = "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n";
        String obj3 = "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + pagePtW + " " + pagePtH + "] /Resources << /XObject << /Im1 4 0 R >> >> /Contents 5 0 R >>\nendobj\n";

        String obj4Header = "4 0 obj\n<< /Type /XObject /Subtype /Image /Width " + imgW + " /Height " + imgH + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length " + imgBytes.length + " >>\nstream\n";
        String obj4Footer = "\nendstream\nendobj\n";

        String contentStream = "q\n" + pagePtW + " 0 0 " + pagePtH + " 0 0 cm\n/Im1 Do\nQ\n";
        byte[] contentBytes = contentStream.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        String obj5 = "5 0 obj\n<< /Length " + contentBytes.length + " >>\nstream\n" + contentStream + "endstream\nendobj\n";

        try (FileOutputStream fos = new FileOutputStream(pdfFile)) {
            List<Long> offsets = new ArrayList<>();
            long currentOffset = 0;

            byte[] header = "%PDF-1.4\n%\u00e2\u00e3\u00cf\u00d3\n".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
            fos.write(header);
            currentOffset += header.length;

            // obj 1
            offsets.add(currentOffset);
            byte[] b1 = obj1.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            fos.write(b1);
            currentOffset += b1.length;

            // obj 2
            offsets.add(currentOffset);
            byte[] b2 = obj2.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            fos.write(b2);
            currentOffset += b2.length;

            // obj 3
            offsets.add(currentOffset);
            byte[] b3 = obj3.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            fos.write(b3);
            currentOffset += b3.length;

            // obj 4 (Image stream)
            offsets.add(currentOffset);
            byte[] b4H = obj4Header.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            fos.write(b4H);
            fos.write(imgBytes);
            byte[] b4F = obj4Footer.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            fos.write(b4F);
            currentOffset += b4H.length + imgBytes.length + b4F.length;

            // obj 5 (Page contents stream)
            offsets.add(currentOffset);
            byte[] b5 = obj5.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            fos.write(b5);
            currentOffset += b5.length;

            // xref table
            long startXref = currentOffset;
            StringBuilder xref = new StringBuilder();
            xref.append("xref\n0 6\n");
            xref.append("0000000000 65535 f \n");
            for (long off : offsets) {
                xref.append(String.format(Locale.US, "%010d 00000 n \n", off));
            }
            xref.append("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n");
            xref.append(startXref).append("\n%%EOF\n");

            fos.write(xref.toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        }
    }

    // =========================================================================
    // === STANDARD AUTOCAD 2D TECHNICAL WORKING DRAWING BLOCK ENGINE ===
    // =========================================================================

    public void renderAutoCADBlock(Graphics2D g2, CADElement el, int sw, int sh, boolean isMonochrome, double scale) {
        String type = el.type;

        // Trade-specific AutoCAD layer pen colors
        Color primaryCol = isMonochrome ? Color.BLACK : new Color(30, 41, 59);
        Color furnCol    = isMonochrome ? Color.BLACK : new Color(3, 105, 161);   // AutoCAD cyan/navy
        Color sanCol     = isMonochrome ? Color.BLACK : new Color(14, 116, 144);  // Plumbing teal-cyan
        Color kitCol     = isMonochrome ? Color.BLACK : new Color(15, 118, 110);  // Architectural casework teal
        Color appCol     = isMonochrome ? Color.BLACK : new Color(51, 65, 85);    // Appliances slate
        Color structCol  = isMonochrome ? Color.BLACK : new Color(30, 41, 59);    // Structural dark slate
        Color landCol    = isMonochrome ? Color.BLACK : new Color(21, 128, 61);   // Landscape green line
        Color vehCol     = isMonochrome ? Color.BLACK : new Color(51, 65, 85);    // Automotive line art
        Color elecCol    = isMonochrome ? Color.BLACK : new Color(180, 83, 9);    // Electrical amber

        float sHeavy = (float) Math.max(1.1, 1.4 / scale);
        float sMed   = (float) Math.max(0.8, 1.0 / scale);
        float sHair  = (float) Math.max(0.6, 0.75 / scale);
        float[] dashPattern = new float[]{ Math.max(3.0f, 4.0f / (float) scale), Math.max(2.0f, 3.0f / (float) scale) };

        // ─── WALL OPENING (Clean cased passage without leaf or frame) ───
        if (type.startsWith("opening")) {
            g2.setColor(primaryCol);
            g2.setStroke(new BasicStroke(sHeavy));
            g2.drawLine(0, 0, 0, sh);
            g2.drawLine(sw, 0, sw, sh);
            // Overhead lintel centerline (dashed)
            g2.setColor(isMonochrome ? new Color(120, 120, 120) : new Color(14, 116, 144));
            g2.setStroke(new BasicStroke(sHair, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, dashPattern, 0));
            g2.drawLine(0, sh / 2, sw, sh / 2);

        // ─── DOORS (AutoCAD Standard Architectural Symbols) ───
        } else if (type.equals("door")) {
            int jw = Math.max(3, (int)(4.0 / scale));
            int leafLen = sw - jw * 2;
            int leafThick = Math.max(2, (int)(3.0 / scale));

            // Jambs
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, jw, sh);
            g2.fillRect(sw - jw, 0, jw, sh);
            g2.setColor(primaryCol);
            g2.setStroke(new BasicStroke(sMed));
            g2.drawRect(0, 0, jw, sh);
            g2.drawRect(sw - jw, 0, jw, sh);

            // Door leaf (90 open)
            g2.setColor(Color.WHITE);
            g2.fillRect(jw, sh - leafLen, leafThick, leafLen);
            g2.setColor(primaryCol);
            g2.setStroke(new BasicStroke(sHeavy));
            g2.drawRect(jw, sh - leafLen, leafThick, leafLen);

            // Swing arc (dashed)
            g2.setStroke(new BasicStroke(sHair, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10, dashPattern, 0));
            g2.draw(new Arc2D.Double(jw - leafLen, sh - leafLen, leafLen * 2, leafLen * 2, 0, 90, Arc2D.OPEN));

            // Threshold hairline
            g2.setStroke(new BasicStroke(sHair));
            g2.drawLine(jw, sh, sw - jw, sh);

        } else if (type.equals("door-double")) {
            int jw = Math.max(3, (int)(4.0 / scale));
            int halfLeaf = (sw - jw * 2) / 2;
            int leafThick = Math.max(2, (int)(3.0 / scale));

            // Jambs
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, jw, sh);
            g2.fillRect(sw - jw, 0, jw, sh);
            g2.setColor(primaryCol);
            g2.setStroke(new BasicStroke(sMed));
            g2.drawRect(0, 0, jw, sh);
            g2.drawRect(sw - jw, 0, jw, sh);

            // Left leaf
            g2.setColor(Color.WHITE);
            g2.fillRect(jw, sh - halfLeaf, leafThick, halfLeaf);
            g2.setColor(primaryCol);
            g2.setStroke(new BasicStroke(sHeavy));
            g2.drawRect(jw, sh - halfLeaf, leafThick, halfLeaf);

            // Right leaf
            g2.setColor(Color.WHITE);
            g2.fillRect(sw - jw - leafThick, sh - halfLeaf, leafThick, halfLeaf);
            g2.setColor(primaryCol);
            g2.drawRect(sw - jw - leafThick, sh - halfLeaf, leafThick, halfLeaf);

            // Swing arcs
            g2.setStroke(new BasicStroke(sHair, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10, dashPattern, 0));
            g2.draw(new Arc2D.Double(jw - halfLeaf, sh - halfLeaf, halfLeaf * 2, halfLeaf * 2, 0, 90, Arc2D.OPEN));
            g2.draw(new Arc2D.Double(sw - jw - halfLeaf, sh - halfLeaf, halfLeaf * 2, halfLeaf * 2, 90, 90, Arc2D.OPEN));

            // Threshold
            g2.setStroke(new BasicStroke(sHair));
            g2.drawLine(jw, sh, sw - jw, sh);

        } else if (type.equals("door-sliding") || type.equals("window-sliding")) {
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, sw, sh);
            g2.setColor(primaryCol);
            g2.setStroke(new BasicStroke(sMed));
            g2.drawRect(0, 0, sw, sh);

            int mid = sw / 2;
            int trackH = sh / 2;
            g2.setStroke(new BasicStroke(sHeavy));
            g2.drawRect(2, 2, mid + 6, trackH - 2);
            g2.drawRect(mid - 6, trackH, sw - mid + 4, trackH - 2);

            g2.setStroke(new BasicStroke(sHair));
            int ay = trackH / 2;
            g2.drawLine(mid - 12, ay, mid + 4, ay);
            g2.drawLine(mid + 4, ay, mid, ay - 3);
            g2.drawLine(mid + 4, ay, mid, ay + 3);

        } else if (type.equals("door-pocket")) {
            g2.setColor(primaryCol);
            g2.setStroke(new BasicStroke(sHair, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, dashPattern, 0));
            g2.drawRect(0, 0, sw / 2, sh);

            g2.setStroke(new BasicStroke(sHeavy));
            g2.drawLine(sw / 3, sh / 2, sw, sh / 2);

            g2.setColor(Color.WHITE);
            g2.fillRect(sw - 3, 0, 3, sh);
            g2.setColor(primaryCol);
            g2.setStroke(new BasicStroke(sMed));
            g2.drawRect(sw - 3, 0, 3, sh);

        } else if (type.equals("door-bifold")) {
            int half = sw / 2;
            g2.setColor(primaryCol);
            g2.setStroke(new BasicStroke(sHeavy));
            g2.drawLine(0, sh, half / 2, 0);
            g2.drawLine(half / 2, 0, half, sh);
            g2.drawLine(half, sh, half + half / 2, 0);
            g2.drawLine(half + half / 2, 0, sw, sh);

            g2.setStroke(new BasicStroke(sHair));
            g2.drawOval(-2, sh - 3, 4, 4);
            g2.drawOval(sw - 2, sh - 3, 4, 4);
            g2.drawOval(half - 2, sh - 3, 4, 4);

        // ─── WINDOWS (AutoCAD Dual-Glass Architectural Symbols) ───
        } else if (type.equals("window") || type.equals("window-bay") || type.equals("window-vent")) {
            int sill = Math.max(3, (int)(4.0 / scale));
            g2.setColor(primaryCol);
            g2.setStroke(new BasicStroke(sHair));
            g2.drawLine(-sill, -sill / 2, sw + sill, -sill / 2);

            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, sw, sh);
            g2.setColor(primaryCol);
            g2.setStroke(new BasicStroke(sMed));
            g2.drawRect(0, 0, sw, sh);

            g2.setStroke(new BasicStroke(sHair));
            int g1 = sh / 2 - 2, g2y = sh / 2 + 2;
            g2.drawLine(0, g1, sw, g1);
            g2.drawLine(0, g2y, sw, g2y);

            if (sw > 40) {
                g2.setStroke(new BasicStroke(sMed));
                g2.drawLine(sw / 2, 0, sw / 2, sh);
            }
            if (type.equals("window-vent")) {
                g2.setStroke(new BasicStroke(sHair));
                for (int x = 6; x < sw; x += 8) g2.drawLine(x, 2, x - 3, sh - 2);
            }

        // ─── BEDS (King / Single) ───
        } else if (type.equals("bed-king") || type.equals("bed-single")) {
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(0, 0, sw, sh, 4, 4);
            g2.setColor(furnCol);
            g2.setStroke(new BasicStroke(sMed));
            g2.drawRoundRect(0, 0, sw, sh, 4, 4);

            int hb = Math.max(6, sh / 8);
            g2.drawRect(0, 0, sw, hb);

            int pW = type.equals("bed-king") ? (sw - 16) / 2 : sw - 14;
            int pH = Math.max(8, (int)(sh * 0.18));
            int pY = hb + 4;
            g2.setStroke(new BasicStroke(sHair));
            if (type.equals("bed-king")) {
                g2.drawRoundRect(5, pY, pW, pH, 4, 4);
                g2.drawLine(5 + pW / 4, pY + pH / 2, 5 + 3 * pW / 4, pY + pH / 2);
                g2.drawRoundRect(sw - pW - 5, pY, pW, pH, 4, 4);
                g2.drawLine(sw - pW - 5 + pW / 4, pY + pH / 2, sw - pW - 5 + 3 * pW / 4, pY + pH / 2);
            } else {
                g2.drawRoundRect(7, pY, pW, pH, 4, 4);
                g2.drawLine(7 + pW / 4, pY + pH / 2, 7 + 3 * pW / 4, pY + pH / 2);
            }

            int foldY = pY + pH + Math.max(8, sh / 8);
            g2.drawLine(4, foldY, sw - 4, foldY);

        // ─── SOFAS (3-Seat / Armchair) ───
        } else if (type.equals("sofa-3seat") || type.equals("sofa-armchair")) {
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(0, 0, sw, sh, 6, 6);
            g2.setColor(furnCol);
            g2.setStroke(new BasicStroke(sMed));
            g2.drawRoundRect(0, 0, sw, sh, 6, 6);

            int armW = Math.max(6, sw / 9);
            int backH = Math.max(6, sh / 4);

            g2.drawRoundRect(0, 0, sw, backH, 4, 4);
            g2.drawRoundRect(0, 0, armW, sh, 4, 4);
            g2.drawRoundRect(sw - armW, 0, armW, sh, 4, 4);

            int seatW = sw - armW * 2;
            int cushions = type.equals("sofa-3seat") ? 3 : 1;
            int cWidth = seatW / cushions;
            g2.setStroke(new BasicStroke(sHair));
            for (int c = 0; c < cushions; c++) {
                int cx = armW + c * cWidth;
                g2.drawRect(cx, backH, cWidth, sh - backH);
            }

        // ─── DINING SET ───
        } else if (type.equals("dining-set")) {
            int chairD = Math.max(6, sh / 6);
            int chairW = Math.max(10, sw / 4);
            int tblY = chairD + 4;
            int tblH = sh - (chairD + 4) * 2;

            g2.setColor(Color.WHITE);
            g2.fillRect(6, tblY, sw - 12, tblH);
            g2.setColor(furnCol);
            g2.setStroke(new BasicStroke(sMed));
            g2.drawRect(6, tblY, sw - 12, tblH);
            g2.setStroke(new BasicStroke(sHair));
            g2.drawLine(sw / 2, tblY + 3, sw / 2, tblY + tblH - 3);

            int spacing = (sw - 16) / 3;
            for (int i = 0; i < 3; i++) {
                int cx = 8 + i * spacing;
                g2.setColor(Color.WHITE);
                g2.fillRect(cx, 0, chairW, chairD);
                g2.fillRect(cx, sh - chairD, chairW, chairD);
                g2.setColor(furnCol);
                g2.setStroke(new BasicStroke(sHair));
                g2.drawRect(cx, 0, chairW, chairD);
                g2.drawRect(cx, sh - chairD, chairW, chairD);
            }

        // ─── DESK ───
        } else if (type.equals("desk")) {
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, sw, sh);
            g2.setColor(furnCol);
            g2.setStroke(new BasicStroke(sMed));
            g2.drawRect(0, 0, sw, sh);

            int mw = Math.max(14, sw / 3);
            int mh = Math.max(4, sh / 6);
            g2.setStroke(new BasicStroke(sHair));
            g2.drawRect((sw - mw) / 2, 4, mw, mh);
            g2.drawLine(sw / 2, 4 + mh, sw / 2, 4 + mh + 4);

            int kw = mw - 6, kh = Math.max(3, mh / 2);
            g2.drawRect((sw - kw) / 2, 4 + mh + 7, kw, kh);

            int cr = Math.min(12, sh / 4);
            g2.drawOval(sw / 2 - cr, sh + 2, cr * 2, cr * 2);
            g2.drawLine(sw / 2, sh + 2, sw / 2, sh + 2 + cr * 2);
            g2.drawLine(sw / 2 - cr, sh + 2 + cr, sw / 2 + cr, sh + 2 + cr);

        // ─── WARDROBE (Classic AutoCAD X-Cross Symbol) ───
        } else if (type.equals("wardrobe")) {
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, sw, sh);
            g2.setColor(furnCol);
            g2.setStroke(new BasicStroke(sMed));
            g2.drawRect(0, 0, sw, sh);
            g2.drawLine(sw / 2, 0, sw / 2, sh);

            g2.setStroke(new BasicStroke(sHair));
            g2.drawLine(0, 0, sw / 2, sh);
            g2.drawLine(sw / 2, sh, sw, 0);
            g2.drawLine(sw / 2 - 4, sh / 2, sw / 2 - 1, sh / 2);
            g2.drawLine(sw / 2 + 1, sh / 2, sw / 2 + 4, sh / 2);

        // ─── TV UNIT & BOOKSHELF ───
        } else if (type.equals("tv-unit")) {
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, sw, sh);
            g2.setColor(furnCol);
            g2.setStroke(new BasicStroke(sMed));
            g2.drawRect(0, 0, sw, sh);
            int tvW = (int)(sw * 0.65);
            g2.setStroke(new BasicStroke(sHair));
            g2.drawRect((sw - tvW) / 2, 2, tvW, Math.max(3, sh / 3));

        } else if (type.equals("bookshelf")) {
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, sw, sh);
            g2.setColor(furnCol);
            g2.setStroke(new BasicStroke(sMed));
            g2.drawRect(0, 0, sw, sh);
            g2.setStroke(new BasicStroke(sHair));
            int shelves = 4;
            int sp = sw / shelves;
            for (int i = 1; i < shelves; i++) g2.drawLine(i * sp, 0, i * sp, sh);

        // ─── TOILET WC ───
        } else if (type.equals("toilet")) {
            int tankH = Math.max(8, sh / 4);
            g2.setColor(Color.WHITE);
            g2.fillRect(1, 1, sw - 2, tankH);
            g2.setColor(sanCol);
            g2.setStroke(new BasicStroke(sMed));
            g2.drawRect(1, 1, sw - 2, tankH);
            g2.drawOval(sw / 2 - 4, tankH / 2 - 2, 3, 3);
            g2.drawOval(sw / 2 + 1, tankH / 2 - 2, 3, 3);

            int bowlW = (int)(sw * 0.88);
            int bowlH = sh - tankH;
            int bx = (sw - bowlW) / 2;
            g2.setColor(Color.WHITE);
            g2.fillOval(bx, tankH, bowlW, bowlH);
            g2.setColor(sanCol);
            g2.drawOval(bx, tankH, bowlW, bowlH);
            g2.setStroke(new BasicStroke(sHair));
            int rimOff = Math.max(3, bowlW / 6);
            g2.drawOval(bx + rimOff, tankH + rimOff, bowlW - rimOff * 2, bowlH - rimOff * 2);

        // ─── WASH BASIN ───
        } else if (type.equals("basin")) {
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, sw, sh);
            g2.setColor(sanCol);
            g2.setStroke(new BasicStroke(sMed));
            g2.drawRect(0, 0, sw, sh);
            int pad = Math.max(4, sw / 8);
            g2.setStroke(new BasicStroke(sHair));
            g2.drawOval(pad, pad, sw - pad * 2, sh - pad * 2);
            g2.drawOval(sw / 2 - 3, sh / 2 - 3, 6, 6);
            g2.drawLine(sw / 2, pad - 2, sw / 2, pad + 6);

        // ─── BATHTUB ───
        } else if (type.equals("bathtub")) {
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, sw, sh);
            g2.setColor(sanCol);
            g2.setStroke(new BasicStroke(sMed));
            g2.drawRect(0, 0, sw, sh);
            int pad = Math.max(4, (int)(sw * 0.08));
            g2.setStroke(new BasicStroke(sHair));
            g2.drawRoundRect(pad, pad, sw - pad * 2, sh - pad * 2, 16, 16);
            g2.drawOval(sw - pad - 10, sh / 2 - 4, 8, 8);
            g2.drawLine(pad + 4, sh / 2 - 5, pad + 4, sh / 2 + 5);

        // ─── SHOWER STALL ───
        } else if (type.equals("shower")) {
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, sw, sh);
            g2.setColor(sanCol);
            g2.setStroke(new BasicStroke(sMed));
            g2.drawRect(0, 0, sw, sh);
            g2.setStroke(new BasicStroke(sHair));
            g2.drawOval(sw / 2 - 5, sh / 2 - 5, 10, 10);
            g2.drawLine(sw / 2 - 5, sh / 2, sw / 2 + 5, sh / 2);
            g2.drawLine(sw / 2, sh / 2 - 5, sw / 2, sh / 2 + 5);
            g2.drawArc(-2, -2, 24, 24, 270, 90);

        // ─── KITCHEN COUNTERTOPS & LAYOUTS ───
        } else if (type.equals("kitchen-l")) {
            int d = Math.max(14, (int) Math.min(sw * 0.35, Math.min(sh * 0.35, (2.0 * FT_PX) * scale)));
            Path2D.Double lCounter = new Path2D.Double();
            lCounter.moveTo(0, 0); lCounter.lineTo(sw, 0); lCounter.lineTo(sw, d);
            lCounter.lineTo(d, d); lCounter.lineTo(d, sh); lCounter.lineTo(0, sh); lCounter.closePath();

            g2.setColor(Color.WHITE); g2.fill(lCounter);
            g2.setColor(kitCol); g2.setStroke(new BasicStroke(sHeavy)); g2.draw(lCounter);

            g2.setStroke(new BasicStroke(sHair));
            g2.drawLine(1, 2, sw - 1, 2);
            g2.drawLine(2, 1, 2, sh - 1);
            g2.drawLine(0, 0, d, d);

            int stoveW = Math.max(18, Math.min(sw / 4, d * 2));
            int stoveH = (int)(d * 0.8);
            drawAutoCADCooktop(g2, d + (sw - d - stoveW) / 2, (d - stoveH) / 2, stoveW, stoveH, sMed, sHair, kitCol);

            int sinkW = (int)(d * 0.8);
            int sinkH = Math.max(18, Math.min(sh / 4, d * 2));
            drawAutoCADSink(g2, (d - sinkW) / 2, d + (sh - d - sinkH) / 2, sinkW, sinkH, sMed, sHair, kitCol);

        } else if (type.equals("kitchen-u")) {
            int d = Math.max(14, (int) Math.min(sw / 3.2, Math.min(sh / 2.8, (2.0 * FT_PX) * scale)));
            Path2D.Double uCounter = new Path2D.Double();
            uCounter.moveTo(0, 0); uCounter.lineTo(sw, 0); uCounter.lineTo(sw, sh);
            uCounter.lineTo(sw - d, sh); uCounter.lineTo(sw - d, d); uCounter.lineTo(d, d);
            uCounter.lineTo(d, sh); uCounter.lineTo(0, sh); uCounter.closePath();

            g2.setColor(Color.WHITE); g2.fill(uCounter);
            g2.setColor(kitCol); g2.setStroke(new BasicStroke(sHeavy)); g2.draw(uCounter);

            g2.setStroke(new BasicStroke(sHair));
            g2.drawLine(2, 2, sw - 2, 2); g2.drawLine(2, 2, 2, sh - 2); g2.drawLine(sw - 2, 2, sw - 2, sh - 2);
            g2.drawLine(0, 0, d, d); g2.drawLine(sw, 0, sw - d, d);

            int sinkW = Math.max(22, Math.min(sw / 3, d * 2));
            int sinkH = (int)(d * 0.8);
            drawAutoCADSink(g2, (sw - sinkW) / 2, (d - sinkH) / 2, sinkW, sinkH, sMed, sHair, kitCol);

            int stoveW = (int)(d * 0.8);
            int stoveH = Math.max(18, Math.min(sh / 3, d * 2));
            drawAutoCADCooktop(g2, (d - stoveW) / 2, d + (sh - d - stoveH) / 2, stoveW, stoveH, sMed, sHair, kitCol);

        } else if (type.equals("kitchen-parallel")) {
            int d = Math.max(14, (int) Math.min(sh / 3.0, (2.0 * FT_PX) * scale));
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, sw, d); g2.fillRect(0, sh - d, sw, d);
            g2.setColor(kitCol); g2.setStroke(new BasicStroke(sHeavy));
            g2.drawRect(0, 0, sw, d); g2.drawRect(0, sh - d, sw, d);

            int stoveW = Math.max(20, Math.min(sw / 3, d * 2));
            int stoveH = (int)(d * 0.8);
            drawAutoCADCooktop(g2, sw / 4 - stoveW / 2, (d - stoveH) / 2, stoveW, stoveH, sMed, sHair, kitCol);

            int sinkW = Math.max(22, Math.min(sw / 3, d * 2));
            int sinkH = (int)(d * 0.8);
            drawAutoCADSink(g2, sw / 2 - sinkW / 2, sh - d + (d - sinkH) / 2, sinkW, sinkH, sMed, sHair, kitCol);

        } else if (type.equals("kitchen-island") || type.equals("kitchen-island-unit")) {
            g2.setColor(Color.WHITE); g2.fillRect(0, 0, sw, sh);
            g2.setColor(kitCol); g2.setStroke(new BasicStroke(sHeavy)); g2.drawRect(0, 0, sw, sh);
            int sinkW = Math.max(16, sw / 3); int sinkH = Math.max(12, sh / 2);
            drawAutoCADSink(g2, 8, (sh - sinkH) / 2, sinkW, sinkH, sMed, sHair, kitCol);

            int stoolR = Math.max(5, (int)(7 * scale));
            int numStools = Math.max(2, sw / 36);
            for (int s = 0; s < numStools; s++) {
                drawAutoCADStool(g2, (s + 1) * (sw / (numStools + 1)), sh + stoolR + 2, stoolR, sMed, sHair, kitCol);
            }

        } else if (type.equals("kitchen-peninsula") || type.equals("kitchen-linear") || type.equals("kitchen-compact") || type.equals("kitchen-bar")) {
            g2.setColor(Color.WHITE); g2.fillRect(0, 0, sw, sh);
            g2.setColor(kitCol); g2.setStroke(new BasicStroke(sHeavy)); g2.drawRect(0, 0, sw, sh);

            int stoveW = Math.max(16, sw / 4); int stoveH = (int)(sh * 0.75);
            drawAutoCADCooktop(g2, 6, (sh - stoveH) / 2, stoveW, stoveH, sMed, sHair, kitCol);

            int sinkW = Math.max(16, sw / 4); int sinkH = (int)(sh * 0.75);
            drawAutoCADSink(g2, sw - sinkW - 6, (sh - sinkH) / 2, sinkW, sinkH, sMed, sHair, kitCol);

        // ─── APPLIANCES ───
        } else if (type.equals("stove")) {
            drawAutoCADCooktop(g2, 0, 0, sw, sh, sMed, sHair, appCol);

        } else if (type.equals("fridge")) {
            g2.setColor(Color.WHITE); g2.fillRect(0, 0, sw, sh);
            g2.setColor(appCol); g2.setStroke(new BasicStroke(sMed)); g2.drawRect(0, 0, sw, sh);
            g2.setStroke(new BasicStroke(sHair));
            g2.drawLine(0, sh / 3, sw, sh / 3);
            g2.drawLine(sw - 4, 4, sw - 4, sh / 3 - 4);
            g2.drawLine(sw - 4, sh / 3 + 4, sw - 4, sh - 4);

        } else if (type.equals("washer")) {
            g2.setColor(Color.WHITE); g2.fillRect(0, 0, sw, sh);
            g2.setColor(appCol); g2.setStroke(new BasicStroke(sMed)); g2.drawRect(0, 0, sw, sh);
            int dr = Math.min(sw, sh) / 3;
            g2.setStroke(new BasicStroke(sHair));
            g2.drawOval(sw / 2 - dr, sh / 2 - dr, dr * 2, dr * 2);
            g2.drawOval(sw / 2 - dr + 4, sh / 2 - dr + 4, dr * 2 - 8, dr * 2 - 8);

        } else if (type.equals("kitchen-sink")) {
            drawAutoCADSink(g2, 0, 0, sw, sh, sMed, sHair, kitCol);

        } else if (type.equals("kitchen-oven") || type.equals("kitchen-dishwasher")) {
            g2.setColor(Color.WHITE); g2.fillRect(0, 0, sw, sh);
            g2.setColor(appCol); g2.setStroke(new BasicStroke(sMed)); g2.drawRect(0, 0, sw, sh);
            g2.setStroke(new BasicStroke(sHair));
            g2.drawRect(4, 4, sw - 8, sh - 8);

        // ─── STRUCTURAL (Column & Stairs) ───
        } else if (type.startsWith("column")) {
            g2.setColor(Color.WHITE); g2.fillRect(0, 0, sw, sh);
            g2.setColor(structCol); g2.setStroke(new BasicStroke(sHeavy)); g2.drawRect(0, 0, sw, sh);
            g2.setStroke(new BasicStroke(sMed));
            g2.drawLine(0, 0, sw, sh);
            g2.drawLine(0, sh, sw, 0);

        } else if (type.startsWith("stair")) {
            g2.setColor(Color.WHITE); g2.fillRect(0, 0, sw, sh);
            g2.setColor(structCol); g2.setStroke(new BasicStroke(sMed)); g2.drawRect(0, 0, sw, sh);
            int treads = type.equals("stair-dogleg") ? 8 : 10;
            g2.setStroke(new BasicStroke(sHair));
            for (int t = 1; t < treads; t++) {
                int ty = (sh / treads) * t;
                g2.drawLine(0, ty, sw, ty);
            }
            if (type.equals("stair-dogleg")) {
                g2.setStroke(new BasicStroke(sMed));
                g2.drawLine(0, sh / 2, sw, sh / 2);
            }
            g2.setFont(new Font("Consolas", Font.BOLD, Math.max(8, (int)(9 * scale))));
            g2.drawString("UP \u2191", sw / 2 - 12, sh - 4);

        // ─── ELECTRICAL (AutoCAD Standard Symbols) ───
        } else if (type.equals("ceiling-fan")) {
            int cx = sw / 2, cy = sh / 2;
            int r = Math.min(sw, sh) / 2 - 2;
            g2.setColor(Color.WHITE); g2.fillOval(cx - r, cy - r, r * 2, r * 2);
            g2.setColor(elecCol); g2.setStroke(new BasicStroke(sHair));
            g2.drawOval(cx - r, cy - r, r * 2, r * 2);
            g2.drawOval(cx - 3, cy - 3, 6, 6);
            for (int b = 0; b < 4; b++) {
                double ang = Math.toRadians(b * 90 + 30);
                g2.drawLine(cx, cy, (int)(cx + r * 0.85 * Math.cos(ang)), (int)(cy + r * 0.85 * Math.sin(ang)));
            }

        } else if (type.equals("ac-unit")) {
            g2.setColor(Color.WHITE); g2.fillRect(0, 0, sw, sh);
            g2.setColor(elecCol); g2.setStroke(new BasicStroke(sMed)); g2.drawRect(0, 0, sw, sh);
            g2.setStroke(new BasicStroke(sHair));
            for (int i = 1; i <= 3; i++) g2.drawLine(4, (sh / 4) * i, sw - 4, (sh / 4) * i);
            g2.setFont(new Font("Consolas", Font.BOLD, Math.max(8, (int)(8 * scale))));
            g2.drawString("AC", 4, sh / 2 + 3);

        } else if (type.equals("light-point")) {
            int cx = sw / 2, cy = sh / 2, r = Math.min(sw, sh) / 3;
            g2.setColor(Color.WHITE); g2.fillOval(cx - r, cy - r, r * 2, r * 2);
            g2.setColor(elecCol); g2.setStroke(new BasicStroke(sMed));
            g2.drawOval(cx - r, cy - r, r * 2, r * 2);
            g2.drawLine(cx - r, cy, cx + r, cy);
            g2.drawLine(cx, cy - r, cx, cy + r);

        } else if (type.equals("switchboard")) {
            g2.setColor(Color.WHITE); g2.fillRect(0, 0, sw, sh);
            g2.setColor(elecCol); g2.setStroke(new BasicStroke(sMed)); g2.drawRect(0, 0, sw, sh);
            g2.setStroke(new BasicStroke(sHair));
            g2.drawRect(sw / 4 - 2, sh / 4, 4, sh / 2);
            g2.drawRect(3 * sw / 4 - 2, sh / 4, 4, sh / 2);

        // ─── LANDSCAPE & SITE ───
        } else if (type.equals("tree-deciduous")) {
            int cx = sw / 2, cy = sh / 2;
            int r = Math.min(sw, sh) / 2 - 3;
            g2.setColor(Color.WHITE); g2.fillOval(cx - r, cy - r, r * 2, r * 2);
            g2.setColor(landCol); g2.setStroke(new BasicStroke(sMed));

            int lobes = 12, lobeR = (int)(r * 0.3);
            for (int i = 0; i < lobes; i++) {
                double ang = (2 * Math.PI / lobes) * i;
                int lx = (int)(cx + (r - lobeR * 0.5) * Math.cos(ang));
                int ly = (int)(cy + (r - lobeR * 0.5) * Math.sin(ang));
                g2.drawArc(lx - lobeR, ly - lobeR, lobeR * 2, lobeR * 2, (int) Math.toDegrees(ang) - 45, 180);
            }
            g2.drawOval(cx - 3, cy - 3, 6, 6);
            g2.setStroke(new BasicStroke(sHair));
            for (int i = 0; i < 8; i++) {
                double ang = (2 * Math.PI / 8) * i;
                g2.drawLine(cx, cy, (int)(cx + r * 0.8 * Math.cos(ang)), (int)(cy + r * 0.8 * Math.sin(ang)));
            }

        } else if (type.equals("tree-pine")) {
            int cx = sw / 2, cy = sh / 2, r = Math.min(sw, sh) / 2 - 2;
            g2.setColor(Color.WHITE); g2.fillOval(cx - r, cy - r, r * 2, r * 2);
            g2.setColor(landCol); g2.setStroke(new BasicStroke(sMed));
            for (int i = 0; i < 16; i++) {
                double ang = (2 * Math.PI / 16) * i;
                g2.drawLine(cx, cy, (int)(cx + r * Math.cos(ang)), (int)(cy + r * Math.sin(ang)));
            }
            g2.drawOval(cx - 3, cy - 3, 6, 6);

        } else if (type.equals("plant-pot")) {
            int cx = sw / 2, cy = sh / 2, r = Math.min(sw, sh) / 2 - 2;
            g2.setColor(Color.WHITE); g2.fillOval(cx - r, cy - r, r * 2, r * 2);
            g2.setColor(landCol); g2.setStroke(new BasicStroke(sMed));
            g2.drawOval(cx - r / 2, cy - r / 2, r, r);
            g2.setStroke(new BasicStroke(sHair));
            for (int l = 0; l < 6; l++) {
                double ang = (2 * Math.PI / 6) * l;
                g2.drawLine(cx, cy, (int)(cx + r * Math.cos(ang)), (int)(cy + r * Math.sin(ang)));
            }

        } else if (type.equals("road-segment")) {
            g2.setColor(Color.WHITE); g2.fillRect(0, 0, sw, sh);
            g2.setColor(primaryCol); g2.setStroke(new BasicStroke(sMed)); g2.drawRect(0, 0, sw, sh);
            int curbH = Math.max(3, (int)(sh * 0.08));
            g2.drawRect(0, 0, sw, curbH); g2.drawRect(0, sh - curbH, sw, curbH);
            g2.setStroke(new BasicStroke(sHair, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, dashPattern, 0));
            g2.drawLine(0, sh / 2, sw, sh / 2);

        } else if (type.equals("pathway")) {
            g2.setColor(Color.WHITE); g2.fillRect(0, 0, sw, sh);
            g2.setColor(primaryCol); g2.setStroke(new BasicStroke(sMed)); g2.drawRect(0, 0, sw, sh);
            int pLen = Math.max(12, sw / 6);
            g2.setStroke(new BasicStroke(sHair));
            for (int x = pLen; x < sw; x += pLen) g2.drawLine(x, 0, x, sh);
            g2.drawLine(0, sh / 2, sw, sh / 2);

        } else if (type.equals("bollard-light")) {
            int cx = sw / 2, cy = sh / 2, r = Math.min(sw, sh) / 2;
            g2.setColor(Color.WHITE); g2.fillOval(cx - r, cy - r, r * 2, r * 2);
            g2.setColor(elecCol); g2.setStroke(new BasicStroke(sMed));
            g2.drawOval(cx - r / 2, cy - r / 2, r, r);
            g2.drawLine(cx - r / 2, cy, cx + r / 2, cy);
            g2.drawLine(cx, cy - r / 2, cx, cy + r / 2);

        // ─── VEHICLES (Pure 2D AutoCAD Vehicle Blocks) ───
        } else if (type.equals("car-sedan") || type.equals("car-suv")) {
            Path2D.Double carBody = new Path2D.Double();
            int noseR = Math.max(4, sh / 6);
            carBody.moveTo(noseR * 2, 0); carBody.lineTo(sw - noseR * 2, 0);
            carBody.quadTo(sw, 0, sw, noseR * 2); carBody.lineTo(sw, sh - noseR * 2);
            carBody.quadTo(sw, sh, sw - noseR * 2, sh); carBody.lineTo(noseR * 2, sh);
            carBody.quadTo(0, sh, 0, sh - noseR * 2); carBody.lineTo(0, noseR * 2);
            carBody.quadTo(0, 0, noseR * 2, 0); carBody.closePath();

            g2.setColor(Color.WHITE); g2.fill(carBody);
            g2.setColor(vehCol); g2.setStroke(new BasicStroke(sHeavy)); g2.draw(carBody);

            int hoodLen = (int)(sw * 0.25);
            int[] wsX = {hoodLen, (int)(sw * 0.4), (int)(sw * 0.4), hoodLen};
            int[] wsY = {3, 6, sh - 6, sh - 3};
            g2.setStroke(new BasicStroke(sHair));
            g2.drawPolygon(wsX, wsY, 4);

            int[] rwsX = {(int)(sw * 0.65), (int)(sw * 0.78), (int)(sw * 0.78), (int)(sw * 0.65)};
            int[] rwsY = {6, 3, sh - 3, sh - 6};
            g2.drawPolygon(rwsX, rwsY, 4);

            g2.drawRoundRect((int)(sw * 0.38), 5, (int)(sw * 0.28), sh - 10, 4, 4);

            int mirrorH = Math.max(3, (int)(sh * 0.16));
            g2.drawLine(hoodLen + 2, 0, hoodLen + 2, -mirrorH);
            g2.drawLine(hoodLen + 2, sh, hoodLen + 2, sh + mirrorH);

        } else if (type.equals("bike-motor") || type.equals("bike-scooter") || type.equals("bicycle")) {
            int midY = sh / 2;
            g2.setColor(vehCol); g2.setStroke(new BasicStroke(sMed));
            g2.drawLine((int)(sw * 0.1), midY, (int)(sw * 0.9), midY);
            int hbX = (int)(sw * 0.25);
            g2.drawLine(hbX, 2, hbX, sh - 2);
            g2.setStroke(new BasicStroke(sHeavy));
            g2.drawOval(2, midY - 2, (int)(sw * 0.15), 4);
            g2.drawOval(sw - (int)(sw * 0.15) - 2, midY - 2, (int)(sw * 0.15), 4);

        // ─── FULL ROOM SETS (AutoCAD 2D Architectural Layouts) ───
        } else if (type.equals("set-bedroom-master") || type.equals("set-bedroom-double")
                || type.equals("set-bedroom-compact") || type.equals("set-bedroom-twin")) {
            g2.setColor(primaryCol);
            g2.setStroke(new BasicStroke(sHair, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, dashPattern, 0));
            g2.drawRoundRect(0, 0, sw, sh, 6, 6);

            int wardH = Math.max(14, (int)(sh * 0.18));
            g2.setColor(Color.WHITE); g2.fillRect(4, 4, sw - 8, wardH);
            g2.setColor(furnCol); g2.setStroke(new BasicStroke(sMed)); g2.drawRect(4, 4, sw - 8, wardH);
            int doors = type.equals("set-bedroom-master") ? 6 : 4;
            g2.setStroke(new BasicStroke(sHair));
            for (int d = 1; d < doors; d++) g2.drawLine(4 + d * (sw - 8) / doors, 4, 4 + d * (sw - 8) / doors, 4 + wardH);
            g2.drawLine(4, 4, sw - 4, 4 + wardH);

            int bedW = Math.max(34, (int)(sw * 0.44));
            int bedH = Math.max(40, (int)(sh * 0.52));
            int bedX = (sw - bedW) / 2;
            int bedY = wardH + 12;

            g2.setColor(Color.WHITE); g2.fillRect(bedX, bedY, bedW, bedH);
            g2.setColor(furnCol); g2.setStroke(new BasicStroke(sMed)); g2.drawRect(bedX, bedY, bedW, bedH);
            int hb = Math.max(6, bedH / 8);
            g2.drawRect(bedX, bedY, bedW, hb);

            int pw = (bedW - 12) / 2, ph = Math.max(8, (int)(bedH * 0.18));
            g2.setStroke(new BasicStroke(sHair));
            g2.drawRoundRect(bedX + 3, bedY + hb + 3, pw, ph, 4, 4);
            g2.drawRoundRect(bedX + bedW - pw - 3, bedY + hb + 3, pw, ph, 4, 4);

            int standW = Math.max(12, (int)(sw * 0.12));
            g2.setColor(Color.WHITE);
            g2.fillRect(bedX - standW - 4, bedY, standW, standW);
            g2.fillRect(bedX + bedW + 4, bedY, standW, standW);
            g2.setColor(furnCol); g2.setStroke(new BasicStroke(sHair));
            g2.drawRect(bedX - standW - 4, bedY, standW, standW);
            g2.drawRect(bedX + bedW + 4, bedY, standW, standW);
            g2.drawOval(bedX - standW / 2 - 4 - 3, bedY + standW / 2 - 3, 6, 6);
            g2.drawOval(bedX + bedW + standW / 2 + 4 - 3, bedY + standW / 2 - 3, 6, 6);

            g2.setFont(new Font("Consolas", Font.BOLD, Math.max(8, (int)(9 * scale))));
            g2.drawString(el.name.toUpperCase(), bedX + 4, sh - 8);

        } else if (type.equals("set-bath-master")) {
            g2.setColor(primaryCol);
            g2.setStroke(new BasicStroke(sHair, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, dashPattern, 0));
            g2.drawRoundRect(0, 0, sw, sh, 6, 6);

            int showerW = Math.max(26, (int)(sw * 0.38));
            int showerH = Math.max(26, (int)(sh * 0.5));
            g2.setColor(Color.WHITE); g2.fillRect(4, 4, showerW, showerH);
            g2.setColor(sanCol); g2.setStroke(new BasicStroke(sMed)); g2.drawRect(4, 4, showerW, showerH);
            g2.setStroke(new BasicStroke(sHair));
            g2.drawOval(4 + showerW / 2 - 4, 4 + showerH / 2 - 4, 8, 8);
            g2.drawArc(4, 4 + showerH - 16, 20, 20, 0, 90);

            int tubW = showerW;
            int tubH = sh - showerH - 12;
            int tubY = showerH + 8;
            g2.setColor(Color.WHITE); g2.fillRect(4, tubY, tubW, tubH);
            g2.setColor(sanCol); g2.setStroke(new BasicStroke(sMed)); g2.drawRect(4, tubY, tubW, tubH);
            g2.setStroke(new BasicStroke(sHair));
            g2.drawRoundRect(8, tubY + 4, tubW - 8, tubH - 8, 12, 12);

            int toiletW = Math.max(16, (int)(sw * 0.25));
            int toiletH = Math.max(22, (int)(sh * 0.35));
            int tx = sw - toiletW - 8, ty = 6;
            g2.setColor(Color.WHITE); g2.fillRect(tx, ty, toiletW, toiletH / 3);
            g2.setColor(sanCol); g2.setStroke(new BasicStroke(sMed)); g2.drawRect(tx, ty, toiletW, toiletH / 3);
            g2.drawOval(tx + 2, ty + toiletH / 3, toiletW - 4, 2 * toiletH / 3);

        // ─── GENERIC FALLBACK ───
        } else {
            g2.setColor(Color.WHITE); g2.fillRect(0, 0, sw, sh);
            g2.setColor(primaryCol); g2.setStroke(new BasicStroke(sMed)); g2.drawRect(0, 0, sw, sh);
            g2.setFont(new Font("Consolas", Font.PLAIN, Math.max(8, (int)(8 * scale))));
            g2.drawString(el.name, 4, sh / 2 + 3);
        }
    }

    private void drawAutoCADCooktop(Graphics2D g2, int x, int y, int w, int h, float sMed, float sHair, Color strokeCol) {
        g2.setColor(Color.WHITE);
        g2.fillRect(x, y, w, h);
        g2.setColor(strokeCol);
        g2.setStroke(new BasicStroke(sMed));
        g2.drawRect(x, y, w, h);

        int b1x = x + w / 4, b2x = x + 3 * w / 4;
        int b1y = y + h / 4, b2y = y + 3 * h / 4;
        int br = Math.max(4, Math.min(w, h) / 5);

        g2.setStroke(new BasicStroke(sHair));
        int[][] burners = {{b1x, b1y}, {b2x, b1y}, {b1x, b2y}, {b2x, b2y}};
        for (int[] b : burners) {
            g2.drawOval(b[0] - br, b[1] - br, br * 2, br * 2);
            g2.drawOval(b[0] - br / 2, b[1] - br / 2, br, br);
            g2.drawLine(b[0] - br, b[1], b[0] + br, b[1]);
            g2.drawLine(b[0], b[1] - br, b[0], b[1] + br);
        }
    }

    private void drawAutoCADSink(Graphics2D g2, int x, int y, int w, int h, float sMed, float sHair, Color strokeCol) {
        g2.setColor(Color.WHITE);
        g2.fillRect(x, y, w, h);
        g2.setColor(strokeCol);
        g2.setStroke(new BasicStroke(sMed));
        g2.drawRect(x, y, w, h);

        int pad = Math.max(3, (int) (Math.min(w, h) * 0.12));
        g2.setStroke(new BasicStroke(sHair));
        g2.drawRoundRect(x + pad, y + pad, w - pad * 2, h - pad * 2, 6, 6);

        int dr = Math.max(3, (int) (Math.min(w, h) * 0.1));
        g2.drawOval(x + w / 2 - dr, y + h / 2 - dr, dr * 2, dr * 2);

        g2.drawLine(x + w / 2 - 4, y + 2, x + w / 2 + 4, y + 2);
        g2.drawArc(x + w / 2 - 3, y + 2, 6, Math.max(6, h / 3), 0, 180);
    }

    private void drawAutoCADStool(Graphics2D g2, int cx, int cy, int r, float sMed, float sHair, Color strokeCol) {
        g2.setColor(Color.WHITE);
        g2.fillOval(cx - r, cy - r, r * 2, r * 2);
        g2.setColor(strokeCol);
        g2.setStroke(new BasicStroke(sMed));
        g2.drawOval(cx - r, cy - r, r * 2, r * 2);
        g2.setStroke(new BasicStroke(sHair));
        g2.drawOval(cx - r * 2 / 3, cy - r * 2 / 3, r * 4 / 3, r * 4 / 3);
    }

    private void exportBlueprintPNG() {
        showExportDialog();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) {}
            new HousePlanCADApp().setVisible(true);
        });
    }
}