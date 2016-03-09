package com.intum.htraffic.server;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.CharsetUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Map;
import java.util.concurrent.LinkedTransferQueue;

import javax.activation.MimetypesFileTypeMap;

import org.bson.types.ObjectId;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBObject;
import com.mongodb.WriteConcern;
import com.mongodb.WriteResult;

public final class HtrafficServerHandler extends
		SimpleChannelInboundHandler<FullHttpRequest> {
	private static final int HTTP_CACHE_SECONDS = 60;
	private final LinkedTransferQueue<MongoDBTransactionExecutor> transactionQueue;

	public HtrafficServerHandler(LinkedTransferQueue<MongoDBTransactionExecutor> transactionQueue) {
		this.transactionQueue = transactionQueue;
	}

	@Override
	protected void channelRead0(final ChannelHandlerContext ctx,
			final FullHttpRequest request) throws Exception {
		if (!request.decoderResult().isSuccess()) {
			sendError(ctx, HttpResponseStatus.BAD_REQUEST);
			return;
		}
		final HttpMethod method = request.method();
		if (method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.DELETE) {
			// check for url
			final String value = request.content().toString(CharsetUtil.UTF_8);
			transactionQueue.transfer(new MongoDBTransactionExecutor() {
				@Override
				public boolean execute(Object context) {
					ObjectMapper objMapper = JsonObjectMapperFactory.getInstance();
					try {
						Map obj = objMapper.readValue(value, Map.class);
						BasicDBObject dbObj = new BasicDBObject(obj);
						WriteResult res =db().getCollection("col1").insert(dbObj,
								WriteConcern.SAFE);
						ObjectId objId = (ObjectId)dbObj.get("_id");
						Map obj_back = db().getCollection("col1").findOne(objId).toMap();
						sendJsonResponse(ctx,HttpResponseStatus.OK,request,objMapper.writeValueAsBytes(obj_back));
						//System.out.println(obj);
					} catch (Throwable e) {
						e.printStackTrace();
						sendError(ctx,HttpResponseStatus.INTERNAL_SERVER_ERROR);
					}
					
					// continue to consume
					return true;
				}
			});
			return;
		}

		final File file = new File("src/test/resources/index.html");
		if (file.isHidden() || !file.exists()) {
			sendError(ctx, HttpResponseStatus.NOT_FOUND);
			return;
		}
		if (!file.isFile()) {
			sendError(ctx, HttpResponseStatus.FORBIDDEN);
			return;
		}

		RandomAccessFile raf;
		try {
			raf = new RandomAccessFile(file, "r");
		} catch (FileNotFoundException e) {
			sendError(ctx, HttpResponseStatus.NOT_FOUND);
			return;
		}
		long fileLength = raf.length();
		HttpResponse response = new DefaultHttpResponse(
				HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
		HttpHeaders.setContentLength(response, fileLength);
		setContentTypeHeaderForFile(response, file);
		//setDateAndCacheHeaders(response, file);

		if (HttpHeaders.isKeepAlive(request)) {
			HttpHeaders.setHeader(response, HttpHeaders.Names.CONNECTION,
					HttpHeaders.Values.KEEP_ALIVE);
		}

		// write header
		ctx.write(response);
		// write the content
		ctx.write(new DefaultFileRegion(raf.getChannel(), 0, fileLength));
		/*,
				ctx.newProgressivePromise()).addListener(
		
		new ChannelProgressiveFutureListener() {
			@Override
			public void operationProgressed(ChannelProgressiveFuture future,
					long progress, long total) {
				if (total < 0) { // total unknown
					System.err.println(future.channel()
							+ " Transfer progress: " + progress);
				} else {
					System.err.println(future.channel()
							+ " Transfer progress: " + progress + " / " + total);
				}
			}

			@Override
			public void operationComplete(ChannelProgressiveFuture future) {
				System.err.println(future.channel() + " Transfer complete.");
			}
		});*/
		
		// write the end marker
		ChannelFuture lastContentFuture = ctx
				.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);

		 if (!HttpHeaders.isKeepAlive(request)) {
			// Close the connection when the whole content is written out.
			lastContentFuture.addListener(ChannelFutureListener.CLOSE);
		 }
	}

	private void setDateAndCacheHeaders(HttpResponse response, File file) {
		DateFormat dateFormatter = DateFormatter.createUTCDateFormat();
		// Date header
		Calendar time = new GregorianCalendar();
		HttpHeaders.setHeader(response, HttpHeaders.Names.DATE,
				dateFormatter.format(time.getTime()));
		// Add cache headers
		time.add(Calendar.SECOND, HTTP_CACHE_SECONDS);
		HttpHeaders.setHeader(response, HttpHeaders.Names.EXPIRES,
				dateFormatter.format(time.getTime()));
		HttpHeaders.setHeader(response, HttpHeaders.Names.CACHE_CONTROL,
				"private, max-age=" + HTTP_CACHE_SECONDS);
		HttpHeaders.setHeader(response, HttpHeaders.Names.LAST_MODIFIED,
				dateFormatter.format(new Date(file.lastModified())));

	}

	private void setContentTypeHeaderForFile(HttpResponse response, File file) {
		MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap();
		HttpHeaders.setHeader(response, HttpHeaders.Names.CONTENT_TYPE,
				mimeTypesMap.getContentType(file.getPath()));
	}

	private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
		HttpResponse response = new DefaultFullHttpResponse(
				HttpVersion.HTTP_1_1, status);
		HttpHeaders.setHeader(response, HttpHeaders.Names.CONTENT_TYPE,
				"text/plain; charset=UTF-8");
		// Close the connection as soon as the error message is sent.
		ctx.write(response);
		ctx.write(Unpooled.copiedBuffer("Failure: " + status.toString()
				+ "\r\n", CharsetUtil.UTF_8));
		ChannelFuture writeFuture = ctx
				.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
		writeFuture.addListener(ChannelFutureListener.CLOSE);
	}
	
	private void sendJsonResponse(ChannelHandlerContext ctx,HttpResponseStatus status,HttpRequest request, byte[] jsonMsg) {
		HttpResponse response = new DefaultHttpResponse(
				HttpVersion.HTTP_1_1, status);
		HttpHeaders.setHeader(response, HttpHeaders.Names.CONTENT_TYPE,
				"application/json; charset=UTF-8");
		HttpHeaders.setHeader(response, HttpHeaders.Names.CONTENT_LENGTH,
				jsonMsg.length);
		if (HttpHeaders.isKeepAlive(request)) {
			HttpHeaders.setHeader(response, HttpHeaders.Names.CONNECTION,
					HttpHeaders.Values.KEEP_ALIVE);
		}

		ctx.write(response);
		ctx.writeAndFlush(Unpooled.copiedBuffer(jsonMsg));
		ChannelFuture lastContentFuture = ctx
				.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
		if (!HttpHeaders.isKeepAlive(request)) {
				// Close the connection when the whole content is written out.
			lastContentFuture.addListener(ChannelFutureListener.CLOSE);
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
			throws Exception {

		if (cause instanceof TooLongFrameException) {
			sendError(ctx, HttpResponseStatus.BAD_REQUEST);
			return;
		}

		cause.printStackTrace();
		if (ctx.channel().isActive()) {
			sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR);
		}
	}
	
	@Override
	public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
		ctx.flush();
	}
}
