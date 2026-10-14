package dev.deathcompass;

/** Pure decision logic for stable finder locks, independent of game APIs. */
final class TargetLockPolicy {
    private TargetLockPolicy() {}

    /** @param stillExists null means the target's chunk is currently unavailable. */
    static boolean shouldRelease(String currentDimension, String lockedDimension,
                                 double squaredDistance, int releaseBlocks, Boolean stillExists) {
        if (!currentDimension.equals(lockedDimension)) return true;
        if (Boolean.FALSE.equals(stillExists)) return true;
        return squaredDistance > releaseBlocks * (double) releaseBlocks;
    }
}
