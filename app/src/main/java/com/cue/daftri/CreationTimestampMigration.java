package com.cue.daftri;

import org.json.JSONObject;

/** Keeps missing legacy creation timestamps unknown instead of inventing a migration-time date. */
final class CreationTimestampMigration {
    private CreationTimestampMigration() {
    }

    static Long readCreatedAt(Object record, String key) {
        if (!(record instanceof JSONObject) || key == null) return null;
        JSONObject object = (JSONObject) record;
        if (!object.has(key) || object.isNull(key)) return null;

        Object rawValue = object.opt(key);
        if (!(rawValue instanceof Number)) return null;
        long timestampMillis = ((Number) rawValue).longValue();
        return timestampMillis > 0L ? timestampMillis : null;
    }
}
