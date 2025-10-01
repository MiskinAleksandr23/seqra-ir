package org.seqra.ir.testing;

import org.seqra.ir.api.jvm.JIRCacheSettings;
import org.seqra.ir.api.jvm.JIRDatabase;
import org.seqra.ir.api.jvm.JIRSettings;
import org.seqra.ir.impl.SeqraIrDbKt;
import org.seqra.ir.impl.features.Usages;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

public class JavaApi {
    // private static class ArgumentResolver extends TypedExprResolver<JIRArgument> {
    //
    //     @Override
    //     public void ifMatches(@NotNull JIRExpr jIRExpr) {
    //         if (jIRExpr instanceof JIRArgument) {
    //             getResult().add((JIRArgument) jIRExpr);
    //         }
    //     }
    //
    // }

    public static void cacheSettings() {
        new JIRCacheSettings().types(10, Duration.of(1, ChronoUnit.MINUTES));
    }

    public static void getDatabase() {
        try {
            JIRDatabase instance = SeqraIrDbKt.async(new JIRSettings().installFeatures(Usages.INSTANCE)).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
