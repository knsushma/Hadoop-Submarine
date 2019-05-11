/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.yarn.themis;

import java.text.SimpleDateFormat;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.MessageFormat;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;

import java.util.regex.Pattern;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.math3.util.MultidimensionalCounter.Iterator;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.FSDataInputStream;

import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.api.records.ApplicationReport;
import org.apache.hadoop.yarn.api.records.YarnApplicationState;
import org.apache.hadoop.yarn.client.cli.ApplicationCLI;
import org.apache.hadoop.yarn.client.cli.LogsCLI;
import org.apache.hadoop.yarn.themis.common.conf.ThemisConfiguration;
import org.apache.hadoop.yarn.themis.common.resources.Cluster;
import org.apache.hadoop.yarn.themis.common.resources.Job;
import org.apache.hadoop.yarn.themis.policies.SysThemis;
import org.apache.hadoop.yarn.themis.ThemisEventType;

import org.apache.hadoop.metrics2.lib.DefaultMetricsSystem;
import org.apache.hadoop.security.SecurityUtil;
import org.apache.hadoop.service.CompositeService;
import org.apache.hadoop.util.ShutdownHookManager;
import org.apache.hadoop.util.StringUtils;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.event.AsyncDispatcher;
import org.apache.hadoop.yarn.event.EventHandler;
import org.apache.hadoop.yarn.exceptions.YarnRuntimeException;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class ThemisMasterService extends CompositeService {

	public static final int SHUTDOWN_HOOK_PRIORITY = 30;
	private static final Log LOG = LogFactory.getLog(ThemisMasterService.class);

	public AsyncDispatcher dispatcher;
	private ThemisEventManager manager;

	public ThemisConfiguration themisConfiguration;
	private YarnConfiguration yarnConfiguration;

	public Cluster cluster;
	public List<Job> workload;
	public SysThemis themisSystem;


	public ThemisMasterService(String name) {
		super(name);
	}

	protected void doSecureLogin() throws IOException {
		SecurityUtil.login(getConfig(), YarnConfiguration.NM_KEYTAB, YarnConfiguration.NM_PRINCIPAL);
	}

	protected AsyncDispatcher createAsyncDispatcher() {
		return new AsyncDispatcher("Themis Dispatcher");
	}

	@Override
	protected void serviceInit(Configuration conf) throws Exception {
		LOG.info("[Themis] - serviceInit method called ...");
		DefaultMetricsSystem.initialize("ThemisMasterService");

		manager = new ThemisEventManager();

		dispatcher = createAsyncDispatcher();
		dispatcher.register(ThemisEventType.class, new ThemisEventHandler());
		dispatcher.setDrainEventsOnStop();
		boolean addedDispatcher = addIfService(dispatcher);

		LOG.info("[Themis] - dispatcher added as service - " + addedDispatcher);

		themisConfiguration = new ThemisConfiguration();
		yarnConfiguration = new YarnConfiguration();
        /*
	String clusterFile = themisConfiguration.getClusterJSONPath();
	JSONParser clusterParser = new JSONParser();
	JSONArray jsonCluster = (JSONArray) clusterParser.parse(new FileReader(clusterFile));
    cluster = new Cluster(jsonCluster);

    workload = new ArrayList<Job>();
    String workloadFile = themisConfiguration.getWorkloadJSONPath();
	JSONParser workloadParser = new JSONParser();
	JSONArray jsonWorkload = (JSONArray) workloadParser.parse(new FileReader(workloadFile));
	loadWorkload(jsonWorkload);

    LOG.info("Cluster = " + jsonCluster.toString());
    LOG.info("Workload = " + jsonWorkload.toString());

	themisSystem = new SysThemis(this, cluster, workload);
	themisSystem.mCluster.mRunnableJobs.addAll(workload);
*/
		super.serviceInit(conf);
	}

	public void loadWorkload(JSONArray jsonWorkload) {

		for(Object obj : jsonWorkload) {
			Job j = new Job();
			j.mJobId = Integer.parseInt(getAttributeValue((JSONObject)obj, "job_id"));
			j.mJobStartTime = Double.parseDouble(getAttributeValue((JSONObject)obj, "start_time"));
			j.mMaxParallelism = Integer.parseInt(getAttributeValue((JSONObject)obj, "max_parallelism"));
			j.jobName = getAttributeValue((JSONObject)obj, "job_name");
			j.WorkerDockerImage = getAttributeValue((JSONObject)obj, "worker_image");
			j.PsDockerImage = getAttributeValue((JSONObject)obj, "ps_image");
			j.WorkerResources = getAttributeValue((JSONObject)obj, "worker_resources");
			j.PsResources = getAttributeValue((JSONObject)obj, "ps_resources");
			j.NumWorker = getAttributeValue((JSONObject)obj, "num_workers");
			j.NumPS = getAttributeValue((JSONObject)obj, "num_ps");
			j.WorkerLaunchCmd = getAttributeValue((JSONObject)obj, "worker_cmd");
			j.PSLaunchCmd = getAttributeValue((JSONObject)obj, "ps_cmd");
			j.InputDataPath = getAttributeValue((JSONObject)obj, "input_data_path");
			j.CheckpointDataPath = getAttributeValue((JSONObject)obj, "checkpoint_data_path");
			j.mTotalExpectedIterations = Integer.parseInt(getAttributeValue((JSONObject)obj, "total_iterations"));
			workload.add(j);
		}

	}

	@Override
	protected void serviceStart() throws Exception {
		try {
			doSecureLogin();
			super.serviceStart();

		} catch (IOException e) {
			throw new YarnRuntimeException("Failed ThemisMasterService login", e);
		}
	}

	@Override
	protected void serviceStop() throws Exception {
		LOG.info("Stopping ThemisMasterService ...");
		super.serviceStop();
	}

	private final class ThemisEventHandler
			implements EventHandler<ThemisEvent> {
		@Override
		public void handle(ThemisEvent event) {
			try {
				LOG.info("[Themis] Received EventType : " + event.getType());
				manager.handle(event);
			} catch (Throwable t) {
				LOG.error(MessageFormat
						.format("[Themis] Error in handling event type {0}",
								event.getType()), t);
			}
		}
	}

	private String getAttributeValue(JSONObject obj, String attribute) {
		return (String) obj.get(attribute);
	}

	public ThemisEvent generateLaunchEvent(String jobName, String workerDockerImage, String psDockerImage,
										   String workerResources, String psResources, String numWorkers, String numPS, String workerLaunchCmd,
										   String psLaunchCmd, String workerLocalityPref, String psLocalityPref, String inputDataPath, String checkpointDataPath) {

		ThemisEvent event = new ThemisEvent(jobName, ThemisEventType.LAUNCH)
				.setInputDataPath(inputDataPath)
				.setCheckpointDataPath(checkpointDataPath)
				.setWorkerDockerImage(workerDockerImage)
				.setPsDockerImage(psDockerImage)
				.setWorkerResources(workerResources)
				.setPsResources(psResources)
				.setNumWorker(numWorkers)
				.setNumPS(numPS)
				.setWorkerLaunchCmd(workerLaunchCmd)
				.setPSLaunchCmd(psLaunchCmd)
				.setWorkerLocalityPrefs(workerLocalityPref)
				.setPsLocalityPrefs(psLocalityPref);

		return event;
	}

	public ThemisEvent generateChangeEvent(String jobName, String numWorkers, String numPS,
										   String workerLocalityPref, String psLocalityPref) {

		ThemisEvent event = new ThemisEvent(jobName, ThemisEventType.CHANGE)
				.setNumWorker(numWorkers)
				.setNumPS(numPS)
				.setWorkerLocalityPrefs(workerLocalityPref)
				.setPsLocalityPrefs(psLocalityPref);

		return event;
	}

	public ThemisEvent generateTerminateEvent(String jobName) {

		ThemisEvent event = new ThemisEvent(jobName, ThemisEventType.TERMINATE);
		return event;
	}

	public void killJobEvent(Job j) {

		ThemisEvent event = generateChangeEvent(j.jobName, "0", "0", null, null); //Should replace this with actual kill using ServiceClient -- will do in the morning

		dispatcher.getEventHandler().handle(event);

	}

	public void launchJobEvent(Job j) {

		ThemisEvent event = generateLaunchEvent(j.jobName, j.WorkerDockerImage, j.PsDockerImage, j.WorkerResources, j.PsResources,
				j.NumWorker, j.NumPS, j.WorkerLaunchCmd, j.PSLaunchCmd, j.WorkerLocalityPrefs, j.PsLocalityPrefs,
				j.InputDataPath, j.CheckpointDataPath);

		dispatcher.getEventHandler().handle(event);

	}

	public void changeJobEvent(Job j) {

		ThemisEvent event = generateChangeEvent(j.jobName, j.NumWorker, j.NumPS, j.WorkerLocalityPrefs, j.PsLocalityPrefs);

		dispatcher.getEventHandler().handle(event);


	}

	public void printSystemEnv() {
		for (Map.Entry<String, String> envs : System.getenv().entrySet()) {
			LOG.info(envs.getKey().toString() + " = " + envs.getValue().toString());
		}
	}

	public void generateWorkloadTest() throws InterruptedException {
		LOG.info("printing system environments");
		//printSystemEnv();
		int perEventIteration = 200;
		ThemisEvent event = generateLaunchEvent("tf-job-001",
				"local/tf-step", "local/tf-step",
				"memory=4G,vcores=2", "memory=4G,vcores=2",
				"3", "1",
				"cd /test/models/tutorials/image/cifar10_estimator && python cifar10_main.py --data-dir=%input_path% --job-dir=%checkpoint_path% --train-steps=100000 --eval-batch-size=16 --train-batch-size=16 --num-gpus=0",
				"cd /test/models/tutorials/image/cifar10_estimator && python cifar10_main.py --data-dir=%input_path% --job-dir=%checkpoint_path% --num-gpus=0",
				"clnode145.clemson.cloudlab.us,clnode152.clemson.cloudlab.us,clnode173.clemson.cloudlab.us",
				"clnode152.clemson.cloudlab.us",
				"hdfs://10.10.1.1/dataset/cifar-10-data", "hdfs://10.10.1.1/tf-job-001");

		ThemisEvent event2 = generateChangeEvent("tf-job-001",
				"6", "1",
//                              "", "");
				"clnode145.clemson.cloudlab.us,clnode152.clemson.cloudlab.us,clnode173.clemson.cloudlab.us,clnode145.clemson.cloudlab.us,clnode152.clemson.cloudlab.us,clnode173.clemson.cloudlab.us",
				"clnode152.clemson.cloudlab.us");
//              "clnode083.clemson.cloudlab.us,clnode083.clemson.cloudlab.us,clnode083.clemson.cloudlab.us,clnode083.clemson.cloudlab.us,clnode083.clemson.cloudlab.us",
//              "clnode073.clemson.cloudlab.us");


		ThemisEvent event3 = generateChangeEvent("tf-job-001",
				"9", "1",
//                              "", "");
				"clnode145.clemson.cloudlab.us,clnode152.clemson.cloudlab.us,clnode173.clemson.cloudlab.us,clnode145.clemson.cloudlab.us,clnode152.clemson.cloudlab.us,clnode173.clemson.cloudlab.us" +
						",clnode145.clemson.cloudlab.us,clnode152.clemson.cloudlab.us,clnode173.clemson.cloudlab.us",
				"clnode152.clemson.cloudlab.us");

		ThemisEvent event4 = generateChangeEvent("tf-job-001",
				"12", "1",
//                              "", "");
				"clnode145.clemson.cloudlab.us,clnode152.clemson.cloudlab.us,clnode173.clemson.cloudlab.us, clnode145.clemson.cloudlab.us,clnode152.clemson.cloudlab.us,clnode173.clemson.cloudlab.us" +
						",clnode145.clemson.cloudlab.us,clnode152.clemson.cloudlab.us,clnode173.clemson.cloudlab.us,clnode145.clemson.cloudlab.us,clnode152.clemson.cloudlab.us,clnode173.clemson.cloudlab.us",
				"clnode152.clemson.cloudlab.us");

		ThemisEvent event5 = generateChangeEvent("tf-job-001",
				"3", "1",
//                              "", "");
				"clnode145.clemson.cloudlab.us,clnode152.clemson.cloudlab.us,clnode173.clemson.cloudlab.us",
				"clnode152.clemson.cloudlab.us");
//              "clnode083.clemson.cloudlab.us,clnode083.clemson.cloudlab.us,clnode083.clemson.cloudlab.us,clnode083.clemson.cloudlab.us,clnode083.clemson.cloudlab.us",
//              "clnode073.clemson.cloudlab.us");

		ThemisEvent event6 = generateChangeEvent("tf-job-001",
				"0", "0",
                              "", "");

		ThemisEvent[] themisEvents = {event, event2, event3, event4, event5, event6};

	  /*
	  ThemisEvent event2 = generateLaunchEvent("tf-job-002",
			  "10.10.1.1:5000/tb-cpu", "10.10.1.1:5000/tb-cpu",
			  "memory=4G,vcores=2", "memory=4G,vcores=2",
			  "2", "1",
			  "cd /test/models/tutorials/image/cifar10_estimator && python cifar10_main.py --data-dir=%input_path% --job-dir=%checkpoint_path% --train-steps=10000 --eval-batch-size=16 --train-batch-size=16 --num-gpus=0 --sync",
			  "cd /test/models/tutorials/image/cifar10_estimator && python cifar10_main.py --data-dir=%input_path% --job-dir=%checkpoint_path% --num-gpus=0",
			  "clnode059.clemson.cloudlab.us,clnode059.clemson.cloudlab.us", "clnode059.clemson.cloudlab.us",
			  "hdfs://default/dataset/cifar-10-data/cifar-10-data");
	  */
		dispatcher.getEventHandler().handle(event);

		TimeUnit.MINUTES.sleep(2);
		ApplicationCLI cli = new ApplicationCLI();
		Set<String> apptypes = new HashSet<String>();
		EnumSet<YarnApplicationState> appStates = EnumSet
				.noneOf(YarnApplicationState.class);
		appStates.add(YarnApplicationState.RUNNING);
		appStates.add(YarnApplicationState.ACCEPTED);
		appStates.add(YarnApplicationState.SUBMITTED);
		Set<String> appTags = new HashSet<String>();
		ApplicationId applicationId = null;
		try {
			List<ApplicationReport> applicationReports = cli.getApplicationsList(apptypes, appStates, appTags);
			for (ApplicationReport appReport : applicationReports) {
				DecimalFormat formatter = new DecimalFormat("###.##%");
				String progress = formatter.format(appReport.getProgress());
				LOG.info(appReport.getApplicationId() + " " +
						appReport.getName() +" " + appReport.getApplicationType() + " " +  appReport
						.getUser() + " " + appReport.getQueue() + " " + appReport
						.getYarnApplicationState() + " " +
						appReport.getFinalApplicationStatus() + " "  + progress + " " + appReport
						.getOriginalTrackingUrl());
				if(appReport.getName().equals("tf-job-001"))
				{
					applicationId = appReport.getApplicationId();
				}
			}

		}
		catch (Exception ex)
		{
			ex.printStackTrace();
			LOG.error("[Themis] Error in getting application list");
			System.exit(-1);
		}



		if(applicationId != null)
		{
			String[] arguments = new String[2];
			arguments[0] = "-applicationId";
			arguments[1] = applicationId.toString();
			LOG.info("Application id found for application with name: tf-job-001 is :"+applicationId.toString());
			LOG.info("fetching logs for application id:"+applicationId.toString() + " ...");

			Configuration conf = new YarnConfiguration();
			LogsCLI logDumper = new LogsCLI();
			logDumper.setConf(conf);
			int maxStepTillNow = 0;

			int currStep;
			int maxCycleStep = 0;
			try {
				int currEvent = 1;
				while(currEvent < themisEvents.length) {
					LOG.info("Getting aggregated logs ...");
					long startTime = System.nanoTime();
					String aggregatedLogs = logDumper.getAggregatedLogsasString(arguments);
					String lines[] = aggregatedLogs.split("\\r?\\n");
					LOG.info("Number of lines in aggregated logs:"+ lines.length);
					String pattern = ".*INFO:tensorflow:.*loss = (\\d+\\.\\d+).*step = (\\d+).*";
					Pattern p = Pattern.compile(pattern);
					Matcher m;

					for(int i=0;i<lines.length;i++)
					{
						m = p.matcher(lines[i]);
						if(m.find())
						{


							try {
								currStep = Integer.parseInt(m.group(2));
								if(maxStepTillNow<currStep)maxStepTillNow = currStep;
								if(currStep >= perEventIteration * currEvent)
								LOG.info("Matche found for line:"+lines[i]+" .... groups found:"+m.group(1)+ ", "+m.group(2));
							}
							catch (Exception ex)
							{
								ex.printStackTrace();
								LOG.error("Could not capture group while reading aggregated logs in line: "+ lines[i]);
							}

						}
					}
					maxCycleStep = maxStepTillNow;
					long endTime = System.nanoTime();
					LOG.info("Log reading time: "+ (endTime-startTime));
					if(maxStepTillNow >= perEventIteration * currEvent) {
						LOG.info("Max steps till now: "+maxStepTillNow+" Launching event .. "+ currEvent);
						dispatcher.getEventHandler().handle(themisEvents[currEvent]);
						currEvent++;

					}
					else
					{
						LOG.info("Waiting for 1 min");
						TimeUnit.MINUTES.sleep(1);
					}

//				LOG.info(aggregatedLogs);
				}
			}
			catch (Exception ex)
			{
				ex.printStackTrace();
				LOG.error("Error while reading aggregated logs: "+ ex.getStackTrace());
			}

		}

		TimeUnit.MINUTES.sleep(2);
//		printSystemEnv();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

		System.out.println("******************" + sdf + "********************");
		System.out.println("Finised all event experiments");
//		dispatcher.getEventHandler().handle(event2);

		//dispatcher.getEventHandler().handle(new ThemisEvent("Event-1", ThemisEventType.LAUNCH));
		//dispatcher.getEventHandler().handle(new ThemisEvent("Event-2", ThemisEventType.LAUNCH));
		//dispatcher.getEventHandler().handle(new ThemisEvent("Event-3", ThemisEventType.TERMINATE));
		//dispatcher.getEventHandler().handle(new ThemisEvent("Event-4", ThemisEventType.CHANGE));
	}

	public int readIterations(String jobname) throws IOException {
		Path path = new Path("hdfs:///hello-world.txt");
		FileSystem fs = path.getFileSystem(yarnConfiguration);
		FSDataInputStream inputStream = fs.open(path);
		String out = IOUtils.toString(inputStream, "UTF-8");
		LOG.info("Reading file = " + out);

		return 0;
	}

	public static void main(String[] args) {
		try {

			StringUtils.startupShutdownMessage(ThemisMasterService.class, args, LOG);
			ThemisMasterService themisMaster = new ThemisMasterService("Themis Master Service");
			ShutdownHookManager.get().addShutdownHook(new CompositeServiceShutdownHook(themisMaster), SHUTDOWN_HOOK_PRIORITY);
			YarnConfiguration conf = new YarnConfiguration();
			ThemisConfiguration themisConfiguration = new ThemisConfiguration();
			conf.addResource(themisConfiguration);
			conf.addResource(new Path("~/conf/core-site.xml"));
			conf.addResource(new Path("~/conf/hdfs-site.xml"));



			themisMaster.init(conf);
			themisMaster.start();
			themisMaster.generateWorkloadTest();
			//themisMaster.readFile(conf);
			//themisMaster.themisSystem.run();

			LOG.info("Trying to see if Themis is enabled = " + themisConfiguration.isThemisEnabled());

			LOG.info("Done starting services!");

		} catch (Throwable t) {
			LOG.error("Error starting Themis Master Service", t);
			System.exit(1);
		}
	}

}
