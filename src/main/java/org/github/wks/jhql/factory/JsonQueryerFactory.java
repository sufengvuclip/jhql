package org.github.wks.jhql.factory;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.codehaus.jackson.map.ObjectMapper;
import org.github.wks.jhql.query.ContextQueryer;
import org.github.wks.jhql.query.IntQueryer;
import org.github.wks.jhql.query.ListQueryer;
import org.github.wks.jhql.query.ObjectQueryer;
import org.github.wks.jhql.query.Queryer;
import org.github.wks.jhql.query.TextQueryer;
import org.github.wks.jhql.query.annotation.Required;

/**
 * A factory class that generates Queryer objects from JSON documents.
 */
public class JsonQueryerFactory {
	private static Map<String, Class<? extends Queryer>> namedQueryers = new HashMap<String, Class<? extends Queryer>>();

	static {
		namedQueryers.put("text", TextQueryer.class);
		namedQueryers.put("int", IntQueryer.class);
		namedQueryers.put("list", ListQueryer.class);
		namedQueryers.put("context", ContextQueryer.class);
	}

	private static ObjectMapper objectMapper = new ObjectMapper();

	/**
	 * Make a Queryer from a File containing a JSON value.
	 */
	public static Queryer makeQueryer(File json) throws JsonException,
			JhqlJsonGrammarException {
		Object queryExpr;
		try {
			queryExpr = objectMapper.readValue(json, Object.class);
		} catch (Exception e) {
			throw new JsonException(e);
		}
		return makeQueryer(queryExpr);
	}

	/**
	 * Make a Queryer from a Reader containing a JSON value.
	 */
	public static Queryer makeQueryer(Reader json) throws JsonException,
			JhqlJsonGrammarException {
		Object queryExpr;
		try {
			queryExpr = objectMapper.readValue(json, Object.class);
		} catch (Exception e) {
			throw new JsonException(e);
		}
		return makeQueryer(queryExpr);
	}

	/**
	 * Make a Queryer from an InputStream containing a JSON value.
	 */
	public static Queryer makeQueryer(InputStream json) throws JsonException,
			JhqlJsonGrammarException {
		Object queryExpr;
		try {
			queryExpr = objectMapper.readValue(json, Object.class);
		} catch (Exception e) {
			throw new JsonException(e);
		}
		return makeQueryer(queryExpr);
	}

	/**
	 * Make a Queryer from an String containing a JSON value.
	 */
	public static Queryer makeQueryer(String json) throws JsonException,
			JhqlJsonGrammarException {
		Object queryExpr;
		try {
			queryExpr = objectMapper.readValue(json, Object.class);
		} catch (Exception e) {
			throw new JsonException(e);
		}
		return makeQueryer(queryExpr);
	}

	/**
	 * Make a Queryer from a mapped JSON object. JSON values are mapped to Java
	 * types like int, String, boolean, List, Map, etc.
	 * 
	 * @param json
	 *            The Java object corresponding to the JSON grammar.
	 * @return A Queryer object.
	 * @throws JhqlJsonGrammarException
	 */
	public static Queryer makeQueryer(Object queryExpr)
			throws JhqlJsonGrammarException {
		if (queryExpr instanceof String) {
			return makeSimpleQueryer((String) queryExpr);
		} else if (queryExpr instanceof Map) {
			@SuppressWarnings("unchecked")
			Map<String, Object> queryExprMap = (Map<String, Object>) queryExpr;
			if (queryExprMap.containsKey("_type")) {
				return makeComplexedQueryer(queryExprMap);
			} else {
				return makeObjectQueryer(queryExprMap);
			}
		}
		throw new JhqlJsonGrammarException("Illegal JHQL query expression:"
				+ queryExpr);
	}

	private static Queryer makeSimpleQueryer(String queryExpr)
			throws JhqlJsonGrammarException {
		String[] pair = queryExpr.split(":");
		if (pair.length != 2) {
			throw new JhqlJsonGrammarException(
					"Illegal JHQL string expression: " + queryExpr);
		}

		String type = pair[0];
		String value = pair[1];

		Map<String, Object> queryExprObj = new HashMap<String, Object>();
		queryExprObj.put("_type", type);
		queryExprObj.put("value", value);

		return makeComplexedQueryer(queryExprObj);
	}

	private static Queryer makeComplexedQueryer(Map<String, Object> queryExpr) {
		String type;
		try {
			type = (String) queryExpr.get("_type");
		} catch (ClassCastException e) {
			throw new JhqlJsonGrammarException(
					"'_type' field must be a string.");
		}
		if (type == null) {
			throw new JhqlJsonGrammarException(
					"Complexed queryers must contain '_type' field.");
		}

		Class<? extends Queryer> queryerClass = namedQueryers.get(type);

		if (queryerClass == null) {
			throw new JhqlJsonGrammarException("Unsupported queryer type: "
					+ type);
		}

		Queryer queryer;
		try {
			queryer = queryerClass.newInstance();
		} catch (Exception e) {
			throw new JhqlJsonGrammarException("Cannot instantiate queryer "
					+ type + ".", e);
		}

		Map<String, Object> queryExprCopy = new HashMap<String, Object>(
				queryExpr);
		queryExprCopy.remove("_type");

		PropertyDescriptor[] propertyDescriptors;

		try {
			BeanInfo beanInfo = Introspector.getBeanInfo(queryerClass);
			propertyDescriptors = beanInfo.getPropertyDescriptors();
		} catch (IntrospectionException e) {
			throw new JhqlJsonGrammarException("Cannot introspect queryer "
					+ type + ".", e);
		}

		for (PropertyDescriptor pd : propertyDescriptors) {
			String propertyName = pd.getName();

			Method writeMethod = pd.getWriteMethod();
			if (writeMethod == null) {
				continue; // Property not writable.
			}

			Object exprValue = queryExpr.get(propertyName);

			if (exprValue != null) {
				Class<?> propertyType = pd.getPropertyType();

				Object valueToWrite;
				if (Queryer.class.isAssignableFrom(propertyType)) {
					valueToWrite = makeQueryer(exprValue);
				} else {
					valueToWrite = exprValue;
				}

				try {
					writeMethod.invoke(queryer, valueToWrite);
				} catch (Exception e) {
					throw new JhqlJsonGrammarException("Cannot set property "
							+ propertyName + " on Queryer type " + type, e);
				}
				queryExprCopy.remove(propertyName);
			} else {
				Required requiredAnnotation = writeMethod
						.getAnnotation(Required.class);
				if (requiredAnnotation != null) {
					throw new JhqlJsonGrammarException("Property "
							+ propertyName + " is required for Queryer type "
							+ type);
				}
			}
		}

		if (!queryExprCopy.isEmpty()) {
			StringBuilder sb = new StringBuilder();
			boolean first = true;
			for (String str : queryExprCopy.keySet()) {
				if (first) {
					first = false;
				} else {
					sb.append(',');
				}
				sb.append(str);
			}
			throw new JhqlJsonGrammarException("Unexpected property "
					+ sb.toString() + " on Queryer type " + type);
		}

		return queryer;
	}

	private static Queryer makeObjectQueryer(Map<String, Object> queryExpr) {
		Map<String, Queryer> fieldRules = new HashMap<String, Queryer>();

		for (Map.Entry<String, Object> pair : queryExpr.entrySet()) {
			Queryer fieldQueryer = makeQueryer(pair.getValue());
			fieldRules.put(pair.getKey(), fieldQueryer);
		}
		return new ObjectQueryer(fieldRules);
	}
}
