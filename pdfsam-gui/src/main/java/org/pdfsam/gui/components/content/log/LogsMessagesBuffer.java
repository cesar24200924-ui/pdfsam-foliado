/*
 * This file is part of the PDF Split And Merge source code
 * Created on 04/08/26
 * Copyright 2026 by Sober Lemur S.r.l. (info@soberlemur.com).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.pdfsam.gui.components.content.log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.sejda.commons.util.RequireUtils.requireArg;

/**
 * Bounded FIFO buffer decoupling a single log-writer thread from a single log-reader thread. The reader is only
 * active while the log view is visible, so the writer can keep adding with nobody draining the buffer; once
 * {@link #capacity(int)} is exceeded, the oldest items are discarded to bound memory usage.
 * <p>
 * {@link #add(Object)} must only be called by the writer thread and {@link #poll()}/{@link #drain(int)} only by
 * the reader thread; {@link #capacity(int)} may be called from any thread.
 *
 * @author Andrea Vacondio
 */
public class LogsMessagesBuffer<E> {

    private final ConcurrentLinkedQueue<E> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger currentSize = new AtomicInteger(0);
    // Written by capacity(int), possibly from a thread other than the writer thread reading it in trim()
    private volatile int capacity;

    public LogsMessagesBuffer(int capacity) {
        requireArg(capacity > 0, "Capacity must be > 0");
        this.capacity = capacity;
    }

    /**
     * Adds an item to the buffer, trimming the oldest items if the capacity is exceeded.
     */
    public void add(E item) {
        queue.add(item);
        currentSize.incrementAndGet();
        trim();
    }

    /**
     * Retrieves and removes the oldest item in the buffer.
     *
     * @return the oldest item, or {@code null} if the buffer is empty
     */
    public E poll() {
        E item = queue.poll();
        if (item != null) {
            currentSize.decrementAndGet();
        }
        return item;
    }

    /**
     * Removes and returns up to {@code max} of the oldest items in the buffer, in FIFO order, so the reader can
     * consume a batch in one call instead of looping over {@link #poll()}.
     *
     * @return the removed items, possibly empty, never more than {@code max}
     */
    public List<E> drain(int max) {
        List<E> drained = new ArrayList<>();
        for (int i = 0; i < max; i++) {
            E item = poll();
            if (item == null) {
                break;
            }
            drained.add(item);
        }
        return drained;
    }

    /**
     * Updates the buffer capacity, immediately trimming the oldest items if the new capacity is lower than the
     * current size.
     */
    public void capacity(int newCapacity) {
        requireArg(newCapacity > 0, "Capacity must be > 0");
        this.capacity = newCapacity;
        trim();
    }

    /**
     * Discards the oldest items until the size is back within {@link #capacity}, re-reading it on every
     * iteration so a concurrent {@link #capacity(int)} call is honoured immediately.
     */
    private void trim() {
        while (currentSize.get() > capacity) {
            if (queue.poll() != null) {
                currentSize.decrementAndGet();
            } else {
                break;
            }
        }
    }

    public int capacity() {
        return this.capacity;
    }

    public int size() {
        return currentSize.get();
    }
}
