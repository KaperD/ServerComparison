package ru.hse.data;

public class IntArray {
    private final int id;
    private final int[] data;

    public IntArray(int id, int[] data) {
        this.id = id;
        this.data = data;
    }

    public int getId() {
        return id;
    }

    public int[] getData() {
        return data;
    }
}
