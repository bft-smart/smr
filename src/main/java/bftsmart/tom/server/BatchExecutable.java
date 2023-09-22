/**
Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the @author tags

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package bftsmart.tom.server;

import bftsmart.tom.MessageContext;
import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.util.ServiceContent;
import bftsmart.tom.util.TOMUtil;

/**
 * 
 * Executables that implement this interface will receive a batch of requests and
 * deliver them to the application in a deterministic way.
 *
 */
public interface BatchExecutable extends Executable {
	
    /**
     * Execute a batch of requests.
     * @param commands The batch of requests
     * @param msgCtx The context associated to each request
     * @return The replies for each request
     */
	ServiceContent[] executeBatch(byte[][] commands, byte[][] replicaSpecificContents, MessageContext[] msgCtx);
    
    default TOMMessage[] executeBatch(int processID, int viewID, boolean[] isReplyHash, byte[][] commonContents,
									  byte[][] replicaSpecificContents, MessageContext[] msgCtx) {
        
        TOMMessage[] replies = new TOMMessage[commonContents.length];

		ServiceContent[] serviceResponse = executeBatch(commonContents, replicaSpecificContents, msgCtx);
		byte[] result;
        for (int i = 0; i < serviceResponse.length; i++) {
			if (serviceResponse[i] == null) {
				continue;
			}
			if (isReplyHash[i]) {
				result = TOMUtil.computeHash(serviceResponse[i].getCommonContent());
			} else {
				result = serviceResponse[i].getCommonContent();
			}
			replies[i] = getTOMMessage(processID, viewID, commonContents[i], msgCtx[i], result,
					serviceResponse[i].getReplicaSpecificContent());
        }
        
        return replies;
    }

}
