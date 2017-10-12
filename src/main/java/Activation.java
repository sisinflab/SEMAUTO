import static java.lang.Math.exp;
import static java.lang.Math.log;

/**
 * Created by bellini on 28/03/17.
 */
public class Activation {
    private ActivationFunction func;

    Activation(ActivationFunction f) {
        this.func = f;
    }

    public static double sigmoid(double t) {
        return 1.0 / (1.0 + exp(-t));
    }

    public static double dsigmoid(double t) {
        return sigmoid(t) * (1.0 - sigmoid(t));
    }

    public static double relu(double t) {
        return t;
    }

    public static double drelu(double t) {
        return 1;
    }

    public static double erelu(double t) {
        return t;
    }

    public static double derelu(double t) {
        final double alpha = 0.3;
        return alpha * (exp(t) -1);
    }

    public static double softplus(double t) {
        return log(1+exp(t));
    }

    public static double dsoftplus(double t) {
        return 1.0 / (1.0 + exp(-t));
    }

    public static double[] softmax(double[] v) {
        double[] r = new double[v.length];

        double sum = 0.0;
        for(int i=0; i<v.length; i++) {
            sum += exp(v[i]);
        }

        for(int i=0; i<v.length; i++) {
            r[i] = exp(v[i]) / sum;
        }

        return r;
    }
}
