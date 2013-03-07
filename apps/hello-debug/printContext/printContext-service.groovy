/*******************************************************************************
* Copyright (c) 2011 GigaSpaces Technologies Ltd. All rights reserved
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
import org.cloudifysource.debug.DebugHook

def debugHook = new DebugHook(context, "onError")

service {
//    extend "../../../services/debug"
    name "printContext"
    type "APP_SERVER"

    compute {
        template "SMALL_UBUNTU"
    }

    lifecycle{
        preInstall debugHook.debug('sayHello.sh')
        install debugHook.debug('printContext.groovy')
//        install --debug 'printContext.groovy'

//        postInstall (new DebugHook(context, "onError").debug(["printContext.groovy", "--help"]))
//
//        start (new DebugHook(context, "after").debug("printContext.groovy",))
    }
}
