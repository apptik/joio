package io.apptik.joio.perf;


import okio.Buffer;
import okio.BufferedSource;
import okio.Okio;
import org.openjdk.jmh.annotations.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

@Fork(1)
@Warmup(iterations = 10, time = 10)
@Measurement(iterations = 10, time = 10)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class PrimitiveBench {
    public static final File OriginPath =
            new File(System.getProperty("joio.bench.origin.path", "/dev/urandom"));

    @Param({"1024"
            //        , "2048"
    })
    int maxReadBytes;

    @Param({"1024"
            //        , "2048"
    })
    int maxWriteBytes;

    byte[] writeBytes;

    byte[] readBytes;

    OutputStream devNull =  new OutputStream() {
        @Override
        public void write(int b) throws IOException {
            //gone
        }
    };



    @Benchmark
    @GroupThreads(1)
    @Group("primWrite")
    public void dbbNioWrite(DbbNioBuffers dbbNioBuffers) throws IOException {
        for (byte b:readBytes){
            dbbNioBuffers.reader.put(b);
        }
        dbbNioBuffers.reader.rewind();
    }

    @Benchmark
    @GroupThreads(1)
    @Group("primRead")
    public void dbbNioRead(DbbNioBuffers dbbNioBuffers) throws IOException {
        for (int i = 0; i < maxWriteBytes; i++)
            devNull.write(dbbNioBuffers.writer.get());
        dbbNioBuffers.writer.rewind();
    }

    @Benchmark
    @GroupThreads(1)
    @Group("primWrite")
    public void hbbNioWrite(HbbNioBuffers hbbNioBuffers) throws IOException {
        for (byte b:readBytes){
            hbbNioBuffers.reader.put(b);
        }
        hbbNioBuffers.reader.rewind();
    }

    @Benchmark
    @GroupThreads(1)
    @Group("primRead")
    public void hbbNioRead(HbbNioBuffers hbbNioBuffers) throws IOException {
        for (int i = 0; i < maxWriteBytes; i++)
            devNull.write(hbbNioBuffers.writer.get());
        hbbNioBuffers.writer.rewind();
    }


    @Benchmark
    @GroupThreads(1)
    @Group("primWrite")
    public void okioWrite(OkioBuffers okioBuffers) throws IOException {
        for (byte b:readBytes){
            okioBuffers.reader.writeByte(b);
        }
        okioBuffers.reader.skip(maxWriteBytes);
    }

    @Benchmark
    @GroupThreads(1)
    @Group("primRead")
    public void okioRead(OkioBuffers okioBuffers) throws IOException {
        Buffer bb = okioBuffers.writer.clone();
        for (int i = 0; i < maxWriteBytes; i++)
            devNull.write(bb.readByte());
    }

    @Benchmark
    @GroupThreads(1)
    @Group("primWrite")
    public void basIoWrite(BasIoBuffers basIoBuffers) throws IOException {
        for (byte b:readBytes){
            basIoBuffers.reader.write(b);
        }
        basIoBuffers.reader.reset();
    }

    @Benchmark
    @GroupThreads(1)
    @Group("primRead")
    public void basIoRead(BasIoBuffers basIoBuffers) throws IOException {
        for (int i = 0; i < maxWriteBytes; i++)
            devNull.write(basIoBuffers.writer.read());

        basIoBuffers.writer.reset();
    }

    @State(Scope.Thread)
    public static class OkioBuffers {
        PrimitiveBench bench;

        @SuppressWarnings("resource")
        final Buffer reader = new Buffer();
        @SuppressWarnings("resource")
        final Buffer writer = new Buffer();

        @Setup(Level.Trial)
        public void setupBench(PrimitiveBench bench) {
            this.bench = bench;
            writer.write(bench.writeBytes);
        }


        @TearDown
        public void dispose() throws IOException {
            reader.clear();
            writer.clear();
        }

    }
    @State(Scope.Thread)
    public static class HbbNioBuffers {
        PrimitiveBench bench;

        @SuppressWarnings("resource")
        ByteBuffer reader;
        @SuppressWarnings("resource")
        ByteBuffer writer;

        @Setup(Level.Trial)
        public void setupBench(PrimitiveBench bench) {
            this.bench = bench;
            reader = ByteBuffer.allocate(bench.maxReadBytes);
            writer = ByteBuffer.wrap(bench.writeBytes);
        }


        @TearDown
        public void dispose() throws IOException {
            reader.clear();
            writer.clear();
        }

    }
    @State(Scope.Thread)
    public static class DbbNioBuffers {
        PrimitiveBench bench;

        @SuppressWarnings("resource")
        ByteBuffer reader;
        @SuppressWarnings("resource")
        ByteBuffer writer;

        @Setup(Level.Trial)
        public void setupBench(PrimitiveBench bench) {
            this.bench = bench;
            reader = ByteBuffer.allocateDirect(bench.maxReadBytes);
            writer = ByteBuffer.allocateDirect(bench.maxReadBytes);
            writer.put(bench.writeBytes);
        }


        @TearDown
        public void dispose() throws IOException {
            reader.clear();
            writer.clear();
        }

    }

    @State(Scope.Thread)
    public static class BasIoBuffers {
        PrimitiveBench bench;

        @SuppressWarnings("resource")
        ByteArrayOutputStream reader;
        @SuppressWarnings("resource")
        ByteArrayInputStream writer;

        @Setup(Level.Trial)
        public void setupBench(PrimitiveBench bench) {
            this.bench = bench;
            reader = new ByteArrayOutputStream();
            writer = new ByteArrayInputStream(bench.writeBytes);
        }


        @TearDown
        public void dispose() throws IOException {
            reader.close();
            writer.close();
        }

    }

    @Setup(Level.Trial)
    public void storeRequestResponseData() throws IOException {
        checkOrigin(OriginPath);

        writeBytes = storeSourceData(new byte[maxWriteBytes]);
        readBytes = storeSourceData(new byte[maxReadBytes]);
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

}
