# Submarine Stack Setup

Details about the semi-automator need to be added (will add at some point of time -- if not added when you want to use it - please ping me)

## TODOs

* Extend it to more than 3 nodes
* See if the external dependencies can be automated completely (can if etcd, and docker first installed and started, only then install and start calico) 
* Get rid of logging warning which comes when the YARN components are started
* Get rid of the hack used to run docker containers by changing the owner to be root of the entire path from /users/.... [container.cfg] 
* Avoid the 16GB sda1 partition to be the bottleneck
* Try setting up the stack on a GPU cluster 
* Add in details to start local registery and also samples examples (currently in the submarine-dev google doc)


