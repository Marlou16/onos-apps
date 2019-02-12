/*
 * Copyright 2019-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.marlou.aggr;

import org.apache.felix.scr.annotations.Service;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketProcessor;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements a static, pro-active aggregator for one switch.
 * Call the first port the 'uplink', where all traffic will come out.
 * At first, all traffic from the 'uplink' downwards will be broadcasted, using groups.
 * 
 * @author Marlou Pors
 *
 */


@Component(immediate = true)
@Service(value = Aggregator.class)
public class Aggregator {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Activate
    protected void activate() {
        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        log.info("Stopped");
    }
    
    private class AggregatorImplementer implements PacketProcessor {

		@Override
		public void process(PacketContext context) {
			/* For starters, we implement this aggregator pro-active,
			 * which means there is no packet processing...
			 * But in the future packet processing for the way back could be here!
			 */		
		}
    	
    }

}