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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * @author Andrea Vacondio
 */
public class LogsMessagesBufferTest {

    @Test
    public void constructorRejectsNonPositiveCapacity() {
        assertThatIllegalArgumentException().isThrownBy(() -> new LogsMessagesBuffer<String>(0));
    }

    @Test
    public void pollReturnsNullWhenEmpty() {
        var victim = new LogsMessagesBuffer<String>(2);
        assertThat(victim.poll()).isNull();
    }

    @Test
    public void addAndPollPreserveFifoOrder() {
        var victim = new LogsMessagesBuffer<String>(3);
        victim.add("first");
        victim.add("second");
        assertThat(victim.poll()).isEqualTo("first");
        assertThat(victim.poll()).isEqualTo("second");
        assertThat(victim.poll()).isNull();
    }

    @Test
    public void addDiscardsOldestItemWhenCapacityIsExceeded() {
        var victim = new LogsMessagesBuffer<String>(2);
        victim.add("first");
        victim.add("second");
        victim.add("third");
        assertThat(victim.size()).isEqualTo(2);
        assertThat(victim.poll()).isEqualTo("second");
        assertThat(victim.poll()).isEqualTo("third");
    }

    @Test
    public void sizeReflectsBufferedItems() {
        var victim = new LogsMessagesBuffer<String>(5);
        victim.add("first");
        victim.add("second");
        assertThat(victim.size()).isEqualTo(2);
        victim.poll();
        assertThat(victim.size()).isEqualTo(1);
    }

    @Test
    public void capacityRejectsNonPositiveValue() {
        var victim = new LogsMessagesBuffer<String>(2);
        assertThatIllegalArgumentException().isThrownBy(() -> victim.capacity(0));
    }

    @Test
    public void shrinkingCapacityTrimsOldestItemsImmediately() {
        var victim = new LogsMessagesBuffer<String>(3);
        victim.add("first");
        victim.add("second");
        victim.add("third");
        victim.capacity(1);
        assertThat(victim.capacity()).isEqualTo(1);
        assertThat(victim.size()).isEqualTo(1);
        assertThat(victim.poll()).isEqualTo("third");
    }

    @Test
    public void growingCapacityDoesNotDiscardExistingItems() {
        var victim = new LogsMessagesBuffer<String>(2);
        victim.add("first");
        victim.add("second");
        victim.capacity(5);
        victim.add("third");
        assertThat(victim.size()).isEqualTo(3);
    }

    @Test
    public void drainReturnsUpToMaxOldestItemsInFifoOrder() {
        var victim = new LogsMessagesBuffer<String>(5);
        victim.add("first");
        victim.add("second");
        victim.add("third");
        assertThat(victim.drain(2)).containsExactly("first", "second");
        assertThat(victim.size()).isEqualTo(1);
    }

    @Test
    public void drainReturnsFewerItemsWhenBufferHasLessThanMax() {
        var victim = new LogsMessagesBuffer<String>(5);
        victim.add("first");
        assertThat(victim.drain(10)).containsExactly("first");
        assertThat(victim.size()).isZero();
    }

    @Test
    public void drainReturnsEmptyListWhenBufferIsEmpty() {
        var victim = new LogsMessagesBuffer<String>(5);
        assertThat(victim.drain(3)).isEmpty();
    }
}
