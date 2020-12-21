# ESA HttpServer

![Build](https://github.com/esastack/esa-httpserver/workflows/Build/badge.svg?branch=main)
[![codecov](https://codecov.io/gh/esastack/esa-httpserver/branch/main/graph/badge.svg?token=C6JT3SKXX5)](https://codecov.io/gh/esastack/esa-httpserver)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.esastack/httpserver/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.esastack/httpserver/)
[![GitHub license](https://img.shields.io/github/license/esastack/esa-httpserver)](https://github.com/esastack/esa-httpserver/blob/main/LICENSE)

ESA HttpServer is an asynchronous event-driven http server based on netty.

## Features

- Asynchronous request handing
- Http1/H2/H2cUpgrade
- Https
- HAProxy
- Epoll/NIO
- Chunked read/write
- Body aggregation
- Multipart
- Metrics
- more features...

## Maven Dependency

```xml
<dependency>
    <groupId>io.esastack</groupId>
    <artifactId>httpserver</artifactId>
    <version>${esa-httpserver.version}</version>
</dependency>
```

## Quick Start

```java
HttpServer.create()
        .handle(req -> {
            req.onData(buf -> {
                // handle http content 
            });
            req.onEnd(p -> {
                req.response()
                        .setStatus(200)
                        .end("Hello ESA Http Server!".getBytes());
                return p.setSuccess(null);
            });
        })
        .listen(8080);
```

## Performance

### Test cases

- We built a echo server by ESA HttpServer and used a http client to do the requests for RPS testing with different payload(16B, 128B, 512B, 1KB, 4KB, 10KB)
- Also we used  netty to build a same echo server which is same with above for RPS testing (use the `HttpServerCodec`, `HttpObjectAggregator` handlers directly).

### Hardware Used

We used the following software for the testing:

- wrk4.1.0

- |        | OS                       | CPU  | Mem(G) |
  | ------ | ------------------------ | ---- | ------ |
  | server | centos:6.9-1.2.5(docker) | 4    | 8      |
  | client | centos:7.6-1.3.0(docker) | 16   | 3      |
  

### JVM Options

```
-server -Xms3072m -Xmx3072m -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=256m -XX:+UseConcMarkSweepGC -XX:+UseCMSInitiatingOccupancyOnly -XX:CMSInitiatingOccupancyFraction=70 -XX:+PrintTenuringDistribution -XX:+PrintGCDateStamps -XX:+PrintGCDetails -Xloggc:logs/gc-${appName}-%t.log -XX:NumberOfGCLogFiles=20 -XX:GCLogFileSize=480M -XX:+UseGCLogFileRotation -XX:HeapDumpPath=.
```

### Server Options

- we set the value of IO threads to 8.

### RPS

|                | 16B       | 128B      | 512B      | 1KB       | 4KB      | 10KB     |
| -------------- | --------- | --------- | --------- | --------- | -------- | -------- |
| Netty Origin   | 133272.34 | 132818.53 | 132390.78 | 127366.28 | 85408.7  | 49798.84 |
| ESA HttpServer | 142063.99 | 139608.23 | 139646.04 | 140159.5  | 92767.53 | 53534.21 |



