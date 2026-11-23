package com.cinestream.android.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cinestream.android.databinding.ItemChoiceBinding;
import com.cinestream.android.R;

import java.util.ArrayList;
import java.util.List;

public final class ChoiceAdapter extends RecyclerView.Adapter<ChoiceAdapter.Holder> {
    public interface Actions {
        void select(String id);
    }

    public static final class Choice {
        public final String id, label;
        public final boolean enabled;

        public Choice(String id, String label, boolean enabled) {
            this.id = id;
            this.label = label;
            this.enabled = enabled;
        }
    }

    private final Actions actions;
    private List<Choice> choices = new ArrayList<>();
    private String selected = "";

    public ChoiceAdapter(Actions actions) {
        this.actions = actions;
    }

    public void submit(List<Choice> choices, String selected) {
        this.choices = new ArrayList<>(choices);
        this.selected = selected;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(ItemChoiceBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Choice choice = choices.get(position);
        holder.binding.getRoot().setText(choice.label);
        holder.binding.getRoot().setEnabled(choice.enabled);
        holder.binding.getRoot().setAlpha(choice.enabled ? 1f : 0.4f);
        holder.binding.getRoot().setSelected(choice.id.equals(selected));
        holder.binding.getRoot().setContentDescription(choice.enabled ? choice.label
            : holder.itemView.getContext().getString(R.string.source_unavailable, choice.label));
        holder.binding.getRoot().setOnClickListener(view -> actions.select(choice.id));
    }

    @Override
    public int getItemCount() {
        return choices.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemChoiceBinding binding;

        Holder(ItemChoiceBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
