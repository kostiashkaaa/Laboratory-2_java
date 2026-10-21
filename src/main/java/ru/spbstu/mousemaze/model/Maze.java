package ru.spbstu.mousemaze.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Неизменяемое прямоугольное поле лабиринта с началом и целью.
 */
public final class Maze {
    private final int rows;
    private final int columns;
    private final CellType[][] cells;
    private final Position start;
    private final Position goal;
    private final boolean[][][] passages;

    /**
     * Создаёт лабиринт.
     *
     * @param cells типы клеток, не пустой прямоугольный массив
     * @param start начальная клетка
     * @param goal  целевая клетка
     */
    public Maze(CellType[][] cells, Position start, Position goal) {
        this(cells, start, goal, null);
    }

    /**
     * Создаёт лабиринт с явно заданными проходами между клетками.
     *
     * @param cells    типы клеток
     * @param start    начало
     * @param goal     цель
     * @param passages passages[row][column][action ordinal]
     */
    public Maze(CellType[][] cells, Position start, Position goal, boolean[][][] passages) {
        Objects.requireNonNull(cells, "cells");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(goal, "goal");
        if (cells.length == 0 || cells[0].length == 0) {
            throw new IllegalArgumentException("Лабиринт не может быть пустым");
        }
        int width = cells[0].length;
        this.cells = new CellType[cells.length][width];
        for (int row = 0; row < cells.length; row++) {
            if (cells[row] == null || cells[row].length != width) {
                throw new IllegalArgumentException("Лабиринт должен быть прямоугольным");
            }
            this.cells[row] = cells[row].clone();
        }
        this.rows = cells.length;
        this.columns = width;
        this.start = start;
        this.goal = goal;
        this.passages = passages == null ? createDefaultPassages() : copyPassages(passages);
        if (!isInside(start) || !isInside(goal) || isWall(start) || isWall(goal)) {
            throw new IllegalArgumentException("Начало и цель должны быть свободными клетками");
        }
    }

    private boolean[][][] createDefaultPassages() {
        boolean[][][] result = new boolean[rows][columns][Action.values().length];
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                Position position = new Position(row, column);
                for (Action action : Action.values()) {
                    Position next = position.move(action);
                    result[row][column][action.ordinal()] = isInside(next) && !isWall(position) && !isWall(next);
                }
            }
        }
        return result;
    }

    private boolean[][][] copyPassages(boolean[][][] source) {
        if (source.length != rows) throw new IllegalArgumentException("Некорректное число строк проходов");
        boolean[][][] result = new boolean[rows][columns][Action.values().length];
        for (int row = 0; row < rows; row++) {
            if (source[row].length != columns)
                throw new IllegalArgumentException("Некорректное число столбцов проходов");
            for (int column = 0; column < columns; column++) {
                if (source[row][column].length != Action.values().length)
                    throw new IllegalArgumentException("Некорректное число направлений");
                result[row][column] = source[row][column].clone();
            }
        }
        return result;
    }

    /**
     * @return число строк
     */
    public int rows() {
        return rows;
    }

    /**
     * @return число столбцов
     */
    public int columns() {
        return columns;
    }

    /**
     * @return начальная позиция
     */
    public Position start() {
        return start;
    }

    /**
     * @return целевая позиция
     */
    public Position goal() {
        return goal;
    }

    /**
     * Проверяет принадлежность позиции полю.
     *
     * @param position позиция
     * @return true, если позиция находится в поле
     */
    public boolean isInside(Position position) {
        return position != null
                && position.row() >= 0 && position.row() < rows
                && position.column() >= 0 && position.column() < columns;
    }

    /**
     * Проверяет, является ли клетка стеной.
     *
     * @param position позиция
     * @return true для стены или позиции вне поля
     */
    public boolean isWall(Position position) {
        return !isInside(position) || cells[position.row()][position.column()] == CellType.WALL;
    }

    /**
     * Проверяет, разрешён ли переход между двумя соседними клетками.
     */
    public boolean canMove(Position position, Action action) {
        if (action == null || !isInside(position) || isWall(position)) {
            return false;
        }
        Position next = position.move(action);
        return isInside(next) && !isWall(next) && passages[position.row()][position.column()][action.ordinal()];
    }

    /**
     * Возвращает все разрешённые переходы из клетки.
     */
    public List<Action> availableActions(Position position) {
        List<Action> actions = new ArrayList<>(Action.values().length);
        for (Action action : Action.values()) {
            if (canMove(position, action)) actions.add(action);
        }
        return actions;
    }

    /**
     * @return сыр достижим от старта по разрешённым переходам
     */
    public boolean isGoalReachable() {
        Set<Position> seen = new HashSet<>(List.of(start));
        ArrayDeque<Position> queue = new ArrayDeque<>(seen);
        while (!queue.isEmpty()) {
            Position position = queue.remove();
            if (position.equals(goal)) return true;
            for (Action action : availableActions(position)) {
                Position next = position.move(action);
                if (seen.add(next)) queue.add(next);
            }
        }
        return false;
    }

    /**
     * Возвращает тип клетки.
     *
     * @param position позиция
     * @return тип клетки
     */
    public CellType cellAt(Position position) {
        if (!isInside(position)) {
            throw new IndexOutOfBoundsException("Позиция вне лабиринта: " + position);
        }
        return cells[position.row()][position.column()];
    }

    /**
     * @return копию карты клеток
     */
    public CellType[][] cells() {
        CellType[][] copy = new CellType[rows][columns];
        for (int row = 0; row < rows; row++) copy[row] = cells[row].clone();
        return copy;
    }

    /**
     * @return копию матрицы проходов
     */
    public boolean[][][] passages() {
        return copyPassages(passages);
    }
}
