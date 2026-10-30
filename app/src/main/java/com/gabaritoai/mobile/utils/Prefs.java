package com.gabaritoai.mobile.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {
    private static final String PREF_NAME = "gabarito_ai_prefs";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_MODEL = "selected_model";
    private static final String KEY_AUTO_CLICK = "auto_click";
    private static final String KEY_SHOW_EXPLANATION = "show_explanation";
    private static final String KEY_STEALTH_MODE = "stealth_mode";

    private final SharedPreferences prefs;

    public Prefs(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public String getApiKey() {
        return prefs.getString(KEY_API_KEY, "");
    }

    public void setApiKey(String key) {
        prefs.edit().putString(KEY_API_KEY, key != null ? key.trim() : "").apply();
    }

    public String getSelectedModel() {
        return prefs.getString(KEY_MODEL, "gemini-3.5-flash-lite");
    }

    public void setSelectedModel(String model) {
        prefs.edit().putString(KEY_MODEL, model).apply();
    }

    public boolean isAutoClickEnabled() {
        return prefs.getBoolean(KEY_AUTO_CLICK, false);
    }

    public void setAutoClickEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_CLICK, enabled).apply();
    }

    public boolean isShowExplanationEnabled() {
        return prefs.getBoolean(KEY_SHOW_EXPLANATION, true);
    }

    public void setShowExplanationEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SHOW_EXPLANATION, enabled).apply();
    }

    public boolean isStealthMode() {
        return prefs.getBoolean(KEY_STEALTH_MODE, false);
    }

    public void setStealthMode(boolean enabled) {
        prefs.edit().putBoolean(KEY_STEALTH_MODE, enabled).apply();
    }
}
