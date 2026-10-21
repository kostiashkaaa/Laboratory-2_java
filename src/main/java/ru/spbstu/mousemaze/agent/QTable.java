package ru.spbstu.mousemaze.agent;

import ru.spbstu.mousemaze.model.Action;
import ru.spbstu.mousemaze.model.Position;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Таблица Q-значений: состояние (x, y) × действие. Незнакомая пара равна нулю.
 */
final class QTable {
    private final Map<Position, EnumMap<Action, Double>> values = new HashMap<>();

    double get(Position state, Action action) {
        EnumMap<Action, Double> row = values.get(state);
        return row == null ? 0.0 : row.getOrDefault(action, 0.0);
    }

    void set(Position state, Action action, double value) {
        values.computeIfAbsent(state, k -> new EnumMap<>(Action.class)).put(action, value);
    }

    /** max Q(s, a) по переданным действиям; 0, если действий нет. */
    double maxQ(Position state, Collection<Action> actions) {
        double max = Double.NEGATIVE_INFINITY;
        for (Action action : actions) {
            max = Math.max(max, get(state, action));
        }
        return max == Double.NEGATIVE_INFINITY ? 0.0 : max;
    }

    /** Лучшее действие среди переданных; при равенстве выбирается случайно. */
    Action bestAction(Position state, Collection<Action> actions, Random random) {
        double max = maxQ(state, actions);
        List<Action> best = new ArrayList<>();
        for (Action action : actions) {
            if (Double.compare(get(state, action), max) == 0) best.add(action);
        }
        return best.get(random.nextInt(best.size()));
    }
}
