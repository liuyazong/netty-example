package l.y.z;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.pool.AbstractChannelPoolMap;
import io.netty.channel.pool.ChannelPool;
import io.netty.channel.pool.ChannelPoolHandler;
import io.netty.channel.pool.FixedChannelPool;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.stream.ChunkedWriteHandler;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.Charset;

/**
 * author: liuyazong datetime: 2018/10/10 下午4:33
 *
 * <p>
 */
@Slf4j
public class AppTest {
    @Test
    public void test() throws InterruptedException {

        AbstractChannelPoolMap<SocketAddress, ChannelPool> channelPoolMap =
                new AbstractChannelPoolMap<SocketAddress, ChannelPool>() {
                    @Override
                    protected ChannelPool newPool(SocketAddress address) {
                        return new FixedChannelPool(
                                new Bootstrap()
                                        .group(new NioEventLoopGroup(Runtime.getRuntime().availableProcessors() << 1))
                                        .option(ChannelOption.SO_KEEPALIVE, true)
                                        .option(ChannelOption.TCP_NODELAY, true)
                                        .channel(NioSocketChannel.class)
                                        .remoteAddress(address),
                                new ChannelPoolHandler() {
                                    @Override
                                    public void channelReleased(Channel ch) {
                                        log.debug("channel released: {}", ch.id());
                                    }

                                    @Override
                                    public void channelAcquired(Channel ch) {
                                        log.debug("channel acquired: {}", ch.id());
                                    }

                                    @Override
                                    public void channelCreated(Channel ch) {
                                        ch.pipeline()
                                                .addLast(HttpClientCodec.class.getName(), new HttpClientCodec())
                                                .addLast(
                                                        HttpObjectAggregator.class.getName(), new HttpObjectAggregator(65535))
                                                .addLast(ChunkedWriteHandler.class.getName(), new ChunkedWriteHandler())
                                                .addLast(
                                                        SimpleChannelInboundHandler.class.getName(),
                                                        new SimpleChannelInboundHandler<FullHttpResponse>() {
                                                            @Override
                                                            protected void channelRead0(
                                                                    ChannelHandlerContext ctx, FullHttpResponse msg) {
                                                                log.info("msg: {}", msg.content().toString(Charset.defaultCharset()));
                                                            }

                                                            @Override
                                                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                                                                super.exceptionCaught(ctx, cause);
                                                                log.error("error", cause);
                                                            }
                                                        });
                                        log.debug("channel created: {}", ch.id());
                                    }
                                }, Runtime.getRuntime().availableProcessors() << 2);
                    }
                };

        ChannelPool channelPool = channelPoolMap.get(new InetSocketAddress("localhost", 8888));

        for (int i = 0; i < 1; i++) {
            channelPool
                    .acquire()
                    .addListener(
                            future -> {
                                if (future.isSuccess()) {
                                    Channel channel = (Channel) future.getNow();
                                    DefaultFullHttpRequest request =
                                            new DefaultFullHttpRequest(
                                                    HttpVersion.HTTP_1_1,
                                                    HttpMethod.POST,
                                                    "/netty/client",
                                                    Unpooled.wrappedBuffer(
                                                            "Hello, Netty!".getBytes(Charset.defaultCharset())));
                                    request
                                            .headers()
                                            .set(HttpHeaderNames.CONTENT_LENGTH, request.content().readableBytes());
                                    request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
                                    channel.writeAndFlush(request);
                                    channelPool.release(channel);
                                } else {
                                    log.info("future.isSuccess --->> {}", false);
                                }
                            });

        }

        Thread.sleep(10000);
    }
}
