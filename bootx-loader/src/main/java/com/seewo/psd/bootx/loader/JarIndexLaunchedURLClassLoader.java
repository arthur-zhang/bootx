package com.seewo.psd.bootx.loader;
/*
 * Copyright 2012-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import org.springframework.boot.loader.Launcher;
import org.springframework.boot.loader.archive.Archive;
import org.springframework.boot.loader.jar.Handler;
import sun.misc.Resource;
import sun.net.www.ParseUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.security.AccessController;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.PrivilegedExceptionAction;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * {@link ClassLoader} used by the {@link Launcher}.
 *
 * @author Phillip Webb
 * @author Dave Syer
 * @author Andy Wilkinson
 * @since 1.0.0
 */
public class JarIndexLaunchedURLClassLoader extends URLClassLoader {

    private static final int BUFFER_SIZE = 4096;

    static {
        ClassLoader.registerAsParallelCapable();
    }

    private final boolean exploded;

    private final Archive rootArchive;

    private final Object packageLock = new Object();

    private volatile DefinePackageCallType definePackageCallType;

    private static Map<String, Set<String>> prefixMap = null; // jarname to package
    private static boolean DEBUG = true;

    static {
        try {
            prefixMap = IndexParser.indexListParser();
        } catch (IOException e) {
        }
    }

    /**
     * Create a new {@link org.springframework.boot.loader.LaunchedURLClassLoader} instance.
     * @param urls the URLs from which to load classes and resources
     * @param parent the parent class loader for delegation
     */
    public JarIndexLaunchedURLClassLoader(URL[] urls, ClassLoader parent) {
        this(false, urls, parent);
    }

    /**
     * Create a new {@link org.springframework.boot.loader.LaunchedURLClassLoader} instance.
     * @param exploded if the underlying archive is exploded
     * @param urls the URLs from which to load classes and resources
     * @param parent the parent class loader for delegation
     */
    public JarIndexLaunchedURLClassLoader(boolean exploded, URL[] urls, ClassLoader parent) {
        this(exploded, null, urls, parent);
    }

    /**
     * Create a new {@link org.springframework.boot.loader.LaunchedURLClassLoader} instance.
     * @param exploded if the underlying archive is exploded
     * @param rootArchive the root archive or {@code null}
     * @param urls the URLs from which to load classes and resources
     * @param parent the parent class loader for delegation
     * @since 2.3.1
     */
    public JarIndexLaunchedURLClassLoader(boolean exploded, Archive rootArchive, URL[] urls, ClassLoader parent) {
        super(urls, parent);
        this.exploded = exploded;
        this.rootArchive = rootArchive;

        initJarIndex(urls);
    }
    private static final Map<String, List<URL>> package2UrlMap = new ConcurrentHashMap<>();

    private void initJarIndex(URL[] urls) {
        Map<String, URL>  urlMap = extracted(urls);
        prefixMap.forEach((jarName, packageNameSet) -> {
            URL url = urlMap.get(jarName);
            if (url == null) return;
            for (String pkgName : packageNameSet) {
                package2UrlMap.putIfAbsent(pkgName, new ArrayList<>());
                package2UrlMap.get(pkgName).add(url);
            }
        });
    }

    private Map<String, URL> extracted(URL[] urls) {
        Map<String, URL> urlMap = new HashMap<>();
        for (URL url : urls) {
            String urlStr = url.toString();
            int idx = urlStr.indexOf(".jar!");
            if(idx < 0) continue;
            if (urlStr.endsWith("!/")) {
                urlStr = urlStr.substring(idx + ".jar!".length(), urlStr.length()-2);
            } else {
                urlStr = urlStr.substring(idx + ".jar!".length());
            }
            urlMap.put(urlStr, url);
        }
        return urlMap;
    }

    @Override
    public URL findResource(String name) {
        if (this.exploded) {
            return super.findResource(name);
        }
        Handler.setUseFastConnectionExceptions(true);
        try {
            return super.findResource(name);
        }
        finally {
            Handler.setUseFastConnectionExceptions(false);
        }
    }

    @Override
    public Enumeration<URL> findResources(String name) throws IOException {
        if (this.exploded) {
            return super.findResources(name);
        }
        Handler.setUseFastConnectionExceptions(true);
        try {
            return new UseFastConnectionExceptionsEnumeration(super.findResources(name));
        }
        finally {
            Handler.setUseFastConnectionExceptions(false);
        }
    }

