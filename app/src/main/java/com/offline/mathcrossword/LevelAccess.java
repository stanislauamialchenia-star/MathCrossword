package com.offline.mathcrossword;

/** Pure progression rules for 100-level pages. */
final class LevelAccess {
    private LevelAccess() { }

    static int pageForLevel(int level) {
        return (Math.max(1, level) - 1) / 100;
    }

    static int maxUnlockedPage(int progressLevel) {
        // Page 1 (1..100) is always available. Page 2 opens when level 101
        // becomes the sequential frontier, i.e. after 1..100 are completed.
        return Math.max(0, pageForLevel(Math.max(1, progressLevel)));
    }

    static int firstLevelOnPage(int page) {
        return Math.max(0, page) * 100 + 1;
    }

    static String sessionMode(int selectedLevel, int progressLevel) {
        if (selectedLevel < progressLevel) return "PATH_REPLAY";
        if (selectedLevel > progressLevel) return "PATH_TEST";
        return "PATH";
    }
}
