package com.example.llmcr.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class LogUtils {

    private final ObjectMapper objectMapper;
    private static LogUtils instance = null;

    private LogUtils() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    public static LogUtils getInstance() {
        if (instance == null) {
            instance = new LogUtils();
        }
        return instance;
    }

    /**
     * Creates a new JSON backup file with an empty array
     * 
     * @param filename the path to the backup file
     * @throws IOException if file creation fails
     */
    public void createJsonBackup(String filename) throws IOException {
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

    /**
     * Appends data to an existing JSON backup file
     * 
     * @param filename the path to the backup file
     * @param data     the data object to append
     * @throws IOException if file operations fail
     */
    public void appendJsonBackup(String filename, Object data) throws IOException {
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
