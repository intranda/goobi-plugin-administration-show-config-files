package de.intranda.goobi.plugins;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IAdministrationPlugin;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.S3FileUtils;
import de.sub.goobi.helper.StorageProvider;
import lombok.Getter;
import lombok.extern.log4j.Log4j;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

@PluginImplementation
@Log4j
public class ShowConfigFilesPlugin implements IAdministrationPlugin {

    private static final long serialVersionUID = -3041469758104032050L;
    private static final String TITLE = "intranda_administration_showconfigfiles";
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
        return "/uii/administration_configfiles.xhtml";
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
            try (InputStreamReader r = new InputStreamReader(StorageProvider.getInstance().newInputStream(p), StandardCharsets.UTF_8);
                    Stream<String> lineStream = new BufferedReader(r)
                            .lines()) {
                contents.put(file, lineStream.collect(Collectors.joining("\n")));
            } catch (IOException e) {
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
        try (S3AsyncClient s3 = S3FileUtils.createS3Client()) {
            Path dest = Paths.get(ConfigurationHelper.getInstance().getConfigurationFolder());
            String bucket = xmlConf.getString("/s3Update/bucket");
            String folderPrefix = xmlConf.getString("/s3Update/prefix");

            String nextContinuationToken = null;
            do {
                ListObjectsV2Request.Builder requestBuilder = ListObjectsV2Request.builder()
                        .bucket(bucket)
                        .delimiter("/")
                        .prefix(folderPrefix)
                        .continuationToken(nextContinuationToken);

                CompletableFuture<ListObjectsV2Response> response = s3.listObjectsV2(requestBuilder.build());
                ListObjectsV2Response resp = response.toCompletableFuture().join();

                nextContinuationToken = resp.nextContinuationToken();

                List<S3Object> files = resp.contents();
                for (S3Object object : files) {
                    downloadObject(s3, dest, object, bucket);
                }

            } while (nextContinuationToken != null);

            this.contents = new HashMap<>();
            this.loadConfig(this.currentConfig);
        } catch (URISyntaxException e) {
            log.error(e);
        }
    }

    public void downloadObject(S3AsyncClient s3, Path dest, S3Object os, String bucket) {
        String filename = os.key();
        int idx = filename.lastIndexOf('/');
        if (idx > 0) {
            filename = (filename.substring(idx + 1));
        }
        CompletableFuture<ResponseInputStream<GetObjectResponse>> responseInputStream = s3.getObject(GetObjectRequest.builder()
                .bucket(bucket)
                .key(os.key())
                .build(),
                AsyncResponseTransformer.toBlockingInputStream());
        try (InputStream in = responseInputStream.toCompletableFuture().join()) {
            Files.copy(in, dest.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error(e);
        }
    }

}
