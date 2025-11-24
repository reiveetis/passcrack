public class StringProducer {
    private static final String charset = "abcdefghijklmnopqrstuvwxyz";

    private final int length;
    private final int maxChar;
    private StringBuilder strBuilder = new StringBuilder();

    // produceNext impl
    private static int[] buffer;


    StringProducer(int length) {
        this.length = length;
        this.maxChar = charset.length();
        buffer = new int[length];
    }

    public String produceNext() {
        if (buffer[0] == maxChar) {
            return null;
        }

        strBuilder.setLength(0);
        for (int i = 0; i < length; i++) {
            strBuilder.append(charset.charAt(buffer[i]));
        }

        buffer[length - 1]++;
        short carryOffset = 1;
        while (buffer[length - carryOffset] == maxChar) {
            if (carryOffset == length) break;
            // set current to 0
            buffer[length - carryOffset] = 0;
            // increment prev
            carryOffset++;
            buffer[length - carryOffset]++;
        }
        return strBuilder.toString();
    }

    public void produceAll() {
        int[] chars = new int[length];

        while (chars[0] != maxChar) {
//            do stuff

//            String str = "";
//            for (int i = 0; i < length; i++) {
//                str += charset.charAt(chars[i]);
//            }
//            System.out.println(str);

            chars[length - 1]++;
            short carryOffset = 1;
            while (chars[length - carryOffset] == maxChar) {
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
