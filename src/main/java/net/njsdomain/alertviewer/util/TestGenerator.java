package net.njsdomain.alertviewer.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public class TestGenerator {

    public static void main(String[] args) {
        System.out.println("Generating test files");
        int NUMFILES = 1;

        LocalDateTime time = LocalDateTime.now();
        time = time.minusDays(2);

        for (int i = 0;i < NUMFILES;i++) {
            File f = new File("/opt/user/dev/logging/index/in/testfile"+i+".csv");
            try {
                f.createNewFile();
            } catch (Exception e) {
                e.printStackTrace(System.out);
            }
            try (FileWriter fw = new FileWriter(f)) {
                BufferedWriter bw = new BufferedWriter(fw);
                bw.write("TIME,MILLIS,ID,LOGTYPE,MESSAGE");
                bw.newLine();
                for (int ii = 0;ii < 5;ii++) {
                    bw.write(getTime(time));
                    time = time.plusMinutes(1);
                    bw.write(",");

                    bw.write("AJKtBXMBSz2ZN8losmhb");
                    bw.write(",");

                    bw.write("ERROR");
                    bw.write(",");

                    bw.write("This is an error message. It has a \"unique\" value in it which is '"+ UUID.randomUUID().toString()+"' and it was (generated) at "+time.toString());
                    bw.newLine();
                }

                for (int ii = 0;ii < 5;ii++) {
                    bw.write(getTime(time));
                    time = time.plusMinutes(1);
                    bw.write(",");

                    bw.write("AJKtBXMBSz2ZN8losmhb");
                    bw.write(",");

                    bw.write("ERROR");
                    bw.write(",");

                    //bw.write("This is an error message. This one will be the same and appear often. This is "+ii+" of 5.");
                    bw.write("This is an error message. This one will be the same and appear often.");
                    bw.newLine();
                }


                time = time.plusSeconds(1);
                bw.flush();
                bw.close();
            } catch(Exception e) {
                e.printStackTrace(System.out);
            }
        }

    }

    public static String getTime(LocalDateTime time) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'.'SSS'Z'");
        String s = time.format(dtf);
        return s;
    }

}
