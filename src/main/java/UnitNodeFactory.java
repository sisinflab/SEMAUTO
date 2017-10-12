import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by bellini on 11/04/17.
 */
public class UnitNodeFactory {
    private final ConcurrentHashMap<String, Node> map = new ConcurrentHashMap<>();

    public Node getNode(String URI) {
        if(map.containsKey(URI)) {
            return map.get(URI);
        } else {
            return build(URI);
        }
    }

    public Node build(String URI) {
        Node n = new Node(URI);
        map.put(URI, n);
        return n;
    }
}
