package com.cinestream.android.adapter;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cinestream.android.R;
import com.cinestream.android.databinding.ItemEpisodeBinding;
import com.cinestream.android.databinding.ItemPlayerDetailsBinding;
import com.cinestream.android.model.Episode;
import com.cinestream.android.model.PlayerState;
import com.cinestream.android.service.PlayerSources;
import com.cinestream.android.utils.ImageLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * Details and episode cards form one scroll surface below the pinned video.
 */
public final class EpisodeAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int DETAILS = 0, EPISODE = 1;

    public interface Actions {
        void selectEpisode(int number);

        void selectSeason(int number);

        void selectSource(String source);

        void retry();

        void move(int direction);
    }

    private final Actions actions;
    private final boolean tv;
    private final String title, overview;
    private PlayerState state;
    private boolean sourcesExpanded, overviewExpanded;

    public EpisodeAdapter(boolean tv, String title, String overview, Actions actions) {
        this.tv = tv;
        this.title = title;
        this.overview = overview;
        this.actions = actions;
        setStateRestorationPolicy(StateRestorationPolicy.PREVENT);
    }

    public void submit(PlayerState state) {
        if (this.state != null && (this.state.episode != state.episode || this.state.season != state.season)) {
            overviewExpanded = false;
        }
        this.state = state;
        setStateRestorationPolicy(state.loading ? StateRestorationPolicy.PREVENT : StateRestorationPolicy.ALLOW);
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return state == null ? 0 : 1 + (tv ? state.episodes.size() : 0);
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? DETAILS : EPISODE;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (type == DETAILS) {
            return new Details(ItemPlayerDetailsBinding.inflate(inflater, parent, false));
        }
        return new Card(ItemEpisodeBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof Details) {
            renderDetails((Details) holder);
            return;
        }
        Card card = (Card) holder;
        Episode episode = state.episodes.get(position - 1);
        Context context = holder.itemView.getContext();
        boolean aired = episode.hasAired();
        boolean selected = episode.number == state.episode;
        card.binding.title.setText(episode.name);
        card.binding.episodeNumber.setText(context.getString(R.string.episode_short, episode.number));
        card.binding.playingBadge.setVisibility(selected ? View.VISIBLE : View.GONE);
        String date = episode.airDate.isEmpty() ? context.getString(R.string.date_unconfirmed)
            : (aired ? "" : context.getString(R.string.upcoming) + " · ") + episode.airDate;
        String runtime = episode.runtime > 0 ? context.getString(R.string.runtime_minutes, episode.runtime) : "";
        card.binding.meta.setText(runtime.isEmpty() ? date : runtime + " · " + date);
        holder.itemView.setEnabled(aired);
        holder.itemView.setSelected(selected);
        holder.itemView.setAlpha(aired ? 1f : 0.45f);
        holder.itemView.setContentDescription(context.getString(R.string.episode_meta, episode.number, episode.name)
            + ", " + card.binding.meta.getText() + (selected ? ", " + context.getString(R.string.playing_badge) : ""));
        holder.itemView.setOnClickListener(view -> {
            if (episode.hasAired()) {
                actions.selectEpisode(episode.number);
            }
        });
        ImageLoader.load(card.binding.thumbnail, episode.image);
    }

    private void renderDetails(Details holder) {
        Context context = holder.itemView.getContext();
        ItemPlayerDetailsBinding binding = holder.binding;
        Episode selected = state.selected();
        binding.selectedTitle.setText(selected == null ? title
            : context.getString(R.string.episode_label, state.season, state.episode, selected.name));
        String synopsis = selected == null ? overview : selected.overview;
        binding.overview.setText(synopsis.isEmpty() ? context.getString(R.string.no_synopsis) : synopsis);
        binding.moreButton.setVisibility((tv && !synopsis.isEmpty()) || synopsis.length() > 140 ? View.VISIBLE : View.GONE);
        showOverview(holder);
        showSources(holder);
        List<ChoiceAdapter.Choice> sourceChoices = new ArrayList<>();
        for (String source : PlayerSources.ALL) {
            sourceChoices.add(new ChoiceAdapter.Choice(source, source, PlayerSources.supports(source, state.playbackId)));
        }
        holder.sources.submit(sourceChoices, state.source);
        List<ChoiceAdapter.Choice> seasonChoices = new ArrayList<>();
        if (state.show != null) {
            for (int season : state.show.seasons) {
                seasonChoices.add(new ChoiceAdapter.Choice(String.valueOf(season), context.getString(R.string.season_label, season), true));
            }
        }
        holder.seasons.submit(seasonChoices, String.valueOf(state.season));
        binding.tvControls.setVisibility(tv ? View.VISIBLE : View.GONE);
        binding.episodeCount.setText(context.getString(R.string.episode_count, state.episodes.size()));
        binding.previousButton.setEnabled(neighbor(state, -1) != null);
        binding.nextButton.setEnabled(neighbor(state, 1) != null);
        binding.previousButton.setAlpha(binding.previousButton.isEnabled() ? 1f : 0.35f);
        binding.nextButton.setAlpha(binding.nextButton.isEnabled() ? 1f : 0.35f);
        String status = guideStatus(context, state);
        binding.guideStatus.setText(status);
        binding.guideStatus.setVisibility(status.isEmpty() ? View.GONE : View.VISIBLE);
        binding.retryButton.setVisibility(state.error == null ? View.GONE : View.VISIBLE);
    }

    private void showOverview(Details holder) {
        holder.binding.overview.setVisibility(tv && !overviewExpanded ? View.GONE : View.VISIBLE);
        holder.binding.overview.setMaxLines(overviewExpanded ? Integer.MAX_VALUE : 3);
        holder.binding.overview.setEllipsize(overviewExpanded ? null : TextUtils.TruncateAt.END);
        holder.binding.moreButton.setText(overviewExpanded ? R.string.less : tv ? R.string.about_episode : R.string.more);
    }

    private void showSources(Details holder) {
        holder.binding.sourcePanel.setVisibility(sourcesExpanded ? View.VISIBLE : View.GONE);
        holder.binding.sourceButton.setText(holder.itemView.getContext().getString(
            sourcesExpanded ? R.string.source_collapse : R.string.source_action, state.source));
        holder.binding.sourceButton.setSelected(sourcesExpanded);
    }

    public static String guideStatus(Context context, PlayerState state) {
        return state.loading ? context.getString(R.string.guide_loading) : state.error != null ? state.error
            : state.episodes.isEmpty() ? context.getString(R.string.guide_empty)
            : state.selected() == null ? context.getString(R.string.no_aired_episodes) : "";
    }

    public static Episode neighbor(PlayerState state, int direction) {
        if (state.loading || state.episode == 0 || direction != -1 && direction != 1) {
            return null;
        }
        int selected = -1;
        for (int i = 0; i < state.episodes.size(); i++) {
            if (state.episodes.get(i).number == state.episode) {
                selected = i;
            }
        }
        if (selected < 0) {
            return null;
        }
        for (int i = selected + direction; i >= 0 && i < state.episodes.size(); i += direction) {
            if (state.episodes.get(i).hasAired()) {
                return state.episodes.get(i);
            }
        }
        return null;
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof Card) {
            ImageLoader.clear(((Card) holder).binding.thumbnail);
        }
    }

    private final class Details extends RecyclerView.ViewHolder {
        final ItemPlayerDetailsBinding binding;
        final ChoiceAdapter sources, seasons;

        Details(ItemPlayerDetailsBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            sources = new ChoiceAdapter(source -> {
                sourcesExpanded = false;
                actions.selectSource(source);
            });
            seasons = new ChoiceAdapter(value -> {
                int number = Integer.parseInt(value);
                if (state != null && number != state.season) {
                    actions.selectSeason(number);
                }
            });
            Context context = itemView.getContext();
            binding.sourceList.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
            binding.sourceList.setAdapter(sources);
            binding.seasonList.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
            binding.seasonList.setAdapter(seasons);
            binding.sourceButton.setOnClickListener(view -> {
                sourcesExpanded = !sourcesExpanded;
                showSources(this);
            });
            binding.moreButton.setOnClickListener(view -> {
                overviewExpanded = !overviewExpanded;
                showOverview(this);
            });
            binding.retryButton.setOnClickListener(view -> actions.retry());
            binding.previousButton.setOnClickListener(view -> actions.move(-1));
            binding.nextButton.setOnClickListener(view -> actions.move(1));
        }
    }

    private static final class Card extends RecyclerView.ViewHolder {
        final ItemEpisodeBinding binding;

        Card(ItemEpisodeBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
