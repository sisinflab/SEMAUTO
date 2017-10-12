
import me.tongfei.progressbar.ProgressBar;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.jena.atlas.io.IndentedWriter;
import org.apache.jena.ext.com.google.common.util.concurrent.AtomicDouble;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.sparql.algebra.op.OpBGP;
import org.apache.jena.sparql.algebra.op.OpFilter;
import org.apache.jena.sparql.core.BasicPattern;
import org.apache.jena.sparql.expr.E_IsIRI;
import org.apache.jena.sparql.expr.E_NotExists;
import org.apache.jena.sparql.expr.ExprAggregator;
import org.apache.jena.sparql.expr.ExprVar;
import org.apache.jena.sparql.expr.aggregate.AggCountVar;
import org.apache.jena.sparql.syntax.ElementFilter;
import org.apache.jena.sparql.syntax.ElementGroup;
import org.apache.jena.vocabulary.RDF;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.Math.*;

/**
 * Created by bellini on 10/03/17.
 */
public class App {
    private static Logger logger = LoggerFactory.getLogger(App.class);

    private static final String REMOTE_ENDPOINT = "https://dbpedia.org/sparql";

    private static int MAX_DEPTH = 0;
    private static int OUTPUT_LAYER = 0;

    // Recs
    private static ConcurrentHashMap<Integer, List<Rate>> recs = new ConcurrentHashMap<>();

    // Maps rates by user
    private static HashMap<Integer, HashSet<Rate>> ratesMap = new HashMap<>();

    // List of users
    private static List<Integer> users = new ArrayList<>();
    private static List<Integer> remainingUsers = new ArrayList<>();
    private static List<Integer> completedUsers = new ArrayList<>();
    private static Set<Integer> coldUsers = new HashSet();

    // List of items
    private static HashSet<Integer> items = new HashSet<>();

    // Props Chain
    private static List<String> propsChain = new ArrayList<>();

    private HashSet<Rate> userRates = new HashSet<>();

    private HashMap<String, Double> resourcesRated = new HashMap<>();

    // DBpedia Map (id, uri)
    private static ConcurrentHashMap<Integer, String> dbpediaMap = new ConcurrentHashMap<>();
    private static ConcurrentHashMap<String, Integer> dbpediaToId = new ConcurrentHashMap<>();

    private static ConcurrentHashMap<String, Integer> resourceNotFound = new ConcurrentHashMap<>();

    private static Boolean preFetchedResources = false;

    private Long resourcesToMap = 0L;

    // Global prefetched graph
    private static ConcurrentHashMap<String, Node> globalNodes = new ConcurrentHashMap<>();
    private static UnitNodeFactory nodePreFactory = new UnitNodeFactory();

    private Layer[] layers;

    Function<Double, Double> activation = t -> Activation.sigmoid(t);
    Function<Double, Double> deactivation = t -> Activation.dsigmoid(t);

    private static Config config;

    static {
        config = new Config("config.properties");

        loadProperties(config.getPropsFile());

        loadDBpediaMappings(config.getDbpediaMappingFile());

        logger.info("Loading ratings...");
        loadRatings(config.getTrainFile(), false);
    }

    public static void main(String[] args) {

        if(config.getColdUsers()) {
            coldUsers = loadColdUsers(config.getColdUsersFile());
        }

        // Compute weights
        if(config.getComputeWeights()) {
            computeWeights();
        }

        // Compute recommendations for all unrated items set
        if(config.getComputeRecommendations()) {
            computeRecommendations();
        }

        // Merge recommendations
        if(config.getMergeRecommendations()) {
            loadRecommendations();
            mergeRecommendations();
        }

        Boolean generateItemModel = !config.getComputeWeights() && !config.getComputeRecommendations() && !config.getMergeRecommendations();
        if(generateItemModel) {
            logger.info("Generating item model mode on");
            fetchItemModel();
        }
    }

    public static void computeWeights() {
        // Check for completed users
        ArrayList<Integer> completedUID = new ArrayList<>();

        File folder = new File(config.getModel());
        File[] listOfFiles = folder.listFiles();
        for(int i=0; i<listOfFiles.length; i++) {
            String filename = listOfFiles[i].getName();
            Integer uid = Integer.parseInt(filename.substring(0, filename.lastIndexOf(".")));
            completedUID.add(uid);
        }

        completedUsers = new ArrayList(completedUID);

        if(config.getColdUsers()) {
            remainingUsers = new ArrayList<>(coldUsers);
        } else {
            remainingUsers = new ArrayList<>(users);
        }
        remainingUsers.removeAll(completedUID);


        List<Integer> users = null;

        if(config.getColdUsers()) {
            users = new ArrayList(coldUsers);
        } else {
            users = new ArrayList(App.users);
        }

        if(completedUID.size() > 0) {
            int startingUsers = users.size();

            users.removeAll(completedUID);

            int remainingUsers = users.size();
            int completedUsers = startingUsers-remainingUsers;

            if(remainingUsers > 0) {
                logger.info("Completed {} users - Remaining {} users ", completedUsers, remainingUsers);
            } else {
                logger.info("Nothing left to do (Total users: {})", completedUID.size());
            }
        }

        // Pre fetch all resources
        if(config.getMemory()) {
            prefetch();
        }

        final ProgressBar pb = new ProgressBar("Users", users.size());
        pb.start();

        AtomicInteger counter = new AtomicInteger(1);
        users.parallelStream().forEach(uid -> {

            try {
                HashSet<Rate> userRates = ratesMap.get(uid);
                App san = new App(userRates);
                san.run(uid);
                san.writeResults(uid);
            } catch (Exception e) {
                logger.error("UID: {} - Exception {}", uid, e);
            }

            if (counter.getAndIncrement() % config.getUserGC() == 0) {
                Runtime runtime = Runtime.getRuntime();
                runtime.gc();
            }

            pb.step();
        });

        pb.stop();

    }

