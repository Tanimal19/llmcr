package com.llmcr;

/**
 * A singleton class that holds the current processing state of the application,
 * and caches intermediate results during the process.
 */
public class ProcessObject {
    private static ProcessObject instance;

    private ProcessObject() {
        // Private constructor to prevent instantiation
    }

    public static synchronized ProcessObject getInstance() {
        if (instance == null) {
            instance = new ProcessObject();
        }
        return instance;
    }

}
