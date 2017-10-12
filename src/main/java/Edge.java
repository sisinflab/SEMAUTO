import java.util.Random;

/**
 * Created by bellini on 10/03/17.
 */
public class Edge {
    private static Config config;

    private static final Random r = new Random();

    private String property;
    private Node node; // destination node
    private double W;

    static {
        config = new Config("config.properties");
    }

    Edge(String property, Node node) {
        this.property = property;
        this.node = node;

        setWeight();
    }

    public String getProperty() {
        return property;
    }

    public Node getNode() {
        return node;
    }

    public double getWeight() {
        return W;
    }

    public void setWeight(double z) {
        this.W = z;
    }
    
    public void setWeight() {
    	switch(config.getWeightStrategy()) {
        case 1:
            this.W = r.nextGaussian();
            break;
        case 2:
            this.W = r.doubles(1, -1, 1).findFirst().getAsDouble();
            break;
        case 3:
            this.W = r.doubles(1, 0, 1).findFirst().getAsDouble();
            break;
        case 4:
            this.W = config.getWeightK();
            break;
    	}
    }
}
