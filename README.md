# ESA HttpServer

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

