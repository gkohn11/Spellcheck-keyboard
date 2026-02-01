/*
 * Copyright (C) 2014 The Android Open Source Project
 * Copyright (C) 2025 Raimondas Rimkus
 * Copyright (C) 2021 wittmane
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

import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;

import com.gkohn11.spellcheckkeyboard.R;
import com.gkohn11.spellcheckkeyboard.keyboard.KeyboardLayoutSet;

/**
 * "Preferences" settings sub screen.
 *
 * This settings sub screen handles the following input preferences.
 * - Auto-capitalization
 * - Show separate number row
 * - Show special characters
 * - Show language switch key
 * - Show on-screen keyboard
 * - Switch to other keyboards
 * - Space swipe cursor move
 * - Delete swipe
 */
public final class PreferencesSettingsFragment extends SubScreenFragment {
    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.prefs_screen_preferences);

        if (Build.VERSION.SDK_INT < 35) { // BAKLAVA (API 35)
            removePreference(Settings.PREF_USE_ON_SCREEN);
        }
        
        // Set up long press for numbers preference to be disabled when:
        // 1. Number row is on, OR
        // 2. Show special characters is on (when number row is off)
        final android.preference.SwitchPreference longPressPref = 
            (android.preference.SwitchPreference) findPreference(Settings.PREF_LONG_PRESS_FOR_NUMBERS);
        final android.preference.SwitchPreference numberRowPref = 
            (android.preference.SwitchPreference) findPreference(Settings.PREF_SHOW_NUMBER_ROW);
        final android.preference.SwitchPreference showSpecialCharsPref = 
            (android.preference.SwitchPreference) findPreference(Settings.PREF_SHOW_SPECIAL_CHARS);
        
        if (longPressPref != null && numberRowPref != null && showSpecialCharsPref != null) {
            // Update enabled state based on current settings
            updateLongPressForNumbersEnabled(longPressPref, numberRowPref.isChecked(), showSpecialCharsPref.isChecked());
            
            // Listen for changes to number row setting
            numberRowPref.setOnPreferenceChangeListener(new android.preference.Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(android.preference.Preference preference, Object newValue) {
                    boolean isNumberRowOn = (Boolean) newValue;
                    updateLongPressForNumbersEnabled(longPressPref, isNumberRowOn, showSpecialCharsPref.isChecked());
                    return true;
                }
            });
            
            // Listen for changes to show special characters setting
            showSpecialCharsPref.setOnPreferenceChangeListener(new android.preference.Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(android.preference.Preference preference, Object newValue) {
                    boolean isShowSpecialCharsOn = (Boolean) newValue;
                    updateLongPressForNumbersEnabled(longPressPref, numberRowPref.isChecked(), isShowSpecialCharsOn);
                    return true;
                }
            });
        }
    }
    
    private void updateLongPressForNumbersEnabled(android.preference.SwitchPreference longPressPref, 
            boolean numberRowEnabled, boolean showSpecialCharsEnabled) {
        // Disable long press for numbers when:
        // 1. Number row is enabled, OR
        // 2. Show special characters is enabled (when number row is off)
        boolean shouldDisable = numberRowEnabled || (!numberRowEnabled && showSpecialCharsEnabled);
        longPressPref.setEnabled(!shouldDisable);
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences prefs, final String key) {
        if (key.equals(Settings.PREF_SHOW_SPECIAL_CHARS) ||
                key.equals(Settings.PREF_SHOW_NUMBER_ROW) ||
                key.equals(Settings.PREF_LONG_PRESS_FOR_NUMBERS)) {
            KeyboardLayoutSet.onKeyboardThemeChanged();
        }
    }
}
