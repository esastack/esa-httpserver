# ESA HttpServer

![Build](https://github.com/esastack/esa-httpserver/workflows/Build/badge.svg?branch=main)
[![codecov](https://codecov.io/gh/esastack/esa-httpserver/branch/main/graph/badge.svg?token=C6JT3SKXX5)](https://codecov.io/gh/esastack/esa-httpserver)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.esastack/httpserver/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.esastack/httpserver/)
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

