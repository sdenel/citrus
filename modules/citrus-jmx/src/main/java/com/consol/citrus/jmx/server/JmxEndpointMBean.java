/*
 * Copyright 2006-2016 the original author or authors.
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

package com.consol.citrus.jmx.server;

import com.consol.citrus.endpoint.EndpointAdapter;
import com.consol.citrus.exceptions.CitrusRuntimeException;
import com.consol.citrus.jmx.endpoint.JmxEndpointConfiguration;
import com.consol.citrus.jmx.model.*;
import com.consol.citrus.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.*;
import javax.xml.transform.Source;
import java.lang.reflect.*;
import java.util.List;
import java.util.Map;

/**
 * Managed bean implementation based on standard mbean implementation. This managed bean delegates incoming requests for operation calls and
 * attribute access to endpoint adapter. The endpoint adapter is capable of returning a invocation result which is converted to a proper operation
 * result.
 *
 * This class supports managed bean operation invocation as well as read and write access to managed bean attributes.
 *
 * @author Christoph Deppisch
 * @since 2.5
 */
public class JmxEndpointMBean extends StandardMBean {

    /** Logger */
    private static Logger log = LoggerFactory.getLogger(JmxEndpointMBean.class);

    /** Endpoint adapter delegate */
    private final EndpointAdapter endpointAdapter;

    /** Endpoint configuration */
    private final JmxEndpointConfiguration endpointConfiguration;

    /** Managed bean interface type */
    private final Class managedBean;

    /**
     * Constructor using the managed bean type.
     * @param managedBean
     */
    public JmxEndpointMBean(Class managedBean, JmxEndpointConfiguration endpointConfiguration, EndpointAdapter endpointAdapter) throws NotCompliantMBeanException {
        super(Proxy.newProxyInstance(managedBean.getClassLoader(), new Class[]{ managedBean }, new UnsupportedInvocationHandler()), managedBean);
        this.managedBean = managedBean;
        this.endpointConfiguration = endpointConfiguration;
        this.endpointAdapter = endpointAdapter;
    }

    @Override
    public Object getAttribute(String name) throws AttributeNotFoundException, MBeanException, ReflectionException {
        ManagedBeanInvocation mbeanInvocation = new ManagedBeanInvocation();
        mbeanInvocation.setMbean(managedBean.getPackage().getName() + ":type=" + managedBean.getSimpleName());
        ManagedBeanInvocation.Attribute attribute = new ManagedBeanInvocation.Attribute();
        attribute.setName(name);
        mbeanInvocation.setAttribute(attribute);

        return handleInvocation(mbeanInvocation);
    }

    @Override
    public void setAttribute(Attribute attribute) throws AttributeNotFoundException, InvalidAttributeValueException, MBeanException, ReflectionException {
        ManagedBeanInvocation mbeanInvocation = new ManagedBeanInvocation();
        mbeanInvocation.setMbean(managedBean.getPackage().getName() + ":type=" + managedBean.getSimpleName());
        ManagedBeanInvocation.Attribute mbeanAttribute = new ManagedBeanInvocation.Attribute();
        mbeanAttribute.setName(attribute.getName());
        mbeanAttribute.setValueObject(attribute.getValue());
        mbeanInvocation.setAttribute(mbeanAttribute);

        handleInvocation(mbeanInvocation);
    }

    @Override
    public AttributeList getAttributes(String[] attributes) {
        AttributeList list = new AttributeList();
        try {
            for (String attribute : attributes) {
                list.add(new Attribute(attribute, getAttribute(attribute)));
            }
        } catch (AttributeNotFoundException | ReflectionException | MBeanException e) {
            throw new CitrusRuntimeException("Failed to get managed bean attribute", e);
        }

        return list;
    }

    @Override
    public AttributeList setAttributes(AttributeList attributes) {
        AttributeList list = new AttributeList();

        try {
            for (Object attribute : attributes) {
                setAttribute((Attribute) attribute);
                list.add(attribute);
            }
        } catch (AttributeNotFoundException | ReflectionException | MBeanException | InvalidAttributeValueException e) {
            throw new CitrusRuntimeException("Failed to get managed bean attribute", e);
        }

        return list;
    }

    @Override
    public Object invoke(String actionName, Object[] params, String[] signature) throws MBeanException, ReflectionException {
        if (log.isDebugEnabled()) {
            log.debug("Received message on JMX server: '" + endpointConfiguration.getServerUrl() + "'");
        }

        ManagedBeanInvocation mbeanInvocation = new ManagedBeanInvocation();
        mbeanInvocation.setMbean(managedBean.getPackage().getName() + ":type=" + managedBean.getSimpleName());
        mbeanInvocation.setOperation(actionName);

        if (params != null) {
            mbeanInvocation.setParameter(new ManagedBeanInvocation.Parameter());

            for (Object arg : params) {
                OperationParam operationParam = new OperationParam();

                operationParam.setValueObject(arg);
                if (Map.class.isAssignableFrom(arg.getClass())) {
                    operationParam.setType(Map.class.getName());
                } else if (List.class.isAssignableFrom(arg.getClass())) {
                    operationParam.setType(List.class.getName());
                } else {
                    operationParam.setType(arg.getClass().getName());
                }

                mbeanInvocation.getParameter().getParameter().add(operationParam);
            }
        }

        return handleInvocation(mbeanInvocation);
    }

    /**
     * Handle managed bean invocation by delegating to endpoint adapter. Response is converted to proper method return result.
     * @param mbeanInvocation
     * @return
     */
    private Object handleInvocation(ManagedBeanInvocation mbeanInvocation) {
        Message response = endpointAdapter.handleMessage(endpointConfiguration.getMessageConverter()
                .convertInbound(mbeanInvocation, endpointConfiguration));

        ManagedBeanResult serviceResult = null;
        if (response != null && response.getPayload() != null) {
            if (response.getPayload() instanceof ManagedBeanResult) {
                serviceResult = (ManagedBeanResult) response.getPayload();
            } else if (response.getPayload() instanceof String) {
                serviceResult = (ManagedBeanResult) endpointConfiguration.getMarshaller().unmarshal(response.getPayload(Source.class));
            }
        }

        if (serviceResult != null) {
            return serviceResult.getResultObject(endpointConfiguration.getApplicationContext());
        } else {
            return null;
        }
    }

    /**
     * Invocation handler that should actually never been called, because object invocation is handled by the surrounding managed bean implementation.
     * When called this is an error that should be reported to the caller.
     */
    private static class UnsupportedInvocationHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            throw new CitrusRuntimeException("Unsupported method call - unexpected call to managed bean proxy instance");
        }
    }
}
