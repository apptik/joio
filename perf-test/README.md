Okio Benchmarks
------------

This module contains microbenchmarks that can be used to measure various aspects of performance for Okio buffers. Okio benchmarks are written using JMH (version 1.4.1 at this time) and require Java 7.

Running Locally
-------------

To run benchmarks locally, first build and package the project modules:

```
$ gradle clean shadowJar
```

This should create a `perf-test-all.jar` file in the `perf-test/build/libs` directory, which is a typical JMH benchmark JAR:

```
$ java -jar perf-test/build/libs/perf-test-all.jar -l
Benchmarks: 
io.apptik.joio.perf.BufferPerfBench.cold
io.apptik.joio.perf.BufferPerfBench.threads16hot
io.apptik.joio.perf.BufferPerfBench.threads1hot
io.apptik.joio.perf.BufferPerfBench.threads2hot
io.apptik.joio.perf.BufferPerfBench.threads32hot
io.apptik.joio.perf.BufferPerfBench.threads4hot
io.apptik.joio.perf.BufferPerfBench.threads8hot
```

More help is available using the `-h` option. A typical run on Mac OS X looks like:

```
$ /usr/libexec/java_home -v 1.7 --exec java -jar perf-test/build/libs/perf-test-all.jar \
"cold" -prof gc,hs_rt,stack -r 60 -t 4 \
-jvmArgsPrepend "-Xms1G -Xmx1G -XX:+HeapDumpOnOutOfMemoryError"
```

This executes the "cold" buffer usage benchmark, using the default number of measurement and warm-up iterations, forks, and threads; it adjusts the thread count to 4, iteration time to 60 seconds, fixes the heap size at 1GB and profiles the benchmark using JMH's GC, Hotspot runtime and stack sampling profilers.

