package de.intranda.goobi.plugins;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IAdministrationPlugin;

import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.StorageProvider;
import lombok.Getter;
import lombok.extern.log4j.Log4j;
import net.xeoh.plugins.base.annotations.PluginImplementation;

@PluginImplementation
@Log4j
public class ShowConfigFilesPlugin implements IAdministrationPlugin {
    private List<String> configFiles = new ArrayList<>();
    private Map<String, String> contents = new HashMap<>();
    @Getter
    private String currentConfig;
    @Getter
    private String configClass;

    @Override
    public String getTitle() {
        return "intranda_administration_showconfigfiles";
    }

    @Override
    public PluginType getType() {
        return PluginType.Administration;
    }

    @Override
    public String getGui() {
        return "administration_configfiles.xhtml";
    }

    public List<String> getConfigFiles() {
        if (this.configFiles.isEmpty()) {
            ConfigurationHelper config = ConfigurationHelper.getInstance();
            this.configFiles.addAll(StorageProvider.getInstance().list(config.getConfigurationFolder()));
        }
        return this.configFiles;
    }

    public void loadConfig(String file) {
        if (!contents.containsKey(file)) {
            ConfigurationHelper config = ConfigurationHelper.getInstance();
            Path p = Paths.get(config.getConfigurationFolder(), file);
            try (Stream<String> lineStream = new BufferedReader(new InputStreamReader(StorageProvider.getInstance().newInputStream(p), "utf-8"))
                    .lines()) {
                contents.put(file, lineStream.collect(Collectors.joining("\n")));
            } catch (IOException e) {
                // TODO Auto-generated catch block
                log.error(e);
            }
        }
        int lastIdx = file.lastIndexOf('.');
        if (lastIdx >= 0) {
            this.configClass = file.substring(lastIdx + 1);
        } else {
            this.configClass = "";
        }
        this.currentConfig = contents.get(file);
    }

}
