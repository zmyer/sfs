/*
 * Copyright 2016 The Simple File Server Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sfs.io;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.logging.Logger;
import org.sfs.rx.ObservableFuture;
import org.sfs.rx.RxHelper;
import rx.Observable;
import rx.Subscriber;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.google.common.base.Preconditions.checkState;
import static io.vertx.core.logging.LoggerFactory.getLogger;

public class MultiEndableWriteStream implements BufferEndableWriteStream {

    private static final Logger LOGGER = getLogger(MultiEndableWriteStream.class);
    private final List<BufferEndableWriteStream> delegateWriteStreams;
    private final List<ObservableFuture<Void>> drainHandlers;
    private final List<ObservableFuture<Void>> endHandlers;
    private final int size;
    private Handler<Void> delegateEndHandler;
    private Handler<Void> delegateDrainHandler;
    private Handler<Throwable> delegateErrorHandler;
    private boolean ended = false;

    public MultiEndableWriteStream(List<BufferEndableWriteStream> writeStreams) {
        this.size = writeStreams.size();
        this.drainHandlers = new ArrayList<>(size);
        this.endHandlers = new ArrayList<>(size);
        this.delegateWriteStreams = writeStreams;
    }

    public MultiEndableWriteStream(BufferEndableWriteStream[] writeStreams) {
        this.delegateWriteStreams = new ArrayList<>(writeStreams.length);
        Collections.addAll(this.delegateWriteStreams, writeStreams);
        this.drainHandlers = new ArrayList<>(writeStreams.length);
        this.endHandlers = new ArrayList<>(writeStreams.length);
        this.size = delegateWriteStreams.size();
    }


    public MultiEndableWriteStream(BufferEndableWriteStream writeStream, BufferEndableWriteStream... writeStreams) {
        this.delegateWriteStreams = new ArrayList<>(1 + writeStreams.length);
        this.drainHandlers = new ArrayList<>(1 + writeStreams.length);
        this.endHandlers = new ArrayList<>(1 + writeStreams.length);
        this.delegateWriteStreams.add(writeStream);
        Collections.addAll(this.delegateWriteStreams, writeStreams);
        this.size = this.delegateWriteStreams.size();
    }

    @Override
    public MultiEndableWriteStream write(Buffer data) {
        checkState(!ended, "Already ended");
        for (BufferEndableWriteStream writeStream : delegateWriteStreams) {
            writeStream.write(data);
        }
        return this;
    }

    @Override
    public MultiEndableWriteStream setWriteQueueMaxSize(int maxSize) {
        for (BufferEndableWriteStream writeStream : delegateWriteStreams) {
            writeStream.setWriteQueueMaxSize(maxSize);
        }
        return this;
    }

    @Override
    public boolean writeQueueFull() {
        for (BufferEndableWriteStream writeStream : delegateWriteStreams) {
            if (writeStream.writeQueueFull()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public MultiEndableWriteStream drainHandler(Handler<Void> handler) {
        this.delegateDrainHandler = handler;
        drainHandlers.clear();
        for (int i = 0; i < size; i++) {
            BufferEndableWriteStream writeStream = delegateWriteStreams.get(i);
            if (writeStream.writeQueueFull()) {
                ObservableFuture<Void> h = RxHelper.observableFuture();
                drainHandlers.add(h);
                writeStream.drainHandler(h::complete);
            }
        }
        handleDrain();
        return this;
    }

    @Override
    public MultiEndableWriteStream exceptionHandler(Handler<Throwable> handler) {
        this.delegateErrorHandler = handler;
        for (BufferEndableWriteStream writeStream : delegateWriteStreams) {
            writeStream.exceptionHandler(this::handleError);
        }
        return this;
    }

    @Override
    public MultiEndableWriteStream endHandler(Handler<Void> handler) {
        this.delegateEndHandler = handler;
        handleEnd();
        return this;
    }

    @Override
    public void end(Buffer buffer) {
        checkState(!ended, "Already ended");
        ended = true;
        endHandlers.clear();
        for (int i = 0; i < size; i++) {
            BufferEndableWriteStream writeStream = delegateWriteStreams.get(i);
            ObservableFuture<Void> h = RxHelper.observableFuture();
            endHandlers.add(h);
            writeStream.endHandler(h::complete);
            writeStream.end(buffer);
        }
        handleEnd();
    }

    @Override
    public void end() {
        checkState(!ended, "Already ended");
        ended = true;
        endHandlers.clear();
        for (int i = 0; i < size; i++) {
            BufferEndableWriteStream writeStream = delegateWriteStreams.get(i);
            ObservableFuture<Void> h = RxHelper.observableFuture();
            endHandlers.add(h);
            writeStream.endHandler(h::complete);
            writeStream.end();
        }
        handleEnd();
    }

    protected void handleDrain() {
        if (delegateDrainHandler != null) {
            Handler<Void> handler = delegateDrainHandler;
            delegateDrainHandler = null;
            Observable.mergeDelayError(drainHandlers)
                    .count()
                    .subscribe(new Subscriber<Integer>() {
                        @Override
                        public void onCompleted() {
                            handler.handle(null);
                        }

                        @Override
                        public void onError(Throwable e) {
                            handleError(e);
                        }

                        @Override
                        public void onNext(Integer count) {
                        }
                    });
        }
    }

    protected void handleEnd() {
        Handler<Void> handler = delegateEndHandler;
        if (ended && handler != null) {
            delegateEndHandler = null;
            Observable.mergeDelayError(endHandlers)
                    .count()
                    .subscribe(new Subscriber<Integer>() {
                        @Override
                        public void onCompleted() {
                            handler.handle(null);
                        }

                        @Override
                        public void onError(Throwable e) {
                            handleError(e);
                        }

                        @Override
                        public void onNext(Integer count) {
                        }
                    });
        }
    }

    protected void handleError(Throwable e) {
        if (delegateErrorHandler != null) {
            Handler<Throwable> handler = delegateErrorHandler;
            delegateErrorHandler = null;
            handler.handle(e);
        } else {
            LOGGER.error("Unhandled Exception", e);
        }
    }
}
