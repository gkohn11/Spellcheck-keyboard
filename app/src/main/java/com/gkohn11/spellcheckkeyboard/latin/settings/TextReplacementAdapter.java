/*
 * Copyright (C) 2025 Raimondas Rimkus
 * 
 * This file is part of Spellcheck Keyboard, a derivative work based on
 * Simple Keyboard (Copyright (C) 2025 Raimondas Rimkus and contributors)
 * which is based on AOSP LatinIME (Copyright (C) 2008 The Android Open Source Project).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.gkohn11.spellcheckkeyboard.latin.settings;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * Adapter for displaying and editing text replacement entries in a RecyclerView.
 */
public class TextReplacementAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    private List<TextReplacementEntry> entries;
    private boolean dataChanged = false;
    private java.util.Set<Integer> selectedPositions = new java.util.HashSet<>();

    public TextReplacementAdapter(List<TextReplacementEntry> entries) {
        this.entries = entries;
        // Ensure there's always at least one empty row at the start (after header)
        ensureEmptyRowAtStart();
    }
    
    /**
     * Ensure there's always an empty row at the start (top) for adding new entries
     */
    private void ensureEmptyRowAtStart() {
        if (entries.isEmpty() || !isEntryEmpty(entries.get(0))) {
            entries.add(0, new TextReplacementEntry());
        }
    }
    
    /**
     * Check if an entry is empty (both misspell and correct are empty)
     */
    private boolean isEntryEmpty(TextReplacementEntry entry) {
        return entry != null && 
               (entry.getMisspell() == null || entry.getMisspell().trim().isEmpty()) &&
               (entry.getCorrect() == null || entry.getCorrect().trim().isEmpty());
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? TYPE_HEADER : TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            View view = inflater.inflate(com.gkohn11.spellcheckkeyboard.R.layout.item_text_replacement_header, parent, false);
            return new HeaderViewHolder(view);
        } else {
            View view = inflater.inflate(com.gkohn11.spellcheckkeyboard.R.layout.item_text_replacement, parent, false);
            return new ItemViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderViewHolder) {
            // Header view - no data to bind
            return;
        }

        ItemViewHolder itemHolder = (ItemViewHolder) holder;
        int dataPosition = position - 1; // Account for header
        if (dataPosition >= 0 && dataPosition < entries.size()) {
            TextReplacementEntry entry = entries.get(dataPosition);
            itemHolder.bind(entry, dataPosition);
        } else {
            // This shouldn't happen, but handle gracefully
            itemHolder.bind(new TextReplacementEntry(), dataPosition);
        }
    }

    @Override
    public int getItemCount() {
        return entries.size() + 1; // +1 for header
    }

    public List<TextReplacementEntry> getEntries() {
        // Return entries excluding empty ones (empty entries are for UI only, not saved to CSV)
        java.util.List<TextReplacementEntry> filtered = new java.util.ArrayList<>();
        for (TextReplacementEntry entry : entries) {
            if (!isEntryEmpty(entry)) {
                filtered.add(entry);
            }
        }
        return filtered;
    }

    public boolean hasDataChanged() {
        return dataChanged;
    }

    public void setDataChanged(boolean changed) {
        this.dataChanged = changed;
    }

    /**
     * ViewHolder for header row
     */
    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        HeaderViewHolder(View itemView) {
            super(itemView);
        }
    }

    /**
     * ViewHolder for data rows
     */
    class ItemViewHolder extends RecyclerView.ViewHolder {
        private CheckBox checkboxSelect;
        private EditText editMisspell;
        private EditText editCorrect;
        private Switch switchAlwaysOn;
        private int currentPosition = -1;
        private TextWatcher misspellWatcher;
        private TextWatcher correctWatcher;
        private CompoundButton.OnCheckedChangeListener switchListener;
        private CompoundButton.OnCheckedChangeListener selectListener;

        ItemViewHolder(View itemView) {
            super(itemView);
            checkboxSelect = itemView.findViewById(com.gkohn11.spellcheckkeyboard.R.id.checkbox_select);
            editMisspell = itemView.findViewById(com.gkohn11.spellcheckkeyboard.R.id.edit_misspell);
            editCorrect = itemView.findViewById(com.gkohn11.spellcheckkeyboard.R.id.edit_correct);
            switchAlwaysOn = itemView.findViewById(com.gkohn11.spellcheckkeyboard.R.id.switch_always_on);
        }

        void bind(TextReplacementEntry entry, int position) {
            // Remove existing listeners to avoid triggering during binding
            if (misspellWatcher != null) {
                editMisspell.removeTextChangedListener(misspellWatcher);
            }
            if (correctWatcher != null) {
                editCorrect.removeTextChangedListener(correctWatcher);
            }
            switchAlwaysOn.setOnCheckedChangeListener(null);
            if (checkboxSelect != null) {
                checkboxSelect.setOnCheckedChangeListener(null);
            }

            // Set current position
            currentPosition = position;

            // Set values
            if (checkboxSelect != null) {
                checkboxSelect.setChecked(selectedPositions.contains(position));
            }
            editMisspell.setText(entry.getMisspell());
            editCorrect.setText(entry.getCorrect());
            switchAlwaysOn.setChecked(entry.isAlwaysOn());

            // Create and add listeners
            misspellWatcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    if (currentPosition >= 0 && currentPosition < entries.size()) {
                        entries.get(currentPosition).setMisspell(s.toString());
                        dataChanged = true;
                        
                        // If this is the first row (index 0) and user started typing, add a new empty row at the start
                        if (currentPosition == 0 && s.length() > 0) {
                            int oldSize = entries.size();
                            ensureEmptyRowAtStart();
                            // Only notify if a new row was actually added
                            if (entries.size() > oldSize) {
                                // Position in RecyclerView = 1 (header at 0, first item at 1)
                                notifyItemInserted(1);
                                // Update the position of the current item since we inserted before it
                                currentPosition = 1;
                            }
                        }
                    }
                }
            };
            editMisspell.addTextChangedListener(misspellWatcher);

            correctWatcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    if (currentPosition >= 0 && currentPosition < entries.size()) {
                        entries.get(currentPosition).setCorrect(s.toString());
                        dataChanged = true;
                        
                        // If this is the first row (index 0) and user started typing, add a new empty row at the start
                        if (currentPosition == 0 && s.length() > 0) {
                            int oldSize = entries.size();
                            ensureEmptyRowAtStart();
                            // Only notify if a new row was actually added
                            if (entries.size() > oldSize) {
                                // Position in RecyclerView = 1 (header at 0, first item at 1)
                                notifyItemInserted(1);
                                // Update the position of the current item since we inserted before it
                                currentPosition = 1;
                            }
                        }
                    }
                }
            };
            editCorrect.addTextChangedListener(correctWatcher);

            switchListener = new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (currentPosition >= 0 && currentPosition < entries.size()) {
                        entries.get(currentPosition).setAlwaysOn(isChecked);
                        dataChanged = true;
                    }
                }
            };
            switchAlwaysOn.setOnCheckedChangeListener(switchListener);
            
            // Selection checkbox listener
            if (checkboxSelect != null) {
                selectListener = new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        if (currentPosition >= 0) {
                            if (isChecked) {
                                selectedPositions.add(currentPosition);
                            } else {
                                selectedPositions.remove(currentPosition);
                            }
                        }
                    }
                };
                checkboxSelect.setOnCheckedChangeListener(selectListener);
            }
        }
    }
    
    /**
     * Delete selected entries
     */
    public void deleteSelectedEntries() {
        if (selectedPositions.isEmpty()) {
            return;
        }
        
        // Sort positions in descending order to avoid index shifting issues
        java.util.List<Integer> sortedPositions = new java.util.ArrayList<>(selectedPositions);
        java.util.Collections.sort(sortedPositions, java.util.Collections.reverseOrder());
        
        // Remove entries from highest index to lowest
        for (int position : sortedPositions) {
            if (position >= 0 && position < entries.size()) {
                entries.remove(position);
                notifyItemRemoved(position + 1); // +1 for header
            }
        }
        
        // Clear selection
        selectedPositions.clear();
        
        // Ensure empty row at start
        ensureEmptyRowAtStart();
        
        dataChanged = true;
    }
    
    /**
     * Check if any entries are selected
     */
    public boolean hasSelectedEntries() {
        return !selectedPositions.isEmpty();
    }
}