    public static void computeRecommendations() {
        if(!config.getMemory()) {
            logger.warn("Mode memory=false is not recommended in computing recommendations.");
        }

        // Check for completed users
        ArrayList<Integer> completedUID = new ArrayList<>();

        File folder = new File(config.getRecsDir());
        File[] listOfFiles = folder.listFiles();
        for(int i=0; i<listOfFiles.length; i++) {
            String filename = listOfFiles[i].getName();
            Integer uid = Integer.parseInt(filename.substring(0, filename.lastIndexOf(".")));
            completedUID.add(uid);
        }

        completedUsers = new ArrayList(completedUID);

        remainingUsers = new ArrayList<>(users);
        remainingUsers.removeAll(completedUID);

        List<Integer> users = new ArrayList(App.users);

        if(completedUID.size() > 0) {
            int startingUsers = users.size();

            users.removeAll(completedUID);

            int remainingUsers = users.size();
            int completedUsers = startingUsers-remainingUsers;

            if(remainingUsers > 0) {
                logger.info("Completed {} users - Remaining {} users ", completedUsers, remainingUsers);
            } else {
                logger.info("Nothing left to do (Total users: {})", completedUID.size());
            }

            completedUID.parallelStream().forEach(uid -> {
                List<Rate> userRecs = new ArrayList<>();
                final String rec = String.format("%s/%d.tsv", config.getRecsDir(), uid);
                Reader reader;
                Iterable<CSVRecord> records;
                try {
                    reader = new FileReader(rec);
                    records = CSVFormat.TDF.parse(reader);
                    for (CSVRecord record : records) {
                        Integer item = Integer.parseInt(record.get(1));
                        Double rate = Double.parseDouble(record.get(2));
                        Rate r = new Rate(item, rate);
                        userRecs.add(r);
                    }
                    reader.close();

                    if(userRecs.size() == 0) {
                        logger.warn("UID: {} - Zero recs loaded!", uid);
                    }

                    recs.put(uid, userRecs);
                } catch (FileNotFoundException e) {
                    logger.error("UID: {} - File not found", uid);
                } catch (IOException e) {
                    logger.error("UID: {} - IO Exception", uid);
                }

            });
        }

        // Pre fetch all resources
        if(config.getMemory()) {
            prefetch();
        }

        logger.info("Computing recommendations...");
        final ProgressBar pb = new ProgressBar("Users", users.size());
        pb.start();

        AtomicInteger counter = new AtomicInteger(1);
        users.parallelStream().forEach(uid -> {

            try {
                HashSet<Rate> userRates = ratesMap.get(uid);
                App san = new App(userRates);
                List<Rate> userRecs = san.recs(uid);
                san.writeRecs(uid, userRecs);
                recs.put(uid, userRecs);
            } catch (Exception e) {
                logger.error("UID: {} - Exception {}", uid, e);
            }

            if (counter.getAndIncrement() % config.getUserGC() == 0) {
                Runtime runtime = Runtime.getRuntime();
                runtime.gc();
            }

            pb.step();
        });

        pb.stop();
    }

    public static void mergeRecommendations() {
        // Merge
        logger.info("Merging recommendations...");

        final String tsvFile = config.getRecFile();

        final ProgressBar pb = new ProgressBar("Merging", App.users.size());
        pb.start();

        FileWriter fileWriter;
        CSVPrinter csvFilePrinter;
        CSVFormat csvFileFormat = CSVFormat.TDF.withRecordSeparator("\n");

        try {
            fileWriter = new FileWriter(tsvFile);
            csvFilePrinter = new CSVPrinter(fileWriter, csvFileFormat);

            for(Integer uid : App.users) {
                if(App.recs.containsKey(uid)) {
                    List<Rate> predictions = App.recs.get(uid);

                    for (Rate rate : predictions) {
                        List record = new ArrayList();
                        record.add(uid);
                        record.add(rate.getItemId());
                        record.add(rate.getRate());

                        try {
                            csvFilePrinter.printRecord(record);
                        } catch (IOException e) {
                            logger.error("UID: {} - Unable to write recs", uid);
                        }

                    }

                } else {
                    logger.error("UID: {} - Recs not found", uid);
                }

                pb.step();
            }

            fileWriter.close();
        } catch (IOException e) {
            logger.error("Unable to write file: {}", tsvFile);
        }

        pb.stop();

        logger.info("Completed: {}", tsvFile);
    }

    private static void loadRecommendations() {
        App.users.parallelStream().forEach(uid -> {
            List<Rate> userRecs = new ArrayList<>();
            final String rec = String.format("%s/%d.tsv", config.getRecsDir(), uid);
            Reader reader;
            Iterable<CSVRecord> records;
            try {
                reader = new FileReader(rec);
                records = CSVFormat.TDF.parse(reader);
                for (CSVRecord record : records) {
                    Integer item = Integer.parseInt(record.get(1));
                    Double rate = Double.parseDouble(record.get(2));
                    Rate r = new Rate(item, rate);
                    userRecs.add(r);
                }
                reader.close();

                if(userRecs.size() == 0) {
                    logger.warn("UID: {} - Zero recs loaded!", uid);
                }

                recs.put(uid, userRecs);
            } catch (FileNotFoundException e) {
                logger.error("UID: {} - File not found", uid);
            } catch (IOException e) {
                logger.error("UID: {} - IO Exception", uid);
            }
        });
    }

    App(HashSet<Rate> rates) {
        userRates = rates;

        userRates.parallelStream().forEach(rate -> {
            String URI = dbpediaMap.get(rate.getItemId());
            resourcesRated.put(URI, rate.getRate());
        });

        layers = new Layer[MAX_DEPTH+2];

        layers[0] = new Layer("input");
        for(int i=0; i<MAX_DEPTH; i++) {
            layers[i+1] = new Layer("hidden_"+i);
        }

        layers[MAX_DEPTH+1] = new Layer("output");
    }

