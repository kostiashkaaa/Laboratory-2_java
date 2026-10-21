package ru.spbstu.mousemaze.ui;

import ru.spbstu.mousemaze.generator.MazeIO;
import ru.spbstu.mousemaze.model.Action;
import ru.spbstu.mousemaze.model.CellType;
import ru.spbstu.mousemaze.model.Maze;
import ru.spbstu.mousemaze.model.Position;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

/** Интерактивный редактор клеток и стен ASCII-схемы. */
public final class MazeEditorController {
    private final JFrame owner;
    private final Consumer<Maze> onMazeChanged;

    /** Создаёт редактор, передающий готовую схему в главное окно. */
    public MazeEditorController(JFrame owner, Consumer<Maze> onMazeChanged) {
        this.owner = owner;
        this.onMazeChanged = Objects.requireNonNull(onMazeChanged, "onMazeChanged");
    }

    /** Открывает редактор текущего лабиринта. */
    public void open(Maze source) {
        if (source == null) return;
        EditorModel model = new EditorModel(source);
        JDialog dialog = new JDialog(owner, "Редактор схемы", true);
        EditorCanvas canvas = new EditorCanvas(model);
        JComboBox<Tool> tools = new JComboBox<>(Tool.values());
        JButton save = new JButton("Применить");
        JButton export = new JButton("Сохранить ASCII");
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Инструмент:"));
        top.add(tools);
        top.add(save);
        top.add(export);

        tools.addActionListener(e -> canvas.tool = (Tool) tools.getSelectedItem());
        save.addActionListener(e -> {
            try {
                Maze maze = model.toMaze();
                onMazeChanged.accept(maze);
                dialog.dispose();
            } catch (IllegalArgumentException exception) {
                showError(dialog, exception.getMessage());
            }
        });
        export.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            if (chooser.showSaveDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                try {
                    MazeIO.save(model.toMaze(), chooser.getSelectedFile().toPath());
                } catch (IOException | IllegalArgumentException exception) {
                    showError(dialog, exception.getMessage());
                }
            }
        });
        dialog.add(top, BorderLayout.NORTH);
        dialog.add(new JScrollPane(canvas), BorderLayout.CENTER);
        dialog.setSize(800, 650);
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    private void showError(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Ошибка схемы", JOptionPane.ERROR_MESSAGE);
    }

    private enum Tool {
        PASSAGE("Проход"),
        WALL("Стена"),
        WATER("Вода (+x)"),
        TRAP("Ток / ловушка (-y)"),
        START("Старт (S)"),
        GOAL("Сыр (+Z)"),
        WALL_BETWEEN_CELLS("Стена между клетками");

        private final String title;

        Tool(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    private static final class EditorModel {
        private final CellType[][] cells;
        private final boolean[][][] passages;
        private Position start;
        private Position goal;

        private EditorModel(Maze maze) {
            cells = maze.cells();
            passages = maze.passages();
            start = maze.start();
            goal = maze.goal();
        }

        private Maze toMaze() {
            if (start == null || goal == null) throw new IllegalArgumentException("Нужно указать старт и сыр");
            Maze maze = new Maze(cells, start, goal, passages);
            if (!maze.isGoalReachable()) throw new IllegalArgumentException("Сыр недостижим от старта");
            return maze;
        }
    }

    private static final class EditorCanvas extends JPanel {
        private final EditorModel model;
        private Tool tool = Tool.PASSAGE;

        private EditorCanvas(EditorModel model) {
            this.model = model;
            // Холст растёт вместе с лабиринтом (клетка не меньше 18 px), чтобы в
            // большом лабиринте все клетки были видны и редактируемы через прокрутку.
            setPreferredSize(new Dimension(Math.max(700, model.cells[0].length * 18),
                    Math.max(600, model.cells.length * 18)));
            setBackground(new Color(28, 24, 38));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
                    edit(event.getX(), event.getY());
                }
            });
        }

        private void edit(int x, int y) {
            int cell = Math.max(18, Math.min(getWidth() / model.cells[0].length, getHeight() / model.cells.length));
            int column = x / cell;
            int row = y / cell;
            if (row < 0 || row >= model.cells.length || column < 0 || column >= model.cells[0].length) return;
            double localX = (x % cell) / (double) cell;
            double localY = (y % cell) / (double) cell;

            if (tool == Tool.WALL_BETWEEN_CELLS && (localX > .82 || localY > .82 || localX < .18 || localY < .18)) {
                if (localX > .82 && column + 1 < model.cells[0].length) {
                    toggleWall(row, column, Action.RIGHT);
                } else if (localY > .82 && row + 1 < model.cells.length) {
                    toggleWall(row, column, Action.DOWN);
                } else if (localX < .18 && column > 0) {
                    toggleWall(row, column, Action.LEFT);
                } else if (localY < .18 && row > 0) {
                    toggleWall(row, column, Action.UP);
                }
            } else if (tool != Tool.WALL_BETWEEN_CELLS) {
                Position currentPos = new Position(row, column);
                if (tool != Tool.START && currentPos.equals(model.start)) {
                    model.start = null;
                }
                if (tool != Tool.GOAL && currentPos.equals(model.goal)) {
                    model.goal = null;
                }
                CellType type = switch (tool) {
                    case PASSAGE -> CellType.FREE;
                    case WALL -> CellType.WALL;
                    case WATER -> CellType.WATER;
                    case TRAP -> CellType.TRAP;
                    case START -> CellType.START;
                    case GOAL -> CellType.GOAL;
                    default -> model.cells[row][column];
                };
                if (tool == Tool.START && model.start != null) {
                    model.cells[model.start.row()][model.start.column()] = CellType.FREE;
                }
                if (tool == Tool.GOAL && model.goal != null) {
                    model.cells[model.goal.row()][model.goal.column()] = CellType.FREE;
                }
                model.cells[row][column] = type;
                if (tool == Tool.START) model.start = currentPos;
                if (tool == Tool.GOAL) model.goal = currentPos;
            }
            repaint();
        }

        private void toggleWall(int row, int column, Action action) {
            boolean value = !model.passages[row][column][action.ordinal()];
            model.passages[row][column][action.ordinal()] = value;
            Position next = new Position(row, column).move(action);
            model.passages[next.row()][next.column()][action.opposite().ordinal()] = value;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            int cell = Math.max(18, Math.min(getWidth() / model.cells[0].length, getHeight() / model.cells.length));
            Graphics2D g = (Graphics2D) graphics;
            for (int row = 0; row < model.cells.length; row++) {
                for (int column = 0; column < model.cells[0].length; column++) {
                    int x = column * cell;
                    int y = row * cell;
                    g.setColor(switch (model.cells[row][column]) {
                        case WALL -> new Color(65, 40, 80);
                        case WATER -> new Color(40, 120, 200);
                        case TRAP -> new Color(180, 140, 35);
                        case START -> new Color(35, 120, 150);
                        case GOAL -> new Color(210, 165, 35);
                        default -> new Color(65, 65, 75);
                    });
                    g.fillRect(x, y, cell, cell);
                    g.setColor(new Color(35, 20, 45));
                    g.setStroke(new BasicStroke(3));
                    if (!model.passages[row][column][Action.RIGHT.ordinal()]) {
                        g.drawLine(x + cell, y, x + cell, y + cell);
                    }
                    if (!model.passages[row][column][Action.DOWN.ordinal()]) {
                        g.drawLine(x, y + cell, x + cell, y + cell);
                    }
                    g.setColor(Color.WHITE);
                    String text = switch (model.cells[row][column]) {
                        case START -> "S";
                        case GOAL -> "C";
                        case WATER -> "W";
                        case TRAP -> "T";
                        default -> "";
                    };
                    if (!text.isEmpty()) {
                        g.drawString(text, x + cell / 3, y + cell * 2 / 3);
                    }
                }
            }
            // Внешняя граница поля (верхняя и левая стороны иначе не рисуются).
            g.setColor(new Color(35, 20, 45));
            g.setStroke(new BasicStroke(3));
            g.drawRect(0, 0, cell * model.cells[0].length, cell * model.cells.length);
        }
    }
}
