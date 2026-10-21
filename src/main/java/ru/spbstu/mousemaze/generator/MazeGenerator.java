package ru.spbstu.mousemaze.generator;

import ru.spbstu.mousemaze.model.Maze;

/**
 * Источник генерации лабиринтов.
 */
@FunctionalInterface
public interface MazeGenerator {
    /**
     * Генерирует новый лабиринт.
     *
     * @return лабиринт
     */
    Maze generate();
}
