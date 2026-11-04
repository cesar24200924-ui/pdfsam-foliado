package org.pdfsam.gui.components.content.log;
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

import jakarta.inject.Inject;
import javafx.application.Platform;
import org.pdfsam.injector.Auto;
import org.pdfsam.model.lifecycle.ShutdownEvent;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.pdfsam.core.context.ApplicationContext.app;
import static org.pdfsam.core.context.IntegerPersistentProperty.LOGVIEW_ROWS_NUMBER;
import static org.pdfsam.eventstudio.StaticStudio.eventStudio;

/**
 * @author Andrea Vacondio
 */
@Auto
public class LogMessagesController {

    //a little more rows than the ones in the LogListView
    private static final int LOG_ROWS_BUFFER = 10;
    private final LogsMessagesBuffer<LogMessage> messages = new LogsMessagesBuffer<>(
            app().persistentSettings().get(LOGVIEW_ROWS_NUMBER) + LOG_ROWS_BUFFER);
    private final LogListView view;

    @Inject
    public LogMessagesController(LogListView view) {
        this.view = view;
        eventStudio().add(MaxLogRowsChangedEvent.class,
                e -> messages.capacity(app().persistentSettings().get(LOGVIEW_ROWS_NUMBER) + LOG_ROWS_BUFFER));
        eventStudio().add(LogMessage.class, messages::add);
        var pump = new LogMessagesPump();
        view.sceneProperty().subscribe(scene -> {
            if (nonNull(scene)) {
                pump.start();
            } else {
                pump.stop();
            }
        });
        eventStudio().add(ShutdownEvent.class, e -> pump.shutdown());
    }

    /**
     * Periodically drains {@link LogMessagesController#messages} into {@link LogMessagesController#view}, adding
     * each batch in a single call so the JavaFX Application Thread sees one change event per tick instead of one
     * per log message.
     * <p>
     * Polling runs on a dedicated single-thread {@link ScheduledExecutorService}, which also satisfies the
     * buffer's single-reader-thread contract; each non-empty batch is then handed to the JavaFX Application
     * Thread via {@link Platform#runLater} since UI mutations must happen there. At most one batch is ever in
     * flight to the FX thread: if it hasn't consumed the previous one yet, a tick skips draining rather than
     * queuing more work, so a stalled FX thread leaves items in the bounded buffer instead of piling up
     * unbounded {@link Platform#runLater} tasks.
     * <p>
     * {@link #start()}, {@link #stop()} and {@link #shutdown()} are not synchronized, so they rely on always
     * being called from the JavaFX Application Thread, which is how {@link LogMessagesController} drives them:
     * {@link #start()}/{@link #stop()} toggle polling as the log view is shown or hidden, and {@link #shutdown()}
     * additionally terminates the executor thread on application shutdown.
     */
    private final class LogMessagesPump {

        private static final int MAX_BATCH_SIZE = 50;

        private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().daemon().name("log-messages-pump").factory());
        private final AtomicBoolean updatePending = new AtomicBoolean(false);
        private ScheduledFuture<?> task;

        private void pump() {
            if (updatePending.compareAndSet(false, true)) {
                var batch = messages.drain(MAX_BATCH_SIZE);
                if (batch.isEmpty()) {
                    updatePending.set(false);
                } else {
                    Platform.runLater(() -> {
                        view.addAll(batch);
                        updatePending.set(false);
                    });
                }
            }
        }

        void start() {
            if (isNull(task)) {
                task = scheduler.scheduleWithFixedDelay(this::pump, 0, 350, TimeUnit.MILLISECONDS);
            }
        }

        void stop() {
            if (nonNull(task)) {
                task.cancel(false);
                task = null;
            }
        }

        public void shutdown() {
            stop();
            scheduler.shutdownNow();
        }
    }
}