    private static void fetchItemModel() {
        if(!preFetchedResources) {
            final Date startDate = new Date();

            ArrayList<Integer> completedItemList = new ArrayList<>();

            File folder = new File(config.getItemModel());

            File[] listOfFiles = folder.listFiles();
            for(int i=0; i<listOfFiles.length; i++) {
                String filename = listOfFiles[i].getName();
                Integer itemID = Integer.parseInt(filename);
                completedItemList.add(itemID);
            }

            ConcurrentHashMap<Integer, String> dbpediaMapRatedItems = new ConcurrentHashMap<>();
            ratesMap.entrySet().parallelStream().forEach(entry -> {
                HashSet<Rate> rates = entry.getValue();
                rates.forEach(rate -> {
                    Integer itemId = rate.getItemId();
                    if(dbpediaMap.containsKey(itemId)) {
                        dbpediaMapRatedItems.put(itemId, dbpediaMap.get(itemId));
                    }
                });
            });

            HashMap<Integer, String> dbpediaMapRemaining = new HashMap<>(dbpediaMapRatedItems);
            dbpediaMapRemaining.keySet().removeAll(completedItemList);

            logger.info("Remaining items: " + dbpediaMapRemaining.keySet().size());

            ProgressBar pb = new ProgressBar("Graph", dbpediaMapRemaining.values().size());
            pb.start();

            dbpediaMapRemaining.values().parallelStream().forEach(itemURI -> {
                Node n = nodePreFactory.getNode(itemURI);
                globalNodes.putIfAbsent(itemURI, n);

                // Fetching resources
                if (propsChain.size() > 0) {

                    propsChain.parallelStream().forEach(property -> {
                        String chain[] = property.split(" ");
                        final int depth = chain.length;

                        for (int i = 0; i < depth; i++) {
                            graphFetcherExecutor(nodePreFactory, n, itemURI, i + 1, chain);
                        }

                    });
                }

                pb.step();
            });

            pb.stop();

            final Date endDate = new Date();
            final String time = executionTime(startDate, endDate);
            logger.info("Graph fetched [Time: {}]", time);

            preFetchedResources = true;

             // Save files
            dbpediaMapRemaining.entrySet().parallelStream().forEach(entry -> {
                Integer itemId = entry.getKey();
                String URI = entry.getValue();

                if(globalNodes.containsKey(URI)) {
                    Node itemNode = globalNodes.get(URI);
                    List<String> resources = itemNode.getEdges().stream().map(edge -> edge.getNode()).map(node -> node.getURI()).collect(Collectors.toList());

                    if(resources.size() > 0) {
                        String path = String.format("%s/%d", config.getItemModel(), itemId);
                        try {
                            Files.write(Paths.get(path), resources);
                        } catch (IOException e) {
                            logger.error("Unable to write file: {}", path);
                        }
                    }
                }
            });

        }
    }

    private static void prefetch() {
        if(!preFetchedResources) {
            final Date startDate = new Date();


            ConcurrentHashMap<Integer, String> dbpediaMapRatedItems = new ConcurrentHashMap<>();
            ratesMap.entrySet().parallelStream().forEach(entry -> {
                HashSet<Rate> rates = entry.getValue();
                rates.forEach(rate -> {
                    Integer itemId = rate.getItemId();
                    if(dbpediaMap.containsKey(itemId)) {
                        dbpediaMapRatedItems.put(itemId, dbpediaMap.get(itemId));
                    }
                });
            });

            Set<Integer> completedItems = new HashSet<>();
            HashMap<Integer, String> dbpediaMapRemaining = new HashMap<>(dbpediaMapRatedItems);
            completedUsers.stream().forEach(uid -> {
                HashSet<Rate> rates = ratesMap.get(uid);
                rates.stream().forEach(rate -> {
                    completedItems.add(rate.getItemId());
                });
            });

            dbpediaMapRemaining.keySet().removeAll(completedItems);

            logger.info("Remaining items: " + dbpediaMapRemaining.keySet().size());

            ProgressBar pb = new ProgressBar("Graph", dbpediaMapRemaining.values().size());
            pb.start();

            dbpediaMapRemaining.values().parallelStream().forEach(itemURI -> {
                Node n = nodePreFactory.getNode(itemURI);
                globalNodes.putIfAbsent(itemURI, n);

                // Fetching resources
                if (propsChain.size() > 0) {

                    propsChain.parallelStream().forEach(property -> {
                        String chain[] = property.split(" ");
                        final int depth = chain.length;

                        for (int i = 0; i < depth; i++) {
                            graphFetcherExecutor(nodePreFactory, n, itemURI, i + 1, chain);
                        }

                    });
                }

                pb.step();
            });

            pb.stop();

            final Date endDate = new Date();
            final String time = executionTime(startDate, endDate);
            logger.info("Graph fetched [Time: {}]", time);

            preFetchedResources = true;
        }
    }

