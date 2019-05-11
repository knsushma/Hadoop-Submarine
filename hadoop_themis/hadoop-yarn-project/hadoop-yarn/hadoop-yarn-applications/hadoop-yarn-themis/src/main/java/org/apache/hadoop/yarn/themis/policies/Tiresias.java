package org.apache.hadoop.yarn.themis.policies;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.hadoop.yarn.themis.common.resources.GPU;
import org.apache.hadoop.yarn.themis.common.resources.Job;

public class Tiresias implements Arbiter {
    public List<Event> runLogic(SysThemis system) {
        List<Event> events = new ArrayList<Event>();
        
        // get all free GPUs
        List<GPU> availableGPUs = new ArrayList<GPU>();
        for (GPU gpu: system.mCluster.mGpusInCluster) {
            if (gpu.mJobUsingGPU == null) {
                availableGPUs.add(gpu);                
            }
        }

        // initialize next iter GPU allocs
        for (Job job: system.mCluster.mRunningJobs) {
            job.mNextIterationGPUs = job.mCurrentIterationGPUs;
        }

        // allocate free GPUs to Jobs with least attained service
        Map<Job, List<GPU>> allocs = new HashMap<Job, List<GPU>>();
        for (GPU gpu: availableGPUs) {
            double leastAttainedService = Double.MAX_VALUE;
            Job bestJob = null;
            for (Job job: system.mCluster.mRunningJobs) {
                if (job.mMaxParallelism == job.mNextIterationGPUs.size())
            		continue;
                if (job.mAttainedService < leastAttainedService) {
                    leastAttainedService = job.mAttainedService;
                    bestJob = job;
                }
            }
            if (bestJob == null)
            	break;
            bestJob.mNextIterationGPUs.add(gpu);
            bestJob.mAttainedService += system.leaseTime;
            gpu.mJobUsingGPU = bestJob;
            gpu.mLeaseStart = System.currentTimeMillis()/(60*1000) - system.startTime;
            gpu.mLeaseEnd = gpu.mLeaseStart + system.leaseTime;
            if (allocs.containsKey(bestJob)) {
                allocs.get(bestJob).add(gpu);
            } else {
                List<GPU> gpuList = new ArrayList<GPU>();
                gpuList.add(gpu);
                allocs.put(bestJob, gpuList);
            }
        }

        for (Job job: allocs.keySet()) {
        	if (job.isLaunch) {
        		job.NumPS = "1";
        		job.NumWorker = Integer.toString(job.mNextIterationGPUs.size());
        		String wlp = "";
	        	String plp = "";
	        	for (GPU gpu: job.mNextIterationGPUs) {
	        		wlp += gpu.machineHostName + ",";
	        		plp = gpu.machineHostName;
	        	}
	        	wlp = wlp.substring(0, wlp.length() - 1);
	        	job.WorkerLocalityPrefs = wlp;
	        	job.PsLocalityPrefs = plp;
	        	events.add(new Event(job, "launchJob"));
	        	job.isLaunch = false;
	        	job.mCurrentIterationGPUs = job.mNextIterationGPUs;
        	} else {
        		job.NumPS = "1";
	        	job.NumWorker = Integer.toString(job.mNextIterationGPUs.size());
	        	String wlp = "";
	        	String plp = "";
	        	for (GPU gpu: job.mNextIterationGPUs) {
	        		wlp += gpu.machineHostName + ",";
	        		plp = gpu.machineHostName;
	        	}
	        	wlp = wlp.substring(0, wlp.length() - 1);
	        	job.WorkerLocalityPrefs = wlp;
	        	job.PsLocalityPrefs = plp;
	            events.add(new Event(job, "changeJob"));
	            job.mCurrentIterationGPUs = job.mNextIterationGPUs;
        	}
        }
        
        return events;
    }
}