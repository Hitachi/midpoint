/*
 * Copyright (c) 2011 Evolveum
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://www.opensource.org/licenses/cddl1 or
 * CDDLv1.0.txt file in the source code distribution.
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 *
 * Portions Copyrighted 2011 [name of copyright owner]
 */

package com.evolveum.midpoint.schema.processor;

import com.evolveum.midpoint.schema.XsdTypeConverter;
import com.evolveum.midpoint.util.QNameUtil;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.namespace.QName;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Definition of a property container.
 * 
 * Property container groups properties into logical blocks. The reason for
 * grouping may be as simple as better understandability of data structure. But
 * the group usually means different meaning, source or structure of the data.
 * For example, the property container is frequently used to hold properties
 * that are dynamic, not fixed by a static schema. Such grouping also naturally
 * translates to XML and helps to "quarantine" such properties to avoid Unique
 * Particle Attribute problems.
 * 
 * Property Container contains a set of (potentially multi-valued) properties.
 * The order of properties is not significant, regardless of the fact that it
 * may be fixed in the XML representation. In the XML representation, each
 * element inside Property Container must be either Property or a Property
 * Container.
 * 
 * This class represents schema definition for property container. See
 * {@link Definition} for more details.
 * 
 * @author Radovan Semancik
 * 
 */
public class PropertyContainerDefinition extends Definition {

	private Set<PropertyDefinition> propertyDefinitions;

	PropertyContainerDefinition(QName name, QName defaultName, QName typeName) {
		super(name, defaultName, typeName);
	}

	/**
	 * Finds a PropertyDefinition by looking at the property name.
	 * 
	 * Returns null if nothing is found.
	 * 
	 * @param name
	 *            property definition name
	 * @return found property definition of null
	 */
	public PropertyDefinition findPropertyDefinition(QName name) {
		return findPropertyDefinition(name,PropertyDefinition.class);
	}

	protected <T extends PropertyDefinition> T findPropertyDefinition(QName name, Class<T> clazz) {
		for (PropertyDefinition def : propertyDefinitions) {
			if (name.equals(def.getName())) {
				return (T) def;
			}
		}
		return null;
	}
	
	/**
	 * Returns set of property definitions.
	 * 
	 * The set contains all property definitions of all types that were parsed.
	 * Order of definitions is insignificant.
	 * 
	 * @return set of definitions
	 */
	public Set<PropertyDefinition> getDefinitions() {
		if (propertyDefinitions == null) {
			propertyDefinitions = new HashSet<PropertyDefinition>();
		}
		return propertyDefinitions;
	}
	
	void setPropertyDefinitions(Set<PropertyDefinition> propertyDefinitions) {
		this.propertyDefinitions = propertyDefinitions;
	}
	
	public PropertyContainer instantiate() {
		return new PropertyContainer(getNameOrDefaultName(), this);
	}
	
	public PropertyContainer instantiate(QName name) {
		return new PropertyContainer(name, this);
	}
	
	public Set<Property> parseProperties(List<Element> elements) {
		return parseProperties(elements,Property.class);
	}
	
	protected <T extends Property> Set<T> parseProperties(List<Element> elements, Class<T> clazz) {
		return parseProperties(elements,clazz,null);
	}
	
	protected <T extends Property> Set<T> parseProperties(List<Element> elements, Class<T> clazz, Set<? extends PropertyDefinition> selection) {
		
		Set<T> props = new HashSet<T>();
		
		// Iterate over all the XML elements there. Each element is
		// an attribute.
		for(int i=0;i<elements.size();i++) {
			Element propElement = elements.get(i);

			QName elementQName =  QNameUtil.getNodeQName(propElement);
			
			// If there was a selection filter specified, filter out the
			// properties that are not in the filter.
			
			// Quite an ugly code. TODO: clean it up
			if (selection!=null) {
				boolean selected=false;
				for (PropertyDefinition selProdDef : selection) {
					if (selProdDef.getNameOrDefaultName().equals(elementQName)) {
						selected = true;
					}
				}
				if (!selected) {
					continue;
				}
			}
			
			// Find Attribute definition from the schema
			PropertyDefinition propDef = findPropertyDefinition(elementQName);
			T prop = (T) propDef.instantiate();
			Set<Object> propValues = prop.getValues();

			if (propDef.isMultiValue()) {
				// Special handling for multivalue attributes. If the type
				// of the property is multivalued, the XML element may appear
				// several times.

				// Convert the first value
				Object value = XsdTypeConverter.toJavaValue(propElement, propDef.getTypeName());
				propValues.add(value);
				// Loop over until the elements have the same local name
				while (i + 1 < elements.size()
					   && QNameUtil.compareQName(elementQName, elements.get(i + 1))) {
					i++;
					propElement = elements.get(i);
					// Conver all the remaining values
					Object avalue = XsdTypeConverter.toJavaValue(propElement, propDef.getTypeName());
					propValues.add(avalue);
				}

			} else {
				// Single-valued properties are easy to convert
				Object value = XsdTypeConverter.toJavaValue(propElement, propDef.getTypeName());
				propValues.add(value);
			}
			props.add(prop);
		}
		return props;
	}
	
	List<Element> serializePropertiesToDom(Set<Property> properties, Document doc) {
		List<Element> elements = new ArrayList<Element>();
		// This is not really correct. We should follow the ordering of elements
		// in the schema so we produce valid XML
		// TODO: FIXME
		for (Property prop : properties) {
			PropertyDefinition propDef = prop.getDefinition();
			if (propDef==null) {
				propDef = findPropertyDefinition(prop.getName());
			}
			Set<Object> values = prop.getValues();
			for (Object val : values) {
				Element element = doc.createElementNS(prop.getName().getNamespaceURI(), prop.getName().getLocalPart());
				XsdTypeConverter.toXsdElement(val,propDef.getTypeName(),element);
				elements.add(element);
			}			
		}
		return elements;
	}

	@Override
	public String debugDump(int indent) {
		StringBuilder sb = new StringBuilder();
		for (int i=0; i<indent; i++) {
			sb.append(Schema.INDENT);
		}
		sb.append(toString());
		sb.append("\n");
		for (PropertyDefinition def : getDefinitions()) {
			sb.append(def.debugDump(indent+1));
		}
		return sb.toString();
	}

}