    private List<Rate> recs(Integer uid) throws IOException {
        // List of items to predict
        List<Integer> itemsRated = userRates.stream().map(rate -> rate.getItemId()).collect(Collectors.toList());

        List<Integer> predict = new ArrayList<>(items);
        predict.removeAll(itemsRated);

        // Load user model
        final String model = String.format("%s/%d.tsv", config.getModel(), uid);

        HashMap<String, Double> weights = new HashMap<>();

        final Reader reader = new FileReader(model);
        Iterable<CSVRecord> records = CSVFormat.TDF.parse(reader);
        for (CSVRecord record : records) {
            String r = record.get(0);
            Double w = Double.parseDouble(record.get(1));
            weights.put(r, w);
        }
        reader.close();

        // Predictions
        List<Rate> predictions = new ArrayList<>();

        predict.parallelStream().forEach(item -> {
            AtomicDouble rate = new AtomicDouble(0);

            // Get resources
            String itemURI = dbpediaMap.get(item);

            if(config.getMemory()) {
                if(globalNodes.contains(itemURI)) {
                    Node itemNode = globalNodes.get(itemURI);

                    switch (config.getRecStrategy()) {
                        case 1:
                            itemNode.getEdges().stream().map(edge -> edge.getNode()).forEach(node -> {
                                rate.addAndGet(weights.getOrDefault(node.getURI(), 0.0));

                                node.getEdges().stream().map(edge2 -> edge2.getNode()).forEach(node2 -> {
                                    rate.addAndGet(weights.getOrDefault(node2.getURI(), 0.0));
                                });
                            });
                            break;

                        case 2:
                            itemNode.getEdges().stream().map(edge -> edge.getNode()).forEach(node -> {
                                final double nodeWeight = weights.getOrDefault(node.getURI(), 0.0);

                                node.getEdges().stream().map(edge2 -> edge2.getNode()).forEach(node2 -> {
                                    final double node2Weight = weights.getOrDefault(node2.getURI(), 0.0);
                                    rate.addAndGet(nodeWeight * node2Weight);
                                });
                            });
                            break;

                        case 3:
                            itemNode.getEdges().stream().map(edge -> edge.getNode()).forEach(node -> {
                                final double nodeWeight = weights.getOrDefault(node.getURI(), 0.0);
                                rate.addAndGet(nodeWeight);
                            });
                            break;

                        case 4:
                            itemNode.getEdges().stream().map(edge -> edge.getNode()).forEach(node -> {
                                final double nodeWeight = weights.getOrDefault(node.getURI(), 0.0);

                                node.getEdges().stream().map(edge2 -> edge2.getNode()).forEach(node2 -> {
                                    final double node2Weight = weights.getOrDefault(node2.getURI(), 0.0);
                                    rate.addAndGet(nodeWeight / node2Weight);
                                });
                            });
                            break;
                    }
                } else {
                    logger.error("Item: {} [{}] not found in map", item, itemURI);
                }

                Rate itemRate = new Rate(item, rate.get());
                predictions.add(itemRate);
            } else {
                UnitNodeFactory liveNodeFactory = new UnitNodeFactory();
                Node itemNode = liveNodeFactory.getNode(itemURI);
                globalNodes.putIfAbsent(itemURI, itemNode);

                // Fetching resources
                if (propsChain.size() > 0) {

                    propsChain.parallelStream().forEach(property -> {
                        String chain[] = property.split(" ");
                        final int depth = chain.length;

                        for (int i = 0; i < depth; i++) {
                            graphFetcherExecutor(liveNodeFactory, itemNode, itemURI, i + 1, chain);
                        }

                    });
                }

                itemNode.getEdges().stream().map(edge -> edge.getNode()).forEach(node -> {
                    rate.addAndGet(weights.getOrDefault(node.getURI(), 0.0));

                    node.getEdges().stream().map(edge2 -> edge2.getNode()).forEach(node2 -> {
                        rate.addAndGet(weights.getOrDefault(node2.getURI(), 0.0));
                    });
                });

                Rate itemRate = new Rate(item, rate.get());
                predictions.add(itemRate);
            }

        });

        // Normalize
        if(config.getNormalizeRates()) {
            final double max;
            final double min;

            OptionalDouble maxOptional = predictions.stream().mapToDouble(rate -> rate.getRate()).max();
            OptionalDouble minOptional = predictions.stream().mapToDouble(rate -> rate.getRate()).min();

            max = (maxOptional.isPresent()) ? maxOptional.getAsDouble() : 0;
            min = (minOptional.isPresent()) ? minOptional.getAsDouble() : 0;

            final double a = config.getNormalizeRateMin().doubleValue();
            final double b = config.getNormalizeRateMax().doubleValue();

            predictions.parallelStream().forEach(rate -> {
                final double x = rate.getRate();
                final double v = a + ((b-a) * (x-min) / max);
                rate.setRate(v);
            });
        }

        // Sort user rates
        final long limit = config.getNumRecsPerUser().longValue();
        List<Rate> p = predictions.stream().sorted().limit(limit).collect(Collectors.toList());

        return p;
    }


