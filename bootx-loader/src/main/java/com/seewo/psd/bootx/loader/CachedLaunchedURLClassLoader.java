package com.seewo.psd.bootx.loader;

import org.springframework.boot.loader.LaunchedURLClassLoader;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class CachedLaunchedURLClassLoader extends LaunchedURLClassLoader {
	static {
		ClassLoader.registerAsParallelCapable();
	}

	private final Map<String, LoadClassResult> classCache = new ConcurrentHashMap<>(3000);
	private final Map<String, Optional<URL>>              resourceUrlCache  = new ConcurrentHashMap<>(
			3000);
	private final Map<String, Optional<Enumeration<URL>>> resourcesUrlCache = new ConcurrentHashMap<>(
			300);
	public CachedLaunchedURLClassLoader(URL[] urls, ClassLoader parent) {
		super(urls, parent);
	}

	@Override
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		return loadClassWithCache(name, resolve);
	}

	@Override
	public URL findResource(String name) {
		Optional<URL> urlOptional = resourceUrlCache.get(name);
		if (urlOptional != null) {
			return urlOptional.orElse(null);
		}
		URL url = super.findResource(name);
		resourceUrlCache.put(name, url != null ? Optional.of(url) : Optional.empty());
		return url;
	}

	@Override
	public Enumeration<URL> findResources(String name) throws IOException {
		Optional<Enumeration<URL>> urlOptional = resourcesUrlCache.get(name);
		if (urlOptional != null) {
			return urlOptional.orElse(null);
		}
		Enumeration<URL> enumeration = super.findResources(name);
		if (enumeration == null || !enumeration.hasMoreElements()) {
			resourcesUrlCache.put(name, Optional.empty());
		}
		return enumeration;
	}

	protected static class LoadClassResult {
		private Class<?> clazz;
		private ClassNotFoundException ex;
		protected static LoadClassResult NOT_FOUND = new LoadClassResult();

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
		LoadClassResult result = classCache.get(name);
		if (result != null) {
			if (result.getEx() != null) {
				throw result.getEx();
			}
			return result.getClazz();
		}

		try {
			Class<?> clazz = super.findLoadedClass(name);
			if (clazz == null) {
				clazz = super.loadClass(name, resolve);
			}
			if (clazz == null) {
				classCache.put(name, LoadClassResult.NOT_FOUND);
			}
			return clazz;
		} catch (ClassNotFoundException exception) {
			classCache.put(name, new LoadClassResult(exception));
			throw exception;
		}
	}
}
