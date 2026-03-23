package com.example.vaultdemo.cli;

import com.example.vaultdemo.service.EnvelopeCryptoService;
import com.example.vaultdemo.service.VaultAuthService;
import com.example.vaultdemo.service.VaultTransitService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.SecureRandom;

@Component
public class VaultCliRunner implements CommandLineRunner {

    private final VaultAuthService authService;
    private final VaultTransitService transitService;
    private final EnvelopeCryptoService envelopeCryptoService;
    private final ResourceLoader resourceLoader;

    public VaultCliRunner(VaultAuthService authService,
                          VaultTransitService transitService,
                          EnvelopeCryptoService envelopeCryptoService, ResourceLoader resourceLoader) {
        this.authService = authService;
        this.transitService = transitService;
        this.envelopeCryptoService = envelopeCryptoService;
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void run(String... args) throws IOException {
//        if (args.length == 0) {
//            printUsage();
//            return;
//        }
//        generateReadableTestFile(Path.of("C:/Users/navne/IdeaProjects/VaultDemo/src/main/resources/OneGBFile.txt"), 1);
//        String arg = "encrypt-huge-file";
        String arg = "decrypt-huge-file";
//        if (1==1) return;
        try {
            switch (arg) {
                case "login" -> {
                    String token = authService.loginWithAppRole();
                    System.out.println("AppRole client token:");
                    System.out.println(token);
                }
                case "generate-dek" -> {
                    String dek = envelopeCryptoService.generateDekBase64();
                    System.out.println("Generated DEK (Base64):");
                    System.out.println(dek);
                }
                case "wrap-dek" -> {
                    requireArgs(args, 2, "wrap-dek <base64Dek>");
                    String wrapped = transitService.wrapDek(args[1]);
                    System.out.println("Wrapped DEK:");
                    System.out.println(wrapped);
                }
                case "unwrap-dek" -> {
                    requireArgs(args, 2, "unwrap-dek <wrappedDek>");
                    String unwrapped = transitService.unwrapDek(args[1]);
                    System.out.println("Unwrapped DEK (Base64):");
                    System.out.println(unwrapped);
                }
                case "rotate-key" -> {
                    transitService.rotateKey();
                    System.out.println("Key rotated.");
                    System.out.println(transitService.keyMetadata());
                }
                case "key-info" -> {
                    System.out.println(transitService.keyMetadata());
                }
                case "demo-rotation" -> {
                    EnvelopeCryptoService.RotationDemoResult result = envelopeCryptoService.demoRotation();
                    System.out.println("Original DEK:");
                    System.out.println(result.dekBase64());
                    System.out.println();
                    System.out.println("Wrapped DEK V1:");
                    System.out.println(result.wrappedDekV1());
                    System.out.println();
                    System.out.println("Wrapped DEK V2:");
                    System.out.println(result.wrappedDekV2());
                    System.out.println();
                    System.out.println("Unwrapped from V1:");
                    System.out.println(result.unwrappedDekV1());
                    System.out.println();
                    System.out.println("Unwrapped from V2:");
                    System.out.println(result.unwrappedDekV2());
                }
                case "encrypt-file" -> {
                    requireArgs(args, 3, "encrypt-file <inputFile> <outputEnvelopeJson>");
                    Path out = envelopeCryptoService.encryptFile(Path.of("C:/Users/navne/IdeaProjects/VaultDemo/src/main/resources/input.txt"),
                            Path.of("C:/Users/navne/IdeaProjects/VaultDemo/src/main/resources/input_enc.txt"));
                    System.out.println("Encrypted envelope written to:");
                    System.out.println(out.toAbsolutePath());

                }
                case "encrypt-huge-file" -> {
                    // Hardcoded paths as per your snippet, but using Path.of for clarity
                    Path inputFile = Path.of("C:/Users/navne/IdeaProjects/VaultDemo/src/main/resources/OneGBFile.txt");
                    Path outputDir = Path.of("C:/Users/navne/IdeaProjects/VaultDemo/src/main/resources");

                    System.out.println("🚀 Starting Huge File Encryption (Streaming)...");
                    long startTime = System.currentTimeMillis();

                    try {
                        // Calling your streaming method
                        envelopeCryptoService.encryptHugeFile(inputFile, outputDir);

                        long duration = (System.currentTimeMillis() - startTime) / 1000;
                        System.out.println("✅ Success! Encryption took: " + duration + " seconds.");
                        System.out.println("Files created in: " + outputDir.toAbsolutePath());
                        System.out.println("1. " + inputFile.getFileName() + ".bin (Encrypted Data)");
                        System.out.println("2. " + inputFile.getFileName() + ".json (Metadata/Keys)");

                    } catch (Exception e) {
                        System.err.println("❌ Encryption failed: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
                case "decrypt-file" -> {
                    requireArgs(args, 3, "decrypt-file <inputEnvelopeJson> <outputFile>");
                    Path out = envelopeCryptoService.decryptFile(Path.of(args[1]), Path.of(args[2]));
                    System.out.println("Decrypted file written to:");
                    System.out.println(out.toAbsolutePath());
                }
                case "decrypt-huge-file" -> {
                    // Hardcoded paths for the demo
                    Path inputDir = Path.of("C:/Users/navne/IdeaProjects/VaultDemo/src/main/resources");
                    String baseFileName = "OneGBFile.txt";

                    Path jsonFile = inputDir.resolve(baseFileName + ".json");
                    Path binFile = inputDir.resolve(baseFileName + ".bin");
                    Path outputFile = inputDir.resolve("RESTORED_" + baseFileName);

                    System.out.println("🔓 Starting Huge File Decryption (Streaming)...");
                    long startTime = System.currentTimeMillis();

                    try {
                        // Calling the streaming decryption method
                        envelopeCryptoService.decryptHugeFile(jsonFile, binFile, outputFile);

                        long duration = (System.currentTimeMillis() - startTime) / 1000;
                        System.out.println("✅ Success! Decryption took: " + duration + " seconds.");
                        System.out.println("Restored file: " + outputFile.toAbsolutePath());

                    } catch (Exception e) {
                        System.err.println("❌ Decryption failed: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
                default -> printUsage();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void requireArgs(String[] args, int expected, String usage) {
        if (args.length < expected) {
            throw new IllegalArgumentException("Usage: " + usage);
        }
    }

    private void printUsage() {
        System.out.println("""
                Usage:
                  login
                  generate-dek
                  wrap-dek <base64Dek>
                  unwrap-dek <wrappedDek>
                  rotate-key
                  key-info
                  demo-rotation
                  encrypt-file <inputFile> <outputEnvelopeJson>
                  decrypt-file <inputEnvelopeJson> <outputFile>
                """);
    }

    public static void generateReadableTestFile(Path outputPath, long sizeInGb) throws IOException {
        long targetSizeBytes = sizeInGb * 1024 * 1024 * 1024;

        // Create a 1MB block of human-readable text
        StringBuilder sb = new StringBuilder();
        String line = "PROPERTY OF VAULT-DEMO-SERVICE: This is a test line for encryption/decryption validation. Index: ";
        while (sb.length() < 1024 * 1024) {
            sb.append(line).append(sb.length()).append("\n");
        }
        byte[] buffer = sb.toString().getBytes(StandardCharsets.UTF_8);

        System.out.println("Generating " + sizeInGb + "GB readable file...");
        long startTime = System.currentTimeMillis();

        try (OutputStream os = new BufferedOutputStream(new FileOutputStream(outputPath.toFile()))) {
            long bytesWritten = 0;
            while (bytesWritten < targetSizeBytes) {
                int toWrite = (int) Math.min(buffer.length, targetSizeBytes - bytesWritten);
                os.write(buffer, 0, toWrite);
                bytesWritten += toWrite;
            }
        }

        long duration = (System.currentTimeMillis() - startTime) / 1000;
        System.out.println("✅ Generated 1GB in " + duration + " seconds.");
    }
}
