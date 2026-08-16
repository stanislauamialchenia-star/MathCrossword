package com.offline.mathcrossword;

/** Distribution-specific capabilities for the direct GitHub APK. */
final class DistributionConfig {
    private DistributionConfig() { }
    static boolean selfUpdateEnabled() { return true; }
    static String channelLabel() { return "GitHub"; }
}
