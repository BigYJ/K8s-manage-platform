package com.proxy.layer7;

import com.proxy.BackendThreadModel;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;

public class FrontendHandler extends ChannelInboundHandlerAdapter {

    private final String remoteHost;
    private final int remotePort;
    private final BackendThreadModel backendThreadModel;
    private Channel outboundChannel;

    public FrontendHandler(String remoteHost, int remotePort, BackendThreadModel backendThreadModel) {
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
        this.backendThreadModel = backendThreadModel;
    }

    static void closeOnFlush(Channel ch) {
        if (ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

    private void read(final ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof FullHttpRequest) {
            outboundChannel.writeAndFlush(msg);
        } else {
            closeOnFlush(ctx.channel());
        }
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) {
        if (outboundChannel != null &&outboundChannel.isActive()) {
            read(ctx, msg);
        } else {
            final Channel inboundChannel = ctx.channel();

            Bootstrap b = new Bootstrap();
            switch (backendThreadModel) {
                case ReuseServerGroup: {
                    b.group(Layer7ProxyServer.serverWorkerGroup);
                    break;
                }
                case IndividualGroup: {
                    b.group(Layer7ProxyServer.backendWorkerGroup);
                    break;
                }
                case ReuseServerThread: {
                    b.group(inboundChannel.eventLoop());
                    break;
                }
                default:
                    break;
            }

            b.channel(EpollSocketChannel.class).handler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline().addLast(new HttpClientCodec(), new HttpObjectAggregator(2000));
                    ch.pipeline().addLast(new BackendHandler(inboundChannel));
                }
            });
            ChannelFuture f = b.connect(remoteHost, remotePort).addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    future.channel().writeAndFlush(msg);
                } else {
                    future.channel().close();
                }
            });
            outboundChannel = f.channel();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (outboundChannel != null) {
            closeOnFlush(outboundChannel);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        closeOnFlush(ctx.channel());
    }
}
