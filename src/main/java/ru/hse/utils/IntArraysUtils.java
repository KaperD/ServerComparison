package ru.hse.utils;

public class IntArraysUtils {
    public static void sort(int[] data) {
        for (int k = 0; k < data.length - 1; k++) {
            for (int i = 0; i < data.length - 1; i++) {
                if (data[i] > data[i + 1]) {
                    int t = data[i + 1];
                    data[i + 1] = data[i];
                    data[i] = t;
                }
            }
        }
    }
}
