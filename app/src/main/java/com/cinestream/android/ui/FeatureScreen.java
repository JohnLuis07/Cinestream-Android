package com.cinestream.android.ui;

import android.view.View;

/**
 * Same binding-backed screen contract used by Stride.
 */
public interface FeatureScreen {
    View root();

    default void close() {
    }
}
