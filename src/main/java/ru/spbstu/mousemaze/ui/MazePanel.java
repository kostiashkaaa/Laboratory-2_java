package ru.spbstu.mousemaze.ui;

import ru.spbstu.mousemaze.model.Action;
import ru.spbstu.mousemaze.model.CellType;
import ru.spbstu.mousemaze.model.Maze;
import ru.spbstu.mousemaze.model.Position;

import javax.swing.*;
import java.awt.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Панель визуализации лабиринта, следа и тепловой карты посещений.
 */
public final class MazePanel extends JPanel {
    private static final int HEAT_LEGEND_BAND = 34;
    private Maze maze;
    private Position mouse;
    private List<Position> trail = List.of();
    private boolean heatmap;
    private int[][] visitCounts;
    private Set<Position> consumedWater = Set.of();
    private Position activeTrap;

    /**
     * Создаёт пустую панель.
     */
    public MazePanel() {
        setBackground(new Color(25, 20, 35));
    }

    /**
     * Устанавливает модель для отображения.
     */
    public void setMaze(Maze maze) {
        this.maze = maze;
        this.mouse = maze == null ? null : maze.start();
        repaint();
    }

    /**
     * Устанавливает положение мыши и след.
     */
    public void setMouse(Position mouse, List<Position> trail) {
        this.mouse = mouse;
        this.trail = trail == null ? List.of() : List.copyOf(trail);
        repaint();
    }

    /**
     * Отмечает воду, выпитую в текущем эпизоде, и последнее попадание в ловушку.
     */
    public void setHazards(Set<Position> consumedWater, Position activeTrap) {
        this.consumedWater = consumedWater == null ? Set.of() : Set.copyOf(consumedWater);
        this.activeTrap = activeTrap;
        repaint();
    }

    /**
     * Включает тепловую карту посещений.
     */
    public void setHeatmap(boolean heatmap) {
        this.heatmap = heatmap;
        repaint();
    }

