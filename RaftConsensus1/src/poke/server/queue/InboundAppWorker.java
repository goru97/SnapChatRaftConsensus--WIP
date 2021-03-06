/*
 * copyright 2015, gash
 * 
 * Gash licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package poke.server.queue;

import io.netty.channel.Channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import poke.comm.App.Request;
//import poke.comm.Image.Request;
import poke.resources.ImageResource;
import poke.server.managers.CompleteRaftManager;
import poke.server.resources.ImgResource;
import poke.server.resources.ResourceFactory;

import com.google.protobuf.GeneratedMessage;

public class InboundAppWorker extends Thread {
	protected static Logger logger = LoggerFactory.getLogger("server");

	int workerId;
	PerChannelQueue pqChannel;
	boolean forever = true;

	public InboundAppWorker(ThreadGroup tgrp, int workerId, PerChannelQueue sq) {
		super(tgrp, "inbound-" + workerId);
		this.workerId = workerId;
		this.pqChannel = sq;

		if (sq.inbound == null)
			throw new RuntimeException("connection worker detected null inbound queue");
	}

	@Override
	public void run() {
		System.out.println("Inbound Worker Thread Running");
		Channel conn = pqChannel.getChannel();
		if (conn == null || !conn.isOpen()) {
			logger.error("connection missing, no inbound communication");
			return;
		}

		while (true) {
			if (!forever && pqChannel.inbound.size() == 0)
				break;

			try {
				// block until a message is enqueued
				GeneratedMessage msg = pqChannel.inbound.take();
				// process request and enqueue response
				if (msg instanceof Request) {
					Request req = ((Request) msg);

					// HEY! if you find yourself here and are tempted to add
					// code to process state or requests then you are in the
					// WRONG place! This is a general routing class, all
					// request specific actions should take place in the
					// resource!

					// handle it locally - we create a new resource per
					// request. This helps in thread isolation however, it
					// creates creation burdens on the server. If
					// we use a pool instead, we can gain some relief.
					/*
					Resource rsc = ResourceFactory.getInstance().resourceInstance(req.getHeader());

					Request reply = null;
					 */
				
				
					ImgResource rsc = (ImageResource)ResourceFactory.getInstance().getImageResourceInstance();
					if (rsc == null) {
						logger.error("failed to obtain resource for " + req);

					} else {
						// message communication can be two-way or one-way.
						// One-way communication will not produce a response
						// (reply).
						//logger.debug("Replicating the image across cluster");
						//System.out.println("Replicating the image across cluster");
						rsc.setPQChannel(pqChannel);
						rsc.setConf(CompleteRaftManager.getConf());
						rsc.setClusterConf(CompleteRaftManager.getClusterConf());
						rsc.process(req);
					}
					
					

				}

			} catch (InterruptedException ie) {
				break;
			} catch (Exception e) {
				logger.error("Unexpected processing failure", e);
				break;
			}
		}

		if (!forever) {
			logger.info("connection queue closing");
		}
	}
}