package ru.spbstu.mousemaze.generator;

import ru.spbstu.mousemaze.model.CellType;
import ru.spbstu.mousemaze.model.Position;

import java.util.Random;

/** Расставляет воду и ловушки по свободным клеткам. */
public final class ItemPlacer {
    private ItemPlacer() {
    }

    /** Размещает предметы, не занимая старт и сыр. */
    public static void place(CellType[][] cells, Position start, Position goal,
                              double waterProbability, double trapProbability, Random random) {
        if (waterProbability < 0 || trapProbability < 0
                || waterProbability + trapProbability > 1) {
            throw new IllegalArgumentException("Некорректные доли воды и ловушек");
        }
        for (int row = 0; row < cells.length; row++) {
            for (int column = 0; column < cells[row].length; column++) {
                Position position = new Position(row, column);
                if (position.equals(start) || position.equals(goal)
                        || cells[row][column] == CellType.WALL) {
                    continue;
                }
                double value = random.nextDouble();
                if (value < waterProbability) {
                    cells[row][column] = CellType.WATER;
                } else if (value < waterProbability + trapProbability) {
                    cells[row][column] = CellType.TRAP;
                }
            }
        }
    }
}
