import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Properties;

/**
 * Created by bellini on 27/03/17.
 */
public class Config {
    private static Logger logger = LoggerFactory.getLogger(Config.class);

    private Properties prop;

    private String model;
    private String itemModel;
    private String recsDir;
    private String recFile;

    private Boolean computeWeights;
    private Boolean computeRecommendations;
    private Integer numRecsPerUser;
    private Boolean mergeRecommendations;
    private Integer recStrategy;

    private Boolean normalizeFeatures;
    private Boolean normalizeRates;
    private Integer normalizeRateMin;
    private Integer normalizeRateMax;

    private String endpoint;
    private Integer retryFetchResource;
    private Boolean memory;
    private Integer userGC;

    private String dbpediaMappingFile;
    private String propsFile;
    private String trainFile;
    private Boolean coldUsers;
    private String coldUsersFile;

    private Integer epochs;
    private Double learningRate;

    private Boolean autoStop;
    private Integer autoStopWindow;

    private Integer weightStrategy;
    private Double weightK;
    

    Config(String filename) {
        this.prop = new Properties();

        try {
            InputStream inputStream = new FileInputStream(new File(filename));
            prop.load(inputStream);

            model = prop.getProperty("model");
            itemModel = prop.getProperty("itemModel");
            recsDir = prop.getProperty("recsDir");
            recFile = prop.getProperty("recFile");

            computeWeights = Boolean.parseBoolean(prop.getProperty("computeWeights"));
            computeRecommendations = Boolean.parseBoolean(prop.getProperty("computeRecommendations"));
            numRecsPerUser = Integer.parseInt(prop.getProperty("numRecsPerUser"));
            mergeRecommendations = Boolean.parseBoolean(prop.getProperty("mergeRecommendations"));
            recStrategy = Integer.parseInt(prop.getProperty("recStrategy"));

            normalizeFeatures = Boolean.parseBoolean(prop.getProperty("normalizeFeatures"));
            normalizeRates = Boolean.parseBoolean(prop.getProperty("normalizeRates"));
            normalizeRateMin = Integer.parseInt(prop.getProperty("normalizeRateMin"));
            normalizeRateMax = Integer.parseInt(prop.getProperty("normalizeRateMax"));

            endpoint = prop.getProperty("endpoint");
            retryFetchResource = Integer.parseInt(prop.getProperty("retryFetchResource"));
            memory = Boolean.parseBoolean(prop.getProperty("memory"));
            userGC = Integer.parseInt(prop.getProperty("userGC"));

            // Train
            dbpediaMappingFile = prop.getProperty("mapping");
            propsFile = prop.getProperty("props");
            trainFile = prop.getProperty("train");
            coldUsers = Boolean.parseBoolean(prop.getProperty("cold_users"));
            coldUsersFile = prop.getProperty("cold_users_file");

            epochs = Integer.parseInt(prop.getProperty("epochs"));
            learningRate = Double.parseDouble(prop.getProperty("learning_rate"));

            autoStop = Boolean.parseBoolean(prop.getProperty("auto_stop"));
            autoStopWindow = Integer.parseInt(prop.getProperty("auto_stop_window"));

            weightStrategy = Integer.parseInt(prop.getProperty("weight_strategy"));
            weightK = Double.parseDouble(prop.getProperty("weight_k"));
            
            logger.info("--- CONFIG ---");
            logger.info("model={} item_model={} recsDir={} recsFile={} computeWeights={} computeRecommendations={} numRecsPerUser={} mergeRecommendations={} recStrategy={}", model, itemModel, recsDir, recFile, computeWeights, computeRecommendations, numRecsPerUser, mergeRecommendations, recStrategy);
            logger.info("normalizeFeatures={} normalizeRates={} normalizeRateMin={} normalizeRateMax={}", normalizeFeatures, normalizeRates, normalizeRateMin, normalizeRateMax);
            logger.info("endpoint={} retryFetchResource={} memory={} userGC={}", endpoint, retryFetchResource, memory, userGC);
            logger.info("mapping={} props={} train={} coldUsers={} coldUsersFile={}", dbpediaMappingFile, propsFile, trainFile, coldUsers, coldUsersFile);
            logger.info("epochs={} learningRate={} weightStrategy={} weightK={}", epochs, learningRate, weightStrategy, weightK);
            logger.info("autostop={} autostopWindow={}", autoStop, autoStopWindow);
            
            logger.info("---");
        } catch(FileNotFoundException e) {
            logger.error("File not found: {}", filename);
        } catch(IOException e) {
            logger.error("IO Exception in loading properties");
        }
    }

    public String getModel() { return model; }

    public String getItemModel() { return itemModel; }

    public String getRecsDir() { return recsDir; }

    public String getRecFile() { return recFile; }

    public Boolean getComputeWeights() { return computeWeights; }

    public Boolean getComputeRecommendations() { return computeRecommendations; }

    public Boolean getMergeRecommendations() { return mergeRecommendations; }

    public Integer getNumRecsPerUser() { return numRecsPerUser; }

    public Integer getRecStrategy() { return recStrategy; }

    public Boolean getNormalizeFeatures() { return normalizeFeatures; }

    public Boolean getNormalizeRates() { return normalizeRates; }

    public Integer getNormalizeRateMin() { return normalizeRateMin; }

    public Integer getNormalizeRateMax() { return normalizeRateMax; }

    public String getEndpoint() { return endpoint; }

    public Integer getRetryFetchResource() { return retryFetchResource; }

    public Boolean getMemory() { return memory; }

    public Integer getUserGC() { return userGC; }

    public String getDbpediaMappingFile() {
        return dbpediaMappingFile;
    }

    public String getPropsFile() {
        return propsFile;
    }

    public String getTrainFile() {
        return trainFile;
    }

    public Boolean getColdUsers() {
        return coldUsers;
    }

    public String getColdUsersFile() {
        return coldUsersFile;
    }

    public Integer getEpochs() { return epochs; }

    public Double getLearningRate() {
        return learningRate;
    }

    public Boolean getAutoStop() { return autoStop; }

    public Integer getAutoStopWindow() { return autoStopWindow; }

    public Integer getWeightStrategy() { return weightStrategy; }

    public Double getWeightK() { return weightK; }

}