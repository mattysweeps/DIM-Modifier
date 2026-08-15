package com.github.cfogrady.dim.modifier;

import com.github.cfogrady.vb.dim.sprite.SpriteData;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.*;
import java.util.function.Consumer;

public class SpriteEditor extends Stage {
    private final SpriteData.Sprite originalSprite;
    private final SpriteImageTranslator translator;
    private final SpriteReplacer replacer;
    private final Consumer<SpriteData.Sprite> onSave;

    private final int width;
    private final int height;
    private Color[][] pixels;

    private int zoom = 16;
    private boolean showGrid = true;
    private Color activeColor = Color.BLACK;

    private int brushSize = 1;
    private Color replaceColorTarget = null;

    private enum Tool {
        PENCIL("Pencil ✏"),
        ERASER("Eraser ❌"),
        FILL("Flood Fill 🪣"),
        DROPPER("Dropper 🧪"),
        LINE("Line ➖"),
        RECTANGLE("Rectangle ⬜"),
        CIRCLE("Circle ⚪"),
        SELECT("Select ⛶"),
        BLUR("Blur 💧"),
        BURN("Burn 🔥"),
        DODGE("Dodge ☀️"),
        LIGHTEN("Lighten 💡"),
        DARKEN("Darken 🌙"),
        FADE("Fade 🌫️"),
        REPLACE_COLOR("Replace Color 🔄");

        private final String label;
        Tool(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    private Tool activeTool = Tool.PENCIL;
    private boolean fillShape = false;

    // Selection fields
    private boolean hasSelection = false;
    private int selectMinX = 0;
    private int selectMinY = 0;
    private int selectMaxX = 0;
    private int selectMaxY = 0;
    private boolean isMovingSelection = false;
    private Color[][] selectionBuffer = null;
    private int origSelectMinX;
    private int origSelectMinY;
    private int origSelectWidth;
    private int origSelectHeight;
    private int dragStartX;
    private int dragStartY;

    // Drawing states
    private boolean isDrawing = false;
    private int startX = -1;
    private int startY = -1;
    private Map<Point, Color> previewPixels = new HashMap<>();

    // Undo / Redo stacks
    private final Stack<Color[][]> undoStack = new Stack<>();
    private final Stack<Color[][]> redoStack = new Stack<>();

    // UI Controls
    private Canvas canvas;
    private Label coordsLabel;
    private Label sizeLabel;
    private Button undoButton;
    private Button redoButton;
    private ColorPicker colorPicker;
    private TextField hexField;
    private Pane activeColorPreview;
    private CheckBox fillShapeCheckbox;
    private Button fillSelectionBtn;
    private Button clearSelectionBtn;
    private Button deselectBtn;
    private FlowPane customColorsPane;
    private final List<Color> customColors = new ArrayList<>();
    private Label brushSizeLabel;
    private Slider brushSizeSlider;

    public SpriteEditor(SpriteData.Sprite sprite, SpriteImageTranslator translator, SpriteReplacer replacer, Consumer<SpriteData.Sprite> onSave) {
        this.originalSprite = sprite;
        this.translator = translator;
        this.replacer = replacer;
        this.onSave = onSave;
        this.width = sprite.getWidth();
        this.height = sprite.getHeight();

        initModality(Modality.APPLICATION_MODAL);
        setTitle("Sprite Editor - " + width + "x" + height);

        // Load pixels
        Image image = translator.loadImageFromSprite(sprite);
        pixels = new Color[width][height];
        PixelReader reader = image.getPixelReader();
        if (reader != null) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    Color c = reader.getColor(x, y);
                    if (c.getOpacity() == 0.0) {
                        pixels[x][y] = null; // transparent
                    } else {
                        pixels[x][y] = c;
                    }
                }
            }
        }

        // Set default zoom based on image size
        if (width > 120 || height > 120) {
            zoom = 4;
        } else if (width > 60 || height > 60) {
            zoom = 8;
        } else if (width > 30 || height > 30) {
            zoom = 12;
        } else {
            zoom = 16;
        }

