import mpi.MPI;
import mpi.Request;
import util.Logger;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Random;
import java.util.Scanner;


public class AppMPJ {
    private final static int ROOT = 0;
    private final static int SHA256_LEN = 64;
    private final static int MD5_LEN = 32;
    private final static int MATCH_FOUND_FLAG = 127;

    // init buffers with offsets

    // generate hash
    // check hash
        // if found, stop everything
    // add +1 to buffer
        // check for carryover

    public static void main(String[] args) {
        // read args to init vars
        int max = 6;
        BigInteger allPerms = new BigInteger("1004");
        char[] hash = "71e50ae29377c232b34b79a7b5900c01".toCharArray();
        char[] charset = "abcdefghijklmnopqrstuvwxyz".toCharArray();

        MPI.Init(args);

        int self = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();
        int[] buffer = new int[max];
        int[] killswitch = new int[1];
        Request kill_req;

        // we are expecting a message in the future
        kill_req = MPI.COMM_WORLD.Irecv(killswitch, 0, 1, MPI.INT, MPI.ANY_SOURCE, 1337);
        while (killswitch[0] != 1) {
            try {
                Thread.sleep(100 + self);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            double rng = Math.random();
            Logger.debug("<"+self+"> " + rng);
            if (rng > .95) {
                Logger.debug("<"+self+"> " + "Match found!");
                killswitch[0] = 1;
                // send message
                for (int i = 0; i < size; i++) {
                    MPI.COMM_WORLD.Isend(killswitch, 0, 1, MPI.INT, i, 1337);
                }
            }
            // did we receive message?
            if (kill_req.Test() != null) {
                // sync buffers
                kill_req.Wait();
            }
        }

        Logger.debug("<"+self+"> " + "done!");

        MPI.Finalize();
    }
}
