package com.seewo.psd.bootx.loader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class IndexParser {
    public static final String JAR_INDEX_VERSION = "1.0";

    public static final String JAR_INDEX_VERSION_KEY = "JarIndex-Version: ";

    public static Map<String, Set<String>> indexListParser(File indexFile) throws IOException {

        System.out.println("parseindex" + indexFile.getAbsolutePath());
        if (!indexFile.exists()) {
            return null;
        }
        LinkedHashMap<String, Set<String>> prefixes = new LinkedHashMap<>();
        // Parse INDEX.LIST if it exists
        try (BufferedReader br = new BufferedReader(new FileReader(indexFile))) {
            // Must start with version info
            String line = br.readLine();
            if (!line.startsWith(JAR_INDEX_VERSION_KEY))
                return prefixes;

            String versionNumber = line.substring(JAR_INDEX_VERSION_KEY.length());
            if (!JAR_INDEX_VERSION.equals(versionNumber)) {
                return null;
            }

            // Blank line must be next
            line = br.readLine();
            if (!"".equals(line)) {
                return prefixes;
            }

            // May contain sections.
            while ((line = br.readLine()) != null) {
                String jarName = line;

                Set<String> values = new HashSet<String>();

                // Read the names in the section.
                while ((line = br.readLine()) != null) {
                    // Stop at section boundary.
                    if ("".equals(line))
                        break;
                    values.add(line.trim());
                }
                prefixes.put(jarName, values);
                // Might have seen an early EOF.
                if (line == null)
                    break;
            }
        }
        return prefixes;
    }
}
