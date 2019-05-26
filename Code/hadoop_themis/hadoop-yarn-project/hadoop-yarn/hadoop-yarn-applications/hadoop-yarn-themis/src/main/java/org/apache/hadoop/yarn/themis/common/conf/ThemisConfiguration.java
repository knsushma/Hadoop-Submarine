package org.apache.hadoop.yarn.themis.common.conf;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;

public class ThemisConfiguration extends Configuration {

  private static final Log LOG = LogFactory.getLog(ThemisConfiguration.class);
  private static final String THEMIS_CONFIGURATION_FILE = "themis-site.xml";

  static {
    Configuration.addDefaultResource(THEMIS_CONFIGURATION_FILE);
  }

  /*
   * Themis Service Configurations
   */

  private static final String PREFIX = "themis.";

  public static final String THEMIS_ENABLED = PREFIX + "enabled";
  public static final boolean DEFAULT_THEMIS_ENABLED = false;
  
  public static final String THEMIS_CLUSTER_DETAILS = PREFIX + "cluster.details.json.path";
  public static final String DEFAULT_THEMIS_CLUSTER_DETAILS = "/users/kshiteej/workload/cluster.json";
  
  public static final String THEMIS_JOBS_DETAILS = PREFIX + "jobs.details.json.path";
  public static final String DEFAULT_THEMIS_JOBS_DETAILS = "/users/kshiteej/workload/jobs.json";  
  
  public static final String THEMIS_WORKLOAD_DETAILS = PREFIX + "workload.details.json.path";
  public static final String DEFAULT_THEMIS_WORKLOAD_DETAILS = "/users/kshiteej/workload/workload.json";
  
  public static final String THEMIS_POLICY = PREFIX + "policy";
  public static final String DEFAULT_THEMIS_POLICY = "themis";
  
  public static final String THEMIS_LEASE = PREFIX + "lease.mins";
  public static final double DEFAULT_THEMIS_LEASE = 5.0; 
  
  public static final String THEMIS_FAIRNESS_KNOB = PREFIX + "fairness.knob";
  public static final double DEFAULT_THEMIS_FAIRNESS_KNOB = 0.2; 
  
  public boolean isThemisEnabled() {
    return getBoolean(THEMIS_ENABLED, DEFAULT_THEMIS_ENABLED);
  }
  
  public String getClusterJSONPath() {
	return get(THEMIS_CLUSTER_DETAILS, DEFAULT_THEMIS_CLUSTER_DETAILS);
  }
  
  public String getJobsJSONPath() {
	return get(THEMIS_JOBS_DETAILS, DEFAULT_THEMIS_JOBS_DETAILS);
  }
  
  public String getWorkloadJSONPath() {
	return get(THEMIS_WORKLOAD_DETAILS, DEFAULT_THEMIS_WORKLOAD_DETAILS);
  }  
  
  public String getPolicy() {
	return get(THEMIS_POLICY, DEFAULT_THEMIS_POLICY);
  }   
  
  public double getLease() {
	    return getDouble(THEMIS_LEASE, DEFAULT_THEMIS_LEASE);
	  }
  
  public double getFairnessKnob() {
	    return getDouble(THEMIS_FAIRNESS_KNOB, DEFAULT_THEMIS_FAIRNESS_KNOB);
	  }  
  
  
}
