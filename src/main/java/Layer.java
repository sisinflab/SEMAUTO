import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by bellini on 10/03/17.
 */

public class Layer {
    private String name;
    private List<Node> nodes;
    
    private UnitNodeFactory nodeFactory;

    Layer(String name) {
        this.nodes = new ArrayList<>();

        this.name = name;
        
        this.nodeFactory = new UnitNodeFactory();
    }

    public boolean containsNode(Node n) {
        return nodes.contains(n);
    }

    public void addNode(Node n) {
        nodes.add(n);
    }

    public List<Node> getNodes() {
        return nodes;
    }

    public List<Node> getNodesButBias() {
        return nodes.stream().filter(n -> !n.isBias()).collect(Collectors.toList());
    }

    public int getNumNodes() {
        return nodes.size();
    }

    public Node getNode(String URI) {
        Optional<Node> result = nodes.stream().filter(node -> node.getURI().equals(URI)).findFirst();
        if(result.isPresent()) {
            return result.get();
        } else {
            return null;
        }
    }

    public Node getNode(Integer TAG) {
        Optional<Node> result = nodes.stream().filter(node -> node.getTAG().equals(TAG)).findFirst();
        if(result.isPresent()) {
            return result.get();
        } else {
            return null;
        }
    }

    // synchronized
    public Set<Node> getDestinationNodesForResourceEdge(String URI, String p) {
        Node node = getNode(URI);
        if(node != null) {
            return node.getEdges(p).stream().map(Edge::getNode).collect(Collectors.toSet());
        }

        return null;
    }

    public double[] getNodeValues() {
        List<Node> os = nodes.stream().filter(node -> node.isTrainable()).sorted(Comparator.comparing(a -> a.getTAG())).collect(Collectors.toList());
        double activatedOutput[] = os.stream().map(Node::getZ).collect(Collectors.toList()).stream().mapToDouble(Double::doubleValue).toArray();
        return activatedOutput;
    }

    public void clear() {
        nodes.forEach(node -> {
            node.clear();
        });
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        Iterator it = nodes.iterator();
        while(it.hasNext()) {
            StringBuilder nb = new StringBuilder();

            Node node = (Node) it.next();

            nb.append(node.toString());

            nb.append(node.getURI());

            Set<Edge> edges = node.getEdges();
            Iterator itEdges = edges.iterator();
            while(itEdges.hasNext()) {
                Edge edge = (Edge) itEdges.next();

                nb.append(String.format(" -(%s)-> ", edge.getProperty()));

                Node dstNode = edge.getNode();
                nb.append(dstNode.getURI());

            }

            sb.append(nb.toString());
            sb.append("\n");
        }

        return sb.toString();
    }
    
    public UnitNodeFactory getNodeFactory() {
    	return nodeFactory;
    }
    
    public void setIsTrainableNodes(Boolean isTrainable) {
    	nodes.parallelStream().forEach(node -> { node.setTrainable(isTrainable); });
    }
}
