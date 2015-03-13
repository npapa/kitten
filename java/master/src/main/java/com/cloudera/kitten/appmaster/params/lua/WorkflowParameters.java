/**
 * Copyright (c) 2012, Cloudera, Inc. All Rights Reserved.
 *
 * Cloudera, Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"). You may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * This software is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions and limitations under the
 * License.
 */
package com.cloudera.kitten.appmaster.params.lua;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.hadoop.conf.Configuration;

import com.cloudera.kitten.ContainerLaunchParameters;
import com.cloudera.kitten.appmaster.ApplicationMasterParameters;
import com.cloudera.kitten.lua.LuaContainerLaunchParameters;
import com.cloudera.kitten.lua.LuaFields;
import com.cloudera.kitten.lua.LuaPair;
import com.cloudera.kitten.lua.LuaWrapper;
import com.cloudera.kitten.util.LocalDataHelper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import org.apache.hadoop.net.NetUtils;

public class WorkflowParameters implements ApplicationMasterParameters {

	private final HashMap<String,LuaWrapper> env;
	private LuaWrapper e0;
  private final Configuration conf;
  private final Map<String, URI> localToUris;
  private final String hostname;

  private int clientPort = 0;
  private String trackingUrl = "";

  public WorkflowParameters(Configuration conf) throws Exception{
    this(LuaFields.KITTEN_WORKFLOW_CONFIG_FILE, System.getenv(LuaFields.KITTEN_JOB_NAME), conf);
  }

  public WorkflowParameters(Configuration conf, Map<String, Object> extras) throws Exception{
    this(LuaFields.KITTEN_WORKFLOW_CONFIG_FILE, System.getenv(LuaFields.KITTEN_JOB_NAME), conf, extras);
  }

  public WorkflowParameters(String script, String jobName, Configuration conf) throws Exception{
    this(script, jobName, conf, ImmutableMap.<String, Object>of());
  }
  
  public WorkflowParameters(String script, String jobName,
      Configuration conf, Map<String, Object> extras) throws Exception {
    this(script, jobName, conf, extras, loadLocalToUris());
  }
  
  public WorkflowParameters(String script, String jobName,
      Configuration conf,
      Map<String, Object> extras,
      Map<String, URI> localToUris) throws Exception {
	  this.env = new HashMap<String,LuaWrapper>();
	  File edgeGraph = new File(script);
		FileInputStream fis = new FileInputStream(edgeGraph);
		BufferedReader br = new BufferedReader(new InputStreamReader(fis));
	 
		String line = null;
		HashMap<String,String> operators = new HashMap<String, String>();
		while ((line = br.readLine()) != null) {
			String[] e =line.split(",");
			if(!e[0].contains(".txt")){
				operators.put(e[0], e[0]+".lua");
			}
		}
		br.close();
		System.out.println("Operators: "+operators);
		int i =0;
		for(Entry<String, String> e : operators.entrySet()){
			LuaWrapper l = new LuaWrapper(e.getValue(), loadExtras(extras)).getTable(e.getKey());
			if(i==0)
				this.e0=l;
			i++;
			this.env.put(e.getKey(),l);
		}
		this.conf = conf;
		this.localToUris = localToUris;
		this.hostname = NetUtils.getHostname();
  }
  
  private static Map<String, URI> loadLocalToUris() {
    Map<String, String> e = System.getenv();
    if (e.containsKey(LuaFields.KITTEN_LOCAL_FILE_TO_URI)) {
      return LocalDataHelper.deserialize(e.get(LuaFields.KITTEN_LOCAL_FILE_TO_URI));
    }
    return ImmutableMap.of();
  }
  
  private static Map<String, Object> loadExtras(Map<String, Object> masterExtras) {
    Map<String, String> e = System.getenv();
    if (e.containsKey(LuaFields.KITTEN_EXTRA_LUA_VALUES)) {
      Map<String, Object> extras = Maps.newHashMap(LocalDataHelper.deserialize(
          e.get(LuaFields.KITTEN_EXTRA_LUA_VALUES)));
      extras.putAll(masterExtras);
      return extras;
    }
    return masterExtras;
  }
  
  @Override
  public Configuration getConfiguration() {
    return conf;
  }
  
  @Override
  public String getHostname() {
    return hostname;
  }

  @Override
  public void setClientPort(int clientPort) {
    this.clientPort = clientPort;
  }

  @Override
  public int getClientPort() {
    return clientPort;
  }

  @Override
  public void setTrackingUrl(String trackingUrl) {
    this.trackingUrl = trackingUrl;
  }

  @Override
  public String getTrackingUrl() {
    return trackingUrl;
  }

  @Override
  public int getAllowedFailures() {
    if (e0.isNil(LuaFields.TOLERATED_FAILURES)) {
      return 4; // TODO: kind of arbitrary, no? :)
    } else {
      return e0.getInteger(LuaFields.TOLERATED_FAILURES);
    }
  }
  
  @Override
  public HashMap<String,ContainerLaunchParameters> getContainerLaunchParameters() {
	  HashMap<String,ContainerLaunchParameters> clp = new HashMap<String, ContainerLaunchParameters>();
	  int i =0;
	  for(Entry<String,LuaWrapper> e : this.env.entrySet()){
		    if (!e.getValue().isNil(LuaFields.CONTAINERS)) {
		      Iterator<LuaPair> iter = e.getValue().getTable(LuaFields.CONTAINERS).arrayIterator();
		      while (iter.hasNext()) {
		    	  String name = "operator_"+i+"_"+e.getKey();
		    	  clp.put(e.getKey(),new LuaContainerLaunchParameters(iter.next().value, name, conf, localToUris));
		    	  i++;
		      }
		      
		    } else if (!e.getValue().isNil(LuaFields.CONTAINER)) {
		    	  String name = "operator_"+i+"_"+e.getKey();
		        clp.put(e.getKey(),new LuaContainerLaunchParameters(e.getValue().getTable(LuaFields.CONTAINER), name, conf, localToUris));
		        i++;
		    }
	  }
      return clp;
  }
}