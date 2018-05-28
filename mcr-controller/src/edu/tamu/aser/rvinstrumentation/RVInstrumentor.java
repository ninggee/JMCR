package edu.tamu.aser.rvinstrumentation;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import edu.tamu.aser.mcr.config.Configuration;
import edu.tamu.aser.reexecution.Scheduler;

public class RVInstrumentor {
    private static final String SLASH = "/";
    private static final String DOT = ".";
    private static final String SEMICOLON = ";";

    //
    public static String bufferClass;
    
    public static String logClass;
    private static final String JUC_DOTS = "java.util.concurrent";

    private static final String INSTRUMENTATION_PACKAGES_DEFAULT = "default";
    public static final String INSTR_EVENTS_RECEIVER = Scheduler.class.getName().replace(DOT, SLASH);

    public static Set<String> packagesThatWereInstrumented = new HashSet<String>();
    public static Set<String> packagesThatWereNOTInstrumented = new HashSet<String>();

    private static final Set<String> pckgPrefixesToIgnore = new HashSet<String>();
    private static final Set<String> pckgsToIgnore = new HashSet<String>();
    private static final Set<String> classPrefixesToIgnore = new HashSet<String>();
    private static final Set<String> classesToIgnore = new HashSet<String>();
    private static final Set<String> pckgPrefixesToAllow = new HashSet<String>();
    private static final Set<String> pckgsToAllow = new HashSet<String>();
    private static final Set<String> classPrefixesToAllow = new HashSet<String>();
    private static final Set<String> classesToAllow = new HashSet<String>();

    private static void storePropertyValues(String values, Set<String> toSet) {
        if (values != null) {
            String[] split = values.split(SEMICOLON);
            for (String val : split) {
                val = val.replace(DOT, SLASH).trim();
                if (!val.isEmpty()) {
                    toSet.add(val);
                }
            }
        }
    }

    private static boolean shouldInstrumentClass(String name) {       
        /*
         * @Alan
         * when using Java 8 for controller and controller-test
         * name could be null
         */
        if (name==null) {
            return false;
        }
        
//        System.err.println("insted class: " + name);
        
//        if (name.sub) {
//            
//        }
        
//        if(name.equals("java/util/concurrent/ArrayBlockingQueue")){
//            return true;
//        }
        
        //concurrent classes
//        if (name.startsWith("java/util/concurrent/ConcurrentHashMap") && !name.equals("java/util/concurrent/ConcurrentHashMap")) {
//            return true;
//        }
        
        String pckgName = INSTRUMENTATION_PACKAGES_DEFAULT;
        int lastSlashIndex = name.lastIndexOf(SLASH);
        // Non-default package
        if (lastSlashIndex != -1) {
            pckgName = name.substring(0, lastSlashIndex);
        }

        // Phase 1 - check if explicitly allowed
        if (classesToAllow.contains(name)) {
            packagesThatWereInstrumented.add(pckgName);
            return true;
        }

        // Phase 2 - check if prefix is allowed
        for (String classPrefix : classPrefixesToAllow) {
            if (name.startsWith(classPrefix)) {
                packagesThatWereInstrumented.add(pckgName);
                return true;
            }
        }

        // Phase 3 - check if package is allowed
        if (pckgsToAllow.contains(pckgName)) {
            packagesThatWereInstrumented.add(pckgName);
            return true;
        }

        // Phase 4 - check if package is allowed via prefix matching
        for (String pckgPrefix : pckgPrefixesToAllow) {
            if (pckgName.startsWith(pckgPrefix)) {
                packagesThatWereInstrumented.add(pckgName);
                return true;
            }
        }

        // Phase 5 - check for any ignores
        if (classesToIgnore.contains(name)) {
            packagesThatWereNOTInstrumented.add(pckgName);
            return false;
        }
        if (pckgsToIgnore.contains(pckgName)) {
            packagesThatWereNOTInstrumented.add(pckgName);
            return false;
        }
        for (String classPrefix : classPrefixesToIgnore) {
            if (name.startsWith(classPrefix)) {
                packagesThatWereNOTInstrumented.add(pckgName);
                return false;
            }
        }
        for (String pckgPrefix : pckgPrefixesToIgnore) {
            //System.out.println(pckgPrefix);
            if (pckgName.startsWith(pckgPrefix)) {
                if (pckgName.startsWith("com/googlecode")) {
                    return true;
                }
                packagesThatWereNOTInstrumented.add(pckgName);
                return false;
            }
        }

        // Otherwise instrument by default
        packagesThatWereInstrumented.add(pckgName);
        return true;
    }

