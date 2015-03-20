/*
 * Copyright 2006-2013 the original author or authors.
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

package com.consol.citrus.channel;

import com.consol.citrus.context.TestContext;
import com.consol.citrus.exceptions.ActionTimeoutException;
import com.consol.citrus.message.Message;
import com.consol.citrus.message.correlation.CorrelationManager;
import com.consol.citrus.message.correlation.PollingCorrelationManager;
import com.consol.citrus.messaging.ReplyConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Synchronous producer sends message to in memory message channel and receives synchronous reply.
 * Reply message is correlated and stored in correlation manager. This way test cases are able to receive synchronous
 * message asynchronously at later time.
 *
 * @author Christoph Deppisch
 * @since 1.4
 */
public class ChannelSyncProducer extends ChannelProducer implements ReplyConsumer {
    /** Logger */
    private static Logger log = LoggerFactory.getLogger(ChannelSyncProducer.class);

    /** Store of reply messages */
    private CorrelationManager<Message> correlationManager;

    /** Endpoint configuration */
    private final ChannelSyncEndpointConfiguration endpointConfiguration;

    /**
     * Default constructor using endpoint configuration.
     *
     * @param name
     * @param endpointConfiguration
     */
    public ChannelSyncProducer(String name, ChannelSyncEndpointConfiguration endpointConfiguration) {
        super(name, endpointConfiguration);
        this.endpointConfiguration = endpointConfiguration;

        this.correlationManager = new PollingCorrelationManager(endpointConfiguration, "Reply message did not arrive yet");
    }

    @Override
    public void send(Message message, TestContext context) {
        String correlationKey = endpointConfiguration.getCorrelator().getCorrelationKey(message);
        correlationManager.createCorrelationKey(
                endpointConfiguration.getCorrelator().getCorrelationKeyName(this), correlationKey, context);

        String destinationChannelName = getDestinationChannelName();

        log.info("Sending message to channel: '" + destinationChannelName + "'");

        if (log.isDebugEnabled()) {
            log.debug("Message to sent is:\n" + message.toString());
        }

        endpointConfiguration.getMessagingTemplate().setReceiveTimeout(endpointConfiguration.getTimeout());

        log.info("Message was successfully sent to channel: '" + destinationChannelName + "'");

        org.springframework.messaging.Message replyMessage = endpointConfiguration.getMessagingTemplate().sendAndReceive(getDestinationChannel(),
                endpointConfiguration.getMessageConverter().convertOutbound(message, endpointConfiguration));

        if (replyMessage == null) {
            throw new ActionTimeoutException("Reply timed out after " +
                    endpointConfiguration.getTimeout() + "ms. Did not receive reply message on reply channel");
        } else {
            log.info("Received synchronous response message from reply channel");
        }

        correlationManager.store(correlationKey, endpointConfiguration.getMessageConverter().convertInbound(replyMessage, endpointConfiguration));
    }

    @Override
    public Message receive(TestContext context) {
        return receive(correlationManager.getCorrelationKey(
                endpointConfiguration.getCorrelator().getCorrelationKeyName(this), context), context);
    }

    @Override
    public Message receive(String selector, TestContext context) {
        return receive(selector, context, endpointConfiguration.getTimeout());
    }

    @Override
    public Message receive(TestContext context, long timeout) {
        return receive(correlationManager.getCorrelationKey(
                endpointConfiguration.getCorrelator().getCorrelationKeyName(this), context), context, timeout);
    }

    @Override
    public Message receive(String selector, TestContext context, long timeout) {
        Message message = correlationManager.find(selector, timeout);

        if (message == null) {
            throw new ActionTimeoutException("Action timeout while receiving synchronous reply message on message channel");
        }

        return message;
    }

    /**
     * Gets the correlation manager.
     * @return
     */
    public CorrelationManager<Message> getCorrelationManager() {
        return correlationManager;
    }

    /**
     * Sets the correlation manager.
     * @param correlationManager
     */
    public void setCorrelationManager(CorrelationManager<Message> correlationManager) {
        this.correlationManager = correlationManager;
    }

}
