public class App {
    private static final String charset = "abcdefghijklmnopqrstuvwxyz";
    static void main(String[] args) throws Exception {


        long start = System.currentTimeMillis();
        int length = 7;
        int[] chars = new int[length];
        int max = charset.length() - 1;

        while (chars[0] != max + 1) {
//            String str = "";
//            for (int i = 0; i < length; i++) {
//                str += charset.charAt(chars[i]);
//            }
//            System.out.println(str);

            chars[length - 1]++;
            int carryOffset = 1;
            while (chars[length - carryOffset] == max + 1) {
                if (carryOffset == length) break;
                // set current to 0
                chars[length - carryOffset] = 0;
                // increment prev
                carryOffset++;
                chars[length - carryOffset]++;
            }
        }
        long end = System.currentTimeMillis();
        System.out.println("Computing took: " + (end - start) + "ms");
    }
}
