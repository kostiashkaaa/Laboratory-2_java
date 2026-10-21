package ru.spbstu.mousemaze.ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.TitledBorder;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/** Компактная двухколоночная панель параметров лабиринта, Q-обучения и наград по заданию. */
public final class SettingsPanel extends JPanel {
    public final JSpinner rows = new JSpinner(new SpinnerNumberModel(10, 2, 100, 1));
    public final JSpinner columns = new JSpinner(new SpinnerNumberModel(10, 2, 100, 1));
    public final JTextField seed = new JTextField("42", 6);
    public final JButton randomSeed = new JButton("Случайный");
    public final JSpinner waterPercent = new JSpinner(new SpinnerNumberModel(8, 0, 100, 1));
    public final JSpinner trapPercent = new JSpinner(new SpinnerNumberModel(6, 0, 100, 1));
    // 0% доп. проходов даёт "идеальный" лабиринт-дерево с ЕДИНСТВЕННЫМ путём
    // старт->цель. Для табличного ε-greedy Q-learning это крайне тяжёлый случай
    // "иглы в стоге сена" — агент может тысячи эпох не наткнуться на верный путь
    // случайно, ведь обходных маршрутов нет вообще. 8% уже дают заметные петли
    // и кардинально повышают шанс найти путь за разумное число эпох (проверено
    // эмпирически: 0% почти никогда не сходится за 1000 эпох даже на 10×10).
    public final JSpinner extraPassagesPercent = new JSpinner(new SpinnerNumberModel(8, 0, 30, 1));

    public final JSpinner episodes = new JSpinner(new SpinnerNumberModel(1000, 1, 100000, 100));
    public final JSpinner maxSteps = new JSpinner(new SpinnerNumberModel(400, 1, 100000, 10));
    public final JSpinner alpha = new JSpinner(new SpinnerNumberModel(0.1, 0.0, 1.0, 0.01));
    // gamma=0.95 слишком агрессивно дисконтирует награду за дальний сыр: при
    // длине пути ~44 шага (обычное дело для лабиринта 10x10) 0.95^44 ≈ 0.10,
    // то есть награда +100 "усыхает" до ~10, а штрафы за шаги по пути
    // дисконтируются слабее и суммарно перевешивают — агент математически
    // корректно учится, что идти к цели НЕВЫГОДНО. При gamma=0.99 дисконт
    // за те же 44 шага — 0.99^44 ≈ 0.64, и путь к цели остаётся выгодным
    // независимо от размера лабиринта, который задаёт пользователь.
    public final JSpinner gamma = new JSpinner(new SpinnerNumberModel(0.99, 0.0, 1.0, 0.01));
    public final JSpinner epsilonStart = new JSpinner(new SpinnerNumberModel(1.0, 0.0, 1.0, 0.01));
    public final JSpinner epsilonMin = new JSpinner(new SpinnerNumberModel(0.05, 0.0, 1.0, 0.01));
    public final JSpinner epsilonDecay = new JSpinner(new SpinnerNumberModel(0.995, 0.0, 1.0, 0.001));

    public final JSpinner cheeseReward = new JSpinner(new SpinnerNumberModel(100.0, 0.01, 100000.0, 1.0));
    public final JSpinner waterReward = new JSpinner(new SpinnerNumberModel(10.0, 0.01, 100000.0, 1.0));
    public final JSpinner trapPenalty = new JSpinner(new SpinnerNumberModel(-20.0, -100000.0, -0.01, 1.0));
    public final JSpinner stepPenalty = new JSpinner(new SpinnerNumberModel(-1.0, -100000.0, -0.01, 0.5));
    public final JSpinner wallPenalty = new JSpinner(new SpinnerNumberModel(-5.0, -100000.0, -0.01, 1.0));
    public final JCheckBox trapTerminates = new JCheckBox("Ток завершает");

    public final JCheckBox heatmap = new JCheckBox("Тепловая карта (частота посещений)");
    public final JSlider animationSpeed = new JSlider(1, 100, 95);
    private final JLabel animationSpeedValue = new JLabel();
    public final JSpinner animationEvery = new JSpinner(new SpinnerNumberModel(100, 1, 1000, 1));

    /** Создаёт панель с эргономичной группировкой в 2 колонки в точном соответствии с заданием. */
    public SettingsPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        configureEditors();

