package org.seqra.ir.testing.structure;

public class HiddenFieldSuperClass {
    public int a, b;

    public static class HiddenFieldSuccClass extends HiddenFieldSuperClass {
        public double b;
    }
}