    private void run(Integer uid) {
        List<Double> X = new ArrayList<>();

        final Date startFetchDate = new Date();

        if(!config.getMemory()) {
            logger.info("UID: {} - Fetching resources", uid);
        }

        Integer tag = 1;
        Iterator userIt = userRates.iterator();
        while(userIt.hasNext()) {
            Rate rating = (Rate) userIt.next();

            // Get resource from itemId
            if(dbpediaMap.containsKey(rating.getItemId())) {

                String itemURI = dbpediaMap.get(rating.getItemId());

                X.add(Rate.getScaledDown(rating.getRate()));

                // Input node
                Node n = layers[0].getNodeFactory().getNode(itemURI);
                n.setRate(rating);
                n.setTAG(tag++);
                layers[0].addNode(n);

                // Adds to output layer
                Node nn = layers[OUTPUT_LAYER].getNodeFactory().getNode(itemURI);
                nn.setRate(rating);
                nn.setTAG(tag++);
                layers[OUTPUT_LAYER].addNode(nn);

                // Fetching resources
                if (propsChain.size() > 0) {

                    propsChain.parallelStream().forEach(property -> {
                        String chain[] = property.split(" ");
                        final int depth = chain.length;

                        logger.debug("P: {} -> Depth: {}", property, chain.length);
                        logger.debug("URI: {}", itemURI);

                        for(int i=0; i<depth; i++) {
                            graphBuilderExecutor(n, itemURI, i+1, depth, chain);
                        }

                    });
                }
            }
        }

        if(!config.getMemory()) {
            final Date endFetchDate = new Date();
            String timeFetch = executionTime(startFetchDate, endFetchDate);
            logger.info("UID: {} - All resources have been fetched [Time: {}]", uid, timeFetch);
        }

        final Date startTrainDate = new Date();
        logger.info("UID: {} - Training the network...", uid);

        int N = config.getAutoStopWindow();
        int inputLen = X.size();
        double[] errorHistory = new double[config.getEpochs()];
        double[] avgErrorsHistory = new double[config.getEpochs()-N];
        double[] input;
        double[] feedforwardOutput = new double[inputLen];

        // X input vector
        input = X.stream().mapToDouble(Double::doubleValue).toArray();

        for(int i=0; i<config.getEpochs(); i++) {
            feedforwardOutput = feedforward(input);
            backpropagate();

            double mse = MSE(input, layers[OUTPUT_LAYER].getNodeValues());
            errorHistory[i] = mse;
            logger.debug("Epoch: {} - MSE: {}", (i+1), mse);

            if(config.getAutoStop()) {
                if(i > N && i < config.getEpochs()-N) {
                    int k = i-N-1;
                    for(int j=0; j<N; j++) {
                        avgErrorsHistory[k] += errorHistory[i-j-1];
                        avgErrorsHistory[k] /= N;
                    }

                    logger.debug("AVG[{}]: {}", k, avgErrorsHistory[k]);

                    if(k > 1 && avgErrorsHistory[k] > avgErrorsHistory[k-1]) {
                        logger.info("*** Autostopping at iteration: {} - mse: {}", i + 1, mse);
                        logger.debug("AVG[{}]: {}", k-1, avgErrorsHistory[k-1]);
                        logger.debug("AVG[{}]: {}", k, avgErrorsHistory[k]);
                        break;
                    }

                }
            }
        }

        // Print predictions
        layers[OUTPUT_LAYER].getNodes().forEach(outputNode -> {
            String prediction = String.format("[%d] %s -> [Predicted: %s - Target: %s (Rate: %.1f)]", outputNode.getRate().getItemId(), outputNode.getURI(), outputNode.getZ(), Rate.getScaledDown(outputNode.getRate().getRate()), outputNode.getRate().getRate());
            logger.debug(prediction);
        });

        final Date endTrainDate = new Date();
        String timeTrain = executionTime(startTrainDate, endTrainDate);
        logger.info("UID: {} - Total Error: {} [Time: {}]", uid, E_tot(), timeTrain);
    }

     private void graphBuilderExecutor(Node n, String itemURI, int depth, int maxDepth, String[] chain) {
        switch (depth) {
            case 1: {
                // Adds nodes to the 1st layer
                String p = chain[0];
                Set<String> objs = (config.getMemory()) ? resourcePropertiesMemory(itemURI, p) : resourceProperties(itemURI, p);

                // Resource found through property p (triple's object in RDF graph)
                objs.stream().forEach(s -> {
                    Node nn = layers[1].getNodeFactory().getNode(s);
                    if(!layers[0].containsNode(nn)) {
                        if (!layers[1].containsNode(nn)) {
                            layers[1].addNode(nn);
                        }

                        try {
                            n.addEdge(p, nn);

                            // Link to output layer
                            if(maxDepth == 1) {
                                try {
                                    nn.addEdge("output", layers[OUTPUT_LAYER].getNode(itemURI));
                                } catch(NullNodeException e) {
                                    logger.error("Unable to link to output layer " + n + " -> " + nn.getURI());
                                }
                            }

                        } catch(NullNodeException e) {
                            logger.error("NullNodeException for URI: " + itemURI);
                        }
                    }
                });

                break;
            }

            case 2: {
                // Adds nodes to the 2nd layer
                String startingP = chain[0];
                String p = chain[1];

                Set<Node> nodes = layers[0].getDestinationNodesForResourceEdge(itemURI, startingP);

                nodes.stream().forEach(node -> {
                    Set<String> objs = (config.getMemory()) ? resourcePropertiesMemory(node.getURI(), p) : resourceProperties(node.getURI(), p);

                    // Resource found through property p (triple's object in RDF graph)
                    objs.forEach(s -> {
                        Node nn = layers[2].getNodeFactory().getNode(s);
                        if(!layers[0].containsNode(nn) && !layers[1].containsNode(nn)) {
                            if (!layers[2].containsNode(nn)) {
                                layers[2].addNode(nn);
                            }

                            try {
                                node.addEdge(p, nn);
                            } catch(NullNodeException e) {
                                logger.error("NullNodeException for URI: " + itemURI);
                            }

                            // Link to output layer
                            try {
                                nn.addEdge("output", n);
                            } catch(NullNodeException e) {
                                logger.error("Unable to link to output layer " + n + " -> " + nn.getURI());
                            }
                        }
                    });
                });

                break;
            }
        }
    }

    private static void graphFetcherExecutor(UnitNodeFactory factory, Node n, String itemURI, int depth, String[] chain) {
        switch (depth) {
            case 1: {
                // Adds nodes to the 1st layer
                String p = chain[0];
                Set<String> objs = null;
                objs = resourceProperties(itemURI, p);

                // Resource found through property p (triple's object in RDF graph)
                objs.stream().forEach(s -> {
                    Node nn = factory.getNode(s);
                    try {
                        n.addEdge(p, nn);
                    } catch(NullNodeException e) {
                        logger.error("NullNodeException for URI: " + itemURI);
                    }
                });

                break;
            }

            case 2: {
                // Adds nodes to the 2nd layer
                String startingP = chain[0];
                String p = chain[1];

                Set<Node> nodes = globalNodes.get(itemURI).getEdges(startingP).stream().map(Edge::getNode).collect(Collectors.toSet());

                nodes.stream().forEach(node -> {
                    Set<String> objs = resourceProperties(node.getURI(), p);

                    // Resource found through property p (triple's object in RDF graph)
                    objs.forEach(s -> {
                        Node nn = factory.getNode(s);
                        try {
                            node.addEdge(p, nn);
                        } catch(NullNodeException e) {
                            logger.error("NullNodeException for URI: " + itemURI);
                        }
                    });
                });

                break;
            }
        }
    }

