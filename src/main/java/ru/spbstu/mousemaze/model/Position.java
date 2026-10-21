package ru.spbstu.mousemaze.model;

/**
 * Координаты клетки лабиринта.
 *
 * @param row номер строки, начиная с нуля
 * @param column номер столбца, начиная с нуля
 */
public record Position(int row, int column) {
    /**
     * Возвращает координаты после выполнения действия.
     *
     * @param action направление перемещения
     * @return новая позиция
     */
    public Position move(Action action) {
        return new Position(row + action.rowDelta(), column + action.columnDelta());
    }
}
