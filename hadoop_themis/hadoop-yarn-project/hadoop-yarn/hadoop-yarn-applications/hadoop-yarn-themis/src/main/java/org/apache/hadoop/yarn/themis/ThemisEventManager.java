package org.apache.hadoop.yarn.themis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.apache.hadoop.yarn.service.client.ServiceClient;
import org.apache.hadoop.yarn.submarine.client.cli.Cli;
import org.apache.hadoop.yarn.submarine.common.Envs;

public class ThemisEventManager{

	  private static final Logger LOG = LoggerFactory.getLogger(
		      ThemisEventManager.class);
	  
	  public ThemisEventManager() {
		  
	  }
	  
	  public void handle(ThemisEvent event) {
		  
	       System.out.println("___________.__                   .__        \n" + 
	       "\\__    ___/|  |__   ____   _____ |__| ______\n" + 
	       "  |    |   |  |  \\_/ __ \\ /     \\|  |/  ___/\n" + 
	       "  |    |   |   Y  \\  ___/|  Y Y  \\  |\\___ \\ \n" + 
	       "  |____|   |___|  /\\___  >__|_|  /__/____  >\n" + 
	       "                \\/     \\/      \\/        \\/ ");
		  
		  switch (event.getType()) {
		  
		  case LAUNCH:
			  LOG.info("[Themis] - Received LAUNCH event");
			  handleLaunch(event);
			  break;
		  case CHANGE:
			  LOG.info("[Themis] - Received CHANGE event");
			  handleChange(event);
			  break;
		  case TERMINATE:
			  LOG.info("[Themis] - Received TERMINATE event");
			  handleTerminate(event);
			  break;
		  default:
			  LOG.error("[Themis] - Received invalid event");
		  }
		  
	  }
	  
	  public void handleLaunch(ThemisEvent event) {
		  
		  String[] workerLocalityPrefArray = event.getWorkerLocalityPrefs().trim().split(",");
		  String masterLocalityPrefs = workerLocalityPrefArray[0];
		  String workerLocalityPrefs = "";
		  for (int i = 1 ; i < (workerLocalityPrefArray.length - 1); i++) {
			  workerLocalityPrefs +=  workerLocalityPrefArray[i].trim() + "," ;
		  }
		  workerLocalityPrefs += workerLocalityPrefArray[workerLocalityPrefArray.length - 1];
		  
		  
		  String[] arguments = new String[] {"job", 
				   "run",
				   "--name",
				   event.getJobName(),
				   "--verbose",
				   "--docker_image",
				   event.getWorkerDockerImage(),
				   "--input_path",
				   event.getInputDataPath(),
				   "--checkpoint_path",
				   event.getCheckpointDataPath(),
				   "--env",
				   "DOCKER_JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64/jre/",
				   "--env",
				   "DOCKER_HADOOP_HDFS_HOME=/hadoop-3.1.0",
				   "--env",
				   "GRPC_VERBOSITY=DEBUG",
				   "--num_workers",
				   event.getNumWorker(),
				   "--worker_resources",
				   event.getWorkerResources(),
				   "--worker_launch_cmd",
				   event.getWorkerLaunchCmd(),
				   "--ps_docker_image",
				   event.getPsDockerImage(),
				   "--num_ps",
				   event.getNumPS(),
				   "--ps_resources",
				   event.getPsResources(),
				   "--ps_launch_cmd",
				   event.getPSLaunchCmd(),
				   "--ps_locality_pref",
				   event.getPsLocalityPrefs(),
				   "--master_locality_pref",
				   masterLocalityPrefs,
				   "--worker_locality_pref",
				   workerLocalityPrefs};
		  
		  try {
			  Cli.main(arguments);
			  return;
		  } catch (Exception e) {
			  LOG.error("Error while trying to launch a submarine job - " + e.getLocalizedMessage());
		  }
		  
	  }
	  
	  protected String getUserName() {
		    return System.getProperty("user.name");
	  }
	  
	  private String getDNSNameCommonSuffix(String serviceName,
		      String userName, String domain, int port) {
		    String commonEndpointSuffix =
		        "." + serviceName + "." + userName + "." + domain + ":" + port;
		    return commonEndpointSuffix;
		  }
	  
	  private String getComponentArrayJson(String componentName, int count,
		      String endpointSuffix) {
		    String component = "\\\"" + componentName + "\\\":";
		    StringBuilder array = new StringBuilder();
		    array.append("[");
		    for (int i = 0; i < count; i++) {
		      array.append("\\\"");
		      array.append(componentName);
		      array.append("-");
		      array.append(i);
		      array.append(endpointSuffix);
		      array.append("\\\"");
		      if (i != count - 1) {
		        array.append(",");
		      }
		    }
		    array.append("]");
		    return component + array.toString();
		  }	  
	  
