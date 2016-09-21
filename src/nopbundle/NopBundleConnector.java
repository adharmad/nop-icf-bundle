package nopbundle;

import org.identityconnectors.framework.common.FrameworkUtil;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.FilterTranslator;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;
import org.identityconnectors.framework.spi.ConnectorClass;
import org.identityconnectors.framework.spi.operations.CreateOp;
import org.identityconnectors.framework.spi.operations.DeleteOp;
import org.identityconnectors.framework.spi.operations.SchemaOp;
import org.identityconnectors.framework.spi.operations.ScriptOnConnectorOp;
import org.identityconnectors.framework.spi.operations.ScriptOnResourceOp;
import org.identityconnectors.framework.spi.operations.SearchOp;
import org.identityconnectors.framework.spi.operations.UpdateOp;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ConnectorClass(displayNameKey = "DummyBundleConnector", configurationClass = DummyBundleConfiguration.class)
public class NopBundleConnector
        implements Connector, CreateOp, DeleteOp, SearchOp<String>, UpdateOp, SchemaOp, ScriptOnConnectorOp,
        ScriptOnResourceOp {

    private static int _uidIndex;
    private static Schema _schema;
    private static Class<? extends Object> _supportedTypes[] = FrameworkUtil.getAllSupportedAttributeTypes().toArray(new Class[0]);
    private static Map<Uid, Set<Attribute>> _map = new HashMap<Uid, Set<Attribute>>();
    private DummyBundleConfiguration _configuration;

    public DummyBundleConnector() {
    }

    public void dispose() {
    }

    public Configuration getConfiguration() {
        return _configuration;
    }

    public void init(Configuration cfg) {
        _configuration = (DummyBundleConfiguration) cfg;
        _schema = schema();
    }

    public Uid create(ObjectClass objClass, Set<Attribute> attrs, OperationOptions options) {
        validateAttributes(objClass, attrs, true);
        Uid uid = generateUid();
        _map.put(uid, attrs);
        return uid;
    }

    private void validateAttributes(ObjectClass oclass, Set<Attribute> attrs, boolean checkRequired) {
        if (!_configuration.isStrict()) {
            return;
        }
        ObjectClassInfo oci = _schema.findObjectClassInfo(oclass.getObjectClassValue());


        Map<String, Attribute> attrMap = new HashMap<String, Attribute>(AttributeUtil.toMap(attrs));
        if (!attrMap.containsKey(Name.NAME))
            throw new IllegalArgumentException((new StringBuilder()).append("Required attribute ").append(Name.NAME).append(" is missing").toString());
        for (AttributeInfo attributeInfo : oci.getAttributeInfo()) {

            if (!(attributeInfo.isCreateable() || attributeInfo.isUpdateable()) && attrMap.containsKey(attributeInfo.getName()))
                throw new IllegalArgumentException((new StringBuilder()).append("Non-writeable attribute ").append(attributeInfo.getName()).append(" is present").toString());
        }

    }

    public synchronized Uid generateUid() {
        _uidIndex++;
        return new Uid((new StringBuilder()).append(_uidIndex).append("").toString());
    }

    public void delete(ObjectClass objClass, Uid uid, OperationOptions options) {
        if (!_map.containsKey(uid)) {
            throw new UnknownUidException();
        } else {
            _map.remove(uid);
        }
    }

    public FilterTranslator<String> createFilterTranslator(ObjectClass oclass, OperationOptions options) {
        return new DummyBundleFilterTranslator();
    }

    public void executeQuery(ObjectClass oclass, String query, ResultsHandler handler, OperationOptions options) {
        String attrsToGet[] = options.getAttributesToGet();
        ConnectorObjectBuilder builder;
        for (Iterator<Map.Entry<Uid, Set<Attribute>>> iter = _map.entrySet().iterator(); iter.hasNext(); handler.handle(builder.build())) {
            java.util.Map.Entry<Uid, Set<Attribute>> entry = iter.next();
            builder = new ConnectorObjectBuilder();
            if (attrsToGet == null) {
                builder.addAttributes(entry.getValue());
            } else {
                Map<String, Attribute> map = AttributeUtil.toMap(entry.getValue());
                int length = attrsToGet.length;
                for (int i = 0; i < length; i++) {
                    String attribute = attrsToGet[i];
                    Attribute fetchedAttribute = (Attribute) map.get(attribute);
                    if (fetchedAttribute != null) {
                        builder.addAttribute(fetchedAttribute);
                    }
                }
                // always add Name
                builder.addAttribute(map.get(Name.NAME));
            }
            builder.setUid((Uid) entry.getKey());
        }

    }


    public Uid update(ObjectClass obj, Uid uid, Set<Attribute> attrs, OperationOptions options) {
        return update(obj, AttributeUtil.addUid(attrs, uid), options);
    }

    private Uid update(ObjectClass objclass, Set<Attribute> attrs, OperationOptions options) {
        Map<String, Attribute> attrMap = new HashMap<String, Attribute>(AttributeUtil.toMap(attrs));
        Uid uid = (Uid) attrMap.remove(Uid.NAME);
        if (uid == null)
            throw new RuntimeException("missing Uid");
        if (!_map.containsKey(uid)) {
            throw new UnknownUidException();
        } else {
            attrMap.remove(OperationalAttributeInfos.CURRENT_PASSWORD.getName());
            Map<String, Attribute> objectMap = new HashMap<String, Attribute>(AttributeUtil.toMap(_map.get(uid)));
            validateAttributes(objclass, attrs, false);
            objectMap.putAll(attrMap);
            _map.put(uid, new HashSet<Attribute>(objectMap.values()));
            return uid;
        }
    }

    public Schema schema() {
        return staticSchema();
    }

    public static Schema staticSchema() {
        if (_schema != null)
            return _schema;
        try {
            SchemaBuilder schemaBuilder = new SchemaBuilder(DummyBundleConnector.class);
            Set<AttributeInfo> attributes = new HashSet<AttributeInfo>();
            attributes.add(OperationalAttributeInfos.CURRENT_PASSWORD);
            attributes.add(OperationalAttributeInfos.DISABLE_DATE);
            attributes.add(OperationalAttributeInfos.ENABLE);
            attributes.add(OperationalAttributeInfos.ENABLE_DATE);
            attributes.add(OperationalAttributeInfos.LOCK_OUT);
            attributes.add(OperationalAttributeInfos.PASSWORD);
            attributes.add(OperationalAttributeInfos.PASSWORD_EXPIRATION_DATE);
            Method setters[] = getAttributeInfoBuilderSetters();
            AttributeInfoBuilder builder = new AttributeInfoBuilder();
            attributes.addAll(buildAttributeInfo(builder, setters));
            schemaBuilder.defineObjectClass(ObjectClass.ACCOUNT_NAME, attributes);
            _schema = schemaBuilder.build();
            return _schema;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private static List<AttributeInfo> buildAttributeInfo(AttributeInfoBuilder builder, Method methods[])
            throws IllegalArgumentException, IllegalAccessException, InvocationTargetException {
        List<AttributeInfo> list = new LinkedList<AttributeInfo>();
        buildAttributeInfo(builder, methods, 0, "", list, _supportedTypes, new int[]{
                0
        });
        return list;
    }

    private static void buildAttributeInfo(AttributeInfoBuilder builder, Method methods[], int index, String name, List<AttributeInfo> list, Class<? extends Object> supportedTypes[], int typeIndex[])
            throws IllegalArgumentException, IllegalAccessException, InvocationTargetException {
        if (index < methods.length) {
            methods[index].invoke(builder, true);
            // We want to make arrays more obvious, so we will use Array in the name
            //
            String t = "_T";
            if (methods[index].getName().contains("MultiValue"))
                t = "_Array";
            buildAttributeInfo(builder, methods, index + 1, (new StringBuilder()).append(name).append(t).toString(), list, supportedTypes, typeIndex);
            methods[index].invoke(builder, false);
            buildAttributeInfo(builder, methods, index + 1, (new StringBuilder()).append(name).append("_F").toString(), list, supportedTypes, typeIndex);
        } else {
            Class<? extends Object> clazz = supportedTypes[typeIndex[0]];
            String prefix = clazz.getSimpleName();
            if (clazz.isArray())
                prefix = (new StringBuilder()).append(clazz.getComponentType().getSimpleName()).append("Array").toString();
            builder.setName((new StringBuilder()).append(prefix).append(name).toString().toUpperCase());
            builder.setType(clazz);
            try {
                AttributeInfo info = builder.build();
                // Exclude attributes marked required, but not creatable
                //
                boolean ignore1 = !info.isCreateable() && info.isRequired();
                boolean ignore2 = !info.isReadable() && info.isReturnedByDefault();
                boolean ignore3 = info.isRequired() && clazz.equals(byte[].class);
                if (!(ignore1 || ignore2 || ignore3)) {
                    list.add(info);
                    typeIndex[0]++;
                    if (typeIndex[0] == supportedTypes.length)
                        typeIndex[0] = 0;
                }
            } catch (IllegalArgumentException iae) {
                // If it was an illegal cobo, we can't use it.
            }
        }
    }

    private static Method[] getAttributeInfoBuilderSetters() {
        List<Method> setters = new LinkedList<Method>();
        Method methods[] = AttributeInfoBuilder.class.getMethods();
        for (Method method : methods) {
            if (!method.getName().startsWith("setReturned") && method.getName().startsWith("set") &&
                    method.getParameterTypes().length == 1 &&
                    (method.getParameterTypes()[0] == Boolean.TYPE || method.getParameterTypes()[0] == Boolean.class))
                setters.add(method);
        }
        Method[] methodArray = (Method[]) setters.toArray(new Method[0]);
        Arrays.sort(methodArray, new MethodComparator());
        System.out.println("Order of setters is:");
        for (Method method : methodArray)
            System.out.println("    " + method.getName());
        return methodArray;
    }

    public Object runScriptOnConnector(ScriptContext request, OperationOptions options) {
        return "OK";
    }

    public Object runScriptOnResource(ScriptContext request, OperationOptions options) {
        return "OK";
    }

    private static class MethodComparator implements Comparator<Method> {
        public int compare(Method o1, Method o2) {
            return o1.getName().compareTo(o2.getName());
        }

    }


}
