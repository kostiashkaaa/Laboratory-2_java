package ru.spbstu.mousemaze.generator;

import ru.spbstu.mousemaze.model.Action;
import ru.spbstu.mousemaze.model.CellType;
import ru.spbstu.mousemaze.model.Maze;
import ru.spbstu.mousemaze.model.Position;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Загрузка и сохранение схемы в формате (2W+1)×(2H+1).
 */
public final class MazeIO {
    private MazeIO() {
    }

    /**
     * Загружает ASCII-схему с клетками на нечётных координатах.
     */
    public static Maze load(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        List<String> lines = new ArrayList<>(Files.readAllLines(path));
        // Пустые строки в конце файла (частый след текстовых редакторов) не часть схемы.
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isBlank()) lines.remove(lines.size() - 1);
        if (lines.isEmpty()) throw new IllegalArgumentException("Файл схемы пуст");
        int asciiHeight = lines.size();
        int asciiWidth = lines.get(0).length();
        if (asciiWidth < 5 || asciiHeight < 5 || asciiWidth % 2 == 0 || asciiHeight % 2 == 0) {
            throw new IllegalArgumentException("Размер схемы должен быть (2W+1)×(2H+1)");
        }
        for (String line : lines) {
            if (line.length() != asciiWidth) throw new IllegalArgumentException("Схема должна быть прямоугольной");
        }
        int rows = (asciiHeight - 1) / 2;
        int columns = (asciiWidth - 1) / 2;
        CellType[][] cells = new CellType[rows][columns];
        Position start = null;
        Position goal = null;
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                char symbol = lines.get(row * 2 + 1).charAt(column * 2 + 1);
                CellType type = switch (symbol) {
                    case '.', ' ' -> CellType.FREE;
                    case 'S' -> CellType.START;
                    case 'C' -> CellType.GOAL;
                    case 'W' -> CellType.WATER;
                    case 'T' -> CellType.TRAP;
                    case '#' -> CellType.WALL;
                    default -> throw new IllegalArgumentException("Недопустимый символ клетки: " + symbol);
                };
                cells[row][column] = type;
                Position position = new Position(row, column);
                if (type == CellType.START) {
                    if (start != null) throw new IllegalArgumentException("Нужно ровно одно S");
                    start = position;
                } else if (type == CellType.GOAL) {
                    if (goal != null) throw new IllegalArgumentException("Нужно ровно одно C");
                    goal = position;
                }
            }
        }
        if (start == null || goal == null) throw new IllegalArgumentException("Нужны ровно один S и один C");
        boolean[][][] passages = new boolean[rows][columns][Action.values().length];
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                if (column + 1 < columns && lines.get(row * 2 + 1).charAt(column * 2 + 2) != '#'
                        && cells[row][column] != CellType.WALL && cells[row][column + 1] != CellType.WALL) {
                    passages[row][column][Action.RIGHT.ordinal()] = true;
                    passages[row][column + 1][Action.LEFT.ordinal()] = true;
                }
                if (row + 1 < rows && lines.get(row * 2 + 2).charAt(column * 2 + 1) != '#'
                        && cells[row][column] != CellType.WALL && cells[row + 1][column] != CellType.WALL) {
                    passages[row][column][Action.DOWN.ordinal()] = true;
                    passages[row + 1][column][Action.UP.ordinal()] = true;
                }
            }
        }
        Maze maze = new Maze(cells, start, goal, passages);
        if (!maze.isGoalReachable()) throw new IllegalArgumentException("Сыр недостижим от старта");
        return maze;
    }

    /**
     * Сохраняет Maze в расширенную ASCII-схему.
     */
    public static void save(Maze maze, Path path) throws IOException {
        Objects.requireNonNull(maze, "maze");
        Objects.requireNonNull(path, "path");
        int height = maze.rows() * 2 + 1;
        int width = maze.columns() * 2 + 1;
        char[][] output = new char[height][width];
        for (int row = 0; row < height; row++) java.util.Arrays.fill(output[row], '#');
        for (int row = 0; row < maze.rows(); row++) {
            for (int column = 0; column < maze.columns(); column++) {
                Position position = new Position(row, column);
                output[row * 2 + 1][column * 2 + 1] = switch (maze.cellAt(position)) {
                    case START -> 'S';
                    case GOAL -> 'C';
                    case WATER -> 'W';
                    case TRAP -> 'T';
                    case WALL -> '#';
                    default -> '.';
                };
                if (column + 1 < maze.columns() && maze.canMove(position, Action.RIGHT)) {
                    output[row * 2 + 1][column * 2 + 2] = '.';
                }
                if (row + 1 < maze.rows() && maze.canMove(position, Action.DOWN)) {
                    output[row * 2 + 2][column * 2 + 1] = '.';
                }
            }
        }
        StringBuilder text = new StringBuilder();
        for (char[] row : output) text.append(row).append(System.lineSeparator());
        Files.writeString(path, text.toString());
    }
}
