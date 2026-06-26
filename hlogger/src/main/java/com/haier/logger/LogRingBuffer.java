package com.haier.logger;

import java.util.Arrays;

class LogRingBuffer {

    private final String[] buffer;
    private int head = 0;
    private int count = 0;

    LogRingBuffer(int capacity) {
        this.buffer = new String[capacity];
    }

    synchronized void add(String entry) {
        buffer[head] = entry;
        head = (head + 1) % buffer.length;
        if (count < buffer.length) count++;
    }

    synchronized String[] snapshot() {
        if (count == 0) return new String[0];

        String[] result = new String[count];
        int start = (head - count + buffer.length) % buffer.length;
        for (int i = 0; i < count; i++) {
            result[i] = buffer[(start + i) % buffer.length];
        }
        return result;
    }

    synchronized void clear() {
        Arrays.fill(buffer, null);
        head = 0;
        count = 0;
    }
}
