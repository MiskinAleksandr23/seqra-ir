package org.seqra.ir.testing.types;

abstract public class PrimitiveAndArrays {

    private int value = 0;
    private int[] intArray = new int[1];

    abstract int[] run(String[] stringArray);

}