        add(createMazeSection());
        add(Box.createVerticalStrut(4));
        add(createLearningSection());
        add(Box.createVerticalStrut(4));
        add(createRewardsSection());
        add(Box.createVerticalStrut(4));
        add(createDisplaySection());
    }

    private void configureEditors() {
        alpha.setEditor(new JSpinner.NumberEditor(alpha, "0.00"));
        gamma.setEditor(new JSpinner.NumberEditor(gamma, "0.00"));
        epsilonStart.setEditor(new JSpinner.NumberEditor(epsilonStart, "0.00"));
        epsilonMin.setEditor(new JSpinner.NumberEditor(epsilonMin, "0.00"));
        epsilonDecay.setEditor(new JSpinner.NumberEditor(epsilonDecay, "0.000"));
        cheeseReward.setEditor(new JSpinner.NumberEditor(cheeseReward, "0.0"));
        waterReward.setEditor(new JSpinner.NumberEditor(waterReward, "0.0"));
        trapPenalty.setEditor(new JSpinner.NumberEditor(trapPenalty, "0.0"));
        stepPenalty.setEditor(new JSpinner.NumberEditor(stepPenalty, "0.0"));
        wallPenalty.setEditor(new JSpinner.NumberEditor(wallPenalty, "0.0"));

        randomSeed.setToolTipText("Сгенерировать случайный seed");
        randomSeed.setMargin(new Insets(1, 4, 1, 4));

        animationSpeed.setMajorTickSpacing(25);
        animationSpeed.setPaintTicks(true);
        animationSpeed.setToolTipText("Скорость анимации: 1 — медленно, 100 — без задержки");
        animationSpeed.setPreferredSize(new Dimension(140, 24));
        updateAnimationSpeedLabel();
        animationSpeed.addChangeListener(e -> updateAnimationSpeedLabel());

        heatmap.setToolTipText("Цвет клетки показывает, как часто мышь реально в ней бывала");
        trapTerminates.setToolTipText("Электротравма (-y) завершает текущий эпизод");
    }

    private JPanel createMazeSection() {
        JPanel panel = createTitledPanel("Лабиринт");
        GridBagConstraints c = baseGbc();

        addPair(panel, c, 0, "Высота", rows, "Ширина", columns);

        // Строка с Seed и кнопкой случайного seed
        JPanel seedBox = new JPanel(new GridBagLayout());
        GridBagConstraints sc = new GridBagConstraints();
        sc.fill = GridBagConstraints.HORIZONTAL;
        sc.weightx = 1.0;
        seedBox.add(seed, sc);
        sc.fill = GridBagConstraints.NONE;
        sc.weightx = 0.0;
        sc.insets = new Insets(0, 3, 0, 0);
        seedBox.add(randomSeed, sc);

        addPair(panel, c, 1, "Seed", seedBox, "Доп. ходы, %", extraPassagesPercent);
        addPair(panel, c, 2, "Вода (+x), %", waterPercent, "Ток (-y), %", trapPercent);
        return panel;
    }

    private JPanel createLearningSection() {
        JPanel panel = createTitledPanel("Обучение (Q-Learning)");
        GridBagConstraints c = baseGbc();

        addPair(panel, c, 0, "Эпохи", episodes, "Лимит шагов", maxSteps);
        addPair(panel, c, 1, "Alpha (α)", alpha, "Gamma (γ)", gamma);
        addPair(panel, c, 2, "Eps старт", epsilonStart, "Eps мин", epsilonMin);
        addPair(panel, c, 3, "Затухание ε", epsilonDecay, null, null);
        return panel;
    }

    private JPanel createRewardsSection() {
        JPanel panel = createTitledPanel("Награды и подкрепления (MDP)");
        GridBagConstraints c = baseGbc();

        addPair(panel, c, 0, "Сыр (+Z)", cheeseReward, "Вода (+x)", waterReward);
        addPair(panel, c, 1, "Ток (−y)", trapPenalty, "Стена (−)", wallPenalty);

        c.gridy = 2;
        c.gridx = 0;
        panel.add(createLabel("Шаг (−):"), c);
        c.gridx = 1;
        panel.add(stepPenalty, c);

        c.gridx = 2;
        c.gridwidth = 2;
        trapTerminates.setFont(trapTerminates.getFont().deriveFont(11f));
        panel.add(trapTerminates, c);
        c.gridwidth = 1;

        return panel;
    }

    private JPanel createDisplaySection() {
        JPanel panel = createTitledPanel("Визуализация");
        GridBagConstraints c = baseGbc();

        heatmap.setFont(heatmap.getFont().deriveFont(11f));
        c.gridy = 0;
        c.gridx = 0;
        c.gridwidth = 4;
        panel.add(heatmap, c);
        c.gridwidth = 1;

        addPair(panel, c, 1, "Каждая эпоха №", animationEvery, "Скорость", animationSpeed);

        c.gridy = 2;
        c.gridx = 2;
        c.gridwidth = 2;
        animationSpeedValue.setFont(animationSpeedValue.getFont().deriveFont(10.5f));
        panel.add(animationSpeedValue, c);
        c.gridwidth = 1;

        return panel;
    }

    private void addPair(JPanel panel, GridBagConstraints c, int row,
                         String label1, JComponent comp1, String label2, JComponent comp2) {
        c.gridy = row;

        c.gridx = 0;
        c.weightx = 0.0;
        panel.add(createLabel(label1 + ":"), c);

        c.gridx = 1;
        c.weightx = 0.5;
        panel.add(comp1, c);

        c.gridx = 2;
        c.weightx = 0.0;
        if (label2 != null && !label2.isEmpty()) {
            panel.add(createLabel(label2 + ":"), c);
        } else {
            panel.add(Box.createHorizontalGlue(), c);
        }

        c.gridx = 3;
        c.weightx = 0.5;
        if (comp2 != null) {
            panel.add(comp2, c);
        } else {
            panel.add(Box.createHorizontalGlue(), c);
        }
    }

    private JLabel createLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(11f));
        return label;
    }

    private JPanel createTitledPanel(String title) {
        JPanel panel = new JPanel(new GridBagLayout());
        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), title);
        border.setTitleFont(border.getTitleFont().deriveFont(Font.BOLD, 11.5f));
        panel.setBorder(border);
        return panel;
    }

    private GridBagConstraints baseGbc() {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(1, 3, 1, 3);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        return c;
    }

    /** @return задержка между шагами анимации: 0 мс при скорости 100, 495 мс при 1 */
    public int animationDelayMillis() {
        return (100 - animationSpeed.getValue()) * 5;
    }

    private void updateAnimationSpeedLabel() {
        int delay = animationDelayMillis();
        animationSpeedValue.setText(delay == 0 ? "Без задержки" : "Задержка: " + delay + " мс");
    }
}
