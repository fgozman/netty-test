package com.intum.htraffic.server;

import java.net.UnknownHostException;

import com.mongodb.DB;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;

public abstract class MongoDBTransactionExecutor {
	private static DB db=null;
	
	protected static DB db(){
		if(db==null){
			MongoClient mongoClient;
			try {
				
				MongoClientOptions options = MongoClientOptions.builder()
						.writeConcern(WriteConcern.ACKNOWLEDGED)
						.readPreference(ReadPreference.primary())
						.connectionsPerHost(1)// single connection
						.build();
				mongoClient = new MongoClient("localhost",options);
			} catch (UnknownHostException e) {
				throw new RuntimeException(e);
			}
			db = mongoClient.getDB("test1");
		}
		return db;
	}
	
	public abstract boolean execute(Object context);

}
