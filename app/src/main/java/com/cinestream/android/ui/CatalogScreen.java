package com.cinestream.android.ui;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.ViewTreeObserver;

import androidx.activity.ComponentActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;

import com.cinestream.android.PlayerActivity;
import com.cinestream.android.adapter.CatalogAdapter;
import com.cinestream.android.databinding.ScreenCatalogBinding;
import com.cinestream.android.model.CatalogState;
import com.cinestream.android.model.MediaItem;
import com.cinestream.android.viewmodel.CatalogViewModel;

public final class CatalogScreen implements FeatureScreen {
    private final ComponentActivity activity;
    private final ScreenCatalogBinding binding;
    private final CatalogViewModel viewModel;
    private final Observer<CatalogState> observer = this::render;
    private final ViewTreeObserver.OnGlobalLayoutListener keyboardListener;

    public CatalogScreen(ComponentActivity activity, ScreenCatalogBinding binding) {
        this.activity = activity;
        this.binding = binding;
        viewModel = new ViewModelProvider(activity).get(CatalogViewModel.class);
        CatalogAdapter adapter = new CatalogAdapter(new CatalogAdapter.Actions() {
            @Override
            public void watch(MediaItem item) {
                PlayerActivity.open(activity, item);
            }

            @Override
            public void retry() {
                viewModel.reload();
            }
        });
        int columns = Math.max(2, Math.min(5, activity.getResources().getConfiguration().screenWidthDp / 170));
        GridLayoutManager layout = new GridLayoutManager(activity, columns);
        layout.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return adapter.fullSpan(position) ? columns : 1;
            }
        });
        binding.catalogList.setLayoutManager(layout);
        binding.catalogList.setAdapter(adapter);
        binding.catalogList.setItemAnimator(null);
        binding.searchInput.setText(viewModel.draft());
        updateClearButton(viewModel.draft());
        binding.searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                viewModel.draft(text.toString());
                updateClearButton(text.toString());
            }

            @Override
            public void afterTextChanged(Editable text) {
            }
        });
        binding.searchInput.setOnEditorActionListener((view, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH) {
                search();
                return true;
            }
            return false;
        });
        binding.searchButton.setOnClickListener(view -> search());
        binding.clearButton.setOnClickListener(view -> {
            binding.searchInput.setText("");
            search();
        });
        binding.discoverButton.setOnClickListener(view -> selectFilter("all"));
        binding.moviesButton.setOnClickListener(view -> selectFilter("movie"));
        binding.tvButton.setOnClickListener(view -> selectFilter("tv"));
        keyboardListener = () -> {
            WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(binding.getRoot());
            boolean typing = insets != null && insets.isVisible(WindowInsetsCompat.Type.ime());
            binding.bottomNavigation.setVisibility(typing ? View.GONE : View.VISIBLE);
        };
        binding.getRoot().getViewTreeObserver().addOnGlobalLayoutListener(keyboardListener);
        viewModel.state().observe(activity, observer);
    }

    private void search() {
        viewModel.search(binding.searchInput.getText().toString());
        binding.searchInput.clearFocus();
        InputMethodManager keyboard = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (keyboard != null) {
            keyboard.hideSoftInputFromWindow(binding.searchInput.getWindowToken(), 0);
        }
        binding.catalogList.scrollToPosition(0);
    }

    private void render(CatalogState state) {
        ((CatalogAdapter) binding.catalogList.getAdapter()).submit(state);
        binding.discoverButton.setSelected("all".equals(state.filter));
        binding.moviesButton.setSelected("movie".equals(state.filter));
        binding.tvButton.setSelected("tv".equals(state.filter));
        if (state.loading) {
            binding.catalogList.scrollToPosition(0);
        }
    }

    private void updateClearButton(String query) {
        binding.clearButton.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void selectFilter(String filter) {
        binding.catalogList.scrollToPosition(0);
        viewModel.filter(filter);
    }

    @Override
    public View root() {
        return binding.getRoot();
    }

    @Override
    public void close() {
        viewModel.state().removeObserver(observer);
        binding.getRoot().getViewTreeObserver().removeOnGlobalLayoutListener(keyboardListener);
        binding.catalogList.setAdapter(null);
    }
}
