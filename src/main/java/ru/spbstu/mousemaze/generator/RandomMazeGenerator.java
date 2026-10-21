package ru.spbstu.mousemaze.generator;

import ru.spbstu.mousemaze.model.Action;
import ru.spbstu.mousemaze.model.CellType;
import ru.spbstu.mousemaze.model.Maze;
import ru.spbstu.mousemaze.model.Position;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/**
 * Генератор лабиринта итеративным поиском в глубину.
 */
public final class RandomMazeGenerator implements MazeGenerator {
    private final int rows;
    private final int columns;
    private final double extraPassageProbability;
    private final double waterProbability;
    private final double trapProbability;
    private final Random random;

    /**
     * @param rows                    число строк (не меньше 2)
     * @param columns                 число столбцов (не меньше 2)
     * @param extraPassageProbability доля стен, убираемых после DFS (0 — чистый DFS-лабиринт)
     * @param waterProbability        доля клеток с водой
     * @param trapProbability         доля клеток с током
     * @param random                  источник случайности
     */
    public RandomMazeGenerator(int rows, int columns, double extraPassageProbability,
                               double waterProbability, double trapProbability, Random random) {
        if (rows < 2 || columns < 2 || extraPassageProbability < 0 || extraPassageProbability > 1
                || waterProbability < 0 || waterProbability > 1
                || trapProbability < 0 || trapProbability > 1
                || waterProbability + trapProbability > 1) {
            throw new IllegalArgumentException("Некорректные параметры генератора");
        }
        this.rows = rows;
        this.columns = columns;
        this.extraPassageProbability = extraPassageProbability;
        this.waterProbability = waterProbability;
        this.trapProbability = trapProbability;
        this.random = Objects.requireNonNull(random, "random");
    }

    /**
     * @return новый лабиринт
     */
    @Override
    public Maze generate() {
        Position start = new Position(rows - 1, columns - 1);
        Position goal = new Position(0, 0);
        CellType[][] cells = new CellType[rows][columns];
        for (int row = 0; row < rows; row++) java.util.Arrays.fill(cells[row], CellType.FREE);
        boolean[][][] passages = new boolean[rows][columns][Action.values().length];
        Set<Position> visited = new HashSet<>();
        Deque<Position> stack = new ArrayDeque<>();
        stack.push(goal);
        visited.add(goal);
        while (!stack.isEmpty()) {
            Position current = stack.peek();
            List<Position> neighbours = new ArrayList<>();
            for (Action action : Action.values()) {
                Position next = current.move(action);
                if (next.row() >= 0 && next.row() < rows && next.column() >= 0
                        && next.column() < columns && !visited.contains(next)) {
                    neighbours.add(next);
                }
            }
            if (neighbours.isEmpty()) {
                stack.pop();
                continue;
            }
            Collections.shuffle(neighbours, random);
            Position next = neighbours.get(0);
            Action action = direction(current, next);
            passages[current.row()][current.column()][action.ordinal()] = true;
            passages[next.row()][next.column()][action.opposite().ordinal()] = true;
            visited.add(next);
            stack.push(next);
        }
        // Дополнительные проходы убирают часть стен и создают альтернативные пути.
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                if (column + 1 < columns && random.nextDouble() < extraPassageProbability) {
                    passages[row][column][Action.RIGHT.ordinal()] = true;
                    passages[row][column + 1][Action.LEFT.ordinal()] = true;
                }
                if (row + 1 < rows && random.nextDouble() < extraPassageProbability) {
                    passages[row][column][Action.DOWN.ordinal()] = true;
                    passages[row + 1][column][Action.UP.ordinal()] = true;
                }
            }
        }
        cells[start.row()][start.column()] = CellType.START;
        cells[goal.row()][goal.column()] = CellType.GOAL;
        ItemPlacer.place(cells, start, goal, waterProbability, trapProbability, random);
        return new Maze(cells, start, goal, passages);
    }

    private Action direction(Position from, Position to) {
        for (Action action : Action.values()) {
            if (from.move(action).equals(to)) return action;
        }
        throw new IllegalArgumentException("Клетки не соседние");
    }
}