    Resource getResource(URL base, String name, final String path) {
        final URL url;
        try {
            url = new URL(base, ParseUtil.encodePath(path, false));
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("name");
        }
        if (DEBUG) System.out.println(">>>>getResource: " + url.toString());

        final URLConnection uc;
        try {
            uc = url.openConnection();
            uc.connect();
        } catch (IOException e) {
            return null;
        }
        return new Resource() {
            public String getName() {
                return name;
            }

            public URL getURL() {
                return url;
            }

            public URL getCodeSourceURL() {
                return base;
            }

            public InputStream getInputStream() throws IOException {
                return uc.getInputStream();
            }

            public int getContentLength() {
                return uc.getContentLength();
            }
        };
    }
        @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // load loader classes directly
        if (name.startsWith("org.springframework.boot.loader.") || name.startsWith("com.seewo.psd.bootx.loader.")) {
            try {
                Class<?> result = loadClassInLaunchedClassLoader(name);
                if (resolve) {
                    resolveClass(result);
                }
                return result;
            }
            catch (ClassNotFoundException ex) {
            }
        }

        // skip java.*, org.w3c.dom.* com.sun.*
        if (!name.startsWith("java") && !name.contains("org.w3c.dom.") && !name.contains("xml") &&!name.startsWith("com.sun")) {

            if (DEBUG) System.out.println(">>>>>loading " + name);
            int lastDot = name.lastIndexOf('.');
            if (lastDot >= 0) {
                String packageName = name.substring(0, lastDot);
                String packageEntryName = packageName.replace('.', '/');
                String path = name.replace('.', '/').concat(".class");

                List<URL> urls = package2UrlMap.get(packageEntryName);
                if (DEBUG) System.out.println(">>>>jar list not null: " + name);
                if (urls != null) {
                    if (DEBUG) System.out.println(">>>>urls not null: " + name);
                    for (int i = 0; i < urls.size(); i++) {
                        URL base = urls.get(i);
                        if (DEBUG) System.out.println(">>>>process url: " + base + "\t" + packageEntryName + "\t" + path);
//                        JarURLConnection urlConnection = (JarURLConnection) base.openConnection();
//                        JarFile jarFile = urlConnection.getJarFile();
//                        JarEntry jarEntry = jarFile.getJarEntry(path);


                        Resource resource = getResource(base, packageName, path);
                        if (resource == null) {
//							System.out.println(">>>>> resource is null: " + base + "\t" + packageName + "\t" + path);
                            continue;
                        }
                        Class<?> definedClass;
                        try {
                            byte[] bytes = resource.getBytes();
                            definedClass = defineClass(name, bytes, 0, bytes.length, new CodeSource(base,new CodeSigner[]{}));
//							if (DEBUG) System.out.println(">>>>>define class success: " + name);
                        } catch (IOException e) {
                            e.printStackTrace();
                            throw new RuntimeException(e);
                        }
                        definePackageIfNecessary(name);
                        return definedClass;
                    }
                } else {
                    if (DEBUG) System.out.println("url is null" + packageEntryName);
                }
            }
        }

