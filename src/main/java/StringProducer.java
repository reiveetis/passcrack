import java.util.ArrayList;

public class StringProducer {
    private static final String charset = "abcdefghijklmnopqrstuvwxyz";
    private final int length;
    private ArrayList<String> strings = new ArrayList<>();

    StringProducer(int length) {
        this.length = length;
        populateArr("");
    }

    public String getString(int index) {
        return strings.get(index);
    }

    private void populateArr(String prefix) {
        if (length == prefix.length()) {
            strings.add(prefix);
            return;
        }

        for (char ch : charset.toCharArray()) {
            populateArr(prefix + ch);
        }
    }

}