    private void clear() {
        for(int i=0; i<layers.length; i++) {
            layers[i].clear();
        }
    }

    private double getRate(String URI) {
        Integer itemId = dbpediaToId.get(URI);

        Iterator it = userRates.iterator();
        while(it.hasNext()) {
            Rate rate = (Rate) it.next();
            if(rate.getItemId().equals(itemId)) {
                return rate.getRate().doubleValue();
            }
        }

        logger.error("Unable to get rate for resource: {}", URI);
        return 0.0;
    }

    private double[] feedforward(double[] input) {

        double output[] = new double[input.length];

        // Feed-Forward the Network

        layers[1].getNodes().forEach(node -> {
            Set<Edge> edges = node.getIncomingEdges();
            edges.forEach(edge -> {
                final double i = Rate.getScaledDown(edge.getNode().getRate().getRate()); // qui mi da null
                final double d = edge.getWeight() * i;
                node.addWeight(d);
            });

            // Node activation
            double z = activation.apply(node.getW());
            node.setZ(z);
        });

        for(int i=2; i<=MAX_DEPTH+1; i++) {
            layers[i].getNodes().forEach(node -> {
                Set<Edge> edges = node.getIncomingEdges();
                edges.forEach(edge -> {
                    final double z = edge.getNode().getZ();
                    final double d = edge.getWeight() * z;
                    node.addWeight(d);
                });

                // Node activation
                double z = activation.apply(node.getW());
                node.setZ(z);
            });
        }

        double activatedOutput[] = layers[OUTPUT_LAYER].getNodeValues();
        return activatedOutput;
    }

    private void backpropagate() {
        final double r = config.getLearningRate();

        double E_tot = 0.0;
        Iterator errorIterator = layers[OUTPUT_LAYER].getNodes().iterator();
        while(errorIterator.hasNext()) {
            Node node = (Node) errorIterator.next();
            double target = Rate.getScaledDown(node.getRate().getRate());

            node.setOutputDelta(target - node.getZ());

            E_tot += 0.5 * pow((target - node.getZ()), 2);
        }

        layers[OUTPUT_LAYER].getNodes().forEach(node -> {
            node.setDelta(Rate.getScaledDown(node.getRate().getRate()) - node.getZ());

            node.getIncomingEdges().forEach(edge -> {
                Node pnode = edge.getNode();
                pnode.addDelta(edge.getWeight()*node.getDelta());
            });
        });

        for(int i=MAX_DEPTH; i>0; i--) {
            layers[i].getNodes().forEach(node -> {
                node.getIncomingEdges().forEach(edge -> {
                    Node pnode = edge.getNode();
                    pnode.addDelta(edge.getWeight()*node.getDelta());
                });
            });
        }

        // Update edges' weights

        layers[1].getNodes().forEach(node -> {
            node.getIncomingEdges().forEach(edge -> {
                final double input = Rate.getScaledDown(edge.getNode().getRate().getRate());
                final double old_weight = edge.getWeight();
                final double new_weight = old_weight + r * node.getDelta() * deactivation.apply(node.getZ()) * input;
                edge.setWeight(new_weight);
            });
        });

        for(int i=2; i<=MAX_DEPTH+1; i++) {
            layers[i].getNodes().forEach(node -> {
                node.getIncomingEdges().forEach(edge -> {
                    final double input = node.getZ();
                    final double old_weight = edge.getWeight();
                    final double new_weight = old_weight + r * node.getDelta() * deactivation.apply(node.getZ()) * input;
                    edge.setWeight(new_weight);
                });
            });
        }

        // Reset node's weight

        for(int i=0; i<layers.length; i++) {
            layers[i].getNodes().forEach(node -> {
                node.setValue(node.getW());
                node.setW(0.0);
                node.setDelta(0.0);
            });
        }

    }

    private void writeRecs(Integer uid, List<Rate> rates) {
        FileWriter fileWriter = null;
        CSVPrinter csvFilePrinter = null;
        CSVFormat csvFileFormat = CSVFormat.TDF.withRecordSeparator("\n");
        String tsvFile = String.format("%s/%d.tsv", config.getRecsDir(), uid);
        try {
            fileWriter = new FileWriter(tsvFile);
            csvFilePrinter = new CSVPrinter(fileWriter, csvFileFormat);
            for(Rate rate : rates) {
                List record = new ArrayList();
                record.add(uid);
                record.add(rate.getItemId());
                record.add(rate.getRate());
                csvFilePrinter.printRecord(record);
            }
            fileWriter.flush();
            fileWriter.close();
        } catch(IOException e) {
            logger.error("Unable to write file: {}", tsvFile);
        }

    }

    private static Set<Integer> loadColdUsers(String filename) {
        Set<Integer> users = new HashSet<>();

        try (Stream<String> stream = Files.lines(Paths.get(filename))) {
            users = stream
                    .map(line -> Integer.parseInt(line))
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            e.printStackTrace();
        }

        return users;
    }

    private void writeResults(Integer uid) {
        // TSV

        // Order features?
        ArrayList<Node> nodeList = new ArrayList<>();
        for(int i=0; i<MAX_DEPTH; i++) {
            layers[i+1].getNodes().forEach(node -> {
                nodeList.add(node);
            });
        }

        // Sort
        Collections.sort(nodeList);

        // Normalize?
        if(config.getNormalizeFeatures()) {
            final double max;
            final double min;

            OptionalDouble maxOptional = nodeList.stream().mapToDouble(node -> node.getValue()).max();
            OptionalDouble minOptional = nodeList.stream().mapToDouble(node -> node.getValue()).min();

            max = (maxOptional.isPresent()) ? maxOptional.getAsDouble() : 0;
            min = (minOptional.isPresent()) ? minOptional.getAsDouble() : 0;

            nodeList.stream().forEach(node -> {
                final double v = ((max-min) > 0) ? (node.getValue() - min) / (max-min) : node.getValue();
                node.setValue(v);
            });
        }

        FileWriter fileWriter = null;
        CSVPrinter csvFilePrinter = null;
        CSVFormat csvFileFormat = CSVFormat.TDF.withRecordSeparator("\n");
        String tsvFile = String.format("%s/%d.tsv", config.getModel(), uid);
        try {
            fileWriter = new FileWriter(tsvFile);
            csvFilePrinter = new CSVPrinter(fileWriter, csvFileFormat);
            for(Node node : nodeList) {
                List nodeRecord = new ArrayList();
                nodeRecord.add(node.getURI());
                nodeRecord.add(node.getValue());
                csvFilePrinter.printRecord(nodeRecord);
            }
            fileWriter.close();
        } catch(IOException e) {
            logger.error("Unable to write file: {}", tsvFile);
        }

    }

