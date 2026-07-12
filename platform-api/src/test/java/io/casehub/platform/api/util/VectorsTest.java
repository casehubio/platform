package io.casehub.platform.api.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class VectorsTest {

    private static final double DELTA = 1e-9;

    // --- cosineSimilarity ---

    @Test
    void cosineSimilarity_identicalVectors_returnsOne() {
        float[] v = {1.0f, 2.0f, 3.0f};
        assertThat(Vectors.cosineSimilarity(v, v)).isCloseTo(1.0, within(DELTA));
    }

    @Test
    void cosineSimilarity_oppositeVectors_returnsNegativeOne() {
        float[] a = {1.0f, 2.0f, 3.0f};
        float[] b = {-1.0f, -2.0f, -3.0f};
        assertThat(Vectors.cosineSimilarity(a, b)).isCloseTo(-1.0, within(DELTA));
    }

    @Test
    void cosineSimilarity_orthogonalVectors_returnsZero() {
        float[] a = {1.0f, 0.0f};
        float[] b = {0.0f, 1.0f};
        assertThat(Vectors.cosineSimilarity(a, b)).isCloseTo(0.0, within(DELTA));
    }

    @Test
    void cosineSimilarity_zeroVectorA_returnsZero() {
        float[] zero = {0.0f, 0.0f, 0.0f};
        float[] v = {1.0f, 2.0f, 3.0f};
        assertThat(Vectors.cosineSimilarity(zero, v)).isEqualTo(0.0);
    }

    @Test
    void cosineSimilarity_zeroVectorB_returnsZero() {
        float[] v = {1.0f, 2.0f, 3.0f};
        float[] zero = {0.0f, 0.0f, 0.0f};
        assertThat(Vectors.cosineSimilarity(v, zero)).isEqualTo(0.0);
    }

    @Test
    void cosineSimilarity_bothZeroVectors_returnsZero() {
        float[] zero = {0.0f, 0.0f};
        assertThat(Vectors.cosineSimilarity(zero, zero)).isEqualTo(0.0);
    }

    @Test
    void cosineSimilarity_singleElement() {
        float[] a = {3.0f};
        float[] b = {5.0f};
        assertThat(Vectors.cosineSimilarity(a, b)).isCloseTo(1.0, within(DELTA));
    }

    @Test
    void cosineSimilarity_singleElementOpposite() {
        float[] a = {3.0f};
        float[] b = {-5.0f};
        assertThat(Vectors.cosineSimilarity(a, b)).isCloseTo(-1.0, within(DELTA));
    }

    @Test
    void cosineSimilarity_emptyArrays_returnsZero() {
        assertThat(Vectors.cosineSimilarity(new float[0], new float[0])).isEqualTo(0.0);
    }

    @Test
    void cosineSimilarity_mismatchedLengths_throws() {
        float[] a = {1.0f, 2.0f};
        float[] b = {1.0f};
        assertThatThrownBy(() -> Vectors.cosineSimilarity(a, b))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dotProduct_basicComputation() {
        float[] a = {1.0f, 2.0f, 3.0f};
        float[] b = {4.0f, 5.0f, 6.0f};
        assertThat(Vectors.dotProduct(a, b)).isCloseTo(32.0, within(DELTA));
    }

    @Test
    void dotProduct_emptyArrays_returnsZero() {
        assertThat(Vectors.dotProduct(new float[0], new float[0])).isEqualTo(0.0);
    }

    @Test
    void dotProduct_mismatchedLengths_throws() {
        float[] a = {1.0f, 2.0f, 3.0f};
        float[] b = {1.0f};
        assertThatThrownBy(() -> Vectors.dotProduct(a, b))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dotProduct_orthogonalVectors_returnsZero() {
        float[] a = {1.0f, 0.0f};
        float[] b = {0.0f, 1.0f};
        assertThat(Vectors.dotProduct(a, b)).isCloseTo(0.0, within(DELTA));
    }

    @Test
    void magnitude_basicComputation() {
        float[] v = {3.0f, 4.0f};
        assertThat(Vectors.magnitude(v)).isCloseTo(5.0, within(DELTA));
    }

    @Test
    void magnitude_zeroVector_returnsZero() {
        assertThat(Vectors.magnitude(new float[]{0.0f, 0.0f})).isEqualTo(0.0);
    }

    @Test
    void magnitude_singleElement() {
        assertThat(Vectors.magnitude(new float[]{7.0f})).isCloseTo(7.0, within(DELTA));
    }

    @Test
    void magnitude_emptyArray_returnsZero() {
        assertThat(Vectors.magnitude(new float[0])).isEqualTo(0.0);
    }
}
