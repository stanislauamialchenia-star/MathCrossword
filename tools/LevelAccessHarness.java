package com.offline.mathcrossword;
public final class LevelAccessHarness {
    public static void main(String[] args) {
        assertEq(0, LevelAccess.maxUnlockedPage(1));
        assertEq(0, LevelAccess.maxUnlockedPage(100));
        assertEq(1, LevelAccess.maxUnlockedPage(101));
        assertEq(1, LevelAccess.maxUnlockedPage(200));
        assertEq(2, LevelAccess.maxUnlockedPage(201));
        assertEq("PATH_REPLAY", LevelAccess.sessionMode(75, 81));
        assertEq("PATH", LevelAccess.sessionMode(81, 81));
        assertEq("PATH_TEST", LevelAccess.sessionMode(90, 81));
        System.out.println("LevelAccess OK");
    }
    static void assertEq(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError(expected + " != " + actual);
    }
}
