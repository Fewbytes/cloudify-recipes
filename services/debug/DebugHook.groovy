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

class DebugHook {
    DebugHook() {}

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

    String keepaliveFilename = "/tmp/.cloudify_debugging"
    //TODO: fix the loop and add the 'mode' logic around it
    def keepaliveContents = ("""\
#preserve the env variables
:>\$HOME/.cloudify_env
printenv | grep -E \"^(CLOUDIFY|USM|LOOKUP)\" | \
    while read var; do echo >>\$HOME/.cloudify_env \"export \$var\"; done

#chmod the actual script
chmod +x \$1


#set up the 'debug' alias to enter the debug shell
if ! alias debug &>/dev/null ; then
    echo >>\$HOME/.bashrc 'echo A cloudify debug shell is available for you by typing \\\"debug\\\"'
    echo >>\$HOME/.bashrc 'alias debug=\"bash --rcfile \$HOME/.debugrc\"'
fi

while [[ -f ${keepaliveFilename} ]]; do
    echo \"A debug environment is ready on \$CLOUDIFY_AGENT_ENV_PUBLIC_IP.\"
    echo \"When finished, delete the file ${keepaliveFilename} (or use the \'finish\' debug command)\"
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
echo Starting a debugging session for hook \\\$DEBUG_TARGET
echo These are the available debug commands:
help
echo
''')


    def debug(String  arg , mode="instead") { return debug([arg], mode) }
    def debug(GString arg , mode="instead") { return debug([arg.toString()], mode) }
    def debug(Map     args, mode="instead") { //TODO: multiple scripts are unsupported yet
        return args.inject([:]) {h, k ,v -> h[k] = debug(v, mode); h }
    }

    //The main hook function
    def debug(List args, mode="instead") {
        prepare_debugrc(args.join(" "))
    
        File keepalive = new File(keepaliveFilename)
        keepalive.withWriter() {it.write(keepaliveContents)}

        //TODO: Move the authorized_keys addition into here

        //TODO: print some notification that will be visible in the shell at the right moment
        println("IMPORTANT: A debug environment will be waiting for you after the instance has launched")
        return [keepaliveFilename] + args
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