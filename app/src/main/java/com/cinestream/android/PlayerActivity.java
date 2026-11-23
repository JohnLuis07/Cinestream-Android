package com.cinestream.android;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;

import com.cinestream.android.model.MediaItem;
import com.cinestream.android.ui.PlayerScreen;
import com.cinestream.android.utils.WindowInsetsHelper;

public final class PlayerActivity extends ComponentActivity {
    private PlayerScreen screen;

    public static void open(Context context, MediaItem item) {
        context.startActivity(new Intent(context, PlayerActivity.class)
            .putExtra("id", item.id).putExtra("type", item.type)
            .putExtra("title", item.title).putExtra("overview", item.overview));
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String id = getIntent().getStringExtra("id");
        String type = getIntent().getStringExtra("type");
        if (id == null || !id.matches("(?:\\d+|tt\\d+)") || (!"movie".equals(type) && !"tv".equals(type))) {
            finish();
            return;
        }
        screen = new PlayerScreen(this, id, "tv".equals(type),
            getIntent().getStringExtra("title"), getIntent().getStringExtra("overview"));
        setContentView(screen.root());
        WindowInsetsHelper.apply(this, screen.root());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!screen.exitFullscreen()) {
                    finish();
                }
            }
        });
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (screen != null) {
            screen.configurationChanged();
        }
    }

    @Override
    protected void onPause() {
        if (screen != null) {
            screen.pause();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (screen != null) {
            screen.resume();
        }
    }

    @Override
    protected void onDestroy() {
        if (screen != null) {
            screen.close();
        }
        super.onDestroy();
    }
}
