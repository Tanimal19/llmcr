package com.example.llmcr.utils;

import java.util.ArrayList;
import java.util.List;

public class BatchUtils {

    private BatchUtils() {
    }

    public static <T> List<List<T>> batch(List<T> list, int batchSize) {
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            batches.add(list.subList(i, Math.min(i + batchSize, list.size())));
        }
        return batches;
    }
}
