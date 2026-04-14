package com.llmcr.utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public final class JsonBackupUtils {

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private JsonBackupUtils() {
    }

    public static void createJsonBackup(String filename) throws IOException {
        File file = new File(filename);

        // Create parent directories if they don't exist
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        // Initialize with empty array
        List<Object> emptyList = new ArrayList<>();
        objectMapper.writeValue(file, emptyList);
    }

    public static void appendJsonBackup(String filename, Object data) throws IOException {
        File file = new File(filename);

        // Create the file if it doesn't exist
        if (!file.exists()) {
            createJsonBackup(filename);
        }

        // Read existing data
        List<Object> existingData = objectMapper.readValue(file,
                objectMapper.getTypeFactory().constructCollectionType(List.class, Object.class));

        // Append new data
        existingData.add(data);

        // Write back to file
        objectMapper.writeValue(file, existingData);
    }
}