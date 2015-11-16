/*
 * Copyright (C) 2014 Square, Inc. and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apptik.joio.perf;


import okio.BufferedSource;
import okio.Okio;
import org.openjdk.jmh.annotations.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

@Fork(1)
@Warmup(iterations = 10, time = 10)
@Measurement(iterations = 10, time = 10)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)

public class NioHeapBufferPerfBench {

    public static final File OriginPath =
            new File(System.getProperty("joio.bench.origin.path", "/dev/urandom"));

  /* Test Workload
   *
   * Each benchmark thread maintains three buffers; a receive buffer, a process buffer
   * and a send buffer. At every operation:
   *
   *   - We fill up the receive buffer using the origin, write the request to the process
   *     buffer, and consume the process buffer.
   *   - We fill up the process buffer using the origin, write the response to the send
   *     buffer, and consume the send buffer.
   *
   * We use an "origin" source that serves as a preexisting sequence of bytes we can read
   * from the file system. The request and response bytes are initialized in the beginning
   * and reused throughout the benchmark in order to eliminate GC effects.
   *
   * Typically, we simulate the usage of small reads and large writes. Requests and
   * responses are satisfied with precomputed buffers to eliminate GC effects on
   * results.
   *
   * There are two types of benchmark tests; hot tests are "pedal to the metal" and
   * use all CPU they can take. These are useful to magnify performance effects of
   * changes but are not realistic use cases that should drive optimization efforts.
   * Cold tests introduce think time between the receiving of the request and sending
   * of the response. They are more useful as a reasonably realistic workload where
   * buffers can be read from and written to during request/response handling but
   * may hide subtle effects of most changes on performance. Prefer to look at the cold
   * benchmarks first to decide if a bottleneck is worth pursuing, then use the hot
   * benchmarks to fine tune optimization efforts.
   *
   * Benchmark threads do not explicitly communicate between each other (except to sync
   * iterations as needed by JMH).
   *
   * We simulate think time for each benchmark thread by parking the thread for a
   * configurable number of microseconds (1000 by default).
   */


    @Benchmark
    @Threads(1)
    public void nioHeap_threads1hot(HotBuffers buffers) throws IOException {
        readWriteRecycle(buffers);
    }

    @Benchmark
    @Threads(2)
    public void nioHeap_threads2hot(HotBuffers buffers) throws IOException {
        readWriteRecycle(buffers);
    }

    @Benchmark
    @Threads(4)
    public void nioHeap_threads4hot(HotBuffers buffers) throws IOException {
        readWriteRecycle(buffers);
    }

    @Benchmark
    @Threads(8)
    public void nioHeap_threads8hot(HotBuffers buffers) throws IOException {
        readWriteRecycle(buffers);
    }

    @Benchmark
    @Threads(16)
    public void nioHeap_threads16hot(HotBuffers buffers) throws IOException {
        readWriteRecycle(buffers);
    }

    @Benchmark
    @Threads(32)
    public void nioHeap_threads32hot(HotBuffers buffers) throws IOException {
        readWriteRecycle(buffers);
    }

    @Benchmark
    @GroupThreads(3)
    @Group("cold")
    public void nioHeap_thinkReadCold(HotBuffers buffers) throws IOException {
        consume(buffers.receive(requestBytes));
    }

    @Benchmark
    @GroupThreads(3)
    @Group("cold")
    public void nioHeap_thinkWriteCold(ColdBuffers buffers) throws IOException {
        consume(buffers.transmit(responseBytes));
    }

    private void readWriteRecycle(HotBuffers buffers) throws IOException {
        consume(buffers.receive(requestBytes));
        consume(buffers.transmit(responseBytes));
    }

    private void consume(ByteBuffer buffer) {
        buffer.flip();
        int n = buffer.remaining();
        for (int i = 0; i < n; ++i) {
            buffer.get();
        }
        buffer.rewind();
    }

    @Param({"1000"
            //, "2000"
    })
    int maxThinkMicros;

    @Param({"1024"
            //        , "2048"
    })
    int maxReadBytes;

    @Param({"1024"
            //        , "2048"
    })
    int maxWriteBytes;

    @Param({"2048"
            ,
            "4096"
            , "8192"
            , "16384"
            , "32768"
    })
    int requestSize;

    @Param({"1"})
    int responseFactor;

    byte[] requestBytes;

    byte[] responseBytes;

    @Setup(Level.Trial)
    public void storeRequestResponseData() throws IOException {
        checkOrigin(OriginPath);

        requestBytes = storeSourceData(new byte[requestSize]);
        responseBytes = storeSourceData(new byte[requestSize * responseFactor]);
    }

    private byte[] storeSourceData(byte[] dest) throws IOException {
        requireNonNull(dest, "dest == null");
        try (BufferedSource source = Okio.buffer(Okio.source(OriginPath))) {
            source.readFully(dest);
        }
        return dest;
    }

    private void checkOrigin(File path) throws IOException {
        requireNonNull(path, "path == null");

        if (!path.canRead()) {
            throw new IllegalArgumentException("can not access: " + path);
        }

        try (InputStream in = new FileInputStream(path)) {
            int available = in.read();
            if (available < 0) {
                throw new IllegalArgumentException("can not read: " + path);
            }
        }
    }

  /*
   * The state class hierarchy is larger than it needs to be due to a JMH
   * issue where states inheriting setup methods depending on another state
   * do not get initialized correctly from benchmark methods making use
   * of groups. To work around, we leave the common setup and teardown code
   * in superclasses and move the setup method depending on the bench state
   * to subclasses. Without the workaround, it would have been enough for
   * `ColdBuffers` to inherit from `HotBuffers`.
   */

    @State(Scope.Thread)
    public static class ColdBuffers extends BufferSetup {

        @Setup(Level.Trial)
        public void setupBench(NioHeapBufferPerfBench bench) {
            super.setupBench(bench);
        }

        @Setup(Level.Invocation)
        public void lag() throws InterruptedException {
            TimeUnit.MICROSECONDS.sleep(bench.maxThinkMicros);
        }

    }

    @State(Scope.Thread)
    public static class HotBuffers extends BufferSetup {

        @Setup(Level.Trial)
        public void setupBench(NioHeapBufferPerfBench bench) {
            super.setupBench(bench);
        }
    }

    @State(Scope.Thread)
    public static abstract class BufferSetup extends BufferState {
        // NioBufferPerfBench bench;

        public ByteBuffer receive(byte[] bytes) throws IOException {
            return super.receive(bytes, bench.maxReadBytes);
        }

        public ByteBuffer transmit(byte[] bytes) throws IOException {
            return super.transmit(bytes, bench.maxWriteBytes);
        }

        @TearDown
        public void dispose() throws IOException {
            releaseBuffers();
        }

    }


    public static class BufferState {
        NioHeapBufferPerfBench bench;

        public void setupBench(NioHeapBufferPerfBench bench) {
            this.bench = bench;
            received = ByteBuffer.allocate(bench.requestSize);
            sent = ByteBuffer.allocate(bench.requestSize);
            process = ByteBuffer.allocate(bench.requestSize);
        }

        @SuppressWarnings("resource")
        ByteBuffer received;
        @SuppressWarnings("resource")
        ByteBuffer sent;
        @SuppressWarnings("resource")
        ByteBuffer process;

        public void releaseBuffers() throws IOException {
            received.clear();
            sent.clear();
            process.clear();
        }

        /**
         * Fills up the receive buffer, hands off to process buffer and returns it for consuming.
         * Expects receive and process buffers to be empty. Leaves the receive buffer empty and
         * process buffer full.
         */
        protected ByteBuffer receive(byte[] bytes, int maxChunkSize) throws IOException {
            // writeChunked(received, bytes, maxChunkSize);
            //received.wrap(bytes);
            System.arraycopy(bytes, 0, received.array(), 0, bytes.length);
            received.limit(bytes.length).position(0);

            //received.flip();
            process.put(received);
            received.rewind();
            return process;
        }

        /**
         * Fills up the process buffer, hands off to send buffer and returns it for consuming.
         * Expects process and sent buffers to be empty. Leaves the process buffer empty and
         * sent buffer full.
         */
        protected ByteBuffer transmit(byte[] bytes, int maxChunkSize) throws IOException {
            //writeChunked(process, bytes, maxChunkSize);
            //process.wrap(bytes);
            System.arraycopy(bytes, 0, process.array(), 0, bytes.length);
            process.limit(bytes.length).position(0);

            //process.flip();
            sent.put(process);
            process.rewind();
            return sent;
        }

        private ByteBuffer writeChunked(ByteBuffer buffer, byte[] bytes, final int chunkSize) {
            int remaining = bytes.length;
            int offset = 0;
            while (remaining > 0) {
                int bytesToWrite = Math.min(remaining, chunkSize);
                buffer.put(bytes, offset, bytesToWrite);
                remaining -= bytesToWrite;
                offset += bytesToWrite;
            }
            return buffer;
        }

    }

}
