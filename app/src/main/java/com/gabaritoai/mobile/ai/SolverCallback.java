package com.gabaritoai.mobile.ai;

import com.gabaritoai.mobile.model.QuestionResult;

public interface SolverCallback {
    void onSuccess(QuestionResult result);
    void onError(String errorMessage);
}
