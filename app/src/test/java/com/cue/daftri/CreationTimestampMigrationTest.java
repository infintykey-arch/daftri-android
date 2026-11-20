package com.cue.daftri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class CreationTimestampMigrationTest {
    @Test
    public void legacyJsonWithoutCreationTimestampsStaysUnknown() throws JSONException {
        JSONObject legacyNotebook = new JSONObject(
                "{\"name\":\"دفتر قديم\",\"tasks\":[{\"text\":\"مهمة كائن\"},\"مهمة نصية\"]}");
        JSONArray legacyTasks = legacyNotebook.getJSONArray("tasks");

        assertNull(CreationTimestampMigration.readCreatedAt(legacyNotebook, "createdAt"));
        assertNull(CreationTimestampMigration.readCreatedAt(legacyTasks.getJSONObject(0), "createdAt"));
        assertNull(CreationTimestampMigration.readCreatedAt(legacyTasks.get(1), "createdAt"));
    }

    @Test
    public void onlyPositiveNumericCreationTimestampsAreTrusted() throws JSONException {
        JSONObject savedNotebook = new JSONObject("{\"createdAt\":1735689600000}");
        JSONObject nullTimestamp = new JSONObject("{\"createdAt\":null}");
        JSONObject stringTimestamp = new JSONObject("{\"createdAt\":\"1735689600000\"}");
        JSONObject nonPositiveTimestamp = new JSONObject("{\"createdAt\":0}");

        assertEquals(Long.valueOf(1735689600000L),
                CreationTimestampMigration.readCreatedAt(savedNotebook, "createdAt"));
        assertNull(CreationTimestampMigration.readCreatedAt(nullTimestamp, "createdAt"));
        assertNull(CreationTimestampMigration.readCreatedAt(stringTimestamp, "createdAt"));
        assertNull(CreationTimestampMigration.readCreatedAt(nonPositiveTimestamp, "createdAt"));
    }
}
