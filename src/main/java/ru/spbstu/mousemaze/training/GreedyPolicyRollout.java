package ru.spbstu.mousemaze.training;

import ru.spbstu.mousemaze.agent.QLearningAgent;
import ru.spbstu.mousemaze.model.Action;
import ru.spbstu.mousemaze.model.CellType;
import ru.spbstu.mousemaze.model.Maze;
import ru.spbstu.mousemaze.model.Position;
import ru.spbstu.mousemaze.model.RewardConfig;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Прогон выученной жадной политики от старта до сыра без обучения и без случайности.
 *
 * <p>Q-таблица хранит ценность только для координат (x, y), а вода по условию среды
 * выпивается один раз за эпизод. Поэтому Q-значение шага на воду навсегда включает
 * бонус {@code +x}, хотя повторно его получить нельзя. Если брать {@code argmax Q}
 * «в лоб», мышь бесконечно ходит туда-обратно к одной и той же воде. Здесь при выборе
 * действия бонус уже выпитой воды вычитается (мышь знает, что пила её), так что
 * итоговый путь — это то, что агент реально прошёл бы в среде.</p>
 *
 * <p>Возвраты в посещённые клетки разрешены (например, выпить воду в тупике и вернуться).
 * Запрещён только повтор полного состояния: то же положение при том же наборе выпитой воды.
 * Если все ходы ведут в уже виденные состояния, путь честно обрывается.</p>
 */
public final class GreedyPolicyRollout {
    private GreedyPolicyRollout() {
    }

    private record Snapshot(Position position, Set<Position> consumedWater) {
    }

    /**
     * Строит путь по жадной политике агента.
     *
     * @param maze    лабиринт
     * @param agent   обученный агент (Q-таблица не изменяется)
     * @param rewards награды, с которыми шло обучение
     * @return клетки пути от старта; если сыр не достигнут, путь обрывается там,
     * где политика зашла в тупик или зациклилась
     */
    public static List<Position> run(Maze maze, QLearningAgent agent, RewardConfig rewards) {
        Set<Position> consumedWater = new HashSet<>();
        Set<Snapshot> seen = new HashSet<>();
        List<Position> path = new ArrayList<>();
        Position current = maze.start();
        path.add(current);
        seen.add(new Snapshot(current, Set.copyOf(consumedWater)));

        int limit = maze.rows() * maze.columns() * 4;
        while (!current.equals(maze.goal()) && path.size() <= limit) {
            Action action = bestAction(maze, agent, rewards, current, consumedWater, seen);
            if (action == null) break;
            current = current.move(action);
            if (maze.cellAt(current) == CellType.WATER) consumedWater.add(current);
            path.add(current);
            seen.add(new Snapshot(current, Set.copyOf(consumedWater)));
        }
        return path;
    }

    /**
     * Выбирает лучшее действие среди тех, что не приводят к уже встречавшемуся состоянию
     * (положение + набор выпитой воды). Так политика не может бесконечно ходить по кругу,
     * но может вернуться из тупика после того, как выпила в нём воду.
     */
    private static Action bestAction(Maze maze, QLearningAgent agent, RewardConfig rewards,
                                     Position current, Set<Position> consumedWater, Set<Snapshot> seen) {
        Action best = null;
        double bestValue = Double.NEGATIVE_INFINITY;
        for (Action action : maze.availableActions(current)) {
            Position next = current.move(action);
            Set<Position> afterWater = consumedWater;
            if (maze.cellAt(next) == CellType.WATER && !consumedWater.contains(next)) {
                afterWater = new HashSet<>(consumedWater);
                afterWater.add(next);
            }
            if (seen.contains(new Snapshot(next, Set.copyOf(afterWater)))) continue;
            double value = agent.qValue(current, action);
            if (consumedWater.contains(next)) {
                // Повторный заход на воду даёт обычный штраф за шаг, а не +x.
                value -= rewards.water() - rewards.stepPenalty();
            }
            if (value > bestValue) {
                bestValue = value;
                best = action;
            }
        }
        return best;
    }
}
