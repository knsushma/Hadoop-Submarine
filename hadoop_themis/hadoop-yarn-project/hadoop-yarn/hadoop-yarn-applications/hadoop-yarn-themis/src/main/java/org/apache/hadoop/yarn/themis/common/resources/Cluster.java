package org.apache.hadoop.yarn.themis.common.resources;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class Cluster {
    public List<GPU> mGpusInCluster;
    public List<Job> mRunningJobs;
    public List<Job> mRunnableJobs;
    public List<Job> mCompletedJobs;
    
    public HashMap<Integer, String> MachineIDHostName;
    public HashMap<Integer, Integer> MachineIDNumGPUs;    
    
	private String getAttributeValue(JSONObject obj, String attribute) {
		return (String) obj.get(attribute);
	}    
    
    public Cluster(JSONArray jsonCluster) {
      mRunningJobs = new ArrayList<Job>();
      mRunnableJobs = new ArrayList<Job>();
      mCompletedJobs = new ArrayList<Job>();
      
      MachineIDHostName = new HashMap<Integer, String>();
      MachineIDNumGPUs = new HashMap<Integer, Integer>();
      mGpusInCluster = new ArrayList<GPU>();
      
      int gid = 0;
    	
  	  for(Object obj : jsonCluster) {
  		  int id = Integer.parseInt(getAttributeValue((JSONObject)obj, "id"));
  		  String hostname = getAttributeValue((JSONObject)obj, "hostname");
  		  int numGPUs = Integer.parseInt(getAttributeValue((JSONObject)obj, "num_gpus"));
  		  
  		  for(int i = 0; i < numGPUs; i++) {
  			mGpusInCluster.add(new GPU(gid++, id, hostname));
  		  }
  	  
  		  MachineIDHostName.put(id, hostname);
  		  MachineIDNumGPUs.put(id, numGPUs);	  
  	  }    	
    	
    	
    }
    
    
}