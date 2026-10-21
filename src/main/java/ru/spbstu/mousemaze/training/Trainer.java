package ru.spbstu.mousemaze.training;

import ru.spbstu.mousemaze.agent.Agent;
import ru.spbstu.mousemaze.environment.Environment;
import ru.spbstu.mousemaze.model.Action;
import ru.spbstu.mousemaze.model.Position;
import ru.spbstu.mousemaze.model.StepResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Цикл обучения с подкреплением: агент действует, среда отвечает наградой,
 * агент обновляет свои оценки. Одна эпоха — один эпизод от старта.
 */
public final class Trainer {
    /**
     * Обучает агента без наблюдателя, паузы и остановки.
     */
    public TrainingResult train(Environment environment, Agent agent, TrainingConfig config) {
        return train(environment, agent, config, new TrainingListener() {
        }, () -> false, () -> false);
    }

    /**
     * Обучает агента и сообщает о ходе обучения наблюдателю.
     *
     * @param paused  пока true, обучение ждёт
     * @param stopped если true, обучение прекращается; незавершённая эпоха не учитывается
     */
    public TrainingResult train(Environment environment, Agent agent, TrainingConfig config,
                                TrainingListener listener, BooleanSupplier paused, BooleanSupplier stopped) {
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(agent, "agent");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(listener, "listener");
        Objects.requireNonNull(paused, "paused");
        Objects.requireNonNull(stopped, "stopped");

        int solved = 0;
        int lastSteps = 0;
        List<Double> rewards = new ArrayList<>();
        List<Integer> stepsHistory = new ArrayList<>();
        List<Boolean> solvedHistory = new ArrayList<>();
        List<Double> epsilonHistory = new ArrayList<>();

        episodes:
        for (int episode = 1; episode <= config.episodes(); episode++) {
            environment.reset();
            boolean done = false;
            int steps = 0;
            double rewardSum = 0.0;
            List<Position> path = new ArrayList<>();
            path.add(environment.state());

            while (!done && steps < config.maxSteps()) {
                while (paused.getAsBoolean() && !stopped.getAsBoolean()) {
                    sleep(50);
                }
                if (stopped.getAsBoolean()) break episodes;

                Position state = environment.state();
                Action action = agent.chooseAction(state, environment.availableActions());
                StepResult result = environment.step(action);
                // В Беллмане будущая ценность обнуляется только в настоящем конце задачи
                // (terminal), а не при обрыве эпизода по лимиту шагов (done).
                agent.update(state, action, result.reward(), result.state(), result.terminal(),
                        environment.availableActions());

                rewardSum += result.reward();
                done = result.done();
                steps++;
                path.add(result.state());
                listener.onStep(episode, steps, result.state(), result);
            }

            boolean solvedEpisode = environment.isAtGoal();
            if (solvedEpisode) solved++;
            lastSteps = steps;
            agent.decayExploration(config.epsilonDecay(), config.minimumEpsilon());
            rewards.add(rewardSum);
            stepsHistory.add(steps);
            solvedHistory.add(solvedEpisode);
            epsilonHistory.add(agent.explorationRate());

            listener.onEpisodeFinished(new EpisodeResult(
                    episode, steps, rewardSum, solvedEpisode, agent.explorationRate(),
                    environment.collectedWater(), environment.trapHits(),
                    environment.blockedMoves(), path));
        }

        TrainingResult result = new TrainingResult(solved, lastSteps, rewards, stepsHistory, solvedHistory, epsilonHistory);
        listener.onFinished(result);
        return result;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
