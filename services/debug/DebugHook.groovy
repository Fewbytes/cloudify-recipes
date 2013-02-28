#!/usr/bin/env groovy
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
//TODO: wrap the below in a nice class hierarchy before compiling

import groovy.util.logging.*

class DebugHook {
    def eventLogger = java.util.logging.Logger.getLogger("org.cloudifysource.usm.USMEventLogger.USM")

    def bashCommands = [
            [name:"run-script", comment:"Run the current script",
                command:'$CLOUDIFY_WORKDIR/$DEBUG_TARGET'],
            [name:"edit-script", comment:"Edit the current script",
                command:'vim $CLOUDIFY_WORKDIR/$DEBUG_TARGET'],
            [name:"launch-groovysh", comment:"Launch a groovy shell",
                command:'$HOME/gigaspaces/tools/groovy/bin/groovysh -q'],
            [name:"finish", comment:"Finish debugging (move on to the next lifecycle event)",
                command:'rm $KEEPALIVE_FILE'],
        ]

    def preparationScript = ("""\
logger -i \"Cloudify Debug: beginning run as `whoami` \"
logger -i \"Cloudify Debug: with full id of `id` \"

#preserve the env variables
:>\$HOME/.cloudify_env
printenv | grep -E \"^(CLOUDIFY|USM|LOOKUP)\" | \
    while read var; do echo >>\$HOME/.cloudify_env \"export \$var\"; done

#chmod the actual debug target script
chmod +x \$1

#TODO: fix this
#import extra ssh public key(s) for the debugging connection
CLOUDIFY_WORKDIR=\$HOME/gigaspaces/work/processing-units/\${USM_APPLICATION_NAME}_\${USM_SERVICE_NAME}_\${USM_INSTANCE_ID}/ext
logger -i \"Cloudify Debug: Looking for a public key in \$CLOUDIFY_WORKDIR/debugPublicKey \"
if [[ -f \$CLOUDIFY_WORKDIR/debugPublicKey ]]; then
logger -i \"Cloudify Debug: Adding public key from \$CLOUDIFY_WORKDIR/debugPublicKey \"
    cat \$CLOUDIFY_WORKDIR/debugPublicKey >>\$HOME/.ssh/authorized_keys
fi

#set up the 'debug' alias to enter the debug shell
if ! alias debug &>/dev/null ; then
    echo >>\$HOME/.bashrc 'echo A cloudify debug shell is available for you by typing \\\"debug\\\"'
    echo >>\$HOME/.bashrc 'alias debug=\"bash --rcfile \$HOME/.debugrc\"'
fi

""")

    String keepaliveFilename = "${System.properties["user.home"]}/.cloudify_debugging"
    
    //TODO: fix the loop
    def waitForFinishLoop = ("""\
#KEEPALIVE_FILE=${keepaliveFilename}
KEEPALIVE_FILE=/home/ubuntu/.cloudify_debugging
logger -i \"Cloudify Debug: Awaiting deletion of ${keepaliveFilename}\"
:>\$KEEPALIVE_FILE
while [[ -f \$KEEPALIVE_FILE ]]; do
    echo \"The service \$USM_SERVICE_NAME (script \$1) is waiting to be debugged on \$CLOUDIFY_AGENT_ENV_PUBLIC_IP.\"
    echo \"When finished, delete the file \$KEEPALIVE_FILE (or use the 'finish' debug command)\"
    sleep 60
done
""")

    def debugrcTemplate = ('''\
# Generated by the Cloudify debug subsystem
echo Loading the debug environment...

#load cloudify environment variables saved for this lifecyle event:
source \\\$HOME/.cloudify_env

export CLOUDIFY_WORKDIR=\\\$HOME/gigaspaces/work/processing-units/\\\${USM_APPLICATION_NAME}_\\\${USM_SERVICE_NAME}_\\\${USM_INSTANCE_ID}/ext
export DEBUG_TARGET=${debugTarget}
export KEEPALIVE_FILE=${keepaliveFile}

cd \\\$CLOUDIFY_WORKDIR
export JAVA_HOME=\\\$HOME/java
export CLASSPATH=`find \\\$HOME/gigaspaces/lib/{required,platform/cloudify} -name *.jar | paste -sd:`
export PATH=\\\$HOME/gigaspaces/tools/groovy/bin:\\\$PATH
chmod +x debug.groovy

#the bash command aliases:
<% bashCommands.each{
    println("alias ${it.name}=\'${it.command}\'")
} %>

#set up shortcut aliases
if [[ ! -f debug_commands ]] ; then
    (./debug.groovy | tail -n+2 >debug_commands)
<% bashCommands.each{
    println(sprintf("echo >>debug_commands \'      %-26s%s\' ; ", it.name, it.comment))
} %>
fi

for COMMAND in `grep -Eo \'\\\\-\\\\-[^ ]*\' debug_commands | cut -c3- `; do
    alias \\\$COMMAND=\"\\\$CLOUDIFY_WORKDIR/debug.groovy --\\\$COMMAND\"
done
#some special treatment for the help alias
alias help=\"cut -c7- <\\\$CLOUDIFY_WORKDIR/debug_commands\"

clear
PS1=\"Debugging[\\\$DEBUG_TARGET]: \"
echo -en \"\\\\e[0;36m" #change to cyan
echo Starting a debugging session for hook \\\$DEBUG_TARGET
echo These are the available debug commands:
echo -en \"\\\\e[2;37m\" #reset to gray
help
echo
''')


    def debug(String  arg , mode="instead") { return debug([arg], mode) }
    def debug(GString arg , mode="instead") { return debug([arg.toString()], mode) }

    //The main hook function
    def debug(List args, mode="instead") {
        prepare_debugrc(args.join(" "))

        def debugScriptContents = preparationScript
        switch (mode) {
            case "instead":
                debugScriptContents += waitForFinishLoop
                break
            case "after":
                debugScriptContents += '$@ \n' + waitForFinishLoop
                break
            case "onError":
                debugScriptContents += '$@ && exit 0 \n' + waitForFinishLoop
                break
            default:
                throw new Exception("Unrecognized debug mode (${mode}), please use one of: 'instead', 'after' or 'onError'")
                break
            }

        def debughookScriptName = System.properties["user.home"] +"/debug-hook.sh"
        new File(debughookScriptName).withWriter() {it.write(debugScriptContents)}

        eventLogger.info "IMPORTANT: A debug environment will be waiting for you after the instance has launched"
        return [debughookScriptName] + args
    }

    def prepare_debugrc(debugTarget) {
        def templateEngine = new groovy.text.SimpleTemplateEngine()
        def preparedTemplate = templateEngine.createTemplate(debugrcTemplate).make(
            [debugTarget: debugTarget,
             keepaliveFile: keepaliveFilename,
             bashCommands: bashCommands,
        ])
        def targetDebugrc = new File(System.properties["user.home"] +"/.debugrc")
        targetDebugrc.withWriter() {it.write(preparedTemplate)}
    }
}