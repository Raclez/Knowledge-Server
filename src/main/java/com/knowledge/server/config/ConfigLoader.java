package com.knowledge.server.config;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.yaml.snakeyaml.Yaml;

public class ConfigLoader {

    public static KnowledgeServerProperties load() {
        KnowledgeServerProperties props = new KnowledgeServerProperties();

        String configPath = System.getProperty("config.path", "src/main/resources/application.yml");
        Path path = Path.of(configPath);

        if (!Files.exists(path)) {
            configPath = "src/main/resources/mcp-server.yml";
            path = Path.of(configPath);
        }

        if (!Files.exists(path)) {
            System.out.println("No config file found, using defaults");
            return props;
        }

        try (InputStream is = Files.newInputStream(path)) {
            Yaml yaml = new Yaml();
            Object data = yaml.load(is);

            if (data instanceof java.util.Map) {
                java.util.Map<String, Object> map = (java.util.Map<String, Object>) data;

                if (map.containsKey("knowledge-server")) {
                    java.util.Map<String, Object> ks = (java.util.Map<String, Object>) map.get("knowledge-server");

                    if (ks.containsKey("index")) {
                        java.util.Map<String, Object> index = (java.util.Map<String, Object>) ks.get("index");
                        KnowledgeServerProperties.IndexConfig ic = props.getIndex();
                        if (index.containsKey("path")) ic.setPath((String) index.get("path"));
                        if (index.containsKey("watch-paths")) {
                            List<String> wp = (List<String>) index.get("watch-paths");
                            ic.setWatchPaths(wp != null ? wp : new ArrayList<>());
                        }
                        if (index.containsKey("supported-extensions")) {
                            List<String> se = (List<String>) index.get("supported-extensions");
                            ic.setSupportedExtensions(se != null ? se : ic.getSupportedExtensions());
                        }
                        if (index.containsKey("max-file-size")) ic.setMaxFileSize(toLong(index.get("max-file-size")));
                        if (index.containsKey("batch-size")) ic.setBatchSize(toInt(index.get("batch-size")));
                    }

                    if (ks.containsKey("watcher")) {
                        java.util.Map<String, Object> watcher = (java.util.Map<String, Object>) ks.get("watcher");
                        KnowledgeServerProperties.WatcherConfig wc = props.getWatcher();
                        if (watcher.containsKey("debounce-ms")) wc.setDebounceMs(toLong(watcher.get("debounce-ms")));
                        if (watcher.containsKey("recursive")) wc.setRecursive(toBool(watcher.get("recursive")));
                    }

                    if (ks.containsKey("search")) {
                        java.util.Map<String, Object> search = (java.util.Map<String, Object>) ks.get("search");
                        KnowledgeServerProperties.SearchConfig sc = props.getSearch();
                        if (search.containsKey("max-results")) sc.setMaxResults(toInt(search.get("max-results")));
                        if (search.containsKey("highlight-fragments")) sc.setHighlightFragments(toInt(search.get("highlight-fragments")));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load config: " + e.getMessage());
        }

        return props;
    }

    private static long toLong(Object val) {
        if (val instanceof Number) return ((Number) val).longValue();
        return 0;
    }

    private static int toInt(Object val) {
        if (val instanceof Number) return ((Number) val).intValue();
        return 0;
    }

    private static boolean toBool(Object val) {
        if (val instanceof Boolean) return (Boolean) val;
        return true;
    }
}
