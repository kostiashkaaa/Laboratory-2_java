package ru.spbstu.mousemaze.ui;

import ru.spbstu.mousemaze.agent.QLearningAgent;
import ru.spbstu.mousemaze.environment.MazeEnvironment;
import ru.spbstu.mousemaze.generator.MazeIO;
import ru.spbstu.mousemaze.generator.RandomMazeGenerator;
import ru.spbstu.mousemaze.model.CellType;
import ru.spbstu.mousemaze.model.Maze;
import ru.spbstu.mousemaze.model.Position;
import ru.spbstu.mousemaze.model.RewardConfig;
import ru.spbstu.mousemaze.training.EpisodeResult;
import ru.spbstu.mousemaze.training.GreedyPolicyRollout;
import ru.spbstu.mousemaze.training.Trainer;
import ru.spbstu.mousemaze.training.TrainingConfig;
import ru.spbstu.mousemaze.training.TrainingListener;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Главное окно приложения Swing.
 */
public final class MainFrame extends JFrame {
    private final MazePanel mazePanel = new MazePanel();
    private final RewardChartPanel chart = new RewardChartPanel();
    private final SettingsPanel settings = new SettingsPanel();
    private final JLabel status = new JLabel("Готово к работе");
    private final JButton pauseButton = new JButton("Пауза");
    private Maze maze;
    private QLearningAgent agent;
    private RewardConfig trainedRewards = RewardConfig.defaults();
    private SwingWorker<?, ?> worker;
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private Timer pathAnimationTimer;
    private long trainingSession;
    private int completedEpisodes;
    private int solvedEpisodes;
    private double totalReward;
    private int totalWaterCollected;
    private int totalTrapHits;
    private int totalBlockedMoves;
    private volatile int animationDelayMillis;
    /** Счётчик фактических посещений клеток мышью за текущую серию обучения. */
    private int[][] visitCounts;
    /** Пути эпох (клетка = row * columns + column) для проигрывания по клику на графике. */
    private final List<int[]> episodePaths = new ArrayList<>();
    private long storedPathCells;
    private static final long MAX_STORED_PATH_CELLS = 10_000_000L;

    /**
     * Создаёт и настраивает главное окно.
     */
    public MainFrame() {
        super("Мышь в лабиринте — Обучение с подкреплением (Q-Learning)");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1200, 780);
        setMinimumSize(new Dimension(1020, 680));
        setLocationByPlatform(true);

        JButton generate = new JButton("Сгенерировать");
        JButton edit = new JButton("Редактор схемы");
        JButton train = new JButton("Старт обучения");
        JButton stop = new JButton("Стоп");
        JButton shot = new JButton("Снимок PNG");
        JButton reset = new JButton("Сброс Q");
        JButton best = new JButton("Лучший путь");

        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        right.add(settings);
        right.add(Box.createVerticalStrut(3));
        right.add(createControlPanel(generate, edit, train, stop, shot, reset, best));

        JScrollPane rightScroll = new JScrollPane(right,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        rightScroll.setBorder(null);
        rightScroll.getVerticalScrollBar().setUnitIncrement(14);

        JSplitPane center = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                mazePanel, rightScroll);
        center.setResizeWeight(0.66);
        center.setDividerLocation(740);
        add(center, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        status.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        bottom.add(status, BorderLayout.NORTH);
        bottom.add(chart, BorderLayout.CENTER);
        chart.setPreferredSize(new Dimension(100, 130));
        add(bottom, BorderLayout.SOUTH);

        generate.addActionListener(e -> generate());
        edit.addActionListener(e -> new MazeEditorController(this, this::replaceMaze).open(maze));
        train.addActionListener(e -> train());
        pauseButton.addActionListener(e -> togglePause());
        stop.addActionListener(e -> {
            if (worker != null && !worker.isDone()) {
                stopTraining();
            } else {
                status.setText("Обучение не запущено");
            }
        });
        shot.addActionListener(e -> saveShot());
        reset.addActionListener(e -> resetQTable());
        best.addActionListener(e -> showBestPath());
        chart.setEpisodeClickListener(this::replayEpisode);
        settings.randomSeed.addActionListener(e -> {
            settings.seed.setText(Long.toString(new Random().nextInt(100000)));
            generate();
        });
        settings.heatmap.addActionListener(e -> mazePanel.setHeatmap(settings.heatmap.isSelected()));
        animationDelayMillis = settings.animationDelayMillis();
        settings.animationSpeed.addChangeListener(e ->
                animationDelayMillis = settings.animationDelayMillis());
        setJMenuBar(createMenu());
        generate();
    }

