package ru.spbstu.mousemaze.training;

import java.util.List;
import ru.spbstu.mousemaze.model.Position;

/** Результат одной эпохи обучения. */
public record EpisodeResult(int episode, int steps, double reward, boolean solved,
                            double epsilon, int waterCollected, int trapHits, int blockedMoves,
                            List<Position> path) {
    /** Создаёт неизменяемый результат эпохи. */
    public EpisodeResult {
        path = List.copyOf(path);
    }
}
