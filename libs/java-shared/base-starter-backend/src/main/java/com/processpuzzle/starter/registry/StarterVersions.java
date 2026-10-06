package com.processpuzzle.starter.registry;

import java.util.Comparator;

/**
 * Orders {@code major.minor.patch} numerically, so {@code 1.10.0} is newer than {@code 1.9.0}; a
 * pre-release sorts before its release. The same order as {@code compareVersions} in the Biz
 * frontend's starter catalog.
 */
public final class StarterVersions {

    public static final Comparator<String> ASCENDING = StarterVersions::compare;

    private StarterVersions() {
    }

    public static int compare(String left, String right) {
        String[] leftSplit = left.split("-", 2);
        String[] rightSplit = right.split("-", 2);
        String[] leftParts = leftSplit[0].split("\\.");
        String[] rightParts = rightSplit[0].split("\\.");
        for (int index = 0; index < Math.max(leftParts.length, rightParts.length); index++) {
            int difference = Long.compare(part(leftParts, index), part(rightParts, index));
            if (difference != 0) {
                return difference;
            }
        }
        String leftPre = leftSplit.length > 1 ? leftSplit[1] : null;
        String rightPre = rightSplit.length > 1 ? rightSplit[1] : null;
        if (leftPre == null && rightPre == null) {
            return 0;
        }
        if (leftPre == null) {
            return 1;
        }
        if (rightPre == null) {
            return -1;
        }
        return leftPre.compareTo(rightPre);
    }

    private static long part(String[] parts, int index) {
        if (index >= parts.length) {
            return 0;
        }
        try {
            return Long.parseLong(parts[index]);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
