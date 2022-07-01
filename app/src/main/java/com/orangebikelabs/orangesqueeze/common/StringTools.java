/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.common;

import java.text.CollationKey;
import java.text.Collator;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Various string utilities.
 *
 * @author tbsandee@orangebikelabs.com
 */
public class StringTools {
    @Nonnull
    public static Collator getNullSafeCollator() {
        final Collator base = Collator.getInstance();
        return new Collator() {

            @Override
            public int hashCode() {
                return base.hashCode();
            }

            @Override
            public CollationKey getCollationKey(String s) {
                return base.getCollationKey(s);
            }

            @Override
            public int compare(@Nullable String s1, @Nullable String s2) {
                if (s1 == null) {
                    if (s2 == null) {
                        return 0;
                    } else {
                        return -1;
                    }
                }
                if (s2 == null) {
                    return 1;
                }
                return base.compare(s1, s2);
            }
        };
    }
}
