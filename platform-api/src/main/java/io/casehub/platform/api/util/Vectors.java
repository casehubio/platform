package io.casehub.platform.api.util;

/**
 * Stateless vector operations for {@code float[]} arrays. All methods promote {@code float}
 * operands to {@code double} before multiplication to preserve precision across high-dimensional
 * vectors.
 */
public final class Vectors {

    private Vectors() {}


    /**
     * Dot product of two vectors.
     *
     * @param a first vector
     * @param b second vector; must be the same length as {@code a}
     * @return sum of element-wise products
     * @throws IllegalArgumentException if the vectors have different lengths
     */
    public static double dotProduct(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException(
                    "Vector length mismatch: " + a.length + " vs " + b.length);
        }
        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            sum += (double) a[i] * b[i];
        }
        return sum;
    }


    /**
     * L2 norm (magnitude) of a vector.
     *
     * @param v the vector
     * @return the Euclidean length of the vector
     */
    public static double magnitude(float[] v) {
        double sum = 0.0;
        for (int i = 0; i < v.length; i++) {
            sum += (double) v[i] * v[i];
        }
        return Math.sqrt(sum);
    }

    /**
     * Cosine similarity between two vectors.
     *
     * @param a first vector
     * @param b second vector; must be the same length as {@code a}
     * @return cosine similarity in [-1, 1]; {@code 0.0} for zero vectors
     * @throws IllegalArgumentException if the vectors have different lengths
     */
    public static double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException(
                    "Vector length mismatch: " + a.length + " vs " + b.length);
        }
        double dot = 0.0;
        double magA = 0.0;
        double magB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            magA += (double) a[i] * a[i];
            magB += (double) b[i] * b[i];
        }
        if (magA == 0.0 || magB == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(magA) * Math.sqrt(magB));
    }
}
