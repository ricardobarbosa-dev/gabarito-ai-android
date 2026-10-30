package com.gabaritoai.mobile.overlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.gabaritoai.mobile.MainActivity;
import com.gabaritoai.mobile.R;
import com.gabaritoai.mobile.ai.GeminiVisionClient;
import com.gabaritoai.mobile.ai.SolverCallback;
import com.gabaritoai.mobile.capture.ScreenCaptureService;
import com.gabaritoai.mobile.model.QuestionResult;
import com.gabaritoai.mobile.service.AutoClickAccessibilityService;
import com.gabaritoai.mobile.utils.Prefs;

public class FloatingBubbleService extends Service {
    private static final String TAG = "FloatingBubbleService";
    private static final String CHANNEL_ID = "gabarito_ai_channel";
    private static final int NOTIFICATION_ID = 1001;

    public static final String ACTION_START = "ACTION_START";
    public static final String ACTION_STOP = "ACTION_STOP";
    public static final String EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE";
    public static final String EXTRA_DATA_INTENT = "EXTRA_DATA_INTENT";

    private WindowManager windowManager;
    private View bubbleView;
    private WindowManager.LayoutParams bubbleParams;

    private View resultCardView;
    private WindowManager.LayoutParams resultCardParams;

    private ScreenCaptureService captureService;
    private GeminiVisionClient visionClient;
    private Prefs prefs;
    private Vibrator vibrator;

    private boolean isSolving = false;
    private boolean isBubbleAttached = false;
    private boolean isResultCardAttached = false;

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        prefs = new Prefs(this);
        visionClient = new GeminiVisionClient();
        captureService = new ScreenCaptureService(this);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        String action = intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        // Start Foreground with mediaProjection type on Android 14+
        startForegroundWithNotification();

        // Initialize MediaProjection if extras are passed
        if (intent.hasExtra(EXTRA_RESULT_CODE) && intent.hasExtra(EXTRA_DATA_INTENT)) {
            int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
            Intent data = intent.getParcelableExtra(EXTRA_DATA_INTENT);
            captureService.initMediaProjection(resultCode, data);
        }

        // Create and show bubble
        if (!isBubbleAttached) {
            setupBubbleView();
        }

