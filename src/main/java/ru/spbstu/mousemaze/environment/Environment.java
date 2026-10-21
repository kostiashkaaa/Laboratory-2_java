package ru.spbstu.mousemaze.environment;

import ru.spbstu.mousemaze.model.Action;
import ru.spbstu.mousemaze.model.Position;
import ru.spbstu.mousemaze.model.StepResult;

import java.util.List;

/**
 * Среда, с которой взаимодействует агент: на каждом шаге сообщает новое состояние
 * и величину выигрыша или проигрыша (подкрепление).
 */
public interface Environment {
    /** Начинает новый эпизод: мышь возвращается на старт. */
    void reset();

    /** @return текущее состояние (x, y) */
    Position state();

    /** @return действия, разрешённые из текущего состояния */
    List<Action> availableActions();

    /**
     * Выполняет действие агента.
     *
     * @param action действие
     * @return новое состояние, награда и признаки завершения
     */
    StepResult step(Action action);

    /** @return мышь в текущем эпизоде дошла до сыра */
    boolean isAtGoal();

    /** @return сколько клеток с водой выпито в текущем эпизоде */
    int collectedWater();

    /** @return сколько раз мышь получила удар током в текущем эпизоде */
    int trapHits();

    /** @return сколько раз мышь упёрлась в стену в текущем эпизоде */
    int blockedMoves();
}
