package ru.spbstu.mousemaze.model;

/**
 * Награды и штрафы среды.
 *
 * @param cheese      большой сыр в конце лабиринта (+Z)
 * @param water       вода по пути (+x), меньше сыра
 * @param trap        электротравма (−y)
 * @param stepPenalty штраф за обычный шаг
 * @param wallPenalty штраф за попытку пройти в стену (мышь остаётся на месте)
 */
public record RewardConfig(double cheese, double water, double trap, double stepPenalty, double wallPenalty) {
    /** Проверяет соотношения из условия: сыр > вода > 0, ток и штрафы отрицательны. */
    public RewardConfig {
        if (!(cheese > water && water > 0 && trap < 0 && stepPenalty < 0 && wallPenalty < 0)) {
            throw new IllegalArgumentException(
                    "Должно выполняться: сыр > вода > 0, ток < 0, штрафы за шаг и стену < 0");
        }
    }

    /** Значения по умолчанию: сыр +100, вода +10, ток −20, шаг −1, стена −5. */
    public static RewardConfig defaults() {
        return new RewardConfig(100.0, 10.0, -20.0, -1.0, -5.0);
    }
}
