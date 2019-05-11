package org.apache.hadoop.yarn.themis.policies;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.yarn.themis.ThemisMasterService;
import org.apache.hadoop.yarn.themis.common.resources.Cluster;
import org.apache.hadoop.yarn.themis.common.resources.Job;
import org.apache.hadoop.yarn.themis.common.resources.GPU;

public class SysThemis {
    public Cluster mCluster;
    public Arbiter arbiter;
    public ThemisMasterService tms;
    public List<Job> workload;
    public double startTime;
    public double leaseTime;
    public double fairKnob;
    public double interArrivalTime;
    public double MAX_TIME;
    public long EPOCH_TIME;
    
    private static final Log LOG = LogFactory.getLog(SysThemis.class);

    public SysThemis (ThemisMasterService tms, Cluster mCluster, List<Job> workload) {
    	this.tms = tms;
    	this.mCluster = mCluster;
    	this.workload = workload;
    	this.MAX_TIME = 10;
    	this.EPOCH_TIME = 2*60*1000;
    	this.interArrivalTime = 4.0; 
        
    	String policy = tms.themisConfiguration.getPolicy();
    	if(policy.equalsIgnoreCase("Gandiva")) {
    		arbiter = new Gandiva();
    	} else if (policy.equalsIgnoreCase("Tiresias")) {
    		arbiter = new Tiresias();
    	} else if (policy.equalsIgnoreCase("Themis")) {
    		arbiter = new Themis();
    	}
    	
        startTime = 0.0;
        leaseTime = tms.themisConfiguration.getLease();
        fairKnob = tms.themisConfiguration.getFairnessKnob();
        
        workloadGenerator();
    }
    
    public void workloadGenerator() {
    	Job j = workload.get(0);
    	double arrivalTime = 0.0;
    	int counter = 1;
    	while (arrivalTime < MAX_TIME) {
    		Job newj = new Job(j, counter);
    		counter++;
    		arrivalTime = arrivalTime + interArrivalTime;
    		newj.mJobStartTime = arrivalTime;
    		mCluster.mRunnableJobs.add(newj);
    	}
    }

    public void run() throws IOException {
        double startTime = System.currentTimeMillis() / (60 * 1000);
        this.startTime = startTime;
        double currentTime = 0;
        double lastIterUpdate = -1.0;
        while (currentTime < MAX_TIME) {
        	List<Event> events = new ArrayList<Event>();

            // update time
            double nowTime = System.currentTimeMillis() / (60 * 1000);
            currentTime = nowTime - startTime;

            
            // update job iterations
            for (Job job : mCluster.mRunningJobs) {
                // int iter = tms.readIterations("dsa");
            	double deltaIterUpdate = 0.0;
            	if (lastIterUpdate < 0.0) {
            		deltaIterUpdate = 0.0;
            	} else {
            		deltaIterUpdate = currentTime - lastIterUpdate;
            	}
            	job.updateIter(deltaIterUpdate);
//                job.mCurrentIteration = job.mCurrentIteration + (int)(deltaIterUpdate/job.mTimePerIteration);
                lastIterUpdate = currentTime;
            }

            // check if any job arrived
            Iterator<Job> runnableJobs = mCluster.mRunnableJobs.iterator();
            while (runnableJobs.hasNext()) {
                Job job = runnableJobs.next();
                if (job.mJobStartTime <= currentTime) {
                    runnableJobs.remove();
                    job.isLaunch = true;
                    mCluster.mRunningJobs.add(job);
                    // job.mJobStartTime = System.currentTimeMillis()/(60*1000);
                    LOG.info("[TS] - Job added to running jobs ID = " + job.mJobId + "StartTime = " + job.mJobStartTime + " CurrentTime = " + currentTime);
                }
            }

            Set<GPU> availableGPUs = new HashSet<GPU>();

            // check if any job ended
            Iterator<Job> runningJobs = mCluster.mRunningJobs.iterator();
            while (runningJobs.hasNext()) {
                Job job = runningJobs.next();
                if (job.mCurrentIteration >= job.mTotalExpectedIterations) {
                    //eventHandler.killJobEvent(job); //////////// interface with themis master service
                	tms.killJobEvent(job);
                	Iterator<GPU> gpuIter = job.mCurrentIterationGPUs.iterator();
                	while (gpuIter.hasNext()) {
                		GPU gpu = gpuIter.next();
                		gpuIter.remove();
                        gpu.init();
                    	availableGPUs.add(gpu);
                    }
                    runningJobs.remove();
                    mCluster.mCompletedJobs.add(job);
                    job.mFinishTime = System.currentTimeMillis()/(60*1000) - startTime;
                    tms.killJobEvent(job);
                }
            }

            // check if any lease expired
            Map<Job, List<GPU>> jobChanges = new HashMap<Job, List<GPU>>();
            for (GPU gpu : mCluster.mGpusInCluster) {
                if ((gpu.mLeaseEnd != -1.0) && (gpu.mLeaseEnd <= currentTime)) {
                	Job jobDelta = gpu.mJobUsingGPU;
                	jobDelta.mCurrentIterationGPUs.remove(gpu);
                	if (jobChanges.containsKey(jobDelta)) {
                		jobChanges.get(jobDelta).add(gpu);
                	} else {
                		List<GPU> gpuList = new ArrayList<GPU>();
                		gpuList.add(gpu);
                		jobChanges.put(jobDelta, gpuList);
                	}
                    gpu.init();
                    availableGPUs.add(gpu);
                }
            }
            for (Job job: jobChanges.keySet()) {
            	job.updatePrefs(job.mCurrentIterationGPUs);
            	tms.changeJobEvent(job);
            }

            // run simulator logic and get events
            List<Event> schedEvents = arbiter.runLogic(this);
            events.addAll(schedEvents);
            for (Event event : events) {
                if (event.eventType.equals("killJob")) {
                    //eventHandler.killJobEvent(event.job); //////////// interface with themis master service
                	event.job.mFinishTime = System.currentTimeMillis()/(60*1000) - startTime;
                	tms.killJobEvent(event.job);
                } else if (event.eventType.equals("launchJob")) {
                    //eventHandler.launchJobEvent(event.job, event.arguments); //////////// interface with themis master service
                	tms.launchJobEvent(event.job);
                	event.job.isLaunch = false;
                } else if (event.eventType.equals("changeJob")) {
                    //eventHandler.changeJobEvent(event.job, event.arguments);
                	tms.changeJobEvent(event.job);
                }
            }

            try {
                Thread.sleep((long) EPOCH_TIME);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        
        LOG.info("Running Jobs.");
        for (Job job: mCluster.mRunningJobs) {
        	currentTime = System.currentTimeMillis()/(60*1000) - startTime;
        	double deltaIterUpdate = currentTime - lastIterUpdate;
        	job.updateIter(deltaIterUpdate);
        	LOG.info("JobID " + job.mJobId + " JCT " + (currentTime - job.mJobStartTime) + 
        			" Rho " + (currentTime - job.mJobStartTime)/job.mTi + 
        			" Iteration " + job.mCurrentIteration + " currentTime = " + currentTime + " JstartTime = " + job.mJobStartTime + " Ts = " + job.mTs + "Ti = " + job.mTi);
        }
        
        LOG.info("Completed Jobs.");
        for (Job job: mCluster.mCompletedJobs) {
        	LOG.info("JobID " + job.mJobId + " JCT " + (job.mFinishTime - job.mJobStartTime) + " Rho " + (job.mFinishTime - job.mJobStartTime)/job.mTi + " Ts = " + job.mTs + "Ti = " + job.mTi);
        }
    }
}