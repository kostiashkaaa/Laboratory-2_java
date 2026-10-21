package ru.spbstu.mousemaze.environment;

import ru.spbstu.mousemaze.model.Action;
import ru.spbstu.mousemaze.model.CellType;
import ru.spbstu.mousemaze.model.Maze;
import ru.spbstu.mousemaze.model.Position;
import ru.spbstu.mousemaze.model.RewardConfig;
import ru.spbstu.mousemaze.model.StepResult;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Лабиринт как среда MDP: состояние — клетка мыши, действия — четыре направления,
 * награды — сыр (+Z), вода (+x, один раз за эпизод на клетку), ток (−y),
 * обычный шаг и попытка пройти в стену (мышь остаётся на месте).
 */
public final class MazeEnvironment implements Environment {
    private final Maze maze;
    private final RewardConfig rewards;
    private final int maxSteps;
    private final boolean trapTerminates;
    private final Set<Position> consumedWater = new HashSet<>();
    private Position state;
    private int steps;
    private int trapHits;
    private int blockedMoves;

    /**
     * @param maze           лабиринт
     * @param rewards        награды и штрафы
     * @param maxSteps       лимит шагов эпизода
     * @param trapTerminates удар током завершает эпизод
     */
    public MazeEnvironment(Maze maze, RewardConfig rewards, int maxSteps, boolean trapTerminates) {
        this.maze = Objects.requireNonNull(maze, "maze");
        this.rewards = Objects.requireNonNull(rewards, "rewards");
        if (maxSteps <= 0) throw new IllegalArgumentException("Лимит шагов должен быть положительным");
        this.maxSteps = maxSteps;
        this.trapTerminates = trapTerminates;
        reset();
    }

    @Override
    public void reset() {
        state = maze.start();
        consumedWater.clear();
        steps = 0;
        trapHits = 0;
        blockedMoves = 0;
    }

    @Override
    public Position state() {
        return state;
    }

    @Override
    public List<Action> availableActions() {
        return maze.availableActions(state);
    }

    @Override
    public StepResult step(Action action) {
        Objects.requireNonNull(action, "action");
        steps++;
        if (!maze.canMove(state, action)) {
            blockedMoves++;
            // Стена: мышь остаётся на месте и получает штраф; конца задачи нет.
            return new StepResult(state, rewards.wallPenalty(), steps >= maxSteps, true, false);
        }
        state = state.move(action);
        CellType cell = maze.cellAt(state);
        boolean goal = state.equals(maze.goal());
        double reward;
        if (goal) {
            reward = rewards.cheese();
        } else if (cell == CellType.WATER) {
            reward = consumedWater.add(state) ? rewards.water() : rewards.stepPenalty();
        } else if (cell == CellType.TRAP) {
            trapHits++;
            reward = rewards.trap();
        } else {
            reward = rewards.stepPenalty();
        }
        // terminal — настоящий конец задачи (сыр или ток, если он завершает эпизод).
        // Исчерпание лимита шагов лишь обрывает эпизод (done), но не terminal:
        // иначе Беллман ошибочно обнулял бы будущую ценность состояния.
        boolean terminal = goal || (cell == CellType.TRAP && trapTerminates);
        return new StepResult(state, reward, terminal || steps >= maxSteps, false, terminal);
    }

    @Override
    public boolean isAtGoal() {
        return state.equals(maze.goal());
    }

    @Override
    public int collectedWater() {
        return consumedWater.size();
    }

    @Override
    public int trapHits() {
        return trapHits;
    }

    @Override
    public int blockedMoves() {
        return blockedMoves;
    }
}
