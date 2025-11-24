import java.util.ArrayList;

public class App {
    static void main(String[] args) {
        long start = System.currentTimeMillis();
        StringProducer strProd = new StringProducer(5);
        long end = System.currentTimeMillis();
        System.out.println("Generated strings in: " + (end - start) + " ms.");
    }
}