    private static Query build(String resourceURI, String propertyChain) {
        Query q = null;

        Boolean orderDesc = true;

        String parts[] = propertyChain.split(" ");

        switch(parts.length) {
            case 1: {
                String property = parts[0];
                if (parts[0].startsWith("*")) {
                    property = parts[0].substring(1, parts[0].length());
                    orderDesc = false;
                }

                q = QueryFactory.make();
                q.getResolver().suppressExceptions();
                q.setQuerySelectType();

                ExprVar exprO = new ExprVar("o");
                ExprVar exprMember = new ExprVar("member");
                ExprVar exprMemberCount = new ExprVar("memberCount");

                q.addResultVar(exprO.asVar());

                ElementGroup elg = new ElementGroup();
                elg.addTriplePattern(new Triple(exprMember.asVar(), NodeFactory.createURI(property), exprO.asVar()));
                elg.addElementFilter(new ElementFilter(new E_IsIRI(new ExprVar("o"))));


                BasicPattern bp = new BasicPattern() ;
                Triple ne = new Triple(exprO.asVar(), NodeFactory.createURI("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"), NodeFactory.createURI("http://dbpedia.org/ontology/Film"));
                bp.add(ne) ;
                OpBGP op = new OpBGP(bp) ;

                elg.addElementFilter(new ElementFilter(new E_NotExists(op)));

                elg.addTriplePattern(new Triple(NodeFactory.createURI(resourceURI), NodeFactory.createURI(property), exprO.asVar()));

                q.setQueryPattern(elg);
                q.addGroupBy(exprO.asVar());
                q.addOrderBy(exprMemberCount, (orderDesc) ? -1 : 1);

                q.addResultVar(exprMemberCount.asVar(), new ExprAggregator(exprMember.asVar(), new AggCountVar(exprMember)));
            }
                break;

            case 2: {
                String property1 = parts[0];
                String property2 = parts[1];

                if (parts[0].startsWith("*")) {
                    property1 = parts[0].substring(1, parts[0].length());
                    orderDesc = false;
                }

                q = QueryFactory.make();
                q.getResolver().suppressExceptions();
                q.setQuerySelectType();

                ExprVar exprO = new ExprVar("o");
                ExprVar exprO2 = new ExprVar("o2");

                ExprVar exprMemberCount = new ExprVar("memberCount");

                q.addResultVar(exprO.asVar());
                q.addResultVar(exprO2.asVar());

                ElementGroup elg = new ElementGroup();
                elg.addElementFilter(new ElementFilter(new E_IsIRI(new ExprVar("o"))));
                elg.addElementFilter(new ElementFilter(new E_IsIRI(new ExprVar("o2"))));

                elg.addTriplePattern(new Triple(NodeFactory.createURI(resourceURI), NodeFactory.createURI(property1), exprO.asVar()));

                elg.addTriplePattern(new Triple(exprO2.asVar(), NodeFactory.createURI(property2), exprO.asVar()));
                elg.addTriplePattern(new Triple(exprO2.asVar(), RDF.type.asNode(), NodeFactory.createURI("http://dbpedia.org/ontology/Film")));
                elg.addElementFilter(new ElementFilter(new E_IsIRI(new ExprVar("o2"))));

                q.setQueryPattern(elg);
                q.addGroupBy(exprO.asVar());
                q.addGroupBy(exprO2.asVar());
                q.addOrderBy(exprMemberCount, (orderDesc) ? -1 : 1);

                q.addResultVar(exprMemberCount.asVar(), new ExprAggregator(exprO2.asVar(), new AggCountVar(exprO2)));

//                    if(config.getFeaturesLimit() > 0) {
//                        q.setLimit(config.getFeaturesLimit());
//                    }

//                    q.serialize(new IndentedWriter(System.out,false));
//                    System.out.println();
            }
                break;

            default:
                logger.info("{} Unmanaged chain length!");
                break;
        }

//        q.serialize(new IndentedWriter(System.out,false));
//        System.out.println();

        logger.debug("Query: {}", q);

        if(q == null) {
            logger.error("Unable to build query for resource: {} property chain: {}", resourceURI, propertyChain);
        }

        return q;
    }

    private static Set<String> resourcePropertiesMemory(String resourceURI, String p) {
        return nodePreFactory.getNode(resourceURI).getEdges(p).stream().map(Edge::getNode).map(Node::getURI).collect(Collectors.toSet());
    }

