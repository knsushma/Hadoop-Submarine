package org.apache.hadoop.yarn.themis.policies;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import org.apache.hadoop.yarn.themis.common.resources.GPU;
import org.apache.hadoop.yarn.themis.common.resources.Job;

public class Themis implements Arbiter {
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

        // allocate free GPUs to Jobs with max Ts/Ti old
        // minimize product of Ts/Ti using greedy heuristic
        Map<Job, List<GPU>> allocs = new HashMap<Job, List<GPU>>();
        for (GPU gpu: availableGPUs) {
            PriorityQueue<Job> jobByRho = new PriorityQueue<Job>(10, new JobRhoComparator());
            for (Job job: system.mCluster.mRunningJobs) {
                jobByRho.add(job);
            }
            int size = (int) Math.floor((1.0 - system.fairKnob) * jobByRho.size());
            if (size <= 0) {
                size = 1;
            }
            List<Job> filteredJobs = new ArrayList<Job>();
            while (size > 0) {
                Job job = jobByRho.poll();
                filteredJobs.add(job);
                size = size - 1;
            }
            double bestRho = Double.MAX_VALUE; // lower out of filtered jobs is better
            Job bestJob = null;
            for (Job job: filteredJobs) {
                if (job.mMaxParallelism == job.mNextIterationGPUs.size())
                    continue;
                if (job.mRho < bestRho) {
                    bestRho = job.mRho;
                    bestJob = job;
                }
            }
            if (bestJob == null)
            	continue;
            bestJob.mNextIterationGPUs.add(gpu);
            bestJob.updateRho();
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

class JobRhoComparator implements Comparator<Job> { 
    public int compare(Job j1, Job j2) { 
        double rho1 = j1.mRho;
        double rho2 = j2.mRho;
        return Double.compare(rho2, rho1); 
    } 
} 