    private JPanel createControlPanel(JButton generate, JButton edit, JButton train,
                                      JButton stop, JButton shot, JButton reset, JButton best) {
        JPanel panel = new JPanel(new GridBagLayout());
        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Управление");
        border.setTitleFont(border.getTitleFont().deriveFont(Font.BOLD, 11.5f));
        panel.setBorder(border);

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 2, 2, 2);
        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridy = 0;
        c.gridx = 0;
        c.weightx = 0.45;
        train.setFont(train.getFont().deriveFont(Font.BOLD, 11.5f));
        panel.add(train, c);

        c.gridx = 1;
        c.weightx = 0.28;
        panel.add(pauseButton, c);

        c.gridx = 2;
        c.weightx = 0.27;
        panel.add(stop, c);

        c.gridy = 1;
        c.gridx = 0;
        c.weightx = 0.45;
        panel.add(generate, c);

        c.gridx = 1;
        c.gridwidth = 2;
        c.weightx = 0.55;
        panel.add(edit, c);
        c.gridwidth = 1;

        c.gridy = 2;
        c.gridx = 0;
        c.weightx = 0.45;
        panel.add(best, c);

        c.gridx = 1;
        c.weightx = 0.28;
        panel.add(shot, c);

        c.gridx = 2;
        c.weightx = 0.27;
        panel.add(reset, c);

