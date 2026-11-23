package com.cinestream.android.adapter;

import android.content.Context;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cinestream.android.R;
import com.cinestream.android.databinding.ItemCatalogFooterBinding;
import com.cinestream.android.databinding.ItemCatalogHeaderBinding;
import com.cinestream.android.databinding.ItemMediaBinding;
import com.cinestream.android.model.CatalogState;
import com.cinestream.android.model.MediaItem;
import com.cinestream.android.utils.ImageLoader;

import java.util.Collections;

/**
 * Hero, status, cards and attribution share one recycled scrolling surface.
 */
public final class CatalogAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int HEADER = 0, MEDIA = 1, FOOTER = 2;

    public interface Actions {
        void watch(MediaItem item);

        void retry();
    }

    private final Actions actions;
    private CatalogState state = new CatalogState("all", "", true, Collections.emptyList(), "", null);

    public CatalogAdapter(Actions actions) {
        this.actions = actions;
        setStateRestorationPolicy(StateRestorationPolicy.PREVENT);
    }

    public void submit(CatalogState state) {
        this.state = state;
        setStateRestorationPolicy(state.loading ? StateRestorationPolicy.PREVENT : StateRestorationPolicy.ALLOW);
        notifyDataSetChanged();
    }

    public boolean fullSpan(int position) {
        return getItemViewType(position) != MEDIA;
    }

    @Override
    public int getItemCount() {
        return 1 + state.items.size() + (!state.loading && state.error == null && !state.items.isEmpty() ? 1 : 0);
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? HEADER : position <= state.items.size() ? MEDIA : FOOTER;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (type == HEADER) {
            return new Header(ItemCatalogHeaderBinding.inflate(inflater, parent, false));
        }
        if (type == FOOTER) {
            return new Footer(ItemCatalogFooterBinding.inflate(inflater, parent, false));
        }
        return new Card(ItemMediaBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Context context = holder.itemView.getContext();
        if (holder instanceof Header) {
            ItemCatalogHeaderBinding binding = ((Header) holder).binding;
            MediaItem featured = state.query.isEmpty() && !state.items.isEmpty() ? state.items.get(0) : null;
            binding.hero.setVisibility(featured == null ? View.GONE : View.VISIBLE);
            if (featured != null) {
                ImageLoader.load(binding.heroImage, featured.backdrop.isEmpty() ? featured.poster : featured.backdrop);
                binding.heroTitle.setText(featured.title);
                binding.heroOverview.setText(featured.overview.isEmpty() ? context.getString(R.string.no_synopsis) : featured.overview);
                binding.heroMeta.setText(metadata(context, featured));
                binding.watchButton.setOnClickListener(view -> actions.watch(featured));
            } else {
                ImageLoader.clear(binding.heroImage);
            }
            int heading = "movie".equals(state.filter) ? R.string.movies_heading : "tv".equals(state.filter) ? R.string.tv_heading : R.string.discover_heading;
            binding.heading.setText(state.query.isEmpty() ? context.getString(heading) : context.getString(R.string.search_heading, state.query));
            binding.count.setText(state.loading || state.error != null ? context.getString(R.string.catalog_subtitle)
                : context.getString(R.string.title_count, state.items.size()));
            String notice = state.notice.contains("credentials") || state.notice.contains("local.properties")
                || state.notice.contains("API key") ? context.getString(R.string.catalog_partial) : state.notice;
            binding.notice.setText(notice);
            binding.notice.setVisibility(state.notice.isEmpty() ? View.GONE : View.VISIBLE);
            binding.progress.setVisibility(state.loading ? View.VISIBLE : View.GONE);
            boolean hasStatus = state.loading || state.error != null || state.items.isEmpty();
            binding.statePanel.setVisibility(hasStatus ? View.VISIBLE : View.GONE);
            binding.stateIcon.setVisibility(state.loading ? View.GONE : View.VISIBLE);
            binding.stateTitle.setText(state.loading ? R.string.state_loading_title
                : state.error != null ? R.string.state_error_title : R.string.state_empty_title);
            String status = state.loading ? context.getString(R.string.state_loading_body)
                : state.error != null ? state.error : state.items.isEmpty() ? context.getString(R.string.empty) : "";
            if (state.error != null && (state.error.contains("local.properties") || state.error.contains("credentials"))) {
                status = context.getString(R.string.catalog_not_ready);
            }
            binding.status.setText(status);
            binding.status.setVisibility(status.isEmpty() ? View.GONE : View.VISIBLE);
            binding.retryButton.setVisibility(state.error == null ? View.GONE : View.VISIBLE);
            binding.retryButton.setOnClickListener(view -> actions.retry());
        } else if (holder instanceof Card) {
            ItemMediaBinding binding = ((Card) holder).binding;
            MediaItem item = state.items.get(position - 1);
            binding.title.setText(item.title);
            binding.meta.setText(context.getString(R.string.poster_details, item.year,
                context.getString(item.isTv() ? R.string.tv_shows : R.string.movies)));
            binding.rating.setText(context.getString(R.string.poster_rating, item.rating));
            binding.rating.setVisibility(item.rating.isEmpty() || "N/A".equals(item.rating) ? View.GONE : View.VISIBLE);
            ImageLoader.load(binding.poster, item.poster);
            holder.itemView.setContentDescription(item.title + ", " + metadata(context, item));
            holder.itemView.setOnClickListener(view -> actions.watch(item));
        } else {
            ItemCatalogFooterBinding binding = ((Footer) holder).binding;
            binding.providerLinks.setText(Html.fromHtml("<a href=\"https://www.themoviedb.org\">TMDB</a> · "
                + "<a href=\"https://www.tvmaze.com\">TVmaze</a> · "
                + "<a href=\"https://creativecommons.org/licenses/by-sa/4.0/\">CC BY-SA</a> · "
                + "<a href=\"https://www.omdbapi.com\">OMDb</a>", Html.FROM_HTML_MODE_LEGACY));
            binding.providerLinks.setMovementMethod(LinkMovementMethod.getInstance());
        }
    }

    private static String metadata(Context context, MediaItem item) {
        return context.getString(R.string.metadata, context.getString(item.isTv() ? R.string.tv_type : R.string.movie_type), item.year, item.rating);
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof Card) {
            ImageLoader.clear(((Card) holder).binding.poster);
        }
        if (holder instanceof Header) {
            ImageLoader.clear(((Header) holder).binding.heroImage);
        }
    }

    static final class Header extends RecyclerView.ViewHolder {
        final ItemCatalogHeaderBinding binding;

        Header(ItemCatalogHeaderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    static final class Footer extends RecyclerView.ViewHolder {
        final ItemCatalogFooterBinding binding;

        Footer(ItemCatalogFooterBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    static final class Card extends RecyclerView.ViewHolder {
        final ItemMediaBinding binding;

        Card(ItemMediaBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.posterFrame.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                int height = (right - left) * 3 / 2;
                if (height > 0 && view.getLayoutParams().height != height) {
                    ViewGroup.LayoutParams params = view.getLayoutParams();
                    params.height = height;
                    view.setLayoutParams(params);
                }
            });
        }
    }
}