        buildUI();
        drawCanvas();
        updateUndoRedoButtons();
    }

    private void buildUI() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        // Top Toolbar
        HBox topBar = new HBox(10);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(0, 0, 10, 0));

        undoButton = new Button("↩ Undo");
        undoButton.setOnAction(e -> undo());
        redoButton = new Button("↪ Redo");
        redoButton.setOnAction(e -> redo());

        Button clearButton = new Button("🗑 Clear Canvas");
        clearButton.setOnAction(e -> {
            saveState();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    pixels[x][y] = null;
                }
            }
            drawCanvas();
        });

        CheckBox gridCheckbox = new CheckBox("Show Grid");
        gridCheckbox.setSelected(showGrid);
        gridCheckbox.setOnAction(e -> {
            showGrid = gridCheckbox.isSelected();
            drawCanvas();
        });

        Label zoomLabel = new Label("Zoom:");
        Slider zoomSlider = new Slider(2, 32, zoom);
        zoomSlider.setBlockIncrement(2);
        zoomSlider.setMajorTickUnit(4);
        zoomSlider.setMinorTickCount(1);
        zoomSlider.setSnapToTicks(true);
        zoomSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            zoom = newVal.intValue();
            resizeCanvas();
            drawCanvas();
        });

        topBar.getChildren().addAll(undoButton, redoButton, new Separator(), clearButton, gridCheckbox, new Separator(), zoomLabel, zoomSlider);
        root.setTop(topBar);

        // Center Canvas Area
        canvas = new Canvas(width * zoom, height * zoom);
        canvas.setOnMousePressed(this::handleMousePressed);
        canvas.setOnMouseDragged(this::handleMouseDragged);
        canvas.setOnMouseReleased(this::handleMouseReleased);
        canvas.setOnMouseMoved(this::handleMouseMoved);
        canvas.setOnMouseExited(e -> coordsLabel.setText("X: - Y: -"));

        StackPane canvasContainer = new StackPane(canvas);
        canvasContainer.setStyle("-fx-background-color: #333333; -fx-border-color: #555555; -fx-border-width: 1;");
        canvasContainer.setPadding(new Insets(20));

        ScrollPane scrollPane = new ScrollPane(canvasContainer);
        scrollPane.setPannable(true);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        root.setCenter(scrollPane);

        // Left Sidebar: Tools
        VBox leftSidebar = new VBox(10);
        leftSidebar.setPadding(new Insets(0, 10, 0, 0));
        leftSidebar.setPrefWidth(150);

        Label toolsLabel = new Label("Tools");
        toolsLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        ToggleGroup toolGroup = new ToggleGroup();
        VBox toolsBox = new VBox(5);
        for (Tool tool : Tool.values()) {
            ToggleButton btn = new ToggleButton(tool.getLabel());
            btn.setToggleGroup(toolGroup);
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setAlignment(Pos.CENTER_LEFT);
            if (tool == activeTool) {
                btn.setSelected(true);
            }
            btn.setOnAction(e -> {
                activeTool = tool;
                updateToolOptions();
                if (tool != Tool.SELECT) {
                    clearSelection();
                }
            });
            toolsBox.getChildren().add(btn);
        }

        ScrollPane toolsScrollPane = new ScrollPane(toolsBox);
        toolsScrollPane.setFitToWidth(true);
        toolsScrollPane.setPrefHeight(250);
        toolsScrollPane.setStyle("-fx-background-color: transparent; -fx-background-insets: 0; -fx-padding: 0;");

        brushSizeLabel = new Label("Brush Size: " + brushSize + "px");
        brushSizeSlider = new Slider(1, 8, brushSize);
        brushSizeSlider.setBlockIncrement(1);
        brushSizeSlider.setMajorTickUnit(1);
        brushSizeSlider.setMinorTickCount(0);
        brushSizeSlider.setSnapToTicks(true);
        brushSizeSlider.setShowTickMarks(true);
        brushSizeSlider.setShowTickLabels(true);
        brushSizeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            brushSize = newVal.intValue();
            brushSizeLabel.setText("Brush Size: " + brushSize + "px");
        });

        fillShapeCheckbox = new CheckBox("Fill Shape");
        fillShapeCheckbox.setSelected(fillShape);
        fillShapeCheckbox.setOnAction(e -> fillShape = fillShapeCheckbox.isSelected());
        fillShapeCheckbox.setDisable(true); // default disabled, only for Rectangle/Circle

        VBox selectionOpsBox = new VBox(5);
        selectionOpsBox.setBorder(new Border(new BorderStroke(Color.GRAY, BorderStrokeStyle.DASHED, CornerRadii.EMPTY, BorderWidths.DEFAULT)));
        selectionOpsBox.setPadding(new Insets(5));
        Label selLabel = new Label("Selection Ops");
        selLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");
        fillSelectionBtn = new Button("Fill Selection");
        fillSelectionBtn.setMaxWidth(Double.MAX_VALUE);
        fillSelectionBtn.setOnAction(e -> fillSelection());
        clearSelectionBtn = new Button("Clear Selection");
        clearSelectionBtn.setMaxWidth(Double.MAX_VALUE);
        clearSelectionBtn.setOnAction(e -> clearSelectionPixels());
        deselectBtn = new Button("Deselect");
        deselectBtn.setMaxWidth(Double.MAX_VALUE);
        deselectBtn.setOnAction(e -> clearSelection());

        selectionOpsBox.getChildren().addAll(selLabel, fillSelectionBtn, clearSelectionBtn, deselectBtn);
        updateSelectionButtons();

        leftSidebar.getChildren().addAll(toolsLabel, toolsScrollPane, new Separator(), brushSizeLabel, brushSizeSlider, fillShapeCheckbox, selectionOpsBox);
        root.setLeft(leftSidebar);

        // Right Sidebar: Color Picker & Palette
        VBox rightSidebar = new VBox(10);
        rightSidebar.setPadding(new Insets(0, 0, 0, 10));
        rightSidebar.setPrefWidth(220);

        Label colorLabel = new Label("Color Selection");
        colorLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        HBox activeColorBox = new HBox(10);
        activeColorBox.setAlignment(Pos.CENTER_LEFT);
        activeColorPreview = new Pane();
        activeColorPreview.setPrefSize(40, 40);
        activeColorPreview.setStyle("-fx-border-color: black; -fx-border-width: 1; -fx-background-color: black;");
        
        colorPicker = new ColorPicker(activeColor);
        colorPicker.setStyle("-fx-color-label-visible: false;");
        colorPicker.setOnAction(e -> setActiveColor(colorPicker.getValue()));

        activeColorBox.getChildren().addAll(activeColorPreview, colorPicker);

        HBox hexBox = new HBox(5);
        hexBox.setAlignment(Pos.CENTER_LEFT);
        Label hexLabel = new Label("Hex:");
        hexField = new TextField("#000000");
        hexField.setPrefWidth(80);
        hexField.setOnAction(e -> parseHexColor());
        Button hexSetBtn = new Button("Set");
        hexSetBtn.setOnAction(e -> parseHexColor());
        hexBox.getChildren().addAll(hexLabel, hexField, hexSetBtn);

        Button addPaletteBtn = new Button("➕ Add to Custom");
        addPaletteBtn.setMaxWidth(Double.MAX_VALUE);
        addPaletteBtn.setOnAction(e -> addActiveToCustomPalette());

        // Palette TabPane
        TabPane paletteTabPane = new TabPane();
        paletteTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        paletteTabPane.setPrefHeight(300);

        Tab charTab = new Tab("Characters");
        FlowPane charColorsPane = new FlowPane(4, 4);
        charColorsPane.setPadding(new Insets(5));
        ScrollPane charScroll = new ScrollPane(charColorsPane);
        charScroll.setFitToWidth(true);
        charTab.setContent(charScroll);

        Tab sysTab = new Tab("System");
        FlowPane sysColorsPane = new FlowPane(4, 4);
        sysColorsPane.setPadding(new Insets(5));
        ScrollPane sysScroll = new ScrollPane(sysColorsPane);
        sysScroll.setFitToWidth(true);
        sysTab.setContent(sysScroll);

        Tab customTab = new Tab("Custom");
        customColorsPane = new FlowPane(4, 4);
        customColorsPane.setPadding(new Insets(5));
        ScrollPane customScroll = new ScrollPane(customColorsPane);
        customScroll.setFitToWidth(true);
        customTab.setContent(customScroll);

        paletteTabPane.getTabs().addAll(charTab, sysTab, customTab);

        // Fetch colors
        List<Color> charColors = replacer.getCharacterColors();
        List<Color> sysColors = replacer.getSystemColors();

        populatePalette(charColorsPane, charColors);
        populatePalette(sysColorsPane, sysColors);

        rightSidebar.getChildren().addAll(colorLabel, activeColorBox, hexBox, addPaletteBtn, paletteTabPane);
        root.setRight(rightSidebar);

        // Bottom Bar: Coordinates, Info, Cancel/Save
        BorderPane bottomBar = new BorderPane();
        bottomBar.setPadding(new Insets(10, 0, 0, 0));

        coordsLabel = new Label("X: - Y: -");
        sizeLabel = new Label("Size: " + width + "x" + height);
        HBox infoBox = new HBox(20, coordsLabel, sizeLabel);
        infoBox.setAlignment(Pos.CENTER_LEFT);
        bottomBar.setLeft(infoBox);

        HBox actionBox = new HBox(10);
        actionBox.setAlignment(Pos.CENTER_RIGHT);
        Button cancelBtn = new Button("Cancel");
        cancelBtn.setOnAction(e -> close());
        Button saveBtn = new Button("Apply & Save");
        saveBtn.setDefaultButton(true);
        saveBtn.setOnAction(e -> saveAndApply());
        actionBox.getChildren().addAll(cancelBtn, saveBtn);
        bottomBar.setRight(actionBox);

        root.setBottom(bottomBar);

        Scene scene = new Scene(root, 950, 650);
        // Load style
        if (getClass().getResource("/style.css") != null) {
            scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        }

        // Add keyboard shortcuts
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleKeyPresses);

        setScene(scene);
    }

    private void handleKeyPresses(KeyEvent event) {
        if (event.isControlDown()) {
            if (event.getCode() == KeyCode.Z) {
                undo();
                event.consume();
            } else if (event.getCode() == KeyCode.Y) {
                redo();
                event.consume();
            }
        } else if (event.getCode() == KeyCode.DELETE || event.getCode() == KeyCode.BACK_SPACE) {
            if (activeTool == Tool.SELECT && hasSelection) {
                clearSelectionPixels();
                event.consume();
            }
        } else if (event.getCode() == KeyCode.ESCAPE) {
            if (hasSelection) {
                clearSelection();
                event.consume();
            }
        }
    }

    private void populatePalette(FlowPane pane, List<Color> colors) {
        pane.getChildren().clear();
        for (Color color : colors) {
            Pane swatch = new Pane();
            swatch.setPrefSize(18, 18);
            swatch.setBackground(new Background(new BackgroundFill(color, CornerRadii.EMPTY, Insets.EMPTY)));
            swatch.setStyle("-fx-border-color: #888888; -fx-border-width: 1; -fx-cursor: hand;");
            
            // Hover effect
            swatch.setOnMouseEntered(e -> swatch.setStyle("-fx-border-color: white; -fx-border-width: 1.5; -fx-cursor: hand;"));
            swatch.setOnMouseExited(e -> swatch.setStyle("-fx-border-color: #888888; -fx-border-width: 1; -fx-cursor: hand;"));
            
            swatch.setOnMouseClicked(e -> setActiveColor(color));
            
            // Tooltip to show hex
            Tooltip.install(swatch, new Tooltip(toHexString(color)));
            pane.getChildren().add(swatch);
        }
    }

    private void addActiveToCustomPalette() {
        if (!customColors.contains(activeColor)) {
            customColors.add(activeColor);
            customColors.sort(Comparator.comparingDouble(Color::getHue)
                .thenComparingDouble(Color::getSaturation)
                .thenComparingDouble(Color::getBrightness));
            populatePalette(customColorsPane, customColors);
        }
    }

    private void parseHexColor() {
        String text = hexField.getText().trim();
        if (!text.startsWith("#")) {
            text = "#" + text;
        }
        try {
            Color color = Color.web(text);
            setActiveColor(color);
        } catch (IllegalArgumentException e) {
            // Revert hex field
            hexField.setText(toHexString(activeColor));
        }
    }

    private void setActiveColor(Color color) {
        if (color == null) return;
        this.activeColor = color;
        colorPicker.setValue(color);
        hexField.setText(toHexString(color));
        activeColorPreview.setBackground(new Background(new BackgroundFill(color, CornerRadii.EMPTY, Insets.EMPTY)));
    }

    private String toHexString(Color color) {
        return String.format("#%02X%02X%02X",
                (int) Math.round(color.getRed() * 255),
                (int) Math.round(color.getGreen() * 255),
                (int) Math.round(color.getBlue() * 255));
    }

    private void resizeCanvas() {
        canvas.setWidth(width * zoom);
        canvas.setHeight(height * zoom);
    }

    private void updateToolOptions() {
        fillShapeCheckbox.setDisable(activeTool != Tool.RECTANGLE && activeTool != Tool.CIRCLE);
        boolean usesBrushSize = (activeTool == Tool.PENCIL || activeTool == Tool.ERASER ||
                                 activeTool == Tool.BLUR || activeTool == Tool.BURN ||
                                 activeTool == Tool.DODGE || activeTool == Tool.LIGHTEN ||
                                 activeTool == Tool.DARKEN || activeTool == Tool.FADE ||
                                 activeTool == Tool.REPLACE_COLOR);
        if (brushSizeSlider != null) {
            brushSizeSlider.setDisable(!usesBrushSize);
        }
        if (brushSizeLabel != null) {
            brushSizeLabel.setDisable(!usesBrushSize);
        }
        updateSelectionButtons();
    }

    private void updateSelectionButtons() {
        boolean selectActive = (activeTool == Tool.SELECT && hasSelection);
        fillSelectionBtn.setDisable(!selectActive);
        clearSelectionBtn.setDisable(!selectActive);
        deselectBtn.setDisable(!selectActive);
    }

    private void saveState() {
        undoStack.push(clonePixels(pixels));
        redoStack.clear();
        if (undoStack.size() > 50) {
            undoStack.remove(0);
        }
        updateUndoRedoButtons();
    }

    private Color[][] clonePixels(Color[][] source) {
        Color[][] copy = new Color[width][height];
        for (int i = 0; i < width; i++) {
            System.arraycopy(source[i], 0, copy[i], 0, height);
        }
        return copy;
    }

    private void undo() {
        if (!undoStack.isEmpty()) {
            redoStack.push(clonePixels(pixels));
            pixels = undoStack.pop();
            drawCanvas();
            updateUndoRedoButtons();
        }
    }

    private void redo() {
        if (!redoStack.isEmpty()) {
            undoStack.push(clonePixels(pixels));
            pixels = redoStack.pop();
            drawCanvas();
            updateUndoRedoButtons();
        }
    }

    private void updateUndoRedoButtons() {
        undoButton.setDisable(undoStack.isEmpty());
        redoButton.setDisable(redoStack.isEmpty());
    }

    // Selection operations
    private void fillSelection() {
        if (!hasSelection) return;
        saveState();
        for (int x = selectMinX; x <= selectMaxX; x++) {
            for (int y = selectMinY; y <= selectMaxY; y++) {
                pixels[x][y] = activeColor;
            }
        }
        drawCanvas();
    }

    private void clearSelectionPixels() {
        if (!hasSelection) return;
        saveState();
        for (int x = selectMinX; x <= selectMaxX; x++) {
            for (int y = selectMinY; y <= selectMaxY; y++) {
                pixels[x][y] = null;
            }
        }
        drawCanvas();
    }

    private void clearSelection() {
        hasSelection = false;
        drawCanvas();
        updateSelectionButtons();
    }

    private void drawCanvas() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        // Draw pixel cells
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color color = pixels[x][y];
                if (color == null) {
                    // Checkerboard
                    if (((x + y) % 2) == 0) {
                        gc.setFill(Color.web("#dcdcdc"));
                    } else {
                        gc.setFill(Color.web("#f5f5f5"));
                    }
                } else {
                    gc.setFill(color);
                }
                gc.fillRect(x * zoom, y * zoom, zoom, zoom);
            }
        }

        // Draw preview pixels if dragging shape tool
        if (isDrawing && !previewPixels.isEmpty()) {
            for (Map.Entry<Point, Color> entry : previewPixels.entrySet()) {
                Point p = entry.getKey();
                Color c = entry.getValue();
                if (p.x >= 0 && p.x < width && p.y >= 0 && p.y < height) {
                    if (c == null) {
                        if (((p.x + p.y) % 2) == 0) {
                            gc.setFill(Color.web("#dcdcdc"));
                        } else {
                            gc.setFill(Color.web("#f5f5f5"));
                        }
                    } else {
                        gc.setFill(c);
                    }
                    gc.fillRect(p.x * zoom, p.y * zoom, zoom, zoom);
                }
            }
        }

        // Draw grid lines
        if (showGrid) {
            gc.setStroke(Color.web("#bbbbbb", 0.4));
            gc.setLineWidth(0.5);
            for (int x = 0; x <= width; x++) {
                gc.strokeLine(x * zoom, 0, x * zoom, height * zoom);
            }
            for (int y = 0; y <= height; y++) {
                gc.strokeLine(0, y * zoom, width * zoom, y * zoom);
            }
        }

        // Draw Selection Outline
        if (hasSelection) {
            gc.setStroke(Color.BLUE);
            gc.setLineWidth(1.5);
            gc.setLineDashes(4.0, 4.0);
            double sx = selectMinX * zoom;
            double sy = selectMinY * zoom;
            double sw = (selectMaxX - selectMinX + 1) * zoom;
            double sh = (selectMaxY - selectMinY + 1) * zoom;
            gc.strokeRect(sx, sy, sw, sh);
            gc.setLineDashes(null);
        }
    }

    // Canvas Interaction
    private void applyToolAt(int px, int py) {
        int radius = brushSize / 2;
        boolean isSinglePixel = (brushSize == 1);

        if (activeTool == Tool.REPLACE_COLOR && startX == px && startY == py) {
            replaceColorTarget = pixels[px][py];
        }

        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int nx = px + dx;
                int ny = py + dy;
                if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                    if (isSinglePixel || dx * dx + dy * dy <= radius * radius) {
                        applyToolToPixel(nx, ny);
                    }
                }
            }
        }
    }

    private void applyToolToPixel(int x, int y) {
        switch (activeTool) {
            case PENCIL -> pixels[x][y] = activeColor;
            case ERASER -> pixels[x][y] = null;
            case LIGHTEN -> {
                Color c = pixels[x][y];
                if (c != null) {
                    pixels[x][y] = Color.hsb(c.getHue(), c.getSaturation(), Math.min(1.0, c.getBrightness() + 0.04));
                }
            }
            case DARKEN -> {
                Color c = pixels[x][y];
                if (c != null) {
                    pixels[x][y] = Color.hsb(c.getHue(), c.getSaturation(), Math.max(0.0, c.getBrightness() - 0.04));
                }
            }
            case BURN -> {
                Color c = pixels[x][y];
                if (c != null) {
                    pixels[x][y] = Color.hsb(c.getHue(), Math.min(1.0, c.getSaturation() * 1.08), Math.max(0.0, c.getBrightness() - 0.04));
                }
            }
            case DODGE -> {
                Color c = pixels[x][y];
                if (c != null) {
                    pixels[x][y] = Color.hsb(c.getHue(), Math.max(0.0, c.getSaturation() * 0.92), Math.min(1.0, c.getBrightness() + 0.04));
                }
            }
            case FADE -> {
                Color c = pixels[x][y];
                if (c != null) {
                    pixels[x][y] = Color.hsb(c.getHue(), Math.max(0.0, c.getSaturation() - 0.04), c.getBrightness());
                }
            }
            case BLUR -> {
                double sumR = 0, sumG = 0, sumB = 0, count = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx;
                        int ny = y + dy;
                        if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                            Color nc = pixels[nx][ny];
                            if (nc != null) {
                                sumR += nc.getRed();
                                sumG += nc.getGreen();
                                sumB += nc.getBlue();
                                count++;
                            }
                        }
                    }
                }
                if (count > 0) {
                    pixels[x][y] = Color.color(sumR / count, sumG / count, sumB / count);
                }
            }
            case REPLACE_COLOR -> {
                if (isSameColor(pixels[x][y], replaceColorTarget)) {
                    pixels[x][y] = activeColor;
                }
            }
            default -> {}
        }
    }

    private void updateSelectionMovePreview(int dx, int dy) {
        previewPixels.clear();
        for (int x = 0; x < origSelectWidth; x++) {
            for (int y = 0; y < origSelectHeight; y++) {
                Color c = selectionBuffer[x][y];
                int targetX = origSelectMinX + x + dx;
                int targetY = origSelectMinY + y + dy;
                if (targetX >= 0 && targetX < width && targetY >= 0 && targetY < height) {
                    previewPixels.put(new Point(targetX, targetY), c);
                }
            }
        }
    }

    private void handleMousePressed(MouseEvent event) {
        int px = (int) (event.getX() / zoom);
        int py = (int) (event.getY() / zoom);

        if (px < 0 || px >= width || py < 0 || py >= height) {
            return;
        }

        if (event.getButton() == MouseButton.SECONDARY) {
            if (activeTool == Tool.SELECT && hasSelection &&
                px >= selectMinX && px <= selectMaxX && py >= selectMinY && py <= selectMaxY) {
                
                saveState();
                isMovingSelection = true;
                isDrawing = true;
                dragStartX = px;
                dragStartY = py;
                origSelectMinX = selectMinX;
                origSelectMinY = selectMinY;
                origSelectWidth = selectMaxX - selectMinX + 1;
                origSelectHeight = selectMaxY - selectMinY + 1;
                
                selectionBuffer = new Color[origSelectWidth][origSelectHeight];
                for (int x = 0; x < origSelectWidth; x++) {
                    for (int y = 0; y < origSelectHeight; y++) {
                        selectionBuffer[x][y] = pixels[origSelectMinX + x][origSelectMinY + y];
                        pixels[origSelectMinX + x][origSelectMinY + y] = null;
                    }
                }
                updateSelectionMovePreview(0, 0);
                drawCanvas();
            }
            return;
        }

        if (event.getButton() == MouseButton.PRIMARY) {
            saveState();
            isDrawing = true;
            startX = px;
            startY = py;
            previewPixels.clear();

            boolean isBrushTool = (activeTool == Tool.PENCIL || activeTool == Tool.ERASER ||
                                   activeTool == Tool.BLUR || activeTool == Tool.BURN ||
                                   activeTool == Tool.DODGE || activeTool == Tool.LIGHTEN ||
                                   activeTool == Tool.DARKEN || activeTool == Tool.FADE ||
                                   activeTool == Tool.REPLACE_COLOR);

            if (isBrushTool) {
                applyToolAt(px, py);
                drawCanvas();
            } else if (activeTool == Tool.FILL) {
                Color targetColor = pixels[px][py];
                floodFill(px, py, targetColor, activeColor);
                isDrawing = false;
                drawCanvas();
            } else if (activeTool == Tool.DROPPER) {
                Color picked = pixels[px][py];
                if (picked != null) {
                    setActiveColor(picked);
                }
                isDrawing = false;
            } else if (activeTool == Tool.SELECT) {
                hasSelection = true;
                selectMinX = px;
                selectMinY = py;
                selectMaxX = px;
                selectMaxY = py;
                drawCanvas();
                updateSelectionButtons();
            }
        }
    }

    private void handleMouseDragged(MouseEvent event) {
        if (!isDrawing) return;

        int px = (int) (event.getX() / zoom);
        int py = (int) (event.getY() / zoom);

        // Clamp
        px = Math.max(0, Math.min(width - 1, px));
        py = Math.max(0, Math.min(height - 1, py));

        coordsLabel.setText("X: " + px + " Y: " + py);

        if (isMovingSelection) {
            int dx = px - dragStartX;
            int dy = py - dragStartY;
            
            selectMinX = origSelectMinX + dx;
            selectMinY = origSelectMinY + dy;
            selectMaxX = selectMinX + origSelectWidth - 1;
            selectMaxY = selectMinY + origSelectHeight - 1;
            
            updateSelectionMovePreview(dx, dy);
            drawCanvas();
            return;
        }

        boolean isBrushTool = (activeTool == Tool.PENCIL || activeTool == Tool.ERASER ||
                               activeTool == Tool.BLUR || activeTool == Tool.BURN ||
                               activeTool == Tool.DODGE || activeTool == Tool.LIGHTEN ||
                               activeTool == Tool.DARKEN || activeTool == Tool.FADE ||
                               activeTool == Tool.REPLACE_COLOR);

        if (isBrushTool) {
            applyToolAt(px, py);
            drawCanvas();
        } else if (activeTool == Tool.DROPPER) {
            Color picked = pixels[px][py];
            if (picked != null) {
                setActiveColor(picked);
            }
        } else if (activeTool == Tool.SELECT) {
            selectMinX = Math.min(startX, px);
            selectMinY = Math.min(startY, py);
            selectMaxX = Math.max(startX, px);
            selectMaxY = Math.max(startY, py);
            drawCanvas();
        } else if (activeTool == Tool.LINE) {
            previewPixels.clear();
            List<Point> linePoints = getLinePixels(startX, startY, px, py);
            for (Point p : linePoints) {
                previewPixels.put(p, activeColor);
            }
            drawCanvas();
        } else if (activeTool == Tool.RECTANGLE) {
            previewPixels.clear();
            List<Point> rectPoints = getRectanglePixels(startX, startY, px, py, fillShape);
            for (Point p : rectPoints) {
                previewPixels.put(p, activeColor);
            }
            drawCanvas();
        } else if (activeTool == Tool.CIRCLE) {
            previewPixels.clear();
            List<Point> circPoints = getCirclePixels(startX, startY, px, py, fillShape);
            for (Point p : circPoints) {
                previewPixels.put(p, activeColor);
            }
            drawCanvas();
        }
    }

    private void handleMouseReleased(MouseEvent event) {
        if (!isDrawing) return;
        isDrawing = false;

        if (isMovingSelection) {
            int px = (int) (event.getX() / zoom);
            int py = (int) (event.getY() / zoom);
            px = Math.max(0, Math.min(width - 1, px));
            py = Math.max(0, Math.min(height - 1, py));

            int dx = px - dragStartX;
            int dy = py - dragStartY;
            
            for (int x = 0; x < origSelectWidth; x++) {
                for (int y = 0; y < origSelectHeight; y++) {
                    int targetX = origSelectMinX + x + dx;
                    int targetY = origSelectMinY + y + dy;
                    if (targetX >= 0 && targetX < width && targetY >= 0 && targetY < height) {
                        pixels[targetX][targetY] = selectionBuffer[x][y];
                    }
                }
            }
            
            selectMinX = Math.max(0, Math.min(width - 1, origSelectMinX + dx));
            selectMinY = Math.max(0, Math.min(height - 1, origSelectMinY + dy));
            selectMaxX = Math.max(0, Math.min(width - 1, selectMinX + origSelectWidth - 1));
            selectMaxY = Math.max(0, Math.min(height - 1, selectMinY + origSelectHeight - 1));
            
            previewPixels.clear();
            selectionBuffer = null;
            drawCanvas();
            return;
        }

        // Apply preview pixels for shape tools
        if (activeTool == Tool.LINE || activeTool == Tool.RECTANGLE || activeTool == Tool.CIRCLE) {
            for (Map.Entry<Point, Color> entry : previewPixels.entrySet()) {
                Point p = entry.getKey();
                if (p.x >= 0 && p.x < width && p.y >= 0 && p.y < height) {
                    pixels[p.x][p.y] = entry.getValue();
                }
            }
            previewPixels.clear();
            drawCanvas();
        } else if (activeTool == Tool.SELECT) {
            updateSelectionButtons();
        }
    }

    private void handleMouseMoved(MouseEvent event) {
        int px = (int) (event.getX() / zoom);
        int py = (int) (event.getY() / zoom);
        if (px >= 0 && px < width && py >= 0 && py < height) {
            coordsLabel.setText("X: " + px + " Y: " + py);
        } else {
            coordsLabel.setText("X: - Y: -");
        }
    }

    // Flood Fill algorithm
    private void floodFill(int startX, int startY, Color targetColor, Color replacementColor) {
        if (isSameColor(targetColor, replacementColor)) return;
        Queue<Point> queue = new LinkedList<>();
        queue.add(new Point(startX, startY));
        while (!queue.isEmpty()) {
            Point p = queue.poll();
            if (p.x < 0 || p.x >= width || p.y < 0 || p.y >= height) continue;
            Color c = pixels[p.x][p.y];
            if (isSameColor(c, targetColor)) {
                pixels[p.x][p.y] = replacementColor;
                queue.add(new Point(p.x - 1, p.y));
                queue.add(new Point(p.x + 1, p.y));
                queue.add(new Point(p.x, p.y - 1));
                queue.add(new Point(p.x, p.y + 1));
            }
        }
    }

    private boolean isSameColor(Color c1, Color c2) {
        if (c1 == null || c1.getOpacity() == 0.0) {
            return c2 == null || c2.getOpacity() == 0.0;
        }
        if (c2 == null || c2.getOpacity() == 0.0) return false;
        return c1.equals(c2);
    }

    // Drawing algorithms
    private List<Point> getLinePixels(int x0, int y0, int x1, int y1) {
        List<Point> line = new ArrayList<>();
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;

        int x = x0;
        int y = y0;

        while (true) {
            line.add(new Point(x, y));
            if (x == x1 && y == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
        }
        return line;
    }

    private List<Point> getRectanglePixels(int x0, int y0, int x1, int y1, boolean fill) {
        List<Point> rect = new ArrayList<>();
        int minX = Math.min(x0, x1);
        int maxX = Math.max(x0, x1);
        int minY = Math.min(y0, y1);
        int maxY = Math.max(y0, y1);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                if (fill || x == minX || x == maxX || y == minY || y == maxY) {
                    rect.add(new Point(x, y));
                }
            }
        }
        return rect;
    }

    private List<Point> getCirclePixels(int x0, int y0, int x1, int y1, boolean fill) {
        List<Point> circle = new ArrayList<>();
        int minX = Math.min(x0, x1);
        int maxX = Math.max(x0, x1);
        int minY = Math.min(y0, y1);
        int maxY = Math.max(y0, y1);
        double rx = (maxX - minX) / 2.0;
        double ry = (maxY - minY) / 2.0;
        double cx = minX + rx;
        double cy = minY + ry;

        if (rx == 0 && ry == 0) {
            circle.add(new Point(x0, y0));
            return circle;
        }

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                double dx = (x - cx) / (rx > 0 ? rx : 1.0);
                double dy = (y - cy) / (ry > 0 ? ry : 1.0);
                double dist = dx*dx + dy*dy;
                if (fill) {
                    if (dist <= 1.0) {
                        circle.add(new Point(x, y));
                    }
                } else {
                    double innerRx = rx - 1.0;
                    double innerRy = ry - 1.0;
                    boolean inside = false;
                    if (innerRx > 0 && innerRy > 0) {
                        double idx = (x - cx) / innerRx;
                        double idy = (y - cy) / innerRy;
                        inside = (idx*idx + idy*idy < 1.0);
                    }
                    if (dist <= 1.0 && !inside) {
                        circle.add(new Point(x, y));
                    }
                }
            }
        }
        return circle;
    }

    private void saveAndApply() {
        WritableImage writableImage = new WritableImage(width, height);
        PixelWriter writer = writableImage.getPixelWriter();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                Color c = pixels[x][y];
                if (c == null) {
                    writer.setColor(x, y, Color.TRANSPARENT);
                } else {
                    writer.setColor(x, y, c);
                }
            }
        }
        SpriteData.Sprite newSprite = translator.translateImageToSprite(writableImage);
        onSave.accept(newSprite);
        close();
    }

    public static class Point {
        public final int x;
        public final int y;
        public Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Point)) return false;
            Point point = (Point) o;
            return x == point.x && y == point.y;
        }
        @Override
        public int hashCode() {
            return 31 * x + y;
        }
    }
}
