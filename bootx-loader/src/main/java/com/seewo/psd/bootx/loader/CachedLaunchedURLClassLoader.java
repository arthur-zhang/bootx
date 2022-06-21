package com.seewo.psd.bootx.loader;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.loader.LaunchedURLClassLoader;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Optional;

import static java.util.concurrent.TimeUnit.SECONDS;

public class CachedLaunchedURLClassLoader extends LaunchedURLClassLoader {
    static {
        ClassLoader.registerAsParallelCapable();
    }

    protected Cache<String, LoadClassResult> classCache = Caffeine.newBuilder().initialCapacity(8000)
            .maximumSize(8000)
            .expireAfterWrite(120, SECONDS).recordStats().build();
    protected Cache<String, Optional<URL>> resourceUrlCache = Caffeine.newBuilder().maximumSize(4000)
            .expireAfterWrite(60, SECONDS).build();
    protected Cache<String, Optional<Enumeration<URL>>> resourcesUrlCache = Caffeine.newBuilder().maximumSize(4000)
            .expireAfterWrite(60, SECONDS).build();

    protected Cache<String, Optional<Package>> packageCache = Caffeine.newBuilder().initialCapacity(4000)
            .maximumSize(4000)
            .expireAfterWrite(120, SECONDS).recordStats().build();

    public CachedLaunchedURLClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
        System.out.println(">>>>>>>>in CachedLaunchedURLClassLoader");
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        return loadClassWithCache(name, resolve);
    }

    @Override
    public URL findResource(String name) {
        Optional<URL> urlOptional = resourceUrlCache.get(name, it -> {
            URL url = CachedLaunchedURLClassLoader.super.findResource(name);
            return url != null ? Optional.of(url) : Optional.empty();
        });
        return urlOptional.orElse(null);
    }

    @Override
    public Enumeration<URL> findResources(String name) throws IOException {
        Optional<Enumeration<URL>> urlOptional = resourcesUrlCache.get(name, it -> {
                    Enumeration<URL> enumeration = null;
                    try {
                        enumeration = CachedLaunchedURLClassLoader.super.findResources(name);
                    } catch (IOException e) {
                    }
                    return enumeration != null ? Optional.of(enumeration) : Optional.empty();
                }
        );
        return urlOptional.orElse(null);
    }

    protected static class LoadClassResult {
        private Class<?> clazz;
        private ClassNotFoundException ex;

        public LoadClassResult() {
        }

        public LoadClassResult(ClassNotFoundException ex) {
            this.ex = ex;
        }

        public Class<?> getClazz() {
            return clazz;
        }

        public void setClazz(Class<?> clazz) {
            this.clazz = clazz;
        }

        public ClassNotFoundException getEx() {
            return ex;
        }

        public void setEx(ClassNotFoundException ex) {
            this.ex = ex;
        }
    }

    private Class<?> loadClassWithCache(String name, boolean resolve) throws ClassNotFoundException {

        LoadClassResult resultInCache = classCache.get(name, it -> {
            LoadClassResult r = new LoadClassResult();
            try {
                Class<?> clazz = CachedLaunchedURLClassLoader.super.loadClass(it, resolve);
                r.setClazz(clazz);
            } catch (ClassNotFoundException e) {
                r.setEx(e);
            }
            return r;
        });

        if (resultInCache.getEx() != null) {
            throw resultInCache.getEx();
        }
        return resultInCache.getClazz();
    }
}
