package com.example.vaultdemo.cli;

import com.example.vaultdemo.service.EnvelopeCryptoService;
import com.example.vaultdemo.service.VaultAuthService;
import com.example.vaultdemo.service.VaultTransitService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
public class VaultCliRunner implements CommandLineRunner {

    private final VaultAuthService authService;
    private final VaultTransitService transitService;
    private final EnvelopeCryptoService envelopeCryptoService;

    public VaultCliRunner(VaultAuthService authService,
                          VaultTransitService transitService,
                          EnvelopeCryptoService envelopeCryptoService) {
        this.authService = authService;
        this.transitService = transitService;
        this.envelopeCryptoService = envelopeCryptoService;
    }

    @Override
    public void run(String... args) {
        if (args.length == 0) {
            printUsage();
            return;
        }

        try {
            switch (args[0]) {
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
                    Path out = envelopeCryptoService.encryptFile(Path.of(args[1]), Path.of(args[2]));
                    System.out.println("Encrypted envelope written to:");
                    System.out.println(out.toAbsolutePath());
                }
                case "decrypt-file" -> {
                    requireArgs(args, 3, "decrypt-file <inputEnvelopeJson> <outputFile>");
                    Path out = envelopeCryptoService.decryptFile(Path.of(args[1]), Path.of(args[2]));
                    System.out.println("Decrypted file written to:");
                    System.out.println(out.toAbsolutePath());
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
}
