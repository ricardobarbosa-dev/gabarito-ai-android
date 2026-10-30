package com.gabaritoai.mobile.model;

import com.google.gson.annotations.SerializedName;

public class QuestionResult {
    @SerializedName("correctLetter")
    private String correctLetter;

    @SerializedName("reasoning")
    private String reasoning;

    private boolean success = true;
    private String errorMessage;
    private String modelUsed;

    public QuestionResult() {}

    public QuestionResult(String correctLetter, String reasoning) {
        this.correctLetter = correctLetter;
        this.reasoning = reasoning;
    }

    public static QuestionResult error(String message) {
        QuestionResult res = new QuestionResult();
        res.success = false;
        res.errorMessage = message;
        return res;
    }

    public String getCorrectLetter() {
        return correctLetter != null ? correctLetter.toUpperCase().trim() : "";
    }

    public void setCorrectLetter(String correctLetter) {
        this.correctLetter = correctLetter;
    }

    public String getReasoning() {
        return reasoning != null ? reasoning.trim() : "";
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getModelUsed() {
        return modelUsed;
    }

    public void setModelUsed(String modelUsed) {
        this.modelUsed = modelUsed;
    }
}
