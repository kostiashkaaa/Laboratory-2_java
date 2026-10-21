package ru.spbstu.mousemaze.model;

/**
 * Допустимое действие мыши в клеточном лабиринте.
 */
public enum Action {
    UP(-1, 0), RIGHT(0, 1), DOWN(1, 0), LEFT(0, -1);

    private final int rowDelta;
    private final int columnDelta;

    Action(int rowDelta, int columnDelta) {
        this.rowDelta = rowDelta;
        this.columnDelta = columnDelta;
    }

    /**
     * Возвращает изменение номера строки.
     *
     * @return изменение строки
     */
    public int rowDelta() {
        return rowDelta;
    }

    /**
     * Возвращает изменение номера столбца.
     *
     * @return изменение столбца
     */
    public int columnDelta() {
        return columnDelta;
    }

    /**
     * Возвращает противоположное направление движения.
     *
     * @return противоположное действие
     */
    public Action opposite() {
        return switch (this) {
            case UP -> DOWN;
            case DOWN -> UP;
            case LEFT -> RIGHT;
            case RIGHT -> LEFT;
        };
    }
}
