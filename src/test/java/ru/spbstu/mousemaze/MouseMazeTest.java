package ru.spbstu.mousemaze;

import org.junit.jupiter.api.Test;
import ru.spbstu.mousemaze.agent.QLearningAgent;
import ru.spbstu.mousemaze.environment.MazeEnvironment;
import ru.spbstu.mousemaze.generator.MazeIO;
import ru.spbstu.mousemaze.generator.RandomMazeGenerator;
import ru.spbstu.mousemaze.model.Action;
import ru.spbstu.mousemaze.model.CellType;
import ru.spbstu.mousemaze.model.Maze;
import ru.spbstu.mousemaze.model.Position;
import ru.spbstu.mousemaze.model.RewardConfig;
import ru.spbstu.mousemaze.model.StepResult;
import ru.spbstu.mousemaze.training.GreedyPolicyRollout;
import ru.spbstu.mousemaze.training.Trainer;
import ru.spbstu.mousemaze.training.TrainingConfig;
import ru.spbstu.mousemaze.training.TrainingResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Проверки требований лабораторной: MDP, Q-learning, генерация DFS, обучение. */
class MouseMazeTest {
    private static final RewardConfig REWARDS = RewardConfig.defaults();

    /** Коридор 1×4: S . W C — для точных проверок наград. */
    private static Maze corridor() {
        CellType[][] cells = {{CellType.START, CellType.FREE, CellType.WATER, CellType.GOAL}};
        return new Maze(cells, new Position(0, 0), new Position(0, 3));
    }

    @Test
    void wallKeepsMouseInPlaceWithPenalty() {
        MazeEnvironment env = new MazeEnvironment(corridor(), REWARDS, 100, false);
        StepResult result = env.step(Action.UP);
        assertTrue(result.blocked());
        assertEquals(new Position(0, 0), result.state());
        assertEquals(REWARDS.wallPenalty(), result.reward());
        assertFalse(result.terminal());
    }

    @Test
    void waterGivesRewardOncePerEpisodeAndCheeseEndsEpisode() {
        MazeEnvironment env = new MazeEnvironment(corridor(), REWARDS, 100, false);
        assertEquals(REWARDS.stepPenalty(), env.step(Action.RIGHT).reward());
        assertEquals(REWARDS.water(), env.step(Action.RIGHT).reward());
        env.step(Action.LEFT);
        assertEquals(REWARDS.stepPenalty(), env.step(Action.RIGHT).reward(), "вода уже выпита");
        StepResult goal = env.step(Action.RIGHT);
        assertEquals(REWARDS.cheese(), goal.reward());
        assertTrue(goal.done());
        assertTrue(goal.terminal());
    }

    @Test
    void trapPenalizesAndTerminatesOnlyWhenConfigured() {
        CellType[][] cells = {{CellType.START, CellType.TRAP, CellType.GOAL}};
        Maze maze = new Maze(cells, new Position(0, 0), new Position(0, 2));
        StepResult soft = new MazeEnvironment(maze, REWARDS, 100, false).step(Action.RIGHT);
        assertEquals(REWARDS.trap(), soft.reward());
        assertFalse(soft.done());
        StepResult hard = new MazeEnvironment(maze, REWARDS, 100, true).step(Action.RIGHT);
        assertTrue(hard.terminal());
    }

    @Test
    void stepLimitStopsEpisodeButIsNotTerminal() {
        MazeEnvironment env = new MazeEnvironment(corridor(), REWARDS, 1, false);
        StepResult result = env.step(Action.RIGHT);
        assertTrue(result.done());
        assertFalse(result.terminal(), "обрыв по лимиту шагов не должен обнулять будущую ценность");
    }

    @Test
    void bellmanUpdateMatchesFormula() {
        QLearningAgent agent = new QLearningAgent(0.5, 0.9, 0.0, new Random(1));
        Position s = new Position(0, 0);
        Position next = new Position(0, 1);
        agent.update(next, Action.RIGHT, 10, new Position(0, 2), true, List.of(Action.RIGHT));
        assertEquals(5.0, agent.qValue(next, Action.RIGHT), 1e-9);
        agent.update(s, Action.RIGHT, -1, next, false, List.of(Action.RIGHT));
        // Q = 0 + 0.5 * (-1 + 0.9 * 5 - 0) = 1.75
        assertEquals(1.75, agent.qValue(s, Action.RIGHT), 1e-9);
    }

