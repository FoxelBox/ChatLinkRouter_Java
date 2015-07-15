/**
 * This file is part of FoxBukkitChatLinkRouter.
 *
 * FoxBukkitChatLinkRouter is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * FoxBukkitChatLinkRouter is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with FoxBukkitChatLinkRouter.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.foxelbox.chatlinkrouter;

import com.foxelbox.dependencies.config.Configuration;
import org.zeromq.ZMQ;

import java.io.File;
import java.io.IOException;

public class Main {
    public static Configuration configuration;

    public static ZMQ.Context zmqContext;

    public static void main(String[] args) throws IOException {
        configuration = new Configuration(new File("."));
        zmqContext = ZMQ.context(4);

        final ZMQ.Socket serverToMe = zmqContext.socket(ZMQ.SUB);
        serverToMe.bind(configuration.getValue("zmq-server-to-broker", "tcp://127.0.0.1:5556"));
        serverToMe.subscribe(new byte[] { '{' });
        final ZMQ.Socket meToLink = zmqContext.socket(ZMQ.PUSH);
        meToLink.bind(configuration.getValue("zmq-broker-to-link", "tcp://127.0.0.1:5557"));

        final ZMQ.Socket linkToMe = zmqContext.socket(ZMQ.XSUB);
        linkToMe.bind(configuration.getValue("zmq-link-to-broker", "tcp://127.0.0.1:5558"));
        final ZMQ.Socket meToServer = zmqContext.socket(ZMQ.XPUB);
        meToServer.bind(configuration.getValue("zmq-broker-to-server", "tcp://127.0.0.1:5559"));

        final ZMQ.Poller poller = new ZMQ.Poller(4);
        poller.register(serverToMe, ZMQ.Poller.POLLIN);
        poller.register(meToLink, ZMQ.Poller.POLLIN);
        poller.register(linkToMe, ZMQ.Poller.POLLIN);
        poller.register(meToServer, ZMQ.Poller.POLLIN);

        new Thread() {
            public void run() {
                while(!Thread.currentThread().isInterrupted()) {
                    poller.poll();
                    boolean more;
                    if(poller.pollin(0)) {
                        do {
                            byte[] msg = serverToMe.recv(0);
                            more = meToLink.hasReceiveMore();
                            meToLink.send(msg, more ? ZMQ.SNDMORE : 0);
                        } while(more);
                    }
                    if(poller.pollin(1)) {
                        do {
                            byte[] msg = meToLink.recv(0);
                            more = meToLink.hasReceiveMore();
                            serverToMe.send(msg, more ? ZMQ.SNDMORE : 0);
                        } while(more);
                    }
                    if(poller.pollin(2)) {
                        do {
                            byte[] msg = linkToMe.recv(0);
                            more = linkToMe.hasReceiveMore();
                            meToServer.send(msg, more ? ZMQ.SNDMORE : 0);
                        } while(more);
                    }
                    if(poller.pollin(3)) {
                        do {
                            byte[] msg = meToServer.recv(0);
                            more = meToServer.hasReceiveMore();
                            linkToMe.send(msg, more ? ZMQ.SNDMORE : 0);
                        } while(more);
                    }
                }
            }
        }.start();
    }
}
