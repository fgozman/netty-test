package com.intum.htraffic.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.net.InetAddress;
import java.util.concurrent.LinkedTransferQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HtrafficServer {
	private static final Logger log = LoggerFactory.getLogger(HtrafficServer.class);
	
	public static void main(String[] args) throws InterruptedException {
		final LinkedTransferQueue<MongoDBTransactionExecutor> transactionQueue= new LinkedTransferQueue<MongoDBTransactionExecutor>();
		final Thread mongodbTranzactionQueueConsumer = new Thread(new Runnable() {
			
			@Override
			public void run() {
				while(!Thread.interrupted()){
					MongoDBTransactionExecutor exec;
					try {
						exec = transactionQueue.take();
					} catch (InterruptedException e) {
						break;
					}
					if(!exec.execute(null)) break;
				}
				
			}
		});
		mongodbTranzactionQueueConsumer.setDaemon(true);
		mongodbTranzactionQueueConsumer.start();
		log.info("No of processors available:"+Runtime.getRuntime().availableProcessors());
		
		NioEventLoopGroup bossGroup = new NioEventLoopGroup(1);// no of threads is 1
		NioEventLoopGroup workerGroup = new NioEventLoopGroup(2);// no of threads is no of processors * 2 = 8 for my mac
		try {
			ServerBootstrap bootstrap = new ServerBootstrap()
					.group(bossGroup,workerGroup)
					.channel(NioServerSocketChannel.class)
					//.handler(new LoggingHandler(LogLevel.INFO))
					.childHandler(new HtrafficServerInitializer(transactionQueue))
					.option(ChannelOption.SO_BACKLOG, 128)
					.childOption(ChannelOption.SO_KEEPALIVE, true)

					// use pooled byte buffers for performance
					.childOption(ChannelOption.ALLOCATOR,PooledByteBufAllocator.DEFAULT);

			// Bind and start to accept incoming connections.
			ChannelFuture f = bootstrap.bind(InetAddress.getLoopbackAddress(),8080).sync();

			// Wait until the server socket is closed.
			// In this example, this does not happen, but you can do that to
			// gracefully
			// shut down your server.
			f.channel().closeFuture().sync();
		} 
		finally {
			workerGroup.shutdownGracefully();
			bossGroup.shutdownGracefully();
			DateFormatter.clear();
			transactionQueue.transfer(new MongoDBTransactionExecutor() {
				
				@Override
				public boolean execute(Object context) {
					return false;
				}
			});
			mongodbTranzactionQueueConsumer.interrupt();
			
		}
		/*
		 * TODO: add also these OpenSSL based SslEngine to reduce memory usage
		 * and latency. Native transport for Linux using Epoll ET for more
		 * performance and less CPU usage. use this Bootstrap bootstrap = new
		 * Bootstrap().group(new EpollEventLoopGroup());
		 * bootstrap.channel(EpollSocketChannel.class);
		 */
	}
}
