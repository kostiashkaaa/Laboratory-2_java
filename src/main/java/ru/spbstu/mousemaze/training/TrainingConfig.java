package ru.spbstu.mousemaze.training;

/**
 * Параметры обучения агента.
 *
 * @param episodes число эпизодов
 * @param maxSteps максимальная длина эпизода
 * @param epsilonDecay множитель затухания epsilon
 * @param minimumEpsilon нижняя граница epsilon
 */
public record TrainingConfig(int episodes, int maxSteps, double epsilonDecay, double minimumEpsilon) {
    /** Проверяет параметры обучения. */
    public TrainingConfig {
        if (episodes <= 0 || maxSteps <= 0 || epsilonDecay < 0 || epsilonDecay > 1
                || minimumEpsilon < 0 || minimumEpsilon > 1) {
            throw new IllegalArgumentException("Некорректные параметры обучения");
        }
    }
}
