package com.proxy;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.util.concurrent.DefaultThreadFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public abstract class ProxyServer {
    @Autowired
    protected ServerConfig serverConfig;

    public static final EventLoopGroup serverBossGroup = new EpollEventLoopGroup(1, new DefaultThreadFactory("boss"));
    public static final EventLoopGroup serverWorkerGroup = new EpollEventLoopGroup(16, new DefaultThreadFactory("worker"));
    public static final EventLoopGroup backendWorkerGroup = new EpollEventLoopGroup(8, new DefaultThreadFactory("proxy"));


    protected abstract ChannelInitializer<Channel> getChannelInitializer();

    protected void config(ServerBootstrap b) {
    }

    public void start() throws Exception {

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(serverBossGroup, serverWorkerGroup)
                    .channel(EpollServerSocketChannel.class)
                    .childHandler(getChannelInitializer());
            b.childOption(ChannelOption.SO_RCVBUF, serverConfig.getReceiveBuffer())
                    .childOption(ChannelOption.SO_SNDBUF, serverConfig.getSendBuffer());
            if (serverConfig.getAllocatorType() == AllocatorType.Pooled)
                b.childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
            if (serverConfig.getAllocatorType() == AllocatorType.Unpooled)
                b.childOption(ChannelOption.ALLOCATOR, UnpooledByteBufAllocator.DEFAULT);
            config(b);
            b.bind(serverConfig.getServerIp(), serverConfig.getServerPort())
                    .addListener(future -> log.info("{} Started with config: {}", getClass().getSimpleName(), serverConfig))
                    .sync().channel().closeFuture().sync();
        } finally {
            serverBossGroup.shutdownGracefully();
            serverWorkerGroup.shutdownGracefully();
        }
    }
}
