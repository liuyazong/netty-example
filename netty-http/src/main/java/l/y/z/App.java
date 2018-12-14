package l.y.z;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.stream.ChunkedWriteHandler;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.Charset;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * author: liuyazong datetime: 2018/10/10 下午12:07
 *
 * <p>
 */
@Slf4j
public class App {
    public static void main(String[] args) {
        int port = 9999;
        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(new NioEventLoopGroup(1), new NioEventLoopGroup(Runtime.getRuntime().availableProcessors() << 2))
                .option(ChannelOption.SO_BACKLOG, 1024)
                .channel(NioServerSocketChannel.class)
                .localAddress("localhost", port)
                .childHandler(
                        new ChannelInitializer<NioSocketChannel>() {
                            @Override
                            protected void initChannel(NioSocketChannel ch) {
                                ch.pipeline()
                                        // .addLast(HttpRequestDecoder.class.getName(), new HttpRequestDecoder())
                                        // .addLast(HttpResponseEncoder.class.getName(), new
                                        // HttpResponseEncoder())
                                        // new HttpServerCodec() 等效于 new HttpRequestDecoder() + new
                                        // HttpResponseEncoder()
                                        .addLast(HttpServerCodec.class.getName(), new HttpServerCodec())
                                        //.addLast(HttpServerKeepAliveHandler.class.getName(), new
                                        //  HttpServerKeepAliveHandler())
                                        .addLast(
                                                HttpObjectAggregator.class.getName(), new HttpObjectAggregator(65535))
                                        .addLast(ChunkedWriteHandler.class.getName(), new ChunkedWriteHandler())
                                        .addLast(
                                                ChannelInboundHandlerAdapter.class.getName(),
                                                new SimpleChannelInboundHandler<FullHttpRequest>() {
                                                    @Override
                                                    protected void channelRead0(
                                                            ChannelHandlerContext ctx, FullHttpRequest msg) {
                                                        QueryStringDecoder queryStringDecoder =
                                                                new QueryStringDecoder(msg.uri());
                                                        Map<String, List<String>> parameters =
                                                                queryStringDecoder.parameters();
                                                        String content = msg.content().toString(Charset.defaultCharset());
                                                        String path = queryStringDecoder.path();

                                                        String body =
                                                                String.format(
                                                                        "path: %s\nparameters: %s\ncontent: %s\ncurrent time: %s",
                                                                        path, parameters, content, new Date());
                                                        log.info("\n{}", body);
                                                        DefaultFullHttpResponse response =
                                                                new DefaultFullHttpResponse(
                                                                        HttpVersion.HTTP_1_1,
                                                                        HttpResponseStatus.OK,
                                                                        Unpooled.copiedBuffer(body, Charset.defaultCharset()));
                                                        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
                                                        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
                                                    }
                                                });
                            }
                        })
                .validate();
        try {
            bootstrap
                    .bind()
                    .sync()
                    .addListener(future -> {
                        log.info("Server started: {}", port);
                        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                            try {
                                if (bootstrap.config().childGroup().shutdownGracefully().await().isSuccess()) {
                                    log.info("childGroup shutdown");
                                }
                                if (bootstrap.config().group().shutdownGracefully().await().isSuccess()) {
                                    log.info("parentGroup shutdown");
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }));
                    })
                    .channel()
                    .closeFuture()
                    .addListener(future -> {
                        log.info("Server stopped");
                    })
                    .sync();
        } catch (Exception e) {
            log.error("Server error", e);
        } finally {

        }
    }
}
