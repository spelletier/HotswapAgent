package org.hotswap.agent.plugin.webobjects;

import static org.hotswap.agent.annotation.LoadEvent.REDEFINE;

import java.lang.reflect.*;
import java.net.URI;

import org.hotswap.agent.annotation.*;
import org.hotswap.agent.config.PluginConfiguration;
import org.hotswap.agent.javassist.*;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;

@Plugin(name = "WebObjects", description = "Hotswap agent plugin for WebObjects app.",
testedVersions = "5.4.3",
expectedVersions = "5.4.3")

public class HotswapWebObjectsPlugin {
	
    // Agent logger is a very simple custom logging mechanism. Do not use any common logging framework
    // to avoid compatibility and classloading issues.
    private static AgentLogger LOGGER = AgentLogger.getLogger(HotswapWebObjectsPlugin.class);
    
	private Method kvcDefaultImplementation_flushCaches;
	private Method kvcReflectionKeyBindingCreation_flushCaches;
	private Method kvcValueAccessor_flushCaches;
	private Method nsValidationDefaultImplementation_flushCaches;
	private Method woApplication_removeComponentDefinitionCacheContents;
	private Object woApplicationObject;
	private Method nsThreadsafeMutableDictionary_removeAllObjects;
	private Object actionClassesCacheDictionnary;
	
	private CtClass woActionCtClass;
	private CtClass woComponentCtClass;
	private CtClass nsValidationCtClass;

    @OnClassLoadEvent(classNameRegexp = "com.webobjects.appserver.WOApplication")
    public static void webObjectsIsStarting(CtClass ctClass) throws NotFoundException, CannotCompileException {
        CtMethod init = ctClass.getDeclaredMethod("run");
        init.insertBefore(PluginManagerInvoker.buildInitializePlugin(HotswapWebObjectsPlugin.class));
        LOGGER.debug("WOApplication.run() enhanced with plugin initialization.");
    }


    // We use reflection to get the methods from WebObjects because the jar is not distribuable publicly
    // and we want to build witout it.
    @Init
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void init(PluginConfiguration pluginConfiguration, ClassLoader appClassLoader) {
		try {
			Class kvcDefaultImplementationClass = Class.forName("com.webobjects.foundation.NSKeyValueCoding$DefaultImplementation", false, appClassLoader);
	    	kvcDefaultImplementation_flushCaches = kvcDefaultImplementationClass.getMethod("_flushCaches");

	    	Class kvcReflectionKeyBindingCreationClass = Class.forName("com.webobjects.foundation.NSKeyValueCoding$_ReflectionKeyBindingCreation", false, appClassLoader);
	    	kvcReflectionKeyBindingCreation_flushCaches = kvcReflectionKeyBindingCreationClass.getMethod("_flushCaches");

	    	Class kvcValueAccessorClass = Class.forName("com.webobjects.foundation.NSKeyValueCoding$ValueAccessor", false, appClassLoader);
	    	kvcValueAccessor_flushCaches = kvcValueAccessorClass.getMethod("_flushCaches");
	    	
	    	Class nsValidationDefaultImplementationClass = Class.forName("com.webobjects.foundation.NSValidation$DefaultImplementation", false, appClassLoader);
	    	nsValidationDefaultImplementation_flushCaches = nsValidationDefaultImplementationClass.getMethod("_flushCaches");

	    	Class woApplicationClass = Class.forName("com.webobjects.appserver.WOApplication", false, appClassLoader);
	    	woApplication_removeComponentDefinitionCacheContents = woApplicationClass.getMethod("_removeComponentDefinitionCacheContents");
	    	woApplicationObject = woApplicationClass.getMethod("application").invoke(null);

	    	ClassPool classPool = ClassPool.getDefault();
	    	woComponentCtClass = classPool.makeClass("com.webobjects.appserver.WOComponent");
	    	nsValidationCtClass = classPool.makeClass("com.webobjects.foundation.NSValidation");
	    	woActionCtClass = classPool.makeClass("com.webobjects.appserver.WOAction");

	    	Class woActionClass = Class.forName("com.webobjects.appserver.WOAction", false, appClassLoader);
			Field actionClassesField = woActionClass.getDeclaredField("_actionClasses");
			actionClassesField.setAccessible(true);
			actionClassesCacheDictionnary = actionClassesField.get(null);
			
	    	Class nsThreadsafeMutableDictionaryClass = Class.forName("com.webobjects.foundation._NSThreadsafeMutableDictionary", false, appClassLoader);
	    	woApplication_removeComponentDefinitionCacheContents = woApplicationClass.getMethod("_removeComponentDefinitionCacheContents");
	    	nsThreadsafeMutableDictionary_removeAllObjects = nsThreadsafeMutableDictionaryClass.getMethod("removeAllObjects");
		} catch (Exception e) {
			e.printStackTrace();
		}
    }

    @OnClassLoadEvent(classNameRegexp = ".*", events = REDEFINE)
    public void reloadClass(CtClass ctClass) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, CannotCompileException {
    	LOGGER.debug("Class "+ctClass.getSimpleName()+" redefined.");
    	
    	LOGGER.info("Resetting KeyValueCoding caches");
    	kvcDefaultImplementation_flushCaches.invoke(null);
    	kvcReflectionKeyBindingCreation_flushCaches.invoke(null);
    	kvcValueAccessor_flushCaches.invoke(null);

		woApplication_removeComponentDefinitionCacheContents.invoke(woApplicationObject);
    	if (ctClass.subclassOf(woComponentCtClass)) {
    		woApplication_removeComponentDefinitionCacheContents.invoke(woApplicationObject);
    		LOGGER.info("Resetting Component Definition cache");
    	}
    	if (ctClass.subclassOf(woActionCtClass)) {
    		nsThreadsafeMutableDictionary_removeAllObjects.invoke(actionClassesCacheDictionnary);
    		LOGGER.info("Resetting Action class cache");
    	}
    	if (ctClass.subclassOf(nsValidationCtClass)) {
    		nsValidationDefaultImplementation_flushCaches.invoke(null);
    		LOGGER.info("Resetting NSValidation cache");
    	}
    }
}
