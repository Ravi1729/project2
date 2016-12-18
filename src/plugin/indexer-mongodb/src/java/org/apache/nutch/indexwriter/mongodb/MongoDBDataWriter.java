package org.apache.nutch.indexwriter.mongodb;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.nutch.indexer.IndexWriter;
import org.apache.nutch.indexer.NutchDocument;
import org.apache.nutch.indexwriter.mongodb.*;
import org.apache.hadoop.conf.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.hadoop.mapred.JobConf;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.MongoClient;
import com.mongodb.ServerAddress;
import com.mongodb.WriteConcern;
import com.mongodb.WriteResult;

public class MongoDBDataWriter implements IndexWriter {
	 public static Logger LOG = LoggerFactory.getLogger(MongoDBDataWriter.class);

	 private int maxInsertDocs = 20;
	 private int maxDeleteDocs = 20;

	 private static int ACTION_INSERT = 1;
	 private static int ACTION_DELETE = 2;
	 private static int ACTION_ALL = 3;
	 private int action;

	 private Configuration config;
	 private String host_port;
	 private String database;
	 private String collection;

	 private MongoClient mongoClient;

	 private ArrayList<BasicDBObject> toBeUpsertedDocs;
	 private ArrayList<String> toBeDeletedDocs;

	 public MongoDBDataWriter() {
	  LOG.info("new MongoDBDataWriter() invoked---");
	 }
	 private void setup() {
		  MongoClient client = null;
		  try {
		   List list = new ArrayList();
		   String[] array = this.host_port.split(",");
		   int index = 0;
		   for (index = 0; index < array.length; index++) {
		    String str = array[index];
		    String[] ip_port = str.split(":");
		    list.add(new ServerAddress(ip_port[0], Integer
		      .parseInt(ip_port[1])));
		   }
		   if (list.size() > 0) {
		    client = new MongoClient(list);
		   }

		  } catch (Exception e) {
		   LOG.info(e.toString());
		   client = null;
		  }
		  mongoClient = client;
		 }
	 @Override
	 public void open(JobConf job, String name) {

	  toBeUpsertedDocs = new ArrayList<BasicDBObject>();
	  toBeDeletedDocs = new ArrayList<String>();
	  LOG.info(this.describe());
	  this.setup();
	  if (null != this.mongoClient) {
	   LOG.info("open for MongoClient succeed...");
	  } else {
	   LOG.info("open for MongoClient fail...");
	  }

	 }

	 @Override
	 public void write(NutchDocument doc) {
	  LOG.info(this.toString() + "write *** invoked");
	  BasicDBObject bo = new BasicDBObject();
	  Collection<String> fields = doc.getFieldNames();
	  for (String key : fields) {
	   //just use the first value...
	   String strValue = doc.getField(key).getValues().get(0).toString();
	   try{
	    int intValue = Integer.parseInt(strValue);
	    bo.put(key, intValue);
	   }catch(Exception e){
	    bo.put(key, strValue);
	   }
	  }
	  this.toBeUpsertedDocs.add(bo);
	  if (this.toBeUpsertedDocs.size() >= this.maxInsertDocs) {
	   action = ACTION_INSERT;
	   this.commit();
	  }

	 }
	 @Override
	 public void update(NutchDocument doc) {
	  LOG.info(this.toString() + "update *** invoked");
	  // won't be called In Nutch 1.7
	  write(doc);
	 }

	 @Override
	 public void delete(String key) {
	  LOG.info(this.toString() + "delete *** invoked");
	  this.toBeDeletedDocs.add(key);
	  if (this.toBeDeletedDocs.size() >= this.maxDeleteDocs) {
	   action = ACTION_DELETE;
	   this.commit();
	  }

	 }
	 @Override
	 public void commit() {
	  if (null != this.mongoClient) {
	   DB db = this.mongoClient.getDB(this.database);
	   if (null != db) {
	    DBCollection dbCollection = db.getCollection(this.collection);
	    if (null != dbCollection) {
	     if (0 != (action & ACTION_INSERT)
	       && this.toBeUpsertedDocs.size() > 0) {
	      for( BasicDBObject obj:this.toBeUpsertedDocs){
	       dbCollection.save(obj, WriteConcern.SAFE);
	      }      
	      //WriteResult result = dbCollection.insert(
	       // this.toBeUpsertedDocs, WriteConcern.SAFE);
	      this.toBeUpsertedDocs.clear();
	     }
	     if (0 != (action & ACTION_DELETE)
	       && this.toBeDeletedDocs.size() > 0) {
	      BasicDBObject in = new BasicDBObject();
	      in.put("$in", this.toBeDeletedDocs);
	      BasicDBObject remove = new BasicDBObject();
	      remove.put("_id", in);
	      dbCollection.remove(remove, WriteConcern.SAFE);
	      this.toBeDeletedDocs.clear();
	     }

	    }
	   }
	  }

	  LOG.info(this.toString() + "commit *** invoked");
	 }
	 
	 @Override
	 public void close() {
	  action = ACTION_ALL;
	  this.commit();

	  if (null != mongoClient) {
	   mongoClient.close();
	   mongoClient = null;
	  }

	  LOG.info("close the mongoclient ok!!!");
	 }
	 
	 protected void finalize( )
	 {
	  // finalization code here
	  this.close();
	 }
	 
	 @Override
	 public String describe() {
	  LOG.info(this.toString() + "describe *** invoked");
	  StringBuffer sb = new StringBuffer("MongoDB Data Writer\n");
	  sb.append("\t").append(MongoDBConstants.HOST_PORT).append(" : ")
	    .append(this.host_port);
	  sb.append("\t").append(MongoDBConstants.DATABASE).append(" : ")
	    .append(this.database);
	  sb.append("\t").append(MongoDBConstants.COLLECTION).append(" : ")
	    .append(this.collection);
	  sb.append("\t").append(MongoDBConstants.MAX_INSERT_DOCS).append(" : ")
	  .append(this.maxInsertDocs);
	  sb.append("\t").append(MongoDBConstants.MAX_DELETE_DOCS).append(" : ")
	  .append(this.maxDeleteDocs);
	  return sb.toString();
	 }

	 @Override
	 public void setConf(Configuration conf) {
	  LOG.info(this.toString() + "setconf *** invoked");
	  config = conf;
	  host_port = config.get(MongoDBConstants.HOST_PORT);
	  database = config.get(MongoDBConstants.DATABASE);
	  collection = config.get(MongoDBConstants.COLLECTION);
	  maxInsertDocs=Integer.parseInt(config.get(MongoDBConstants.MAX_INSERT_DOCS));
	  maxDeleteDocs=Integer.parseInt(config.get(MongoDBConstants.MAX_DELETE_DOCS));
	 }

	 @Override
	 public Configuration getConf() {
	  LOG.info(this.toString() + "getconf *** invoked");
	  return config;
	 }
	}