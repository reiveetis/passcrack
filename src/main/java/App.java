import java.util.Arrays;
import java.util.Scanner;

public class App {
    private static final String charset = "abcdefghijklmnopqrstuvwxyz";
    static void main(String[] args){
        long start = System.currentTimeMillis();
        Scanner sc = new Scanner(System.in);
        StringProducer strProd = new StringProducer(4);
        String str = strProd.produceNext();
        while (str != null) {
//            System.out.println(str);
            str = strProd.produceNext();
        }
        long end = System.currentTimeMillis();
        System.out.println("Computing took: " + (end - start) + "ms");
    }
}