package com.offline.mathcrossword;

import java.util.HashSet;
import java.util.Set;
import static com.offline.mathcrossword.PuzzleModel.*;

/** Small invariant check for v19 PATH deceptive decoys. */
public final class DeceptiveDecoyHarness {
    public static void main(String[] args) {
        int[] levels = {70, 80, 95};
        System.out.println("level,added,false_values,unique");
        for (int level : levels) {
            Puzzle p = PuzzleGenerator.generatePath(level);
            Set<Integer> truth = new HashSet<>();
            for (Pos pos : p.hidden) truth.add(p.cells.get(pos).number);
            boolean falseValues = true;
            int n = p.deceptiveDecoyCount;
            for (int i = Math.max(0, p.tiles.size() - n); i < p.tiles.size(); i++) {
                if (truth.contains(p.tiles.get(i).value)) falseValues = false;
            }
            boolean unique = SolutionCounter.countSolutions(p, 2) == 1;
            System.out.println(level + "," + n + "," + falseValues + "," + unique);
            if (!falseValues || !unique) throw new AssertionError("decoy invariant failed at " + level);
        }
    }
}
