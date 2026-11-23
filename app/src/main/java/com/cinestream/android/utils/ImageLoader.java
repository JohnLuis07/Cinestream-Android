package com.cinestream.android.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import com.cinestream.android.R;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Bounded, sampled image cache with recycled-view request cancellation.
 */
public final class ImageLoader {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ThreadPoolExecutor WORKERS = new ThreadPoolExecutor(3, 3, 20,
        TimeUnit.SECONDS, new LinkedBlockingQueue<>(80), new ThreadPoolExecutor.DiscardOldestPolicy());
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(
        (int) Math.min(Runtime.getRuntime().maxMemory() / 8, 24 * 1024 * 1024)) {
        @Override
        protected int sizeOf(String key, Bitmap bitmap) {
            return bitmap.getAllocationByteCount();
        }
    };

    private static final class Request {
        final WeakReference<ImageView> target;
        Future<?> future;

        Request(ImageView target) {
            this.target = new WeakReference<>(target);
        }
    }

    public static void clear(ImageView target) {
        Object tag = target.getTag(R.id.image_request);
        if (tag instanceof Request && ((Request) tag).future != null) {
            ((Request) tag).future.cancel(true);
        }
        target.setTag(R.id.image_request, null);
        target.setImageResource(R.drawable.poster_placeholder);
        WORKERS.purge();
    }

    public static void load(ImageView target, String address) {
        clear(target);
        if (address == null || !address.startsWith("https://")) {
            return;
        }
        Bitmap cached = CACHE.get(address);
        if (cached != null) {
            target.setImageBitmap(cached);
            return;
        }
        Request request = new Request(target);
        target.setTag(R.id.image_request, request);
        request.future = WORKERS.submit(() -> {
            Bitmap bitmap = download(address);
            if (bitmap == null || Thread.currentThread().isInterrupted()) {
                return;
            }
            CACHE.put(address, bitmap);
            MAIN.post(() -> {
                ImageView view = request.target.get();
                if (view != null && view.getTag(R.id.image_request) == request) {
                    view.setImageBitmap(bitmap);
                }
            });
        });
    }

    private static Bitmap download(String address) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            if (connection.getResponseCode() != 200) {
                return null;
            }
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted() || output.size() + count > 5 * 1024 * 1024) {
                        return null;
                    }
                    output.write(buffer, 0, count);
                }
                byte[] bytes = output.toByteArray();
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                if (options.outWidth <= 0 || options.outHeight <= 0) {
                    return null;
                }
                options.inSampleSize = 1;
                while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 1000) {
                    options.inSampleSize *= 2;
                }
                options.inJustDecodeBounds = false;
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
