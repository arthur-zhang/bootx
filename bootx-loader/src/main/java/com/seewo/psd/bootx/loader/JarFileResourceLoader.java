package com.seewo.psd.bootx.loader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

final class JarFileResourceLoader implements AutoCloseable {
    private final JarFile jarFile;
    private final URL rootUrl;

    private final Map<CodeSigners, CodeSource> codeSources = new HashMap<>();

    JarFileResourceLoader(final URL url) {
        JarFile jarFile = getJarFileFromUrl(url);
        if (jarFile == null) throw new RuntimeException("jar file is null for url: " + url);
        this.jarFile = jarFile;
        this.rootUrl = url;
    }

    public URL getResource(final String fileName) {
        URL url;
        try {
            url = new URL(rootUrl, fileName);
            url.openConnection().connect();
            return url;
        } catch (Exception e) {
        }
        return null;
    }
    public synchronized ClassSpec getClassSpec(final String fileName) throws IOException {
        final ClassSpec spec = new ClassSpec();
        final JarEntry entry = getJarEntry(fileName);
        if (entry == null) {
            // no such entry
            return null;
        }
        final long size = entry.getSize();
        try (final InputStream is = jarFile.getInputStream(entry)) {
            if (size == -1) {
                // size unknown
                final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                final byte[] buf = new byte[16384];
                int res;
                while ((res = is.read(buf)) > 0) {
                    baos.write(buf, 0, res);
                }
                // done
                CodeSource codeSource = createCodeSource(entry);
                baos.close();
                is.close();
                spec.setBytes(baos.toByteArray());
                spec.setCodeSource(codeSource);
                return spec;
            } else if (size <= (long) Integer.MAX_VALUE) {
                final int castSize = (int) size;
                byte[] bytes = new byte[castSize];
                int a = 0, res;
                while ((res = is.read(bytes, a, castSize - a)) > 0) {
                    a += res;
                }
                // consume remainder so that cert check doesn't fail in case of wonky JARs
                while (is.read() != -1) {
                    //
                }
                // done
                CodeSource codeSource = createCodeSource(entry);
                is.close();
                spec.setBytes(bytes);
                spec.setCodeSource(codeSource);
                return spec;
            } else {
                throw new IOException("Resource is too large to be a valid class file");
            }
        }
    }

    private CodeSource createCodeSource(final JarEntry entry) {
        final CodeSigner[] entryCodeSigners = entry.getCodeSigners();
        final CodeSigners codeSigners = entryCodeSigners == null || entryCodeSigners.length == 0 ? EMPTY_CODE_SIGNERS : new CodeSigners(entryCodeSigners);
        CodeSource codeSource = codeSources.get(codeSigners);
        if (codeSource == null) {
            codeSources.put(codeSigners, codeSource = new CodeSource(rootUrl, entryCodeSigners));
        }
        return codeSource;
    }

    private JarEntry getJarEntry(final String fileName) {
        return jarFile.getJarEntry(fileName);
    }

    private JarFile getJarFileFromUrl(URL url) {
        try {
            URLConnection urlConnection = url.openConnection();
            return ((JarURLConnection) urlConnection).getJarFile();
        } catch (IOException e) {
        }
        return null;
    }

    @Override
    public void close() {
        try {
            jarFile.close();
        } catch (IOException e) {
            // ignored
        }
    }


    private static final CodeSigners EMPTY_CODE_SIGNERS = new CodeSigners(new CodeSigner[0]);

    static final class CodeSigners {

        private final CodeSigner[] codeSigners;
        private final int hashCode;

        CodeSigners(final CodeSigner[] codeSigners) {
            this.codeSigners = codeSigners;
            hashCode = Arrays.hashCode(codeSigners);
        }

        public boolean equals(final Object obj) {
            return obj instanceof CodeSigners && equals((CodeSigners) obj);
        }

        private boolean equals(final CodeSigners other) {
            return Arrays.equals(codeSigners, other.codeSigners);
        }

        public int hashCode() {
            return hashCode;
        }
    }
}
