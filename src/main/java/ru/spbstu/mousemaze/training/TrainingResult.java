package ru.spbstu.mousemaze.training;

import java.util.List;

/**
 * Итоги обучения.
 *
 * @param solvedEpisodes   число эпизодов, завершившихся в цели
 * @param lastEpisodeSteps длина последнего эпизода
 * @param rewards          суммарные награды по эпизодам
 * @param steps            число шагов по эпизодам
 * @param solved           признак успешного завершения по эпизодам
 * @param epsilon          значения epsilon по эпизодам
 */
public record TrainingResult(
        int solvedEpisodes,
        int lastEpisodeSteps,
        List<Double> rewards,
        List<Integer> steps,
        List<Boolean> solved,
        List<Double> epsilon
) {
    /**
     * Создаёт неизменяемый результат обучения.
     */
    public TrainingResult {
        rewards = List.copyOf(rewards);
        steps = List.copyOf(steps);
        solved = List.copyOf(solved);
        epsilon = List.copyOf(epsilon);
    }

    /**
     * @return доля успешных эпизодов
     */
    public double successRate() {
        return solved.isEmpty() ? 0 : solvedEpisodes / (double) solved.size();
    }
}
