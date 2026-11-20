package com.cue.daftri;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Purges expired notebooks and their unreferenced images without requiring the app to be open. */
public final class TrashCleanupWorker extends Worker {
    public static final String UNIQUE_WORK_NAME = "daftri_trash_cleanup";
    public static final Object DATA_LOCK = new Object();
    static final int RETENTION_DAYS = 30;
    private static final String PREFS_NAME = "daftri";

    public TrashCleanupWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static long expiryTime(long deletedAtMillis) {
        return deletedAtMillis + TimeUnit.DAYS.toMillis(RETENTION_DAYS);
    }

    public static void schedule(Context context, long earliestExpiryMillis) {
        WorkManager manager = WorkManager.getInstance(context.getApplicationContext());
        if (earliestExpiryMillis <= 0L) {
            manager.cancelUniqueWork(UNIQUE_WORK_NAME);
            return;
        }
        long delay = Math.max(0L, earliestExpiryMillis - System.currentTimeMillis());
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(TrashCleanupWorker.class)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build();
        manager.enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request);
    }

    @NonNull
    @Override
    public Result doWork() {
        synchronized (DATA_LOCK) {
            return doWorkLocked();
        }
    }

    private Result doWorkLocked() {
        Context context = getApplicationContext();
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        JSONArray notebooks;
        JSONArray savedTrash;
        try {
            notebooks = new JSONArray(preferences.getString("notebooks", "[]"));
            savedTrash = new JSONArray(preferences.getString("trash", "[]"));
        } catch (JSONException error) {
            return Result.retry();
        }

        long now = System.currentTimeMillis();
        long earliestExpiry = Long.MAX_VALUE;
        JSONArray remainingTrash = new JSONArray();
        Set<String> referencedImages = new HashSet<>();
        Set<String> expiredTrashImages = new HashSet<>();

        for (int i = 0; i < notebooks.length(); i++) {
            JSONObject notebook = notebooks.optJSONObject(i);
            if (notebook == null) return Result.retry();
            collectNotebookImages(notebook, referencedImages);
        }
        for (int i = 0; i < savedTrash.length(); i++) {
            JSONObject entry = savedTrash.optJSONObject(i);
            if (entry == null) return Result.retry();
            JSONObject notebook = entry.optJSONObject("notebook");
            if (notebook == null) return Result.retry();
            long deletedAt = entry.optLong("deletedAt", 0L);
            long expiresAt = deletedAt > 0L ? expiryTime(deletedAt) : Long.MAX_VALUE;
            if (now >= expiresAt) {
                collectNotebookImages(notebook, expiredTrashImages);
                continue;
            }
            remainingTrash.put(entry);
            if (expiresAt < earliestExpiry) earliestExpiry = expiresAt;
            collectNotebookImages(notebook, referencedImages);
        }

        if (!preferences.edit().putString("trash", remainingTrash.toString()).commit()) {
            return Result.retry();
        }
        File imageDirectory = new File(context.getFilesDir(), "task_images");
        for (String imageName : expiredTrashImages) {
            if (referencedImages.contains(imageName)) continue;
            File imageFile = new File(imageDirectory, imageName);
            if (imageFile.isFile() && imageName.endsWith(".jpg")) imageFile.delete();
        }
        schedule(context, earliestExpiry == Long.MAX_VALUE ? 0L : earliestExpiry);
        return Result.success();
    }

    private static void collectNotebookImages(JSONObject notebook, Set<String> output) {
        addImageName(notebook.optString("titleImage", null), output);
        JSONArray tasks = notebook.optJSONArray("tasks");
        if (tasks == null) return;
        for (int i = 0; i < tasks.length(); i++) {
            JSONObject task = tasks.optJSONObject(i);
            // Legacy string-only tasks are valid and cannot reference image files.
            if (task == null) continue;
            JSONArray images = task.optJSONArray("images");
            if (images != null) {
                for (int j = 0; j < images.length(); j++) {
                    addImageName(images.optString(j, null), output);
                }
            } else {
                addImageName(task.optString("image", null), output);
            }
        }
    }

    private static void addImageName(String name, Set<String> output) {
        if (name != null && !name.isEmpty() && !name.contains("/") && !name.contains("\\")) {
            output.add(name);
        }
    }
}
