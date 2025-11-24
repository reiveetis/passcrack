import java.util.ArrayList;

public class StringProducer {
    private static final String charset = "abcdefghijklmnopqrstuvwxyz";
    private final int length;

    // produceNext impl
    private static int[] buffer;


    StringProducer(int length) {
        this.length = length;
        buffer = new int[length];
    }

    public String produceNext() {
        int max = charset.length() - 1;
        if (buffer[0] == max + 1) {
            return null;
        }

        String str = "";
        for (int i = 0; i < length; i++) {
            str += charset.charAt(buffer[i]);
        }

        buffer[length - 1]++;
        for (int i = length - 1; i > 0; i--) {
            if (buffer[i] == max + 1) {
                buffer[i] = 0;
                buffer[i - 1]++;
            }
        }
        return str;
    }

    public void produceAll() {
        int[] chars = new int[length];
        int max = charset.length() - 1;

        while (chars[0] != max + 1) {
//            do stuff

//            String str = "";
//            for (int i = 0; i < length; i++) {
//                str += charset.charAt(chars[i]);
//            }
//            System.out.println(str);

            chars[length - 1]++;
            short carryOffset = 1;
            while (chars[length - carryOffset] == max + 1) {
                if (carryOffset == length) break;
                // set current to 0
                chars[length - carryOffset] = 0;
                // increment prev
                carryOffset++;
                chars[length - carryOffset]++;
            }
        }
    }

}
