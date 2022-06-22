package com.seewo.psd.bootx.loader;

import java.nio.ByteBuffer;
import java.security.CodeSource;

/**
 * A class definition specification.
 */
public final class ClassSpec {
    private byte[] bytes;
    private ByteBuffer byteBuffer;
    private CodeSource codeSource;

    /**
     * Construct a new instance.
     */
    public ClassSpec() {
    }

    /**
     * Get the class file bytes, if they are set.
     *
     * @return the class file bytes, if they are set; {@code null} otherwise
     */
    public byte[] getBytes() {
        return bytes;
    }

    /**
     * Set the class file bytes.  Calling this method will clear any previously set {@code ByteBuffer}.
     *
     * @param bytes the class file bytes
     * @return this class specification
     */
    public ClassSpec setBytes(final byte[] bytes) {
        this.bytes = bytes;
        byteBuffer = null;
        return this;
    }

    public void setBytes$$bridge(final byte[] bytes) {
        setBytes(bytes);
    }

    /**
     * Get the class byte buffer, if one is set.
     *
     * @return the class byte buffer, if one is set; {@code null} otherwise
     */
    public ByteBuffer getByteBuffer() {
        return byteBuffer;
    }

    /**
     * Set the class byte buffer.  Calling this method will clear any previously set class {@code byte[]}.
     *
     * @param byteBuffer the class byte buffer
     * @return this class specification
     */
    public ClassSpec setByteBuffer(final ByteBuffer byteBuffer) {
        this.byteBuffer = byteBuffer;
        bytes = null;
        return this;
    }

    /**
     * Get the code source (should not be {@code null}).
     *
     * @return the code source
     */
    public CodeSource getCodeSource() {
        return codeSource;
    }

    /**
     * Set the code source (should not be {@code null}).
     *
     * @param codeSource the code source
     * @return this class specification
     */
    public ClassSpec setCodeSource(final CodeSource codeSource) {
        this.codeSource = codeSource;
        return this;
    }
}