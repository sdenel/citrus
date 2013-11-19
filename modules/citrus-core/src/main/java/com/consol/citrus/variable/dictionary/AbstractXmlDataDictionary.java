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

package com.consol.citrus.variable.dictionary;

import com.consol.citrus.context.TestContext;
import com.consol.citrus.message.MessageType;
import com.consol.citrus.util.XMLUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.*;
import org.w3c.dom.ls.*;
import org.w3c.dom.traversal.NodeFilter;

import java.io.StringWriter;

/**
 * Abstract data dictionary works on XML message payloads only with parsing the document and translating each element
 * and attribute with respective value in dictionary.
 *
 * @author Christoph Deppisch
 * @since 1.4
 */
public abstract class AbstractXmlDataDictionary extends AbstractDataDictionary {

    @Override
    protected String interceptMessagePayload(String messagePayload, String messageType, TestContext context) {
        if (!StringUtils.hasText(messagePayload)) {
            return messagePayload;
        }

        Document doc = XMLUtils.parseMessagePayload(messagePayload);

        LSSerializer serializer = XMLUtils.createLSSerializer();

        serializer.setFilter(new TranslateFilter(context));

        LSOutput output = XMLUtils.createLSOutput();
        String charset = XMLUtils.getTargetCharset(doc).displayName();
        output.setEncoding(charset);

        StringWriter writer = new StringWriter();
        output.setCharacterStream(writer);

        serializer.write(doc, output);

        return writer.toString();
    }

    /**
     * Serializer filter uses data dictionary translation on elements and attributes.
     */
    private class TranslateFilter implements LSSerializerFilter {
        private TestContext context;

        public TranslateFilter(TestContext context) {
            this.context = context;
        }

        @Override
        public int getWhatToShow() {
            return NodeFilter.SHOW_ALL;
        }

        @Override
        public short acceptNode(Node node) {
            if (node instanceof Element) {
                Element element = (Element) node;

                if (StringUtils.hasText(DomUtils.getTextValue(element))) {
                    element.setTextContent(translate(DomUtils.getTextValue(element), XMLUtils.getNodesPathName(element), context));
                } else if (!element.hasChildNodes()) {
                    String translated = translate("", XMLUtils.getNodesPathName(element), context);
                    if (StringUtils.hasText(translated)) {
                        element.appendChild(element.getOwnerDocument().createTextNode(translated));
                    }
                }

                NamedNodeMap attributes = element.getAttributes();
                for (int i = 0; i < attributes.getLength(); i++) {
                    Attr attribute = (Attr) attributes.item(i);
                    attribute.setValue(translate(attribute.getNodeValue(), XMLUtils.getNodesPathName(attribute), context));
                }
            }

            return NodeFilter.FILTER_ACCEPT;
        }
    }

    @Override
    public boolean supportsMessageType(String messageType) {
        return MessageType.XML.toString().equalsIgnoreCase(messageType);
    }


}