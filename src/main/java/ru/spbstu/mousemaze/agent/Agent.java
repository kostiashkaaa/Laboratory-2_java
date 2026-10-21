package ru.spbstu.mousemaze.agent;

import ru.spbstu.mousemaze.model.Action;
import ru.spbstu.mousemaze.model.Position;

import java.util.List;

/** Обучаемый агент (мышь): выбирает действия и учится на подкреплении среды. */
public interface Agent {
    /**
     * Выбирает действие в состоянии.
     *
     * @param state            текущее состояние (x, y)
     * @param availableActions действия, разрешённые лабиринтом из этого состояния
     * @return выбранное действие
     */
    Action chooseAction(Position state, List<Action> availableActions);

    /**
     * Учитывает переход s --a--> s' с наградой r.
     *
     * @param terminal             s' — конец задачи (у него нет будущей награды)
     * @param nextAvailableActions действия, разрешённые из s'
     */
    void update(Position state, Action action, double reward, Position nextState,
                boolean terminal, List<Action> nextAvailableActions);

    /** Уменьшает долю случайных ходов после эпохи, не опуская ниже минимума. */
    void decayExploration(double factor, double minimum);

    /** @return текущая доля случайных ходов (ε) */
    double explorationRate();
}