        if (this.exploded) {
            return super.loadClass(name, resolve);
        }
        Handler.setUseFastConnectionExceptions(true);
        try {
            try {
                definePackageIfNecessary(name);
            }
            catch (IllegalArgumentException ex) {
                // Tolerate race condition due to being parallel capable
                if (getPackage(name) == null) {
                    // This should never happen as the IllegalArgumentException indicates
                    // that the package has already been defined and, therefore,
                    // getPackage(name) should not return null.
                    throw new AssertionError("Package " + name + " has already been defined but it could not be found");
                }
            }
            return super.loadClass(name, resolve);
        }
        finally {
            Handler.setUseFastConnectionExceptions(false);
        }
    }

    private Class<?> loadClassInLaunchedClassLoader(String name) throws ClassNotFoundException {
        String internalName = name.replace('.', '/') + ".class";
        InputStream inputStream = getParent().getResourceAsStream(internalName);
        if (inputStream == null) {
            throw new ClassNotFoundException(name);
        }
        try {
            try {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead = -1;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                inputStream.close();
                byte[] bytes = outputStream.toByteArray();
                Class<?> definedClass = defineClass(name, bytes, 0, bytes.length);
                definePackageIfNecessary(name);
                return definedClass;
            }
            finally {
                inputStream.close();
            }
        }
        catch (IOException ex) {
            throw new ClassNotFoundException("Cannot load resource for class [" + name + "]", ex);
        }
    }

    /**
     * Define a package before a {@code findClass} call is made. This is necessary to
     * ensure that the appropriate manifest for nested JARs is associated with the
     * package.
     * @param className the class name being found
     */
    private void definePackageIfNecessary(String className) {
        int lastDot = className.lastIndexOf('.');
        if (lastDot >= 0) {
            String packageName = className.substring(0, lastDot);
            if (getPackage(packageName) == null) {
                try {
                    definePackage(className, packageName);
                }
                catch (IllegalArgumentException ex) {
                    // Tolerate race condition due to being parallel capable
                    if (getPackage(packageName) == null) {
                        // This should never happen as the IllegalArgumentException
                        // indicates that the package has already been defined and,
                        // therefore, getPackage(name) should not have returned null.
                        throw new AssertionError(
                                "Package " + packageName + " has already been defined but it could not be found");
                    }
                }
            }
        }
    }

    private void definePackage(String className, String packageName) {
        try {
            AccessController.doPrivileged((PrivilegedExceptionAction<Object>) () -> {
                String packageEntryName = packageName.replace('.', '/') + "/";
                String classEntryName = className.replace('.', '/') + ".class";
                for (URL url : getURLs()) {
                    try {
                        URLConnection connection = url.openConnection();
                        if (connection instanceof JarURLConnection) {
                            JarFile jarFile = ((JarURLConnection) connection).getJarFile();
                            if (jarFile.getEntry(classEntryName) != null && jarFile.getEntry(packageEntryName) != null
                                    && jarFile.getManifest() != null) {
                                definePackage(packageName, jarFile.getManifest(), url);
                                return null;
                            }
                        }
                    }
                    catch (IOException ex) {
                        // Ignore
                    }
                }
                return null;
            }, AccessController.getContext());
        }
        catch (java.security.PrivilegedActionException ex) {
            // Ignore
        }
    }

    @Override
    protected Package definePackage(String name, Manifest man, URL url) throws IllegalArgumentException {
        if (!this.exploded) {
            return super.definePackage(name, man, url);
        }
        synchronized (this.packageLock) {
            return doDefinePackage(DefinePackageCallType.MANIFEST, () -> super.definePackage(name, man, url));
        }
    }

    @Override
    protected Package definePackage(String name, String specTitle, String specVersion, String specVendor,
                                    String implTitle, String implVersion, String implVendor, URL sealBase) throws IllegalArgumentException {
        if (!this.exploded) {
            return super.definePackage(name, specTitle, specVersion, specVendor, implTitle, implVersion, implVendor,
                    sealBase);
        }
        synchronized (this.packageLock) {
            if (this.definePackageCallType == null) {
                // We're not part of a call chain which means that the URLClassLoader
                // is trying to define a package for our exploded JAR. We use the
                // manifest version to ensure package attributes are set
                Manifest manifest = getManifest(this.rootArchive);
                if (manifest != null) {
                    return definePackage(name, manifest, sealBase);
                }
            }
            return doDefinePackage(DefinePackageCallType.ATTRIBUTES, () -> super.definePackage(name, specTitle,
                    specVersion, specVendor, implTitle, implVersion, implVendor, sealBase));
        }
    }

    private Manifest getManifest(Archive archive) {
        try {
            return (archive != null) ? archive.getManifest() : null;
        }
        catch (IOException ex) {
            return null;
        }
    }

    private <T> T doDefinePackage(DefinePackageCallType type, Supplier<T> call) {
        DefinePackageCallType existingType = this.definePackageCallType;
        try {
            this.definePackageCallType = type;
            return call.get();
        }
        finally {
            this.definePackageCallType = existingType;
        }
    }

    /**
     * Clear URL caches.
     */
    public void clearCache() {
        if (this.exploded) {
            return;
        }
        for (URL url : getURLs()) {
            try {
                URLConnection connection = url.openConnection();
                if (connection instanceof JarURLConnection) {
                    clearCache(connection);
                }
            }
            catch (IOException ex) {
                // Ignore
            }
        }

    }

    private void clearCache(URLConnection connection) throws IOException {
        Object jarFile = ((JarURLConnection) connection).getJarFile();
        if (jarFile instanceof org.springframework.boot.loader.jar.JarFile) {
            ((org.springframework.boot.loader.jar.JarFile) jarFile).clearCache();
        }
    }

    private static class UseFastConnectionExceptionsEnumeration implements Enumeration<URL> {

        private final Enumeration<URL> delegate;

        UseFastConnectionExceptionsEnumeration(Enumeration<URL> delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean hasMoreElements() {
            Handler.setUseFastConnectionExceptions(true);
            try {
                return this.delegate.hasMoreElements();
            }
            finally {
                Handler.setUseFastConnectionExceptions(false);
            }

        }

        @Override
        public URL nextElement() {
            Handler.setUseFastConnectionExceptions(true);
            try {
                return this.delegate.nextElement();
            }
            finally {
                Handler.setUseFastConnectionExceptions(false);
            }
        }

    }

    /**
     * The different types of call made to define a package. We track these for exploded
     * jars so that we can detect packages that should have manifest attributes applied.
     */
    private enum DefinePackageCallType {

        /**
         * A define package call from a resource that has a manifest.
         */
        MANIFEST,

        /**
         * A define package call with a direct set of attributes.
         */
        ATTRIBUTES

    }

}