        return START_STICKY;
    }

    private void startForegroundWithNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.floating_service_notification_title))
                .setContentText(getString(R.string.floating_service_notification_text))
                .setSmallIcon(R.drawable.ic_bubble_gemini)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } else {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            }
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "GabaritoAI Assistente Flutuante",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Mantém a bolha flutuante ativa sobre outros apps");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void setupBubbleView() {
        bubbleView = LayoutInflater.from(this).inflate(R.layout.layout_floating_bubble, null);

        int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        bubbleParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = 20;
        bubbleParams.y = 300;

        bubbleView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private long touchStartTime;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = bubbleParams.x;
                        initialY = bubbleParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        touchStartTime = System.currentTimeMillis();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        bubbleParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                        bubbleParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                        if (isBubbleAttached) {
                            windowManager.updateViewLayout(bubbleView, bubbleParams);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        long duration = System.currentTimeMillis() - touchStartTime;
                        float diffX = Math.abs(event.getRawX() - initialTouchX);
                        float diffY = Math.abs(event.getRawY() - initialTouchY);

                        if (duration < 300 && diffX < 15 && diffY < 15) {
                            // Single Tap Detected
                            onBubbleClicked();
                        } else {
                            // Snap to edge
                            snapBubbleToEdge();
                        }
                        return true;
                }
                return false;
            }
        });

        try {
            windowManager.addView(bubbleView, bubbleParams);
            isBubbleAttached = true;
        } catch (Exception e) {
            Log.e(TAG, "Falha ao adicionar bubbleView no WindowManager", e);
        }
    }

    private void snapBubbleToEdge() {
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(metrics);
        int screenWidth = metrics.widthPixels;

        if (bubbleParams.x + (bubbleView.getWidth() / 2) < screenWidth / 2) {
            bubbleParams.x = 10;
        } else {
            bubbleParams.x = screenWidth - bubbleView.getWidth() - 10;
        }

        if (isBubbleAttached) {
            windowManager.updateViewLayout(bubbleView, bubbleParams);
        }
    }

    private void onBubbleClicked() {
        if (isSolving) return;

        String apiKey = prefs.getApiKey();
        if (apiKey.isEmpty()) {
            Toast.makeText(this, "Por favor, configure sua chave do Gemini no app!", Toast.LENGTH_LONG).show();
            return;
        }

        triggerVibration();
        dismissResultCard();

        isSolving = true;
        setBubbleLoading(true);

        // Hide bubble temporarily so it doesn't get captured in screenshot
        bubbleView.setVisibility(View.INVISIBLE);

        // Take screen capture
        bubbleView.postDelayed(() -> {
            captureService.captureScreen(new ScreenCaptureService.CaptureCallback() {
                @Override
                public void onCaptured(Bitmap bitmap, String base64Jpeg) {
                    // Restore bubble visibility with spinner active
                    bubbleView.setVisibility(View.VISIBLE);

                    // Send to Gemini Vision
                    String model = prefs.getSelectedModel();
                    visionClient.solveQuestionFromScreenshot(apiKey, model, base64Jpeg, new SolverCallback() {
                        @Override
                        public void onSuccess(QuestionResult result) {
                            isSolving = false;
                            setBubbleLoading(false);
                            triggerVibration();
                            showResultCard(result);

                            // Optional auto-click via Accessibility
                            if (prefs.isAutoClickEnabled()) {
                                AutoClickAccessibilityService.clickOption(result.getCorrectLetter());
                            }
                        }

                        @Override
                        public void onError(String errorMessage) {
                            isSolving = false;
                            setBubbleLoading(false);
                            Toast.makeText(FloatingBubbleService.this, errorMessage, Toast.LENGTH_LONG).show();
                        }
                    });
                }

                @Override
                public void onError(String error) {
                    isSolving = false;
                    bubbleView.setVisibility(View.VISIBLE);
                    setBubbleLoading(false);
                    Toast.makeText(FloatingBubbleService.this, error, Toast.LENGTH_SHORT).show();
                }
            });
        }, 150);
    }

    private void setBubbleLoading(boolean loading) {
        if (bubbleView == null) return;
        ImageView icon = bubbleView.findViewById(R.id.bubble_icon);
        ProgressBar spinner = bubbleView.findViewById(R.id.bubble_loading);
        if (icon != null && spinner != null) {
            icon.setVisibility(loading ? View.GONE : View.VISIBLE);
            spinner.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
    }

    private void showResultCard(QuestionResult result) {
        dismissResultCard();

        resultCardView = LayoutInflater.from(this).inflate(R.layout.layout_result_card, null);

        int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        resultCardParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
        );

        resultCardParams.gravity = Gravity.BOTTOM;
        resultCardParams.y = 80;

        // Bind data
        TextView tvLetter = resultCardView.findViewById(R.id.tv_correct_letter);
        TextView tvReasoning = resultCardView.findViewById(R.id.tv_explanation_text);
        TextView tvModel = resultCardView.findViewById(R.id.tv_model_used);
        ImageButton btnClose = resultCardView.findViewById(R.id.btn_close_result);
        Button btnDismiss = resultCardView.findViewById(R.id.btn_dismiss_card);
        Button btnCopy = resultCardView.findViewById(R.id.btn_copy_explanation);

        tvLetter.setText(result.getCorrectLetter());
        tvReasoning.setText(result.getReasoning());
        if (result.getModelUsed() != null) {
            tvModel.setText("Resolvido com " + result.getModelUsed());
        }

        View.OnClickListener closeListener = v -> dismissResultCard();
        btnClose.setOnClickListener(closeListener);
        btnDismiss.setOnClickListener(closeListener);

        btnCopy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Gabarito", "Alternativa: " + result.getCorrectLetter() + "\n" + result.getReasoning());
            if (cm != null) {
                cm.setPrimaryClip(clip);
                Toast.makeText(FloatingBubbleService.this, "Copiado para a área de transferência!", Toast.LENGTH_SHORT).show();
            }
        });

        try {
            windowManager.addView(resultCardView, resultCardParams);
            isResultCardAttached = true;
        } catch (Exception e) {
            Log.e(TAG, "Falha ao exibir resultCardView", e);
        }
    }

    private void dismissResultCard() {
        if (isResultCardAttached && resultCardView != null) {
            try {
                windowManager.removeView(resultCardView);
            } catch (Exception ignored) {}
            isResultCardAttached = false;
            resultCardView = null;
        }
    }

    private void triggerVibration() {
        if (vibrator != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(50);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        dismissResultCard();
        if (isBubbleAttached && bubbleView != null) {
            try {
                windowManager.removeView(bubbleView);
            } catch (Exception ignored) {}
            isBubbleAttached = false;
        }
        if (captureService != null) {
            captureService.stop();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
