package com.cinestream.android;

import android.os.Bundle;

import androidx.activity.ComponentActivity;

import com.cinestream.android.databinding.ActivityMainBinding;
import com.cinestream.android.ui.CatalogScreen;
import com.cinestream.android.utils.WindowInsetsHelper;

/**
 * Hosts native binding-backed screens, following Stride's activity structure.
 */
public final class MainActivity extends ComponentActivity {
    private CatalogScreen screen;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        WindowInsetsHelper.apply(this, binding.getRoot());
        screen = new CatalogScreen(this, binding.catalogScreen);
    }

    @Override
    protected void onDestroy() {
        if (screen != null) {
            screen.close();
        }
        super.onDestroy();
    }
}