    public static void premain(String agentArgs, Instrumentation inst) {
        MCRProperties mcrProps = MCRProperties.getInstance();
        storePropertyValues(mcrProps.getProperty(MCRProperties.INSTRUMENTATION_PACKAGES_IGNORE_PREFIXES_KEY), pckgPrefixesToIgnore);
        storePropertyValues(mcrProps.getProperty(MCRProperties.INSTRUMENTATION_PACKAGES_IGNORE_KEY), pckgsToIgnore);
        storePropertyValues(mcrProps.getProperty(MCRProperties.INSTRUMENTATION_CLASSES_IGNORE_PREFIXES_KEY), classPrefixesToIgnore);
        storePropertyValues(mcrProps.getProperty(MCRProperties.INSTRUMENTATION_CLASSES_IGNORE_KEY), classesToIgnore);
        storePropertyValues(mcrProps.getProperty(MCRProperties.INSTRUMENTATION_PACKAGES_ALLOW_PREFIXES_KEY), pckgPrefixesToAllow);
        storePropertyValues(mcrProps.getProperty(MCRProperties.INSTRUMENTATION_PACKAGES_ALLOW_KEY), pckgsToAllow);
        storePropertyValues(mcrProps.getProperty(MCRProperties.INSTRUMENTATION_CLASSES_ALLOW_PREFIXES_KEY), classPrefixesToAllow);
        storePropertyValues(mcrProps.getProperty(MCRProperties.INSTRUMENTATION_CLASSES_ALLOW_KEY), classesToAllow);
        
        
        
//        pckgPrefixesToIgnore.remove("com/google");
        
        /* @Alan set the memory model */
        String memory_model = System.getProperty("memory_model");
        if (memory_model != null && !memory_model.isEmpty()) {
            RVConfig.instance.mode=memory_model;
        }
//        System.out.println("memory model:" + RVConfig.instance.mode);
        
        /* 
         * @Alan  setup the configuration here 
         * I wanna some files just read once
         */
        final boolean debug = Boolean.parseBoolean(System.getProperty("debug"));
        final boolean static_opt = Boolean.parseBoolean(System.getProperty("static_opt"));
        Configuration.DEBUG = debug;
        Configuration.Optimize = static_opt;
        Configuration.setup();  
        
        /*
         * choose the runtime instrumentation based on the model checker specified
         */
        logClass = "edu/tamu/aser/rvinstrumentation/RVRunTime"; 
        /*
         * 1. create an anonymous class which implements classfiletransformer
         * 2. create an instance of the anonymous class
         */
        inst.addTransformer(new ClassFileTransformer() {
            //the transform method has to be implemented by the transformer class
            
            //when a class is loaded by the JVM, the function is invoked
            public byte[] transform(ClassLoader l, String name, Class<?> c, ProtectionDomain d, byte[] bytes) throws IllegalClassFormatException {
                try {
                    /*
                     * If the package is included in the packages to instrument,
                     * or the class is included in the classes to instrument,
                     * instrument the class
                     */
                    if (shouldInstrumentClass(name)) {
//                        System.err.println("Instrumented " + name);
                        ClassReader classReader = new ClassReader(bytes); //bytes is the .class we are going to read
                        ClassWriter classWriter = new ExtendedClassWriter(classReader, ClassWriter.COMPUTE_FRAMES);

                        RVSharedAccessEventsClassTransformer rvsharedAccessEventsTransformer = new RVSharedAccessEventsClassTransformer(classWriter);
                        //classReader.accept(tcv, ClassReader.EXPAND_FRAMES);
                        //in the accept method, it calls visitor.visit()
                        classReader.accept(rvsharedAccessEventsTransformer, ClassReader.EXPAND_FRAMES);
                        bytes = classWriter.toByteArray();
                         
                        /*
                         * If debugging is enabled, check and print out the
                         * instrumented class
                         */
                        if (Configuration.DEBUG) 
                        {
                            System.out.println("Instrumented " + name);
                        }
                        
                        // output new class files after instrumenting classes
//                        
//                        try {
//                            File file =  new File("D:/MCR/" + name.replace("/", "."));
//                          
//                            FileOutputStream fout = new FileOutputStream(file);
//                            fout.write(bytes);
//                            fout.close();
//                        } catch(IOException e) {
//                            e.printStackTrace();
//                        }
                        
                    }
                } catch (Throwable th) {
                    th.printStackTrace();
                    System.err.println(th.getMessage());
                }   
                //返回插桩好的代码
                return bytes;
            }
        }, true);

        /* Re-transform already loaded java.util.concurrent classes */
        try {
            List<Class<?>> classesToReTransform = new ArrayList<Class<?>>();
            
            for (Class<?> loadedClass : inst.getAllLoadedClasses()) {
//                System.err.println("all loaded classes: " + loadedClass.toString());
                if (inst.isModifiableClass(loadedClass) && loadedClass.getPackage().getName().startsWith(JUC_DOTS)) {
                    classesToReTransform.add(loadedClass);
                }
            }
            inst.retransformClasses(classesToReTransform.toArray(new Class<?>[classesToReTransform.size()]));
        } catch (UnmodifiableClassException e) {
            e.printStackTrace();
            System.err.println("Unable to modify a pre-loaded java.util.concurrent class!");
            System.exit(2);
        }
    }
   
}
