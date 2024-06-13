package com.vandendaelen.nicephore.util;

import java.io.File;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;

public class IdentifierGenerator {

    public static String generateIdentifier(File file) throws Exception {
        Path filePath = Paths.get(file.getAbsolutePath());
        BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);
        String fileInfo = file.getName() + attrs.lastModifiedTime().toString();

        MessageDigest md = MessageDigest.getInstance("MD5");
        md.update(fileInfo.getBytes());
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b & 0xff));
        }

        return sb.toString();
    }

    public static void main(String[] args) throws Exception {
        File screenshotsDir = new File("./screenshots");
        File[] screenshots = screenshotsDir.listFiles();
        for (File screenshot : screenshots) {
            System.out.println("File: " + screenshot.getName() + ", Identifier: " + generateIdentifier(screenshot));
        }
    }
}