package com.gabaritoai.mobile.capture;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScreenCaptureService {
    private static final String TAG = "ScreenCapture";

    public interface CaptureCallback {
        void onCaptured(Bitmap bitmap, String base64Jpeg);
        void onError(String error);
    }

    private final Context context;
    private MediaProjection mediaProjection;
    private ImageReader imageReader;
    private VirtualDisplay virtualDisplay;
    private final Handler mainHandler;
    private final ExecutorService backgroundExecutor;

    public ScreenCaptureService(Context context) {
        this.context = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.backgroundExecutor = Executors.newSingleThreadExecutor();
    }

    /**
     * Initializes MediaProjection and registers mandatory callback for Android 14+
     */
    public void initMediaProjection(int resultCode, Intent data) {
        try {
            MediaProjectionManager mgr = (MediaProjectionManager) context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            if (mgr != null && data != null) {
                this.mediaProjection = mgr.getMediaProjection(resultCode, data);
                if (this.mediaProjection != null) {
                    // MANDATORY IN ANDROID 14 (API 34): Register callback before createVirtualDisplay
                    this.mediaProjection.registerCallback(new MediaProjection.Callback() {
                        @Override
                        public void onStop() {
                            super.onStop();
                            Log.i(TAG, "MediaProjection finalizado pelo sistema");
                        }
                    }, mainHandler);
                    Log.i(TAG, "MediaProjection inicializado e registrado com sucesso!");
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Falha ao inicializar MediaProjection", t);
        }
    }

    public boolean isInitialized() {
        return mediaProjection != null;
    }

    /**
     * Captures current screen without freezing the UI, returns Bitmap + Base64 JPEG
     */
    public void captureScreen(final CaptureCallback callback) {
        if (mediaProjection == null) {
            postError(callback, "Permissão de captura de tela não foi concedida. Abra o app GabaritoAI e ative a bolha novamente.");
            return;
        }

        try {
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            DisplayMetrics metrics = new DisplayMetrics();
            if (wm != null && wm.getDefaultDisplay() != null) {
                wm.getDefaultDisplay().getRealMetrics(metrics);
            } else {
                metrics = context.getResources().getDisplayMetrics();
            }

            final int width = metrics.widthPixels > 0 ? metrics.widthPixels : 1080;
            final int height = metrics.heightPixels > 0 ? metrics.heightPixels : 1920;
            final int density = metrics.densityDpi > 0 ? metrics.densityDpi : 420;

            // Release any lingering display/reader
            releaseVirtualDisplay();

            // Create ImageReader
            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);

            final AtomicBoolean captured = new AtomicBoolean(false);

            // Create VirtualDisplay
            virtualDisplay = mediaProjection.createVirtualDisplay(
                    "GabaritoAIScreenCapture",
                    width,
                    height,
                    density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(),
                    null,
                    mainHandler
            );

            // Listener for immediate frame capture when rendered
            imageReader.setOnImageAvailableListener(reader -> {
                if (captured.compareAndSet(false, true)) {
                    processAvailableImage(reader, width, height, callback);
                }
            }, mainHandler);

            // Safety timeout: if onImageAvailable does not trigger in 500ms, pull latest frame directly
            mainHandler.postDelayed(() -> {
                if (captured.compareAndSet(false, true)) {
                    if (imageReader != null) {
                        processAvailableImage(imageReader, width, height, callback);
                    } else {
                        postError(callback, "Tempo limite excedido na captura de tela.");
                    }
                }
            }, 500);

        } catch (Throwable t) {
            Log.e(TAG, "Erro crítico ao criar VirtualDisplay", t);
            releaseVirtualDisplay();
            postError(callback, "Falha na captura: " + t.getMessage());
        }
    }

    private void processAvailableImage(ImageReader reader, int width, int height, CaptureCallback callback) {
        backgroundExecutor.execute(() -> {
            Image image = null;
            try {
                image = reader.acquireLatestImage();
                if (image == null) {
                    image = reader.acquireNextImage();
                }
                if (image == null) {
                    try {
                        Thread.sleep(150);
                        image = reader.acquireLatestImage();
                        if (image == null) image = reader.acquireNextImage();
                    } catch (InterruptedException ignored) {}
                }
                if (image == null) {
                    postError(callback, "Não foi possível obter os pixels da tela.");
                    return;
                }

                Image.Plane[] planes = image.getPlanes();
                if (planes == null || planes.length == 0) {
                    postError(callback, "Formato de imagem inválido.");
                    return;
                }

                ByteBuffer buffer = planes[0].getBuffer();
                int pixelStride = planes[0].getPixelStride();
                int rowStride = planes[0].getRowStride();
                int rowPadding = rowStride - pixelStride * width;

                // Create bitmap with padding
                Bitmap fullBitmap = Bitmap.createBitmap(
                        width + rowPadding / pixelStride,
                        height,
                        Bitmap.Config.ARGB_8888
                );
                fullBitmap.copyPixelsFromBuffer(buffer);

                // Crop out row padding if necessary
                Bitmap finalBitmap = (rowPadding > 0)
                        ? Bitmap.createBitmap(fullBitmap, 0, 0, width, height)
                        : fullBitmap;

                // Compress to JPEG in background thread
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream);
                byte[] jpegBytes = outputStream.toByteArray();
                String base64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP);

                // Post success back to main thread
                mainHandler.post(() -> {
                    if (callback != null) callback.onCaptured(finalBitmap, base64);
                });

            } catch (Throwable e) {
                Log.e(TAG, "Erro ao processar imagem capturada", e);
                postError(callback, "Erro ao processar imagem: " + e.getMessage());
            } finally {
                if (image != null) {
                    try {
                        image.close();
                    } catch (Throwable ignored) {}
                }
                releaseVirtualDisplay();
            }
        });
    }

    private void releaseVirtualDisplay() {
        mainHandler.post(() -> {
            try {
                if (virtualDisplay != null) {
                    virtualDisplay.release();
                    virtualDisplay = null;
                }
                if (imageReader != null) {
                    imageReader.close();
                    imageReader = null;
                }
            } catch (Throwable ignored) {}
        });
    }

    private void postError(CaptureCallback callback, String error) {
        mainHandler.post(() -> {
            if (callback != null) callback.onError(error);
        });
    }

    public void stop() {
        releaseVirtualDisplay();
        if (mediaProjection != null) {
            try {
                mediaProjection.stop();
            } catch (Throwable ignored) {}
            mediaProjection = null;
        }
        try {
            backgroundExecutor.shutdown();
        } catch (Throwable ignored) {}
    }
}
