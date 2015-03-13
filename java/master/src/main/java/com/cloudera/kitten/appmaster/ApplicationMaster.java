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
package com.cloudera.kitten.appmaster;

import java.io.File;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import com.cloudera.kitten.appmaster.params.lua.LuaApplicationMasterParameters;
import com.cloudera.kitten.appmaster.params.lua.WorkflowParameters;
import com.cloudera.kitten.appmaster.service.ApplicationMasterServiceImpl;
import com.cloudera.kitten.appmaster.service.WorkflowService;
import com.cloudera.kitten.lua.LuaFields;

/**
 * A simple ApplicationMaster to use when there isn't any master logic that is requried to run.
 */
public class ApplicationMaster extends Configured implements Tool {

  @Override
  public int run(String[] args) throws Exception {
	  File script = new File(LuaFields.KITTEN_LUA_CONFIG_FILE);
	  if(script.exists()){
	  	//single operator
		ApplicationMasterParameters params = new LuaApplicationMasterParameters(getConf());
		ApplicationMasterService service = new ApplicationMasterServiceImpl(params, getConf());
		service.startAndWait();
		while (service.hasRunningContainers()) {
		  Thread.sleep(1000);
		}
		service.stopAndWait();
	  }
	  else{
		  //workflow
		  WorkflowParameters params = new WorkflowParameters(getConf());
		  WorkflowService service = new WorkflowService(params, getConf());
			service.startAndWait();
			while (service.hasRunningContainers()) {
			  Thread.sleep(1000);
			}
			service.stopAndWait();
	  }
	  
	  return 0;
  }

  public static void main(String[] args) throws Exception {
    try { 
      int rc = ToolRunner.run(new Configuration(), new ApplicationMaster(), args);
      System.exit(rc);
    } catch (Exception e) {
    	e.printStackTrace();
      System.exit(1);
    }
  }
}
