package com.intum.htraffic.server;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.stream.ChunkedWriteHandler;

import java.util.concurrent.LinkedTransferQueue;

final class HtrafficServerInitializer extends
		ChannelInitializer<SocketChannel> {
	private final LinkedTransferQueue<MongoDBTransactionExecutor> transactionQueue;
	public HtrafficServerInitializer(LinkedTransferQueue<MongoDBTransactionExecutor> transactionQueue) {
		this.transactionQueue = transactionQueue;
	}
	
	protected void initChannel(SocketChannel channel)
			throws Exception {
		//System.err.println("Init channel: "+channel);
		channel.pipeline()
				.addLast(new HttpServerCodec())
				.addLast(new HttpObjectAggregator(65536))//10*1024*1024									
				.addLast(new ChunkedWriteHandler())
				//.addLast(new HttpContentCompressor())
				.addLast(new HtrafficServerHandler(transactionQueue));
		
	}
}