    /**
     * Передаёт счётчики фактических посещений клеток мышью за время обучения.
     * Массив сохраняется по ссылке и может изменяться извне между кадрами —
     * это ожидаемо, карта отображает текущее накопленное состояние.
     *
     * @param visitCounts счётчики посещений размером [строки][столбцы], либо {@code null}
     */
    public void setVisitCounts(int[][] visitCounts) {
        this.visitCounts = visitCounts;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (maze == null) return;
        Graphics2D g = (Graphics2D) graphics.create();
        int maxVisits = heatmap ? maxVisits() : 0;
        boolean legend = heatmap && maxVisits > 0;
        // Под легенду тепловой карты резервируем полосу под лабиринтом, чтобы она не
        // закрывала клетки.
        int legendBand = heatmap ? HEAT_LEGEND_BAND : 0; // постоянно, чтобы поле не «прыгало»
        int height = getHeight() - legendBand;
        int cell = Math.max(8, Math.min(getWidth() / maze.columns(), height / maze.rows()));
        int ox = (getWidth() - cell * maze.columns()) / 2;
        int oy = (height - cell * maze.rows()) / 2;
        Set<Position> trailCells = trail.isEmpty() ? Set.of() : new HashSet<>(trail);

        for (int r = 0; r < maze.rows(); r++) {
            for (int c = 0; c < maze.columns(); c++) {
                Position p = new Position(r, c);
                int x = ox + c * cell;
                int y = oy + r * cell;
                Color color = heatColor(p, maxVisits);
                if (color == null) {
                    color = maze.cellAt(p) == CellType.WALL
                            ? new Color(55, 35, 70)
                            : new Color(45, 42, 55);
                }
                g.setColor(color);
                g.fillRect(x, y, cell, cell);

                if (!heatmap && trailCells.contains(p)) {
                    g.setColor(new Color(90, 170, 220, 100));
                    g.fillRect(x + 2, y + 2, cell - 4, cell - 4);
                }

                drawMarker(g, maze.cellAt(p), x, y, cell);

                if (maze.cellAt(p) == CellType.WATER && consumedWater.contains(p)) {
                    g.setColor(new Color(180, 220, 255, 150));
                    g.setStroke(new BasicStroke(Math.max(1, cell / 12f)));
                    g.drawLine(x + cell / 4, y + cell / 4, x + cell * 3 / 4, y + cell * 3 / 4);
                    g.drawLine(x + cell * 3 / 4, y + cell / 4, x + cell / 4, y + cell * 3 / 4);
                }

                if (p.equals(activeTrap)) {
                    g.setColor(new Color(255, 70, 40, 210));
                    g.setStroke(new BasicStroke(Math.max(2, cell / 10f)));
                    g.drawOval(x + 2, y + 2, cell - 4, cell - 4);
                }
            }
        }

        if (legend) {
            drawHeatLegend(g, ox, oy + cell * maze.rows() + 6);
        }

        g.setColor(new Color(35, 18, 45));
        g.setStroke(new BasicStroke(Math.max(2, cell / 8f)));
        for (int r = 0; r < maze.rows(); r++) {
            for (int c = 0; c < maze.columns(); c++) {
                Position p = new Position(r, c);
                int x = ox + c * cell;
                int y = oy + r * cell;
                if (r == 0 || !maze.canMove(p, Action.UP)) g.drawLine(x, y, x + cell, y);
                if (c == 0 || !maze.canMove(p, Action.LEFT)) g.drawLine(x, y, x, y + cell);
                if (!maze.canMove(p, Action.DOWN)) g.drawLine(x, y + cell, x + cell, y + cell);
                if (!maze.canMove(p, Action.RIGHT)) g.drawLine(x + cell, y, x + cell, y + cell);
            }
        }

        if (heatmap && trail.size() > 1) {
            // На тепловой карте заливка следа сливается с цветом клеток — рисуем путь линией.
            g.setColor(new Color(90, 200, 255, 220));
            g.setStroke(new BasicStroke(Math.max(2, cell / 7f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 1; i < trail.size(); i++) {
                Position from = trail.get(i - 1);
                Position to = trail.get(i);
                g.drawLine(ox + from.column() * cell + cell / 2, oy + from.row() * cell + cell / 2,
                        ox + to.column() * cell + cell / 2, oy + to.row() * cell + cell / 2);
            }
        }

        if (mouse != null) {
            int mx = ox + mouse.column() * cell;
            int my = oy + mouse.row() * cell;
            if (mouse.equals(maze.goal())) {
                // Мышь дошла до сыра: подсвечиваем клетку и рисуем мышь поверх сыра,
                // иначе сыр закрывал мышь и успех был не виден.
                g.setColor(new Color(90, 220, 120, 200));
                g.setStroke(new BasicStroke(Math.max(3, cell / 8f)));
                g.drawRect(mx + 2, my + 2, cell - 4, cell - 4);
            }
            drawMouse(g, mx, my, cell);
        }
        g.dispose();
    }

    /**
     * Цвет клетки по частоте реальных посещений мышью (не по Q-значению и не
     * по эвристике — только по тому, сколько раз мышь фактически там побывала
     * во время обучения).
     */
    private Color heatColor(Position p, int maxVisits) {
        if (!heatmap || visitCounts == null || maze.cellAt(p) == CellType.WALL) return null;
        int visits = visitCounts[p.row()][p.column()];
        if (maxVisits <= 0 || visits <= 0) {
            return new Color(55, 55, 62);
        }
        float intensity = (float) Math.min(1.0, Math.sqrt(visits / (double) maxVisits));
        return blend(new Color(55, 55, 62), new Color(235, 95, 55), intensity);
    }

    private int maxVisits() {
        if (visitCounts == null) return 0;
        int max = 0;
        for (int row = 0; row < maze.rows(); row++) {
            for (int column = 0; column < maze.columns(); column++) {
                if (!maze.isWall(new Position(row, column))) {
                    max = Math.max(max, visitCounts[row][column]);
                }
            }
        }
        return max;
    }

    private Color blend(Color from, Color to, float amount) {
        int red = (int) (from.getRed() + (to.getRed() - from.getRed()) * amount);
        int green = (int) (from.getGreen() + (to.getGreen() - from.getGreen()) * amount);
        int blue = (int) (from.getBlue() + (to.getBlue() - from.getBlue()) * amount);
        return new Color(red, green, blue);
    }

    /** Градиент «редко → часто» в полосе под лабиринтом. */
    private void drawHeatLegend(Graphics2D g, int x, int y) {
        int width = 160;
        int height = 10;
        for (int i = 0; i < width; i++) {
            float ratio = i / (float) (width - 1);
            g.setColor(blend(new Color(55, 55, 62), new Color(235, 95, 55), ratio));
            g.fillRect(x + i, y, 1, height);
        }
        g.setColor(Color.WHITE);
        g.setFont(g.getFont().deriveFont(Font.PLAIN, 11f));
        FontMetrics metrics = g.getFontMetrics();
        int baseline = y + height + metrics.getAscent() + 2;
        g.drawString("редко", x, baseline);
        g.drawString("часто", x + width - metrics.stringWidth("часто"), baseline);
        g.drawString("посещения клеток мышью за обучение", x + width + 10, y + height);
    }

    private void drawMarker(Graphics2D g, CellType type, int x, int y, int cell) {
        switch (type) {
            case GOAL -> drawCheese(g, x, y, cell);
            case START -> {
                g.setColor(new Color(120, 210, 255));
                g.setFont(g.getFont().deriveFont(Font.BOLD, Math.max(10, cell * .48f)));
                g.drawString("S", x + cell / 3, y + (int) (cell * .68));
            }
            case WATER -> drawWaterDrop(g, x, y, cell);
            case TRAP -> drawLightning(g, x, y, cell);
            default -> {
            }
        }
    }

    /**
     * Капля воды: круг снизу и сужающийся к вершине верх той же ширины, что и круг.
     * Раньше основание треугольника было шире круга, и капля выглядела как стрелка «вверх».
     */
    private void drawWaterDrop(Graphics2D g, int x, int y, int cell) {
        double r = cell * 0.2;
        double cx = x + cell / 2.0;
        double cy = y + cell * 0.62;
        java.awt.geom.Path2D.Double drop = new java.awt.geom.Path2D.Double();
        drop.moveTo(cx, y + cell * 0.16);
        drop.curveTo(cx + r * 0.3, cy - r * 1.4, cx + r, cy - r * 0.6, cx + r, cy);
        drop.curveTo(cx + r, cy + r * 1.35, cx - r, cy + r * 1.35, cx - r, cy);
        drop.curveTo(cx - r, cy - r * 0.6, cx - r * 0.3, cy - r * 1.4, cx, y + cell * 0.16);
        drop.closePath();
        g.setColor(new Color(80, 190, 255));
        g.fill(drop);
        g.setColor(new Color(30, 110, 180));
        g.setStroke(new BasicStroke(Math.max(1, cell / 30f)));
        g.draw(drop);
    }

    /**
     * Молния тока рисуется многоугольником, а не символом «⚡»: в стандартных
     * шрифтах Swing (например, на Windows) этого символа может не быть.
     */
    private void drawLightning(Graphics2D g, int x, int y, int cell) {
        double[][] shape = {{.58, .12}, {.30, .55}, {.48, .55}, {.38, .88}, {.72, .42}, {.53, .42}, {.66, .12}};
        int[] xs = new int[shape.length];
        int[] ys = new int[shape.length];
        for (int i = 0; i < shape.length; i++) {
            xs[i] = x + (int) Math.round(shape[i][0] * cell);
            ys[i] = y + (int) Math.round(shape[i][1] * cell);
        }
        g.setColor(new Color(255, 215, 50));
        g.fillPolygon(xs, ys, xs.length);
        g.setColor(new Color(150, 95, 10));
        g.setStroke(new BasicStroke(Math.max(1, cell / 25f)));
        g.drawPolygon(xs, ys, xs.length);
    }

    private void drawCheese(Graphics2D g, int x, int y, int cell) {
        int padding = Math.max(2, cell / 6);
        int[] xs = {x + padding, x + cell - padding, x + cell - padding, x + padding};
        int[] ys = {y + padding, y + cell / 3, y + cell - padding, y + cell - padding};
        g.setColor(new Color(255, 210, 45));
        g.fillPolygon(xs, ys, 4);
        g.setColor(new Color(120, 75, 15));
        g.setStroke(new BasicStroke(Math.max(1, cell / 18f)));
        g.drawPolygon(xs, ys, 4);
        g.fillOval(x + cell / 2, y + cell / 2, Math.max(2, cell / 8), Math.max(2, cell / 8));
        g.fillOval(x + cell * 2 / 3, y + cell * 3 / 5, Math.max(2, cell / 9), Math.max(2, cell / 9));
    }

    private void drawMouse(Graphics2D g, int x, int y, int cell) {
        int body = Math.max(6, cell / 2);
        int bx = x + (cell - body) / 2;
        int by = y + (cell - body) / 2;
        g.setColor(new Color(235, 235, 240));
        g.fillOval(bx, by, body, body);
        g.setColor(new Color(205, 205, 215));
        g.fillOval(bx, by - body / 4, body / 2, body / 2);
        g.fillOval(bx + body / 2, by - body / 4, body / 2, body / 2);
        g.setColor(new Color(45, 45, 55));
        g.fillOval(bx + body / 3, by + body / 3, Math.max(2, cell / 12), Math.max(2, cell / 12));
        g.fillOval(bx + body / 2, by + body / 3, Math.max(2, cell / 12), Math.max(2, cell / 12));
    }
}
