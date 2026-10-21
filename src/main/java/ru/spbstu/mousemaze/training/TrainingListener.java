package ru.spbstu.mousemaze.training;

import ru.spbstu.mousemaze.model.Position;
import ru.spbstu.mousemaze.model.StepResult;

/**
 * Наблюдатель за ходом обучения.
 */
public interface TrainingListener {
    /**
     * Вызывается после шага.
     */
    default void onStep(int episode, int step, Position state, StepResult result) {
    }

    /**
     * Вызывается после каждой эпохи.
     */
    default void onEpisodeFinished(EpisodeResult result) {
    }

    /**
     * Вызывается после окончания всего обучения с итоговыми данными.
     *
     * @param result итоговый результат
     */
    default void onFinished(TrainingResult result) {
    }
}
