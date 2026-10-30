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
    private final Handler handler;

    public ScreenCaptureService(Context context) {
        this.context = context.getApplicationContext();
        this.handler = new Handler(Looper.getMainLooper());
    }

    public void initMediaProjection(int resultCode, Intent data) {
        MediaProjectionManager mgr = (MediaProjectionManager) context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (mgr != null && data != null) {
            this.mediaProjection = mgr.getMediaProjection(resultCode, data);
        }
    }

    public boolean isInitialized() {
        return mediaProjection != null;
    }

    /**
     * Captures current screen and returns Bitmap + Base64 JPEG
     */
    public void captureScreen(final CaptureCallback callback) {
        if (mediaProjection == null) {
            if (callback != null) callback.onError("Permissão de captura de tela não foi concedida.");
            return;
        }

        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(metrics);

        final int width = metrics.widthPixels;
        final int height = metrics.heightPixels;
        final int density = metrics.densityDpi;

        // ImageReader for RGB format
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);

        virtualDisplay = mediaProjection.createVirtualDisplay(
                "GabaritoAIScreenCapture",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null,
                handler
        );

        // Allow 300ms for virtual display to capture a clean frame
        handler.postDelayed(() -> {
            Image image = null;
            try {
                image = imageReader.acquireLatestImage();
                if (image != null) {
                    Image.Plane[] planes = image.getPlanes();
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

                    // Crop out the padding if needed
                    Bitmap finalBitmap = Bitmap.createBitmap(fullBitmap, 0, 0, width, height);

                    // Compress to JPEG (quality 85 for crisp text readability and small payload size)
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    finalBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream);
                    byte[] jpegBytes = outputStream.toByteArray();
                    String base64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP);

                    if (callback != null) {
                        callback.onCaptured(finalBitmap, base64);
                    }
                } else {
                    if (callback != null) callback.onError("Não foi possível adquirir a imagem da tela.");
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao capturar tela", e);
                if (callback != null) callback.onError("Erro na captura de tela: " + e.getMessage());
            } finally {
                if (image != null) image.close();
                releaseVirtualDisplay();
            }
        }, 350);
    }

    private void releaseVirtualDisplay() {
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
    }

    public void stop() {
        releaseVirtualDisplay();
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
    }
}
