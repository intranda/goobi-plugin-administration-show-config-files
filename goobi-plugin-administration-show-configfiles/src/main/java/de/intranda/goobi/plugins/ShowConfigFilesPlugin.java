package de.intranda.goobi.plugins;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IAdministrationPlugin;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.S3FileUtils;
import de.sub.goobi.helper.StorageProvider;
import lombok.Getter;
import lombok.extern.log4j.Log4j;
import net.xeoh.plugins.base.annotations.PluginImplementation;

@PluginImplementation
@Log4j
public class ShowConfigFilesPlugin implements IAdministrationPlugin {
    private final static String TITLE = "intranda_administration_showconfigfiles";
    private List<String> configFiles = new ArrayList<>();
    private Map<String, String> contents = new HashMap<>();
    private XMLConfiguration xmlConf;
    @Getter
    private String currentConfig;
    @Getter
    private String currentConfigContent = " - select a file from the list on the left - ";
    @Getter
    private String configClass;

    public ShowConfigFilesPlugin() {
        xmlConf = ConfigPlugins.getPluginConfig(TITLE);
        xmlConf.setExpressionEngine(new XPathExpressionEngine());
    }

    @Override
    public String getTitle() {
        return TITLE;
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
        this.currentConfig = file;
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
        this.currentConfigContent = contents.get(file);
    }

    public boolean isRenderUpdateButton() {
        return xmlConf.getBoolean("/s3Update/enabled", false);
    }

    public void downloadConfigFromS3() {
        AmazonS3 s3 = S3FileUtils.createS3Client();
        Path dest = Paths.get(ConfigurationHelper.getInstance().getConfigurationFolder());
        String bucket = xmlConf.getString("/s3Update/bucket");
        String folderPrefix = xmlConf.getString("/s3Update/prefix");
        ListObjectsRequest req = new ListObjectsRequest().withBucketName(bucket).withPrefix(folderPrefix);
        ObjectListing listing = s3.listObjects(req);
        for (S3ObjectSummary os : listing.getObjectSummaries()) {
            downloadObject(s3, dest, os);
        }
        while (listing.isTruncated()) {
            listing = s3.listNextBatchOfObjects(listing);
            for (S3ObjectSummary os : listing.getObjectSummaries()) {
                downloadObject(s3, dest, os);
            }
        }
        this.contents = new HashMap<>();
        this.loadConfig(this.currentConfig);
    }

    public void downloadObject(AmazonS3 s3, Path dest, S3ObjectSummary os) {
        String filename = os.getKey();
        int idx = filename.lastIndexOf('/');
        if (idx > 0) {
            filename = (filename.substring(idx + 1));
        }
        try (InputStream in = s3.getObject(os.getBucketName(), os.getKey()).getObjectContent()) {
            Files.copy(in, dest.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error(e);
        }
    }

}
