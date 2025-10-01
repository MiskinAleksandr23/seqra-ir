package org.seqra.ir.testing.structure;

import org.seqra.ir.testing.Common;

public class FieldsAndMethods {

    public static class Common1Child extends Common.Common1 {
        private int privateFieldsAndMethods;

        private boolean publicField;

        private void privateFieldsAndMethods() {
        }

        private int accessIntField() {
            return ((Common.Common1) this).publicField;
        }

        private boolean accessBooleanField() {
            return publicField;
        }
    }

}