    private static Set<String> resourceProperties(String resourceURI, String propertyChain) {
        Set<String> resultResources = new HashSet<>();

        Query query = build(resourceURI, propertyChain);
        QueryExecution qexec = QueryExecutionFactory.sparqlService(config.getEndpoint(), query);

        try {
            ResultSet results = qexec.execSelect();

            while (results.hasNext()) {
                QuerySolution binding = results.nextSolution();
                Resource obj = (Resource) binding.get("o");
                String objectResourceURI = obj.getURI();

                resultResources.add(objectResourceURI);
                logger.debug("{} -> ({}) -> {}", resourceURI, propertyChain, objectResourceURI);
            }

            resourceNotFound.put(resourceURI, config.getRetryFetchResource()+1);
        } catch(Exception e) {

            Integer retry = resourceNotFound.getOrDefault(resourceURI, 0);

            if(retry < config.getRetryFetchResource()) {
                retry = retry +1;
                resultResources = resourceProperties(resourceURI, propertyChain);
                resourceNotFound.put(resourceURI, retry);
            } else {
                logger.error("{} - [{}] - Resource not found: {}", e, retry, resourceURI);
            }
        } finally {
            qexec.close();
        }

        return resultResources;
    }

    private static Long countFileLines(String filename) {
        logger.info("Counting lines of file {}", filename);

        try (Stream<String> stream = Files.lines(Paths.get(filename))) {
            return stream.count();
        } catch(IOException e) {
            logger.error("Error loading file: {}", filename);
        }

        return Long.valueOf(0);
    }

    private static String executionTime(Date startDate, Date endDate) {
        // milliseconds
        long different = endDate.getTime() - startDate.getTime();

        long secondsInMilli = 1000;
        long minutesInMilli = secondsInMilli * 60;
        long hoursInMilli = minutesInMilli * 60;
        long daysInMilli = hoursInMilli * 24;

        long elapsedDays = different / daysInMilli;
        different = different % daysInMilli;

        long elapsedHours = different / hoursInMilli;
        different = different % hoursInMilli;

        long elapsedMinutes = different / minutesInMilli;
        different = different % minutesInMilli;

        long elapsedSeconds = different / secondsInMilli;

        return String.format("%d minutes, %d seconds",elapsedMinutes, elapsedSeconds);
    }

    private static void loadRatings(String filename, Boolean firstRecordAsHeader) {
        Iterable<CSVRecord> records;

        try {
            Long ratingsCount = 0L;
            Reader in = new FileReader(filename);

            records = (firstRecordAsHeader) ? CSVFormat.TDF.withFirstRecordAsHeader().parse(in) : CSVFormat.TDF.parse(in);

            for (CSVRecord record : records) {
                Integer userId = (firstRecordAsHeader) ? Integer.parseInt(record.get("userId")) : Integer.parseInt(record.get(0));
                Integer itemId = (firstRecordAsHeader) ? Integer.parseInt(record.get("movieId")) : Integer.parseInt(record.get(1));
                Double rateVal = (firstRecordAsHeader) ? Double.parseDouble(record.get("rating")) : Double.parseDouble(record.get(2));

                Rate rate = new Rate(itemId, rateVal);

                if(ratesMap.containsKey(userId)) {
                    ratesMap.get(userId).add(rate);
                } else {
                    HashSet<Rate> rates = new HashSet<>();
                    rates.add(rate);
                    ratesMap.put(userId, rates);
                }

                if(!items.contains(itemId)) {
                    items.add(itemId);
                }

                ratingsCount++;
            }

            // Collect ordered list of users
            users = ratesMap.keySet().stream().collect(Collectors.toList());
            Collections.sort(users);

            logger.info("Ratings ({}) file read: {}", ratingsCount, filename);
        } catch (IOException e) {
            logger.error("Error reading file {}", e, filename);
        }
    }

    private static void loadDBpediaMappings(String filename) {
        try {
            Integer moviesCount = 0;
            Reader in = new FileReader(filename);
            Iterable<CSVRecord> records = CSVFormat.TDF.parse(in);

            for (CSVRecord record : records) {
                Integer movieId = Integer.parseInt(record.get(0));
                String movieResourceURI = record.get(1);

                dbpediaMap.put(movieId, movieResourceURI);
                dbpediaToId.put(movieResourceURI, movieId);

                moviesCount++;
            }

            logger.info("DBpedia mapping file read ({} items)", moviesCount);
        } catch (IOException e) {
            logger.error("Error reading DBpedia mapping file {}", e, filename);
        }
    }

    private static void loadProperties(String filename) {
        try (Stream<String> stream = Files.lines(Paths.get(filename))) {
            stream.map(line -> line.trim())
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .forEach(line -> {
                        if(!propsChain.contains(line)) {
                            propsChain.add(line);
                        }
                    });

        } catch (IOException e) {
            logger.error("File not found: {}", filename);
            System.exit(-1);
        }

        logger.info("*** PROPS ***");

        propsChain.forEach(s -> {
            logger.info("{}", s.replaceAll("\\s"," -> "));
        });

        // Check max depth
        int maxDepth = 0;
        for(String s : propsChain) {
            int depth = s.split(" ").length;
            maxDepth = (depth > maxDepth) ? depth : maxDepth;
        }
        MAX_DEPTH = maxDepth;
        OUTPUT_LAYER = maxDepth+1;

        logger.info("Loaded {} property chains [maxDepth: {}]", propsChain.size(), maxDepth);
        logger.info("***");
    }

    private double RMSE(double input[], double output[]) {
        int n = input.length;

        double rmse = 0.0;

        // RMSE
        for(int i=0; i<n; i++) {
            rmse += pow(input[i]-output[i], 2);
        }
        rmse /= n;
        rmse = sqrt(rmse);

        return rmse;
    }

    private double MSE(double target[], double predicted[]) {
        int n = target.length;

        double mse = 0.0;

        for(int i=0; i<n; i++) {
            mse += pow(target[i]-predicted[i], 2);
        }
        mse /= n;

        return mse;
    }

    private double E_tot() {
        double E_tot = 0.0;
        Iterator errorIterator = layers[OUTPUT_LAYER].getNodes().iterator();
        while(errorIterator.hasNext()) {
            Node node = (Node) errorIterator.next();
            double target = Rate.getScaledDown(node.getRate().getRate());

            node.setOutputDelta(target - node.getZ());

            E_tot += 0.5 * pow((target - node.getZ()), 2);
        }
        return E_tot;
    }

}