package org.example.parameter;

/**
 * Describes one setting of a signal, so that a user interface can show it and check its value.
 *
 * @param key the short name used in requests, for example {@code threshold}
 * @param description a short explanation for the user
 * @param defaultValue the recommended value
 * @param min the smallest allowed value
 * @param max the largest allowed value
 * @param wholeNumber true if only whole numbers are allowed, for example a number of levels
 */
public record ParameterSpec(
        String key,
        String description,
        double defaultValue,
        double min,
        double max,
        boolean wholeNumber) {

    /**
     * Checks that the description itself makes sense.
     *
     * @throws IllegalArgumentException if the key is blank or the default is not allowed
     */
    public ParameterSpec {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Parameter key must not be blank");
        }
        if (min > max) {
            throw new IllegalArgumentException(key + ": min " + min + " is above max " + max);
        }
        if (!isAllowed(defaultValue, min, max, wholeNumber)) {
            throw new IllegalArgumentException(
                    key + ": default " + defaultValue + " is not allowed");
        }
    }

    /**
     * Tells whether a value may be used for this parameter.
     *
     * @param value the value to check
     * @return true if the value is between min and max, and whole if it must be
     */
    public boolean allows(double value) {
        return isAllowed(value, min, max, wholeNumber);
    }

    private static boolean isAllowed(double value, double min, double max, boolean wholeNumber) {
        if (!(value >= min && value <= max)) {
            return false;
        }
        return !wholeNumber || value == Math.rint(value);
    }
}
