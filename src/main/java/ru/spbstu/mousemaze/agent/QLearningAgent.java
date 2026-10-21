package ru.spbstu.mousemaze.agent;

import ru.spbstu.mousemaze.model.Action;
import ru.spbstu.mousemaze.model.Position;

import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Табличный Q-learning агент.
 *
 * <p>Обновление по уравнению Беллмана:
 * {@code Q(s,a) ← Q(s,a) + α · (r + γ · max Q(s',a') − Q(s,a))}.</p>
 */
public final class QLearningAgent implements Agent {
    private final double alpha;
    private final double gamma;
    private double epsilon;
    private final Random random;
    private final QTable qTable = new QTable();

    /**
     * @param alpha   скорость обучения α в [0, 1]
     * @param gamma   коэффициент дисконтирования γ в [0, 1]
     * @param epsilon начальная вероятность случайного действия ε в [0, 1]
     * @param random  источник случайности
     */
    public QLearningAgent(double alpha, double gamma, double epsilon, Random random) {
        if (alpha < 0 || alpha > 1 || gamma < 0 || gamma > 1 || epsilon < 0 || epsilon > 1) {
            throw new IllegalArgumentException("Параметры Q-learning должны быть от 0 до 1");
        }
        this.alpha = alpha;
        this.gamma = gamma;
        this.epsilon = epsilon;
        this.random = Objects.requireNonNull(random, "random");
    }

    /**
     * ε-жадный выбор: с вероятностью ε — любое из четырёх направлений (в том числе
     * в стену, за что среда даёт штраф), иначе — лучшее по Q среди доступных.
     */
    @Override
    public Action chooseAction(Position state, List<Action> availableActions) {
        if (availableActions.isEmpty() || random.nextDouble() < epsilon) {
            return Action.values()[random.nextInt(Action.values().length)];
        }
        return qTable.bestAction(state, availableActions, random);
    }

    @Override
    public void update(Position state, Action action, double reward, Position nextState,
                       boolean terminal, List<Action> nextAvailableActions) {
        double futureQ = terminal ? 0.0 : gamma * qTable.maxQ(nextState, nextAvailableActions);
        double old = qTable.get(state, action);
        qTable.set(state, action, old + alpha * (reward + futureQ - old));
    }

    @Override
    public void decayExploration(double factor, double minimum) {
        if (factor < 0 || factor > 1 || minimum < 0 || minimum > 1) {
            throw new IllegalArgumentException("Параметры затухания ε должны быть от 0 до 1");
        }
        epsilon = Math.max(minimum, epsilon * factor);
    }

    @Override
    public double explorationRate() {
        return epsilon;
    }

    /**
     * @return Q(s,a), равное нулю для ещё не встречавшейся пары
     */
    public double qValue(Position state, Action action) {
        return qTable.get(state, action);
    }
}
