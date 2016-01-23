package org.rmatil.sync.core;

import java.util.Comparator;

/**
 * Compares two strings by their length.
 */
public class StringLengthComparator implements Comparator<String> {

    /**
     * Compares the strings by their length.
     * <br>
     * <br>
     * {@inheritDoc}
     */
    @Override
    public int compare(String o1, String o2) {
        if (o1.length() < o2.length()) {
            return - 1;
        }

        if (o1.length() > o2.length()) {
            return 1;
        }

        return o1.compareTo(o2);
    }
}
