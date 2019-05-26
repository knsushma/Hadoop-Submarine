if [ $# -eq 0 ]
  then
    echo "./submarine-installer UNUSED_PARTITION USERNAME GROUPNAME MASTER_IP PRIVATE_IP1 PRIVATE_IP2 PRIVATE_IP3 NODE_ID"
    exit 1
fi

PWD=$(pwd)
DNS_NAMESERVER=$(cat /etc/resolv.conf | grep nameserver | cut -d" " -f2)

UNUSED_PARTITION=$1
USERNAME=$2
GROUPNAME=$3
MASTER_IP=$4
PRIVATE_IP1=$5
PRIVATE_IP2=$6
PRIVATE_IP3=$7
NODE_ID=$8

echo "Mounting the unused partition for HDFS, YARN and ZOOKEEPER data ..."
#sudo mkfs -t ext3 /dev/$UNUSED_PARTITION
#sudo mount /dev/$UNUSED_PARTITION /workspace
sudo chown -R $USERNAME:$GROUPNAME /workspace

echo "Installing necessary packages for YARN ..."
sudo yum -y install vim
sudo yum -y install java-1.8.0-openjdk-devel
#sudo yum -y install pdsh
sudo yum -y install git
sudo yum -y install jsvc
sudo yum -y install https://dl.fedoraproject.org/pub/epel/epel-release-latest-7.noarch.rpm
sudo yum -y install pdsh

echo "Creating the necessary directory structure for HDFS, YARN and ZOOKEEPER ..."
mkdir -p /home/$USERNAME/software
mkdir -p /home/$USERNAME/workload
mkdir -p /home/$USERNAME/logs/apps
mkdir -p /home/$USERNAME/logs/hadoop
mkdir -p /home/$USERNAME/logs/zookeeper
mkdir -p /workspace/storage/data/local/nm
mkdir -p /workspace/storage/data/local/tmp
mkdir -p /workspace/storage/hdfs/hdfs_dn_dirs
mkdir -p /workspace/storage/hdfs/hdfs_nn_dir
mkdir -p /workspace/storage/data/zookeeper

echo "Downloading HADOOP and ZOOKEEPER binaries ..."
cd /home/$USERNAME/software
wget https://archive.apache.org/dist/hadoop/common/hadoop-3.2.0/hadoop-3.2.0.tar.gz
wget http://apache.mirrors.tds.net/zookeeper/stable/zookeeper-3.4.14.tar.gz
tar -xvzf hadoop-3.2.0.tar.gz
tar -xvzf zookeeper-3.4.14.tar.gz

echo "Modifying configuration files ..."
cd /home/$USERNAME/azure-cluster-setup
cd conf
sed -i s/MASTER_IP/$MASTER_IP/g core-site.xml 
sed -i s/MASTER_IP/$MASTER_IP/g hdfs-site.xml 
sed -i s/MASTER_IP/$MASTER_IP/g hive-site.xml
sed -i s/MASTER_IP/$MASTER_IP/g mapred-site.xml
sed -i s/MASTER_IP/$MASTER_IP/g yarn-site.xml
sed -i s/PRIVATE_IP1/$PRIVATE_IP1/g yarn-site.xml
sed -i s/PRIVATE_IP2/$PRIVATE_IP2/g yarn-site.xml
sed -i s/PRIVATE_IP3/$PRIVATE_IP3/g yarn-site.xml
sed -i s/PRIVATE_IP1/$PRIVATE_IP1/g zoo.cfg
sed -i s/PRIVATE_IP2/$PRIVATE_IP2/g zoo.cfg
sed -i s/PRIVATE_IP3/$PRIVATE_IP3/g zoo.cfg

sed -i s/asinghvi/$USERNAME/g core-site.xml
sed -i s/asinghvi/$USERNAME/g hdfs-site.xml
sed -i s/asinghvi/$USERNAME/g hive-site.xml
sed -i s/asinghvi/$USERNAME/g mapred-site.xml
sed -i s/asinghvi/$USERNAME/g yarn-site.xml
sed -i s/timeseries-PG0/$GROUPNAME/g yarn-site.xml
sed -i s/asinghvi/$USERNAME/g container-executor.cfg
sed -i s/MASTER_IP/$MASTER_IP/g container-executor.cfg
sed -i s/timeseries-PG0/$GROUPNAME/g container-executor.cfg
mv container-executor.cfg /home/$USERNAME/software/hadoop-3.2.0/etc/hadoop/
mv zoo.cfg /home/$USERNAME/software/zookeeper-3.4.14/conf/
cd ..
sed -i s/asinghvi/$USERNAME/g run.sh
chmod +x run.sh

mv conf ~/
mv run.sh ~/

cd /home/$USERNAME/
echo $PRIVATE_IP1 > instances
echo $PRIVATE_IP2 >> instances
echo $PRIVATE_IP3 >> instances

echo $NODE_ID > /workspace/storage/data/zookeeper/myid

mkdir /workspace/storage/docker

#echo "Making changes to the external dependencies installer (ETCD, DOCKER, CALICO) ..."
#cd /home/$USERNAME/cloudlab-submarine-gpu-installer
#git clone https://github.com/hadoopsubmarine/submarine-installer.git 
#cd submarine-installer
#git checkout b7ebe249cab9593dd6437b5d45a4a83a86ba6baa .
#cd ..
#mv submarine-installer other-dependencies-installer 

#sed -i '131,160d' other-dependencies-installer/scripts/utils.sh
#sed -i s/"etcd-\*-linux-amd64"/etcd-v3.3.9-linux-amd64/g other-dependencies-installer/scripts/etcd.sh
#sed -i "110i sudo usermod -a -G docker $USER" other-dependencies-installer/scripts/docker.sh
#sed -i s/PRIVATE_IP1/$PRIVATE_IP1/g other-dependencies-installer.conf
#sed -i s/PRIVATE_IP2/$PRIVATE_IP2/g other-dependencies-installer.conf
#sed -i s/PRIVATE_IP3/$PRIVATE_IP3/g other-dependencies-installer.conf
#sed -i s/DNS_NAMESERVER/$DNS_NAMESERVER/g other-dependencies-installer.conf

#mv other-dependencies-installer.conf other-dependencies-installer/install.conf

#cd /home/$USERNAME/cloudlab-submarine-gpu-installer/other-dependencies-installer 

sudo chown root /home/$USERNAME/software/hadoop-3.2.0/bin/container-executor
sudo chmod 6050 /home/$USERNAME/software/hadoop-3.2.0/bin/container-executor
sudo chown root /home/$USERNAME/software/hadoop-3.2.0/etc/hadoop/container-executor.cfg
sudo chown root /home/$USERNAME/software/hadoop-3.2.0/etc/hadoop
sudo chown root /home/$USERNAME/software/hadoop-3.2.0/etc
sudo chown root /home/$USERNAME/software/hadoop-3.2.0
sudo chown root /home/$USERNAME/software
sudo chown root /home/$USERNAME

echo "MANUALLY TODO -- SETUP PASSWORD-LESS CONNECTION BETWEEN MASTER and SLAVES!"
