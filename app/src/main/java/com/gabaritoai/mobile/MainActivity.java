package com.gabaritoai.mobile;

import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.gabaritoai.mobile.ai.GeminiVisionClient;
import com.gabaritoai.mobile.ai.SolverCallback;
import com.gabaritoai.mobile.model.QuestionResult;
import com.gabaritoai.mobile.overlay.FloatingBubbleService;
import com.gabaritoai.mobile.utils.Prefs;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private Prefs prefs;
    private GeminiVisionClient visionClient;

    private TextInputEditText etApiKey;
    private Spinner spModelSelector;
    private Button btnTestConnection;
    private TextView tvTestFeedback;
    private Button btnToggleFloatingService;
    private TextView tvServiceStatus;
    private TextView tvStatusBadge;
    private SwitchMaterial swAutoClick;
    private SwitchMaterial swShowExplanation;
    private SwitchMaterial swStealthMode;
    private Button btnOpenAccessibility;
    private Button btnXiaomiPermissions;

    private boolean isServiceRunning = false;
    private ActivityResultLauncher<Intent> mediaProjectionLauncher;
    private ActivityResultLauncher<Intent> overlayPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = new Prefs(this);
        visionClient = new GeminiVisionClient();

        initViews();
        setupModelSpinner();
        loadSavedPreferences();
        setupActivityLaunchers();
        setupListeners();
    }

    private void initViews() {
        etApiKey = findViewById(R.id.et_api_key);
        spModelSelector = findViewById(R.id.sp_model_selector);
        btnTestConnection = findViewById(R.id.btn_test_connection);
        tvTestFeedback = findViewById(R.id.tv_test_feedback);
        btnToggleFloatingService = findViewById(R.id.btn_toggle_floating_service);
        tvServiceStatus = findViewById(R.id.tv_service_status);
        tvStatusBadge = findViewById(R.id.tv_status_badge);
        swAutoClick = findViewById(R.id.sw_auto_click);
        swShowExplanation = findViewById(R.id.sw_show_explanation);
        swStealthMode = findViewById(R.id.sw_stealth_mode);
        btnOpenAccessibility = findViewById(R.id.btn_open_accessibility);
        btnXiaomiPermissions = findViewById(R.id.btn_xiaomi_permissions);

        if (isXiaomiDevice()) {
            btnXiaomiPermissions.setVisibility(View.VISIBLE);
            btnXiaomiPermissions.setOnClickListener(v -> openXiaomiPermissions());
        }
    }

    private void setupModelSpinner() {
        List<String> models = new ArrayList<>();
        models.add("gemini-3.5-flash-lite");
        models.add("gemini-3.5-flash");
        models.add("gemini-2.5-flash");
        models.add("gemini-2.0-flash");

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                models
        );
        spModelSelector.setAdapter(adapter);

        String saved = prefs.getSelectedModel();
        int index = models.indexOf(saved);
        if (index >= 0) {
            spModelSelector.setSelection(index);
        }

        spModelSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                prefs.setSelectedModel(models.get(position));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void loadSavedPreferences() {
        String key = prefs.getApiKey();
        etApiKey.setText(key);
        swAutoClick.setChecked(prefs.isAutoClickEnabled());
        swShowExplanation.setChecked(prefs.isShowExplanationEnabled());
        swStealthMode.setChecked(prefs.isStealthMode());

        updateStatusBadge(key);
    }

    private void updateStatusBadge(String key) {
        if (key != null && key.trim().length() > 10) {
            tvStatusBadge.setText("Configurado ✓");
            tvStatusBadge.setTextColor(getColor(R.color.primary));
        } else {
            tvStatusBadge.setText("Pendente ⚠️");
            tvStatusBadge.setTextColor(getColor(R.color.warning));
        }
    }

    private void setupActivityLaunchers() {
        // MediaProjection Capture Launcher
        mediaProjectionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        startFloatingBubbleService(result.getResultCode(), result.getData());
                    } else {
                        Toast.makeText(this, "Permissão de captura de tela é necessária para o assistente ler a questão!", Toast.LENGTH_LONG).show();
                    }
                }
        );

        // Overlay Permission Launcher
        overlayPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                        requestMediaProjection();
                    } else {
                        Toast.makeText(this, "Permissão de sobreposição necessária para exibir a bolha!", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void setupListeners() {
        // API Key live saving
        etApiKey.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int count, int after) {
                String val = s != null ? s.toString().trim() : "";
                prefs.setApiKey(val);
                updateStatusBadge(val);
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Switches
        swAutoClick.setOnCheckedChangeListener((btn, checked) -> prefs.setAutoClickEnabled(checked));
        swShowExplanation.setOnCheckedChangeListener((btn, checked) -> prefs.setShowExplanationEnabled(checked));
        swStealthMode.setOnCheckedChangeListener((btn, checked) -> prefs.setStealthMode(checked));

        // Accessibility Settings Button
        btnOpenAccessibility.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });

        // Test Connection Button
        btnTestConnection.setOnClickListener(v -> testGeminiConnection());

        // Toggle Floating Service Button
        btnToggleFloatingService.setOnClickListener(v -> {
            if (isServiceRunning) {
                stopFloatingBubbleService();
            } else {
                checkPermissionsAndStartService();
            }
        });
    }

    private void testGeminiConnection() {
        String key = prefs.getApiKey();
        if (key.isEmpty()) {
            Toast.makeText(this, "Cole sua chave de API antes de testar!", Toast.LENGTH_SHORT).show();
            return;
        }

        tvTestFeedback.setVisibility(View.VISIBLE);
        tvTestFeedback.setText("⏳ Testando conexão com Gemini...");
        tvTestFeedback.setTextColor(getColor(R.color.primary_light));
        btnTestConnection.setEnabled(false);

        String model = prefs.getSelectedModel();
        visionClient.testConnection(key, model, new SolverCallback() {
            @Override
            public void onSuccess(QuestionResult result) {
                btnTestConnection.setEnabled(true);
                tvTestFeedback.setText("✓ Conexão bem-sucedida com " + result.getModelUsed() + "!");
                tvTestFeedback.setTextColor(getColor(R.color.primary));
            }

            @Override
            public void onError(String errorMessage) {
                btnTestConnection.setEnabled(true);
                tvTestFeedback.setText("✗ " + errorMessage);
                tvTestFeedback.setTextColor(getColor(R.color.danger));
            }
        });
    }

    private void checkPermissionsAndStartService() {
        String key = prefs.getApiKey();
        if (key.isEmpty()) {
            Toast.makeText(this, "Por favor, configure sua chave do Gemini antes de iniciar!", Toast.LENGTH_LONG).show();
            return;
        }

        // 1. Check Overlay Permission (Can Draw Over Other Apps)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
            );
            overlayPermissionLauncher.launch(intent);
            return;
        }

        // 2. Request MediaProjection (Screen capture)
        requestMediaProjection();
    }

    private void requestMediaProjection() {
        MediaProjectionManager mgr = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        if (mgr != null) {
            mediaProjectionLauncher.launch(mgr.createScreenCaptureIntent());
        }
    }

    private void startFloatingBubbleService(int resultCode, Intent data) {
        Intent serviceIntent = new Intent(this, FloatingBubbleService.class);
        serviceIntent.setAction(FloatingBubbleService.ACTION_START);
        serviceIntent.putExtra(FloatingBubbleService.EXTRA_RESULT_CODE, resultCode);
        serviceIntent.putExtra(FloatingBubbleService.EXTRA_DATA_INTENT, data);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        isServiceRunning = true;
        btnToggleFloatingService.setText("🔴 Parar Assistente Flutuante");
        btnToggleFloatingService.setBackgroundColor(getColor(R.color.danger));
        tvServiceStatus.setText("Assistente ativo! Abra o app da Estácio e toque na bolha verde.");
        tvServiceStatus.setTextColor(getColor(R.color.primary));

        Toast.makeText(this, "Bolha GabaritoAI ativada na tela!", Toast.LENGTH_SHORT).show();
    }

    private boolean isXiaomiDevice() {
        String m = Build.MANUFACTURER != null ? Build.MANUFACTURER.toLowerCase() : "";
        String b = Build.BRAND != null ? Build.BRAND.toLowerCase() : "";
        return m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") ||
                b.contains("xiaomi") || b.contains("redmi") || b.contains("poco");
    }

    private void openXiaomiPermissions() {
        try {
            Intent intent = new Intent("miui.intent.action.APP_PERM_EDITOR");
            intent.setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity");
            intent.putExtra("extra_pkgname", getPackageName());
            startActivity(intent);
        } catch (Exception e) {
            try {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception ignored) {}
        }
    }

    private void stopFloatingBubbleService() {
        Intent serviceIntent = new Intent(this, FloatingBubbleService.class);
        serviceIntent.setAction(FloatingBubbleService.ACTION_STOP);
        startService(serviceIntent);

        isServiceRunning = false;
        btnToggleFloatingService.setText("🟢 Ativar Bolha Flutuante");
        btnToggleFloatingService.setBackgroundColor(getColor(R.color.primary));
        tvServiceStatus.setText("Serviço desativado no momento");
        tvServiceStatus.setTextColor(getColor(R.color.text_muted));
    }
}
