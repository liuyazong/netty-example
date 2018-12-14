package l.y.z;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * author: liuyazong <br>
 * datetime: 2018/12/2 下午2:31 <br>
 * <p></p>
 */
@Slf4j
public class App {
    public static void main(String[] args) throws Exception {

        //用于处理业务操作
        ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() << 1);

        //构建ServerBootstrap实例
        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(new NioEventLoopGroup(1), new NioEventLoopGroup())
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 2 << 9)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .handler(new LoggingHandler(LogLevel.DEBUG))
                .childHandler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        ch.pipeline()
                                .addLast(new LengthFieldBasedFrameDecoder(1024, 0, 2, 0, 2))
                                .addLast(new StringDecoder(Charset.defaultCharset()))
                                .addLast(new LengthFieldPrepender(2, 0))
                                .addLast(new StringEncoder(Charset.defaultCharset()))
                                .addLast(new SimpleChannelInboundHandler<String>() {

                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
                                        log.info("received msg: {}", msg);
                                        String result = executorService.submit(() -> {
                                            String s = new StringBuffer(msg).reverse().toString();
                                            log.info("handle msg: {}, result: {}", msg, s);
                                            return s;
                                        }).get();

                                        ctx.writeAndFlush(result)
                                                .addListener(future -> {
                                                    log.info("send msg: {}", result);
                                                });
                                    }
                                });
                    }
                });

        //添加JVM回调，在JVM停止允许时（非强制停止，如Unix/Linux的kill -9），关闭你所创建的EventLoopGroup
        Runtime.getRuntime()
                .addShutdownHook(new Thread(() -> {
                    try {
                        EventLoopGroup childGroup = bootstrap.config().childGroup();
                        if (!childGroup.isShuttingDown() && !childGroup.isShutdown() && !childGroup.isTerminated()
                                && childGroup.shutdownGracefully()
                                .addListener(future -> {
                                    log.info("childGroup shutdown {}", future.isSuccess());
                                })
                                .await()
                                .isSuccess()) {
                            log.info("childGroup shutdown at {}", new Date());
                        }
                        EventLoopGroup parentGroup = bootstrap.config().group();
                        if (!parentGroup.isShuttingDown() && !parentGroup.isShutdown() && !parentGroup.isTerminated()
                                && parentGroup.shutdownGracefully()
                                .addListener(future -> {
                                    log.info("parentGroup shutdown {}", future.isSuccess());
                                })
                                .await()
                                .isSuccess()) {
                            log.info("parentGroup shutdown at {}", new Date());
                        }
                    } catch (Exception e) {
                        log.error("shutdown error", e);
                    }
                }, "ShutdownHook"));

        String inetHost = InetAddress.getLocalHost().getHostAddress();
        int inetPort = 8888;
        InetSocketAddress localAddress = new InetSocketAddress(inetHost, inetPort);
        //启动Server
        bootstrap.bind(localAddress)
                .sync()
                .addListener(future -> {
                    log.info("server {}:{} started at {}", inetHost, inetPort, new Date());
                })
                .channel()
                //关闭Server，事实上，在本程序中，你所创建的EventLoopGroup不会在这里关闭，所以把关闭操作放到了JVM的回调中。
                .closeFuture()
                .sync()
                .addListener(future -> {
                    log.info("server {}:{} stopped at {}", inetHost, inetPort, new Date());
                });

    }
}
