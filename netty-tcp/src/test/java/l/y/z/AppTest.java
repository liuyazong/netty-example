package l.y.z;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.pool.AbstractChannelPoolHandler;
import io.netty.channel.pool.AbstractChannelPoolMap;
import io.netty.channel.pool.ChannelPool;
import io.netty.channel.pool.FixedChannelPool;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.DefaultThreadFactory;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

/**
 * author: liuyazong <br>
 * datetime: 2018/12/2 下午2:31 <br>
 * <p></p>
 */
@Slf4j
public class AppTest {


    @Test
    public void test() throws InterruptedException {
        AbstractChannelPoolMap<SocketAddress, ChannelPool> channelPoolMap = new AbstractChannelPoolMap<SocketAddress, ChannelPool>() {
            @Override
            protected ChannelPool newPool(SocketAddress key) {
                return new FixedChannelPool(new Bootstrap()
                        .group(
                                new NioEventLoopGroup(
                                        Runtime.getRuntime().availableProcessors() << 1,
                                        new DefaultThreadFactory("client-worker-pool")))
                        .channel(NioSocketChannel.class)
                        .option(ChannelOption.SO_KEEPALIVE, true)
                        .option(ChannelOption.TCP_NODELAY, true)
                        .remoteAddress(key), new AbstractChannelPoolHandler() {
                    @Override
                    public void channelCreated(Channel ch) throws Exception {
                        ch.pipeline()
                                //.addLast(new LoggingHandler(LogLevel.ERROR))
                                .addLast(new LengthFieldBasedFrameDecoder(1024, 0, 2, 0, 2))
                                .addLast(new StringDecoder(Charset.defaultCharset()))
                                .addLast(new LengthFieldPrepender(2, 0))
                                .addLast(new StringEncoder(Charset.defaultCharset()))
                                .addLast(new SimpleChannelInboundHandler<String>() {
                                    @Override
                                    protected void channelRead0(
                                            ChannelHandlerContext ctx, String msg) throws Exception {
                                        log.info("Client Received: {}", msg);
                                    }

                                    @Override
                                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
                                            throws Exception {
                                        super.exceptionCaught(ctx, cause);
                                        log.error("Client Error", cause);
                                    }
                                });
                        log.debug("Server : {}, Channel Created : {}", key, ch);
                    }
                }, 32);
            }
        };


        IntStream.range(0, 500).parallel().forEach(i -> {
            ChannelPool pool = channelPoolMap.get(new InetSocketAddress("10.3.6.138", 8888));
            pool.acquire()
                    .addListener(future -> {
                        if (future.isSuccess()) {
                            Channel channel = (Channel) future.get();
                            String msg = i + "";
                            channel.writeAndFlush(msg).addListener(future1 -> {
                                log.info("Client Send: {}", msg);
                            });
                            pool.release(channel);
                        } else {
                            log.info("Client Send: {}", "false");
                        }
                    });
        });

        Thread.currentThread().join();
    }

    /**
     *
     */
    @Test
    public void shouldAnswerWithTrue() throws Exception {

        ExecutorService executorService = Executors.newFixedThreadPool(8);


        for (int i = 0; i < 1; i++) {
            int finalI = i;
            executorService.execute(() -> {
                try {
                    test(finalI);
                } catch (Exception e) {

                }
            });
        }

        Thread.sleep(30000);
        executorService.shutdown();
    }

    private void test(int i) throws Exception {
        Bootstrap bootstrap = new Bootstrap()
                .group(new NioEventLoopGroup(1))
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(NioSocketChannel ch) throws Exception {
                        ch.pipeline()
                                .addLast(LoggingHandler.class.getName(), new LoggingHandler(LogLevel.DEBUG))
                                .addLast(IdleStateHandler.class.getName(), new IdleStateHandler(0, 3, 0))
                                .addLast(LengthFieldBasedFrameDecoder.class.getName(), new LengthFieldBasedFrameDecoder(1024, 0, 2, 0, 2))
                                .addLast(StringDecoder.class.getName(), new StringDecoder(Charset.defaultCharset()))
                                .addLast(LengthFieldPrepender.class.getName(), new LengthFieldPrepender(2, 0))
                                .addLast(StringEncoder.class.getName(), new StringEncoder(Charset.defaultCharset()))
                                .addLast(SimpleChannelInboundHandler.class.getName(), new SimpleChannelInboundHandler<String>() {
                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
                                        log.info("received msg: {}", msg);
                                    }

                                    @Override
                                    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                                        super.channelInactive(ctx);
                                        log.info("channel {} inactive", ctx);
                                    }

                                    @Override
                                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                                        super.userEventTriggered(ctx, evt);
                                        if (evt instanceof IdleStateEvent) {
                                            log.info("evt: {}", ((IdleStateEvent) evt).state());
                                            if (evt.equals(IdleStateEvent.WRITER_IDLE_STATE_EVENT)) {
                                                ctx.close().addListener(future -> log.info("ctx {} close", ctx));
                                            }
                                        }
                                    }
                                });
                    }
                });

        try {
            String inetHost = InetAddress.getLocalHost().getHostAddress();
            int inetPort = 8888;
            ChannelFuture channelFuture = bootstrap.connect(inetHost, inetPort)
                    .addListener(future -> {
                        log.info("connect to {}:{}", inetHost, inetPort);
                        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                            try {
                                if (bootstrap.config().group().shutdownGracefully().await().addListener(future1 -> {
                                    log.info("group shutdown");
                                }).isSuccess()) {
                                    log.info("group shutdown");
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }));
                    })
                    .sync();


            {
                String arg = String.format("%s * %s = %s", i, i, i * i);
                channelFuture.channel()
                        .writeAndFlush(arg)
                        .addListener(future -> {
                            log.info("send msg: {}", arg);
                        });
            }

            channelFuture
                    .channel()
                    .closeFuture()
                    .addListener(future -> {
                        log.info("disconnect");
                    }).sync();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
