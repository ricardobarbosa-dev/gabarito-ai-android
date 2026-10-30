package com.gabaritoai.mobile.ai;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.gabaritoai.mobile.model.QuestionResult;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GeminiVisionClient {
    private static final String TAG = "GeminiVisionClient";
    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final Gson gson;
    private final Handler mainHandler;

    public GeminiVisionClient() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Solves a question using an image screenshot sent to Gemini Vision
     */
    public void solveQuestionFromScreenshot(
            final String apiKey,
            final String model,
            final String base64Jpeg,
            final SolverCallback callback) {

        final String activeModel = (model != null && !model.isEmpty()) ? model : "gemini-3.5-flash-lite";
        final String endpoint = BASE_URL + activeModel + ":generateContent?key=" + apiKey;

        // Build Chain-of-Thought System & User Prompt
        String prompt = "Você é o GabaritoAI, um avaliador acadêmico com taxa de acerto de 100% em questões de concurso e faculdade.\n" +
                "Analise visualmente a imagem da tela que contém a questão de múltipla escolha.\n\n" +
                "INSTRUÇÕES OBRIGATÓRIAS:\n" +
                "1. Leia atentamente o enunciado completo, incluindo qualquer código, tabela ou texto introdutório.\n" +
                "2. Analise cada alternativa (A, B, C, D, E) individualmente antes de decidir.\n" +
                "3. Escolha a única alternativa 100% correta de acordo com a bibliografia e lógica acadêmica.\n" +
                "4. Responda ESTRITAMENTE em formato JSON (sem formatação markdown fora das chaves) com os campos:\n" +
                "{\n" +
                "  \"correctLetter\": \"A\",\n" +
                "  \"reasoning\": \"Explicação concisa e didática de por que esta alternativa é a correta e as outras estão erradas.\"\n" +
                "}";

        // Build Multimodal Payload
        JsonObject root = new JsonObject();
        JsonArray contents = new JsonArray();
        JsonObject contentObj = new JsonObject();
        JsonArray parts = new JsonArray();

        // 1. Image part
        JsonObject imagePart = new JsonObject();
        JsonObject inlineData = new JsonObject();
        inlineData.addProperty("mime_type", "image/jpeg");
        inlineData.addProperty("data", base64Jpeg);
        imagePart.add("inline_data", inlineData);
        parts.add(imagePart);

        // 2. Text prompt part
        JsonObject textPart = new JsonObject();
        textPart.addProperty("text", prompt);
        parts.add(textPart);

        contentObj.add("parts", parts);
        contents.add(contentObj);
        root.add("contents", contents);

        // Generation Config: deterministic temperature 0.0
        JsonObject genConfig = new JsonObject();
        genConfig.addProperty("temperature", 0.0);
        genConfig.addProperty("response_mime_type", "application/json");
        root.add("generationConfig", genConfig);

        String jsonBody = gson.toJson(root);
        RequestBody body = RequestBody.create(jsonBody, JSON_MEDIA_TYPE);
        Request request = new Request.Builder()
                .url(endpoint)
                .post(body)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                postError(callback, "Erro de rede ao conectar à API do Gemini: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                final String responseBody = response.body() != null ? response.body().string() : "";

                if (!response.isSuccessful()) {
                    // Check for model deprecation recommendation in error message
                    Pattern p = Pattern.compile("use models/([a-zA-Z0-9\\.\\-_]+)");
                    Matcher m = p.matcher(responseBody);
                    if (m.find()) {
                        String suggestedModel = m.group(1);
                        Log.i(TAG, "Redirecionando automaticamente para o modelo recomendado: " + suggestedModel);
                        solveQuestionFromScreenshot(apiKey, suggestedModel, base64Jpeg, callback);
                        return;
                    }
                    postError(callback, "Erro Gemini (" + response.code() + "): " + responseBody);
                    return;
                }

                try {
                    JsonObject respJson = JsonParser.parseString(responseBody).getAsJsonObject();
                    JsonArray candidates = respJson.getAsJsonArray("candidates");
                    if (candidates == null || candidates.isEmpty()) {
                        postError(callback, "Nenhuma resposta gerada pelo modelo.");
                        return;
                    }

                    JsonObject firstCandidate = candidates.get(0).getAsJsonObject();
                    JsonObject content = firstCandidate.getAsJsonObject("content");
                    JsonArray resParts = content.getAsJsonArray("parts");
                    String rawText = resParts.get(0).getAsJsonObject().get("text").getAsString();

                    // Parse the JSON inside rawText
                    String cleanJson = rawText.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
                    QuestionResult result = gson.fromJson(cleanJson, QuestionResult.class);
                    result.setModelUsed(activeModel);

                    postSuccess(callback, result);
                } catch (Exception ex) {
                    Log.e(TAG, "Falha ao processar resposta do Gemini: " + responseBody, ex);
                    postError(callback, "Falha ao interpretar resposta da IA: " + ex.getMessage());
                }
            }
        });
    }

    /**
     * Test connection with a simple text prompt
     */
    public void testConnection(final String apiKey, final String model, final SolverCallback callback) {
        final String activeModel = (model != null && !model.isEmpty()) ? model : "gemini-3.5-flash-lite";
        final String endpoint = BASE_URL + activeModel + ":generateContent?key=" + apiKey;

        JsonObject root = new JsonObject();
        JsonArray contents = new JsonArray();
        JsonObject contentObj = new JsonObject();
        JsonArray parts = new JsonArray();
        JsonObject textPart = new JsonObject();
        textPart.addProperty("text", "Responda apenas com a palavra CONECTADO.");
        parts.add(textPart);
        contentObj.add("parts", parts);
        contents.add(contentObj);
        root.add("contents", contents);

        String jsonBody = gson.toJson(root);
        RequestBody body = RequestBody.create(jsonBody, JSON_MEDIA_TYPE);
        Request request = new Request.Builder()
                .url(endpoint)
                .post(body)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                postError(callback, "Falha na conexão: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                final String responseBody = response.body() != null ? response.body().string() : "";

                if (!response.isSuccessful()) {
                    Pattern p = Pattern.compile("use models/([a-zA-Z0-9\\.\\-_]+)");
                    Matcher m = p.matcher(responseBody);
                    if (m.find()) {
                        String suggestedModel = m.group(1);
                        testConnection(apiKey, suggestedModel, callback);
                        return;
                    }
                    postError(callback, "Erro Gemini (" + response.code() + "): " + responseBody);
                    return;
                }

                QuestionResult res = new QuestionResult("A", "Conexão estabelecida com sucesso!");
                res.setModelUsed(activeModel);
                postSuccess(callback, res);
            }
        });
    }

    private void postSuccess(final SolverCallback callback, final QuestionResult result) {
        mainHandler.post(() -> {
            if (callback != null) callback.onSuccess(result);
        });
    }

    private void postError(final SolverCallback callback, final String message) {
        mainHandler.post(() -> {
            if (callback != null) callback.onError(message);
        });
    }
}