    @Test
    void dfsGeneratorBuildsConnectedMazeOfAnySize() {
        for (int[] size : new int[][]{{2, 2}, {3, 17}, {25, 40}}) {
            Maze maze = new RandomMazeGenerator(size[0], size[1], 0.0, 0.1, 0.1, new Random(7)).generate();
            assertEquals(size[0], maze.rows());
            assertEquals(size[1], maze.columns());
            assertEquals(size[0] * size[1], reachableCells(maze), "DFS обходит все клетки");
        }
    }

    @Test
    void mazeSchemeSurvivesSaveAndLoad() throws Exception {
        Maze maze = new RandomMazeGenerator(6, 9, 0.1, 0.1, 0.1, new Random(3)).generate();
        Path file = Files.createTempFile("maze", ".txt");
        try {
            MazeIO.save(maze, file);
            Maze loaded = MazeIO.load(file);
            assertEquals(maze.start(), loaded.start());
            assertEquals(maze.goal(), loaded.goal());
            for (int r = 0; r < maze.rows(); r++) {
                for (int c = 0; c < maze.columns(); c++) {
                    Position p = new Position(r, c);
                    assertEquals(maze.cellAt(p), loaded.cellAt(p));
                    for (Action a : Action.values()) assertEquals(maze.canMove(p, a), loaded.canMove(p, a));
                }
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void schemeFileWithTrailingBlankLineLoadsAndUnreachableCheeseIsRejected() throws Exception {
        Path file = Files.createTempFile("maze", ".txt");
        try {
            Files.writeString(file, "#####\n#S.C#\n#.#.#\n#...#\n#####\n\n\n");
            Maze maze = MazeIO.load(file);
            assertEquals(new Position(0, 0), maze.start());
            assertEquals(new Position(0, 1), maze.goal());

            Files.writeString(file, "#####\n#S#C#\n###.#\n#.#.#\n#####\n");
            assertThrows(IllegalArgumentException.class, () -> MazeIO.load(file));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void agentLearnsAndGreedyPathReachesCheese() {
        Maze maze = new RandomMazeGenerator(10, 10, 0.08, 0.08, 0.06, new Random(42)).generate();
        QLearningAgent agent = new QLearningAgent(0.1, 0.99, 1.0, new Random(5));
        MazeEnvironment env = new MazeEnvironment(maze, REWARDS, 400, false);
        TrainingResult result = new Trainer().train(env, agent, new TrainingConfig(1000, 400, 0.995, 0.05));

        long lastSolved = result.solved().subList(800, 1000).stream().filter(b -> b).count();
        assertTrue(lastSolved >= 180, "успех в последних 200 эпохах: " + lastSolved);

        List<Position> path = GreedyPolicyRollout.run(maze, agent, REWARDS);
        assertEquals(maze.goal(), path.get(path.size() - 1));
        for (int i = 1; i < path.size(); i++) {
            Position from = path.get(i - 1);
            Position to = path.get(i);
            assertTrue(maze.availableActions(from).stream().anyMatch(a -> from.move(a).equals(to)),
                    "путь не проходит сквозь стены");
        }
    }

    @Test
    void untrainedAgentDoesNotFakeSuccess() {
        Maze maze = new RandomMazeGenerator(12, 12, 0.0, 0.0, 0.0, new Random(9)).generate();
        QLearningAgent agent = new QLearningAgent(0.1, 0.99, 1.0, new Random(1));
        List<Position> path = GreedyPolicyRollout.run(maze, agent, REWARDS);
        assertNotEquals(maze.goal(), path.get(path.size() - 1),
                "лучший путь без обучения не должен подменяться поиском кратчайшего пути");
    }

    private static int reachableCells(Maze maze) {
        Set<Position> seen = new HashSet<>(List.of(maze.start()));
        ArrayDeque<Position> queue = new ArrayDeque<>(seen);
        while (!queue.isEmpty()) {
            Position p = queue.remove();
            for (Action a : maze.availableActions(p)) {
                if (seen.add(p.move(a))) queue.add(p.move(a));
            }
        }
        return seen.size();
    }
}
