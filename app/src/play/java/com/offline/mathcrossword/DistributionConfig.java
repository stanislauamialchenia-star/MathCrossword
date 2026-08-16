package com.offline.mathcrossword;

/** Google Play build: Play owns updates; no sideload/install capability is exposed. */
final class DistributionConfig {
    private DistributionConfig() { }
    static boolean selfUpdateEnabled() { return false; }
    static String channelLabel() { return "Google Play"; }
}
