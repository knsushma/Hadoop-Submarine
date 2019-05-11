#!/bin/bash

VER=3.2.0
TIMEOUT=5
THREADS=2

COMMON_VERSION=${COMMON_VERSION:-${VER}}
HDFS_VERSION=${HDFS_VERSION:-${VER}}
YARN_VERSION=${YARN_VERSION:-${VER}}
HIVE_VERSION=${HIVE_VERSION:-1.2.1}
TEZ_VERSION=${TEZ_VERSION:-0.7.1-SNAPSHOT-minimal}

ENV="JAVA_HOME=/usr/lib/jvm/java-1.8.0-openjdk-1.8.0.201.b09-2.el7_6.x86_64 \
  YARN_HOME=/home/hadoop/software/hadoop-${YARN_VERSION} \
  HADOOP_LOG_DIR=/users/asinghvi/logs/hadoop \
  HADOOP_CONF_DIR=/users/asinghvi/conf \
  HADOOP_USER_CLASSPATH_FIRST=1 \
  HADOOP_COMMON_HOME=/users/asinghvi/software/hadoop-${COMMON_VERSION} \
  HADOOP_HDFS_HOME=/users/asinghvi/software/hadoop-${HDFS_VERSION} \
  HADOOP_YARN_HOME=/users/asinghvi/software/hadoop-${YARN_VERSION} \
  HADOOP_HOME=/users/asinghvi/software/hadoop-${COMMON_VERSION} \
  HADOOP_MAPRED_HOME=/users/asinghvi/software/hadoop-${COMMON_VERSION} \
  HADOOP_BIN_PATH=/users/asinghvi/software/hadoop-${COMMON_VERSION}/bin \
  HADOOP_SBIN=/users/asinghvi/software/hadoop-${COMMON_VERSION}/bin \
  HIVE_HOME=/users/asinghvi/software/hive-1.2.1 \
  TEZ_CONF_DIR=/users/asinghvi/software/conf \
  TEZ_JARS=/users/asinghvi/software/tez-${TEZ_VERSION}"

case "$1" in
  (-q|--quiet)
    for i in ${ENV}
    do
      export $i
    done
    ;;
  (*)
    echo "setting variables:"
    for i in $ENV
    do
      echo $i
      export $i
    done
    ;;
esac

export HADOOP_CLASSPATH=$HADOOP_HOME:$HADOOP_CONF_DIR:$HIVE_HOME:$TEZ_JARS/*:$TEZ_JARS/lib/*:
export HADOOP_HEAPSIZE=10240

export PATH=/users/asinghvi/software/hadoop-${COMMON_VERSION}/bin:/users/asinghvi/software/hadoop-${COMMON_VERSION}/sbin:$HIVE_HOME/bin:$PATH
export LD_LIBRARY_PATH=${HADOOP_COMMON_HOME}/share/hadoop/common/lib/native/:${LD_LIBRARY_PATH}
export JAVA_LIBRARY_PATH=${LD_LIBRARY_PATH}


start_hdfs(){
	printf "\n==== START HDFS daemons ! ====\n"
	#hadoop-daemon.sh start namenode
	hdfs --daemon start namenode
        pdsh -R exec -f $THREADS -w ^instances ssh -o ConnectTimeout=$TIMEOUT %h '( . /users/asinghvi/run.sh -q ; hdfs --daemon start datanode;)'
	hdfs dfsadmin -safemode leave
}

stop_hdfs(){
	printf "\n==== STOP HDFS daemons ! ====\n"
	pdsh -R exec -f $THREADS -w ^instances ssh -o ConnectTimeout=$TIMEOUT %h '( . /users/asinghvi/run.sh -q ; hdfs --daemon stop datanode;)'
	hdfs --daemon stop namenode
}

start_yarn(){
	printf "\n===== START YARN daemons ! ====\n"
	yarn --daemon start resourcemanager
	pdsh -R exec -f $THREADS -w ^instances ssh -o ConnectTimeout=$TIMEOUT %h '( . /users/asinghvi/run.sh -q ; yarn --daemon start nodemanager;)'
	yarn --daemon start registrydns
}
 
stop_yarn(){
	printf "\n==== STOP YARN daemons ! ====\n"
	pdsh -R exec -f $THREADS -w ^instances ssh -o ConnectTimeout=$TIMEOUT %h '( . /users/asinghvi/run.sh -q ; yarn --daemon stop nodemanager;)'
	yarn --daemon stop resourcemanager
	yarn --daemon stop registrydns
}

start_history_mr(){
	printf "\n==== START M/R history server ! ====\n"
	mapred --daemon	start historyserver
}

stop_history_mr(){
	printf "\n==== STOP M/R history server ! ====\n"
	mapred --daemon	stop historyserver
}

start_timeline_server(){
	printf "\n==== START timelineserver ! ====\n"
	yarn --daemon start timelineserver
}

stop_timeline_server(){
	printf "\n==== STOP timelineserver ! ====\n"
	yarn --daemon stop timelineserver
}

start_etcd(){
	sudo systemctl restart etcd.service
}

status_etcd(){
	echo " ===== Check the status of the etcd service ====="
	echo " exec etcdctl cluster-health"
	etcdctl cluster-health
	echo " exec etcdctl cluster-health"
	etcdctl member list
}

start_docker(){
	sudo systemctl restart docker
}

status_docker(){
	sudo systemctl status docker
	docker info
}

start_calico(){
	sudo systemctl restart calico-node.service
}

status_calico(){
	sudo systemctl status calico-node.service
	sudo calicoctl node status
}

start_zookeeper(){
	/users/asinghvi/software/zookeeper-3.4.14/bin/zkServer.sh start
}

status_zookeeper(){
	/users/asinghvi/software/zookeeper-3.4.14/bin/zkServer.sh status
}

start_all(){
	start_zookeeper
	start_hdfs
	start_yarn
	start_timeline_server
	start_history_mr
}

stop_all(){
	stop_hdfs
	stop_yarn
	stop_timeline_server
	stop_history_mr
}

export -f start_hdfs
export -f start_yarn
export -f start_all
export -f stop_hdfs
export -f stop_yarn
export -f stop_all
export -f start_history_mr
export -f stop_history_mr
export -f start_timeline_server
export -f stop_timeline_server
export -f start_etcd
export -f status_etcd
export -f start_docker
export -f status_docker
export -f start_calico
export -f status_calico
export -f start_zookeeper
export -f status_zookeeper