	  private String getTFConfigEnv(String curCommponentName, int nWorkers,
		      int nPs, String serviceName, String userName, String domain) {
		    String commonEndpointSuffix = getDNSNameCommonSuffix(serviceName, userName,
		        domain, 8000);

		    String json = "{\\\"cluster\\\":{";

		    String master = getComponentArrayJson("master", 1, commonEndpointSuffix)
		        + ",";
		    String worker = getComponentArrayJson("worker", nWorkers - 1,
		        commonEndpointSuffix) + ",";
		    String ps = getComponentArrayJson("ps", nPs, commonEndpointSuffix) + "},";

		    StringBuilder sb = new StringBuilder();
		    sb.append("\\\"task\\\":{");
		    sb.append(" \\\"type\\\":\\\"");
		    sb.append(curCommponentName);
		    sb.append("\\\",");
		    sb.append(" \\\"index\\\":");
		    sb.append('$');
		    sb.append("_TASK_INDEX" + "},");
		    String task = sb.toString();
		    String environment = "\\\"environment\\\":\\\"cloud\\\"}";

		    sb = new StringBuilder();
		    sb.append(json);
		    sb.append(master);
		    sb.append(worker);
		    sb.append(ps);
		    sb.append(task);
		    sb.append(environment);
		    return sb.toString();
		  }	  
	  
	  public void handleChange(ThemisEvent event) {
		  
		  YarnConfiguration conf = new YarnConfiguration();
		  
		  //Flex Down

		   Map<String, String> componentCountStrings = new HashMap<String, String>();
		   componentCountStrings.put("worker", "0");
		   componentCountStrings.put("master", "0");
		   componentCountStrings.put("ps", "0");
		   
		   ServiceClient sc = new ServiceClient();
	       sc.init(conf);
	       sc.start();
	       try {
			int result = sc
			       .actionFlexThemis(event.getJobName(), componentCountStrings, null, null);
			sc.close();
		} catch (YarnException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	       		   
		 
		  //Flex Up, if necessary
	       int numPS = Integer.parseInt(event.getNumPS());
	       if(numPS != 0) {
	    	   
	    	   Map<String, List<String>> componentLocalityPrefs = new HashMap<String, List<String>>();
	    	   
	    	   List<String> psLocalityPrefs = new ArrayList<String>();
	    	   for (String hostname : event.getPsLocalityPrefs().trim().split(",")) {
	    	          hostname = hostname.trim();
	    	          psLocalityPrefs.add(hostname);
	    	   }
	    	  
	    	   List<String> masterLocalityPrefs = new ArrayList<String>(); 
	    	   List<String> workerLocalityPrefs = new ArrayList<String>();
	 		   String[] workerLocalityPrefArray = event.getWorkerLocalityPrefs().trim().split(",");
	 		   masterLocalityPrefs.add(workerLocalityPrefArray[0]);
			   for (int i = 1 ; i < workerLocalityPrefArray.length; i++) {
				  String hostname = workerLocalityPrefArray[i].trim();
				  workerLocalityPrefs.add(hostname);
			   }
			   componentLocalityPrefs.put("ps", psLocalityPrefs);
			   componentLocalityPrefs.put("master", masterLocalityPrefs);
			   componentLocalityPrefs.put("worker", workerLocalityPrefs);
			   
			   int totalWorkers = workerLocalityPrefArray.length;
			   
			   componentCountStrings.put("worker", Integer.toString(totalWorkers -1 ));
			   componentCountStrings.put("master", "1");
			   componentCountStrings.put("ps", event.getNumPS());			   
			   
			   Map<String, String> componentTfConfigStrings = new HashMap<String, String>();
			      	   
	    	   String yarnDNSDomain = conf.get("hadoop.registry.dns.domain-name");
	    	   
	    	   componentTfConfigStrings.put("ps", getTFConfigEnv(
	    			      "ps", Integer.parseInt(event.getNumWorker()),
	    			      Integer.parseInt(event.getNumPS()), event.getJobName(), getUserName(),
	    			      yarnDNSDomain));
	    
	    	   componentTfConfigStrings.put("master", getTFConfigEnv(
	    			      "master", Integer.parseInt(event.getNumWorker()),
	    			      Integer.parseInt(event.getNumPS()), event.getJobName(), getUserName(),
	    			      yarnDNSDomain));
	    	   
	    	   componentTfConfigStrings.put("worker", getTFConfigEnv(
	    			      "worker", Integer.parseInt(event.getNumWorker()),
	    			      Integer.parseInt(event.getNumPS()), event.getJobName(), getUserName(),
	    			      yarnDNSDomain));
	    	   
			   ServiceClient sc1 = new ServiceClient();
		       sc1.init(conf);
		       sc1.start();
		       try {
				int result = sc1
				       .actionFlexThemis(event.getJobName(), componentCountStrings, componentTfConfigStrings, componentLocalityPrefs);
				sc1.close();
			} catch (YarnException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}	    	   
	    	   
	    	   
	    	   
	       }
		  
		  
	  }
	  
	  public void handleTerminate(ThemisEvent event) {
		  
	  }
	
}