        return panel;
    }

    private JMenuBar createMenu() {
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("Файл");
        JMenuItem open = new JMenuItem("Открыть схему");
        JMenuItem edit = new JMenuItem("Редактор схемы");
        JMenuItem save = new JMenuItem("Сохранить скриншот");
        JMenuItem saveMaze = new JMenuItem("Сохранить схему");
        JMenuItem reset = new JMenuItem("Сбросить Q-таблицу");
        JMenuItem best = new JMenuItem("Показать лучший путь");
        JMenuItem exit = new JMenuItem("Выход");
        open.addActionListener(e -> openMaze());
        edit.addActionListener(e -> new MazeEditorController(this, this::replaceMaze).open(maze));
        save.addActionListener(e -> saveShot());
        saveMaze.addActionListener(e -> saveMaze());
        reset.addActionListener(e -> resetQTable());
        best.addActionListener(e -> showBestPath());
        exit.addActionListener(e -> dispose());
        file.add(open);
        file.add(edit);
        file.add(saveMaze);
        file.add(save);
        file.add(reset);
        file.add(best);
        file.addSeparator();
        file.add(exit);
        bar.add(file);
        JMenu help = new JMenu("Справка");
        JMenuItem about = new JMenuItem("О программе");
        about.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "Мышь в лабиринте — Обучение с подкреплением (Q-Learning)\n"
                        + "Лабораторная работа №2, СПбПУ\n"
                        + "Преподаватель: Хасанов Д.С.\nJava 17, Swing"));
        help.add(about);
        bar.add(help);
        return bar;
    }

    private void generate() {
        try {
            stopTrainingIfRunning();
            stopPathAnimation();
            int rows = (Integer) settings.rows.getValue();
            int columns = (Integer) settings.columns.getValue();
            long seed = Long.parseLong(settings.seed.getText().trim());
            double water = (Integer) settings.waterPercent.getValue() / 100.0;
            double traps = (Integer) settings.trapPercent.getValue() / 100.0;
            double extra = (Integer) settings.extraPassagesPercent.getValue() / 100.0;
            maze = new RandomMazeGenerator(rows, columns, extra, water, traps,
                    new Random(seed)).generate();
            settings.maxSteps.setValue(defaultMaxSteps(rows, columns));
            resetView();
            status.setText("Сгенерирован DFS-лабиринт " + rows + "×" + columns
                    + ", seed=" + seed);
        } catch (RuntimeException exception) {
            showError("Ошибка параметров", exception.getMessage());
        }
    }

    /** Лимит шагов эпизода по умолчанию: 4 шага на клетку. */
    private static int defaultMaxSteps(int rows, int columns) {
        return Math.min(100000, rows * columns * 4);
    }

    private void train() {
        if (maze == null) return;
        if (worker != null && !worker.isDone()) {
            status.setText("Обучение уже идёт — нажмите «Стоп», чтобы начать заново");
            return;
        }
        stopPathAnimation();

        final long mazeSeed;
        final int episodes;
        final int maxSteps;
        final int animationEvery;
        final double alpha;
        final double gamma;
        final double epsilonStart;
        final double epsilonMin;
        final double epsilonDecay;
        final boolean trapStops;
        final RewardConfig rewards;

        try {
            mazeSeed = Long.parseLong(settings.seed.getText().trim());
            episodes = (Integer) settings.episodes.getValue();
            maxSteps = (Integer) settings.maxSteps.getValue();
            animationEvery = (Integer) settings.animationEvery.getValue();
            alpha = ((Number) settings.alpha.getValue()).doubleValue();
            gamma = ((Number) settings.gamma.getValue()).doubleValue();
            epsilonStart = ((Number) settings.epsilonStart.getValue()).doubleValue();
            epsilonMin = ((Number) settings.epsilonMin.getValue()).doubleValue();
            epsilonDecay = ((Number) settings.epsilonDecay.getValue()).doubleValue();
            trapStops = settings.trapTerminates.isSelected();
            rewards = new RewardConfig(
                    ((Number) settings.cheeseReward.getValue()).doubleValue(),
                    ((Number) settings.waterReward.getValue()).doubleValue(),
                    ((Number) settings.trapPenalty.getValue()).doubleValue(),
                    ((Number) settings.stepPenalty.getValue()).doubleValue(),
                    ((Number) settings.wallPenalty.getValue()).doubleValue());
        } catch (RuntimeException exception) {
            showError("Ошибка параметров обучения", exception.getMessage());
            return;
        }

        paused.set(false);
        stopped.set(false);
        pauseButton.setText("Пауза");
        completedEpisodes = 0;
        solvedEpisodes = 0;
        totalReward = 0;
        totalWaterCollected = 0;
        totalTrapHits = 0;
        totalBlockedMoves = 0;

        final long session = ++trainingSession;
        final Maze trainingMaze = maze;
        final long trainingSeed = mazeSeed ^ (session * 0x9E3779B97F4A7C15L);

        final QLearningAgent trainingAgent = new QLearningAgent(alpha, gamma, epsilonStart,
                new Random(trainingSeed));
        agent = trainingAgent;
        trainedRewards = rewards;
        visitCounts = new int[trainingMaze.rows()][trainingMaze.columns()];
        chart.clearValues();
        clearEpisodePaths();
        mazePanel.setVisitCounts(visitCounts);

        worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                MazeEnvironment environment = new MazeEnvironment(
                        trainingMaze, rewards, maxSteps, trapStops);
                TrainingListener listener = new TrainingListener() {
                    // След и выпитая вода текущей показываемой эпохи (поток обучения).
                    private final List<Position> trail = new ArrayList<>();
                    private final Set<Position> drunkWater = new HashSet<>();
                    private Position lastTrap;

                    @Override
                    public void onStep(int episode, int step, Position state,
                                       ru.spbstu.mousemaze.model.StepResult result) {
                        if (!isAnimated(episode)) return;
                        if (step == 1) {
                            trail.clear();
                            drunkWater.clear();
                            lastTrap = null;
                            trail.add(trainingMaze.start());
                        }
                        trail.add(state);
                        CellType type = trainingMaze.cellAt(state);
                        if (type == CellType.WATER) drunkWater.add(state);
                        if (type == CellType.TRAP && !result.blocked()) lastTrap = state;
                        List<Position> trailCopy = List.copyOf(trail);
                        Set<Position> waterCopy = Set.copyOf(drunkWater);
                        Position trapCopy = lastTrap;
                        SwingUtilities.invokeLater(() -> {
                            if (trainingSession != session) return;
                            mazePanel.setMouse(state, trailCopy);
                            mazePanel.setHazards(waterCopy, trapCopy);
                        });
                        int delay = animationDelayMillis;
                        // Момент, когда мышь дошла до сыра, задерживаем, чтобы его было видно.
                        if (state.equals(trainingMaze.goal())) delay = Math.max(delay, 600);
                        if (delay > 0) sleep(delay);
                    }

                    /**
                     * Пошагово показываются эпохи 1, каждая N-я, последняя и эпохи
                     * снимков для презентации (1, 50, 500); остальные считаются на полной
                     * скорости и обновляют лишь график, статистику и тепловую карту.
                     */
                    private boolean isAnimated(int episode) {
                        return episode == 1 || episode % animationEvery == 0 || episode == episodes
                                || isPresentationEpoch(episode);
                    }

                    @Override
                    public void onEpisodeFinished(EpisodeResult result) {
                        SwingUtilities.invokeLater(() -> {
                            if (trainingSession != session) return;
                            completedEpisodes++;
                            if (result.solved()) solvedEpisodes++;
                            totalReward += result.reward();
                            totalWaterCollected += result.waterCollected();
                            totalTrapHits += result.trapHits();
                            totalBlockedMoves += result.blockedMoves();
                            chart.addValue(result.reward(), result.solved());
                            storeEpisodePath(result.path(), trainingMaze.columns());
                            for (Position position : result.path()) {
                                visitCounts[position.row()][position.column()]++;
                            }
                            mazePanel.repaint();
                            // Мышь двигаем только по анимируемым эпохам: иначе после каждой
                            // быстро посчитанной эпохи она прыгала в конец пути (на сыр) и
                            // казалось, что мышь просто стоит на сыре.
                            if (isAnimated(result.episode())) {
                                mazePanel.setMouse(result.path().get(result.path().size() - 1),
                                        result.path());
                                Set<Position> water = new HashSet<>();
                                Position lastTrap = null;
                                for (Position position : result.path()) {
                                    CellType type = trainingMaze.cellAt(position);
                                    if (type == CellType.WATER) water.add(position);
                                    if (type == CellType.TRAP) lastTrap = position;
                                }
                                mazePanel.setHazards(water, lastTrap);
                            }
                            double success = 100.0 * solvedEpisodes / completedEpisodes;
                            status.setText(String.format(
                                    "Эпоха %d/%d; шагов: %d; награда: %.1f; средняя: %.1f; успех: %.1f%%; сыр (+Z): %s; вода (+x): %d; ток (-y): %d; стены: %d; ε: %.3f",
                                    result.episode(), episodes, result.steps(), result.reward(),
                                    totalReward / completedEpisodes, success,
                                    result.solved() ? "да" : "нет", result.waterCollected(),
                                    result.trapHits(), result.blockedMoves(), result.epsilon())
                                    + (isAnimated(result.episode()) ? "" : "  [эпоха посчитана без показа]"));
                            savePresentationSnapshot(result.episode()); // после статуса, чтобы номер эпохи совпадал
                        });
                    }

                    @Override
                    public void onFinished(ru.spbstu.mousemaze.training.TrainingResult trainingResult) {
                        SwingUtilities.invokeLater(() -> {
                            if (trainingSession == session) {
                                pauseButton.setText("Пауза");
                                status.setText(stopped.get()
                                        ? "Обучение остановлено"
                                        : String.format("Обучение завершено; успех: %d/%d; "
                                                + "средняя награда: %.1f; вода (+x): %d; ток (-y): %d; стены: %d; seed обучения: %d",
                                        solvedEpisodes, completedEpisodes,
                                        completedEpisodes == 0 ? 0 : totalReward / completedEpisodes,
                                        totalWaterCollected, totalTrapHits, totalBlockedMoves,
                                        trainingSeed));
                            }
                        });
                    }
                };
                new Trainer().train(environment, trainingAgent,
                        new TrainingConfig(episodes, maxSteps, epsilonDecay, epsilonMin),
                        listener, paused::get, stopped::get);
                return null;
            }
        };
        worker.execute();
    }

    private void replaceMaze(Maze replacement) {
        stopTrainingIfRunning();
        stopPathAnimation();
        maze = replacement;
        // Поля размера и лимит шагов должны описывать текущий лабиринт, а не прежний.
        settings.rows.setValue(replacement.rows());
        settings.columns.setValue(replacement.columns());
        settings.maxSteps.setValue(defaultMaxSteps(replacement.rows(), replacement.columns()));
        resetView();
        status.setText("Схема изменена в редакторе");
    }

    private void resetView() {
        stopPathAnimation();
        agent = null;
        visitCounts = maze == null ? null : new int[maze.rows()][maze.columns()];
        mazePanel.setVisitCounts(visitCounts);
        mazePanel.setMaze(maze);
        mazePanel.setHazards(Set.of(), null);
        chart.clearValues();
        clearEpisodePaths();
        completedEpisodes = 0;
        solvedEpisodes = 0;
        totalReward = 0;
        totalWaterCollected = 0;
        totalTrapHits = 0;
        totalBlockedMoves = 0;
    }

    private void storeEpisodePath(List<Position> path, int columns) {
        if (storedPathCells + path.size() > MAX_STORED_PATH_CELLS) {
            episodePaths.add(null);
            return;
        }
        int[] encoded = new int[path.size()];
        for (int i = 0; i < encoded.length; i++) {
            encoded[i] = path.get(i).row() * columns + path.get(i).column();
        }
        storedPathCells += encoded.length;
        episodePaths.add(encoded);
    }

    private void clearEpisodePaths() {
        episodePaths.clear();
        storedPathCells = 0;
    }

    private void togglePause() {
        if (worker == null || worker.isDone()) return;
        boolean nowPaused = !paused.get();
        if (!nowPaused) stopPathAnimation(); // проигрывание записи не должно мешать обучению
        paused.set(nowPaused);
        pauseButton.setText(nowPaused ? "Продолжить" : "Пауза");
        status.setText(nowPaused ? "Обучение на паузе" : "Обучение продолжается");
    }

    private void stopTrainingIfRunning() {
        if (worker != null && !worker.isDone()) stopTraining();
    }

    private void stopTraining() {
        stopped.set(true);
        paused.set(false);
        pauseButton.setText("Пауза");
        trainingSession++;
        if (worker != null) worker.cancel(true);
        // onFinished старой сессии игнорируется (trainingSession уже сменился),
        // поэтому итоговый статус выставляем сразу.
        status.setText("Обучение остановлено");
    }

    private void stopPathAnimation() {
        if (pathAnimationTimer != null && pathAnimationTimer.isRunning()) {
            pathAnimationTimer.stop();
        }
        pathAnimationTimer = null;
    }

    private void openMaze() {
        stopTrainingIfRunning();
        stopPathAnimation();
        JFileChooser chooser = new JFileChooser(new File("examples"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                replaceMaze(MazeIO.load(chooser.getSelectedFile().toPath()));
                status.setText("Загружена схема: " + chooser.getSelectedFile().getName());
            } catch (Exception exception) {
                showError("Ошибка схемы", exception.getMessage());
            }
        }
    }

    private void saveShot() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".png")) {
                file = new File(file.getParentFile(), file.getName() + ".png");
            }
            try {
                ScreenshotUtil.save(this, file);
                status.setText("Снимок сохранён: " + file.getName());
            } catch (Exception exception) {
                showError("Ошибка сохранения", exception.getMessage());
            }
        }
    }

    private void saveMaze() {
        if (maze == null) return;
        JFileChooser chooser = new JFileChooser(new File("examples"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                MazeIO.save(maze, chooser.getSelectedFile().toPath());
                status.setText("Схема сохранена: " + chooser.getSelectedFile().getName());
            } catch (Exception exception) {
                showError("Ошибка сохранения схемы", exception.getMessage());
            }
        }
    }

    private void resetQTable() {
        stopTrainingIfRunning();
        stopPathAnimation();
        // Агента не очищаем, а отбрасываем: остановленный поток обучения может ещё
        // дописывать в его Q-таблицу. Новое обучение всегда создаёт нового агента.
        agent = null;
        visitCounts = maze == null ? null : new int[maze.rows()][maze.columns()];
        mazePanel.setVisitCounts(visitCounts);
        mazePanel.setMouse(maze == null ? null : maze.start(), List.of());
        mazePanel.setHazards(Set.of(), null);
        chart.clearValues();
        clearEpisodePaths();
        status.setText("Q-таблица, статистика посещений и epsilon сброшены");
    }

    private void showBestPath() {
        if (maze == null || agent == null) {
            status.setText("Сначала выполните обучение");
            return;
        }
        if (worker != null && !worker.isDone()) {
            // Раньше кнопка молча обрывала обучение и строила путь по недообученной таблице.
            status.setText("Обучение ещё идёт — дождитесь окончания или нажмите «Стоп», затем «Лучший путь»");
            return;
        }
        stopPathAnimation();
        mazePanel.setHazards(Set.of(), null); // отметки последней эпохи обучения к пути не относятся

        List<Position> greedyPath = GreedyPolicyRollout.run(maze, agent, trainedRewards);
        boolean reachedGoal = !greedyPath.isEmpty()
                && greedyPath.get(greedyPath.size() - 1).equals(maze.goal());
        pathAnimationTimer = createPathAnimationTimer(greedyPath, reachedGoal
                ? "Лучший путь (по Q-политике) показан (" + (greedyPath.size() - 1) + " шагов)"
                : "Агент пока не нашёл путь к сыру по выученной политике — "
                + "увеличьте число эпох обучения или проверьте параметры Q-learning");
        pathAnimationTimer.start();
    }

    private Timer createPathAnimationTimer(List<Position> path, String doneMessage) {
        final List<Position> displayedPath = List.copyOf(path);
        final int[] index = {0};
        final Set<Position> drunk = new HashSet<>();
        final Position[] lastTrap = {null};
        Timer timer = new Timer(Math.max(20, animationDelayMillis), null);
        timer.addActionListener(event -> {
            if (index[0] >= displayedPath.size()) {
                timer.stop();
                status.setText(doneMessage);
                return;
            }
            Position position = displayedPath.get(index[0]++);
            CellType type = maze.cellAt(position);
            if (type == CellType.WATER) drunk.add(position);
            if (type == CellType.TRAP) lastTrap[0] = position;
            mazePanel.setMouse(position, displayedPath.subList(0, index[0]));
            mazePanel.setHazards(drunk, lastTrap[0]);
        });
        return timer;
    }

    /**
     * Проигрывает записанный путь мыши в выбранной на графике эпохе — так видно,
     * где именно мышь коснулась сыра в эпохах, которые не показывались при обучении.
     */
    private void replayEpisode(int episode) {
        if (worker != null && !worker.isDone() && !paused.get()) {
            status.setText("Чтобы проиграть эпоху " + episode + ", поставьте обучение на паузу");
            return;
        }
        int[] encoded = episode - 1 < episodePaths.size() ? episodePaths.get(episode - 1) : null;
        if (encoded == null || maze == null) {
            status.setText("Путь эпохи " + episode + " не сохранён (слишком много данных обучения)");
            return;
        }
        List<Position> path = new ArrayList<>(encoded.length);
        for (int cell : encoded) path.add(new Position(cell / maze.columns(), cell % maze.columns()));
        boolean solved = path.get(path.size() - 1).equals(maze.goal());
        stopPathAnimation();
        mazePanel.setHazards(Set.of(), null);
        status.setText("Проигрывается эпоха " + episode + "...");
        pathAnimationTimer = createPathAnimationTimer(path, "Эпоха " + episode + ": "
                + (solved ? "мышь дошла до сыра за " + (path.size() - 1) + " шагов"
                          : "сыр не найден, " + (path.size() - 1) + " шагов"));
        pathAnimationTimer.start();
    }

    private static boolean isPresentationEpoch(int episode) {
        return episode == 1 || episode == 50 || episode == 500;
    }

    private void savePresentationSnapshot(int episode) {
        if (!isPresentationEpoch(episode)) return;
        try {
            Path directory = Path.of("presentation-screenshots");
            Files.createDirectories(directory);
            ScreenshotUtil.save(this, directory.resolve("epoch-" + episode + ".png").toFile());
        } catch (Exception exception) {
            status.setText("Не удалось сохранить снимок презентации: " + exception.getMessage());
        }
    }

    private void showError(String title, String message) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }

    private void sleep(int milliseconds) {
        if (milliseconds <= 0) return;
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
