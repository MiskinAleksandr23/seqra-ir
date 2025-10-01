package org.seqra.ir.approximations.approx;

import org.seqra.ir.approximation.annotation.Approximate;


@Approximate(java.lang.Integer.class)
public class IntegerApprox {
    private final int value;

    public IntegerApprox(int value) {
        this.value = value;
    }

    public static IntegerApprox valueOf(int value) {
        return new IntegerApprox(value);
    }

    public int getValue() {
        return value;
    }
}
