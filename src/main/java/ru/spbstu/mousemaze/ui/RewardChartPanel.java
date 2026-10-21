package ru.spbstu.mousemaze.ui;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Простой график суммарной награды по эпохам.
 */
public final class RewardChartPanel extends JPanel {
    private static final int LEFT = 58;
    private static final int RIGHT = 14;
    private final List<Double> values = new ArrayList<>();
    private final List<Boolean> solved = new ArrayList<>();
    private static final int MOVING_AVERAGE_WINDOW = 20;
    private IntConsumer episodeClickListener = episode -> { };

    /** Создаёт график: подсказка по наведению, клик по эпохе передаётся слушателю. */
    public RewardChartPanel() {
        setToolTipText("");
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                int index = indexAt(event.getX());
                if (index >= 0) episodeClickListener.accept(index + 1);
            }
        });
    }

    /** Задаёт обработчик клика по эпохе (номер эпохи начиная с 1). */
    public void setEpisodeClickListener(IntConsumer listener) {
        this.episodeClickListener = listener == null ? episode -> { } : listener;
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        int index = indexAt(event.getX());
        if (index < 0) return null;
        return String.format("Эпоха %d: награда %.1f, сыр: %s — нажмите, чтобы проиграть эпоху",
                index + 1, values.get(index), solved.get(index) ? "да" : "нет");
    }

    /** Индекс ближайшей к координате x эпохи, либо -1. */
    private int indexAt(int x) {
        if (values.isEmpty()) return -1;
        int w = Math.max(1, getWidth() - LEFT - RIGHT);
        if (x < LEFT - 5 || x > LEFT + w + 5) return -1;
        if (values.size() == 1) return 0;
        int index = (int) Math.round((x - LEFT) * (values.size() - 1) / (double) w);
        return Math.max(0, Math.min(values.size() - 1, index));
    }

    /**
     * Добавляет награду эпохи и признак достижения сыра.
     */
    public void addValue(double value, boolean solvedEpisode) {
        values.add(value);
        solved.add(solvedEpisode);
        repaint();
    }

    /**
     * Очищает историю.
     */
    public void clearValues() {
        values.clear();
        solved.clear();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (values.isEmpty()) return;
        Graphics2D g = (Graphics2D) graphics.create();
        int left = LEFT;
        int right = RIGHT;
        int top = 24;     // над графиком — отдельная полоса для легенды
        int bottom = 18;  // под графиком — номера эпох
        int w = Math.max(1, getWidth() - left - right);
        int h = Math.max(1, getHeight() - top - bottom);
        double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(1);
        if (Math.abs(max - min) < 0.0001) {
            min -= 1;
            max += 1;
        }
        double scale = max - min;
        g.setColor(new Color(100, 100, 100));
        g.drawLine(left, top, left, top + h);
        g.drawLine(left, top + h, left + w, top + h);
        g.setFont(g.getFont().deriveFont(Font.PLAIN, 11f));
        FontMetrics metrics = g.getFontMetrics();
        int baseline = top + h + metrics.getAscent() + 2;
        g.drawString(String.format("%.0f", max), 4, top + metrics.getAscent() / 2);
        g.drawString(String.format("%.0f", min), 4, top + h + metrics.getAscent() / 2);
        g.drawString("1", left, baseline);
        String lastEpisode = Integer.toString(values.size());
        g.drawString(lastEpisode, left + w - metrics.stringWidth(lastEpisode), baseline);
        g.drawString("Эпохи", left + (w - metrics.stringWidth("Эпохи")) / 2, baseline);
        if (values.size() >= 2) {
            g.setColor(new Color(125, 180, 145));
            for (int i = 1; i < values.size(); i++) {
                int x1 = left + (i - 1) * w / (values.size() - 1);
                int x2 = left + i * w / (values.size() - 1);
                int y1 = top + h - (int) ((values.get(i - 1) - min) / scale * h);
                int y2 = top + h - (int) ((values.get(i) - min) / scale * h);
                g.drawLine(x1, y1, x2, y2);
            }
        }
        g.setColor(new Color(35, 120, 75));
        g.setStroke(new BasicStroke(2f));
        for (int i = 0; i < values.size(); i++) {
            int from = Math.max(0, i - MOVING_AVERAGE_WINDOW + 1);
            double average = values.subList(from, i + 1).stream().mapToDouble(Double::doubleValue).average().orElse(0);
            if (i > 0) {
                int x1 = left + (i - 1) * w / (Math.max(1, values.size() - 1));
                int x2 = left + i * w / (Math.max(1, values.size() - 1));
                int prevFrom = Math.max(0, i - 1 - MOVING_AVERAGE_WINDOW + 1);
                double previous = values.subList(prevFrom, i).stream().mapToDouble(Double::doubleValue).average().orElse(0);
                int y1 = top + h - (int) ((previous - min) / scale * h);
                int y2 = top + h - (int) ((average - min) / scale * h);
                g.drawLine(x1, y1, x2, y2);
            }
            if (Boolean.TRUE.equals(solved.get(i))) {
                int x = left + i * w / (Math.max(1, values.size() - 1));
                int y = top + h - (int) ((values.get(i) - min) / scale * h);
                g.setColor(new Color(220, 170, 35));
                g.fillOval(x - 3, y - 3, 7, 7);
                g.setColor(new Color(35, 120, 75));
            }
        }
        drawLegend(g, metrics);
        g.dispose();
    }

    /** Легенда в полосе над графиком, чтобы не перекрывать данные. */
    private void drawLegend(Graphics2D g, FontMetrics metrics) {
        int y = 4 + metrics.getAscent();
        int x = 4;
        g.setColor(new Color(90, 90, 90));
        g.drawString("Награда за эпоху:", x, y);
        x += metrics.stringWidth("Награда за эпоху:") + 12;
        g.setColor(new Color(125, 180, 145));
        g.drawLine(x, y - 4, x + 14, y - 4);
        g.drawString("по эпохам", x + 18, y);
        x += 18 + metrics.stringWidth("по эпохам") + 14;
        g.setColor(new Color(35, 120, 75));
        g.setStroke(new BasicStroke(2f));
        g.drawLine(x, y - 4, x + 14, y - 4);
        g.drawString("среднее за " + MOVING_AVERAGE_WINDOW, x + 18, y);
        x += 18 + metrics.stringWidth("среднее за " + MOVING_AVERAGE_WINDOW) + 14;
        g.setColor(new Color(220, 170, 35));
        g.fillOval(x, y - 8, 7, 7);
        g.drawString("мышь дошла до сыра (клик по точке — показать эпоху)", x + 11, y);
    }
}
