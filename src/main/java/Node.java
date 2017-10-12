import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * Created by bellini on 10/03/17.
 */

public class Node implements Comparable<Node> {
    private Rate rate;
    private Integer TAG;
    private String URI;
    private boolean bias;
    private ConcurrentHashMap<Edge, String> edges;
    private ConcurrentHashMap<Edge, String> incomingEdges;
    private double W; // weights
    private double Z; // outgoing w
    private double value; // final outgoing weight
    private double delta;
    private double outputDelta;
    
    private Boolean isTrainable = true;

    Node() {
        edges = new ConcurrentHashMap<>();
        incomingEdges = new ConcurrentHashMap<>();
    }

    Node(String URI) {
        this();
        this.URI = URI;
        W = 0.0;
        Z = 0.0;
        
        delta = 0.0;
    }

    public String getURI() { return URI; }

    public Rate getRate() {
        return rate;
    }

    public void setRate(Rate rate) {
        this.rate = rate;
    }

    public Integer getTAG() {
        return TAG;
    }

    public void setTAG(Integer TAG) {
        this.TAG = TAG;
    }

    public boolean isBias() {
        return bias;
    }

    public void setBias(boolean bias) {
        this.bias = bias;
    }

    public void addEdge(Edge edge) {
        edge.getNode().addIncomingEdge(edge.getProperty(), this);
        edges.put(edge, edge.getProperty());
    }

    public void addEdge(String p, Node n) throws NullNodeException {
        if(n == null) {
            throw new NullNodeException("P: " + p);
        }

        Edge e = new Edge(p, n);
        edges.put(e, e.getProperty());
        n.addIncomingEdge(p, this);
    }

    public void addIncomingEdge(String p, Node n) {
        incomingEdges.put(new Edge(p, n), p);
    }

    public Set<Edge> getIncomingEdges() {
        return incomingEdges.keySet();
    }

    public Set<Edge> getEdges() {
        return edges.keySet();
    }

    public Set<Edge> getEdges(String p) {
        return edges.keySet().stream().filter(edge -> edge.getProperty().equals(p)).collect(Collectors.toSet());
    }

    public void setZ(double weight) {
        this.Z = weight;
    }

    public double getZ() {
        return Z;
    }

    public void setW(double weight) {
        this.W = weight;
    }

    public double getW() {
        return W;
    }

    public void addWeight(double t) {
        this.W += t;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }

    public double getDelta() {
        return delta;
    }

    public void setDelta(double delta) {
        this.delta = delta;
    }

    public void addDelta(double delta) {
        this.delta += delta;
    }

    public double getOutputDelta() {
        return outputDelta;
    }

    public void setOutputDelta(double outputDelta) {
        this.outputDelta = outputDelta;
    }

    public void clear() {
        edges.clear();
        incomingEdges.clear();
    }

    @Override
    public int compareTo(Node t) {
        return Double.compare(t.value, this.value);
    }
    
    public void clearEdges() {
        edges.clear();
    }
    
    public void clearIncomingEdges() {
        incomingEdges.clear();
    }
    
    public Boolean isTrainable() {
    	return isTrainable;
    }
    
    public void setTrainable(Boolean isTrainable) {
    	this.isTrainable = isTrainable;
    }
    
}