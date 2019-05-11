package org.apache.hadoop.yarn.themis;

import java.util.List;

import org.apache.hadoop.yarn.event.AbstractEvent;

public class ThemisEvent extends AbstractEvent<ThemisEventType>{
	private final String jobName; 
	private final ThemisEventType type;
	private String WorkerDockerImage;
	private String PsDockerImage;
	private String WorkerResources;
	private String PsResources;
	private String NumWorker;
	private String NumPS;
	private String WorkerLaunchCmd;
	private String PSLaunchCmd;
	private String WorkerLocalityPrefs;
	private String PsLocalityPrefs;
	private String InputDataPath;
	private String CheckpointDataPath;
	
	public ThemisEvent(String jobName,ThemisEventType type) {
		super(type);
		this.jobName = jobName;
		this.type = type;
	}
	
	public ThemisEventType getType() {
		return type;
	}
	
	public String getJobName() {
		return jobName;
	}

	public ThemisEvent setWorkerDockerImage(String WorkerDockerImage) {
		this.WorkerDockerImage = WorkerDockerImage;
		return this;
	}	
	
	public String getWorkerDockerImage() {
		return WorkerDockerImage;
	}

	public ThemisEvent setPsDockerImage(String PsDockerImage) {
		this.PsDockerImage = PsDockerImage;
		return this;
	}	
	
	public String getPsDockerImage() {
		return PsDockerImage;
	}
	
	public ThemisEvent setWorkerResources(String WorkerResources) {
		this.WorkerResources = WorkerResources;
		return this;
	}		
	
	public String getWorkerResources() {
		return WorkerResources;
	}
	
	public ThemisEvent setPsResources(String PsResources) {
		this.PsResources = PsResources;
		return this;
	}		
	
	public String getPsResources() {
		return PsResources;
	}	
	
	public ThemisEvent setNumWorker(String NumWorker) {
		this.NumWorker = NumWorker;
		return this;
	}		
	
	public String getNumWorker() {
		return NumWorker;
	}
	
	public ThemisEvent setNumPS(String NumPS) {
		this.NumPS = NumPS;
		return this;
	}		
	
	public String getNumPS() {
		return NumPS;
	}
	
	public ThemisEvent setWorkerLaunchCmd(String WorkerLaunchCmd) {
		this.WorkerLaunchCmd = WorkerLaunchCmd;
		return this;
	}			
	
	public String getWorkerLaunchCmd() {
		return WorkerLaunchCmd;
	}
	
	public ThemisEvent setPSLaunchCmd(String PSLaunchCmd) {
		this.PSLaunchCmd = PSLaunchCmd;
		return this;
	}		
	
	public String getPSLaunchCmd() {
		return PSLaunchCmd;
	}
	
	public ThemisEvent setWorkerLocalityPrefs(String WorkerLocalityPrefs) {
		this.WorkerLocalityPrefs = WorkerLocalityPrefs;
		return this;
	}		
	
	public String getWorkerLocalityPrefs() {
		return WorkerLocalityPrefs;
	}
	
	public ThemisEvent setPsLocalityPrefs(String PsLocalityPrefs) {
		this.PsLocalityPrefs = PsLocalityPrefs;
		return this;
	}		
	
	public String getPsLocalityPrefs() {
		return PsLocalityPrefs;
	}
	
	public ThemisEvent setInputDataPath(String InputDataPath) {
		this.InputDataPath = InputDataPath;
		return this;
	}		
	
	public String getInputDataPath() {
		return InputDataPath;
	}
	
	public ThemisEvent setCheckpointDataPath(String CheckpointDataPath) {
		this.CheckpointDataPath = CheckpointDataPath;
		return this;
	}		
	
	public String getCheckpointDataPath() {
		return CheckpointDataPath;
	}		
}
