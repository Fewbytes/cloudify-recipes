/*******************************************************************************
* Copyright (c) 2012 GigaSpaces Technologies Ltd. All rights reserved
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*       http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*******************************************************************************/
import org.cloudifysource.dsl.context.ServiceContextFactory


		/* 
			This custom command enables users to add a server (to the 1st server).
			Usage :  invoke couchbase addServer newServerHost newServerPort
			Example: invoke couchbase addServer 1234.543.556.33 8097 
		*/

println "couchbase_addServer.groovy: Starting ..."

context = ServiceContextFactory.getServiceContext()

def instanceID = context.instanceId
if ( instanceID != 1 ) {
	return
}

def scriptsFolder = context.attributes.thisInstance["scriptsFolder"]

def addServerScript="${scriptsFolder}/addServer.sh"
def firstInstancePort = context.attributes.thisInstance["currentPort"]

def clusterAdmin = context.attributes.thisInstance["couchbaseUser"]
def clusterPassword = context.attributes.thisInstance["couchbasePassword"]
def newServerHost = args[0]
println "couchbase_addServer.groovy: newServerHost is " + newServerHost
def newServerPort = args[1]
println "couchbase_addServer.groovy: newServerPort is " + newServerPort
newServerAdmin =  args[2]
println "couchbase_addServer.groovy: newServerAdmin is " + newServerAdmin
newServerPassword = args[3] 
println "couchbase_addServer.groovy: newServerPassword is " + newServerPassword


builder = new AntBuilder()
builder.sequential {	
	echo(message:"couchbase_addServer.groovy: Running ${addServerScript} ...")
	exec(executable:"${addServerScript}", failonerror: "true") {
		arg(value:"localhost")
		arg(value:firstInstancePort)	
		arg(value:"${clusterAdmin}")	
		arg(value:"${clusterPassword}")	
		arg(value:"${newServerHost}")	
		arg(value:"${newServerPort}")		
		arg(value:"${newServerAdmin}")	
		arg(value:"${newServerPassword}")	
	}
}	



println "couchbase_addServer.groovy: End of addServer script"