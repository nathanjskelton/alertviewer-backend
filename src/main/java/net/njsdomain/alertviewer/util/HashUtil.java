package net.njsdomain.alertviewer.util;

import javax.xml.bind.DatatypeConverter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashUtil {

    public static String getHash(String message) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        md.update(message.getBytes());
        String hash = DatatypeConverter.printHexBinary(md.digest()).toUpperCase();
        return hash;
    }
}
