package com.merito.agynb.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.EnumSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gerencia o token de autenticação local da Bridge Suite.
 *
 * O token é gerado na primeira inicialização e gravado em
 * {@code ~/.config/agy-nb-bridge/token} com permissão 0600. Os clientes
 * (netbeans-mcp-server.py e agy_nb_client.py) leem o mesmo arquivo e enviam
 * o valor no header {@code X-Bridge-Token} — nenhuma configuração manual é
 * necessária. Apenas processos rodando como o mesmo usuário do sistema
 * conseguem ler o token, o que impede que páginas web ou outros usuários da
 * máquina acionem a bridge.
 */
public final class BridgeToken {

    private static final Logger LOG = Logger.getLogger(BridgeToken.class.getName());
    private static final SecureRandom RANDOM = new SecureRandom();

    private static volatile String currentToken;

    private BridgeToken() {
    }

    /**
     * Caminho do arquivo de token compartilhado entre o plugin e os clientes.
     */
    public static Path tokenFile() {
        return Paths.get(System.getProperty("user.home"), ".config", "agy-nb-bridge", "token");
    }

    /**
     * Retorna o token vigente, reutilizando o arquivo existente ou gerando
     * um novo na primeira execução.
     */
    public static synchronized String getOrCreate() {
        if (currentToken != null) {
            return currentToken;
        }

        Path file = tokenFile();
        try {
            if (Files.isRegularFile(file)) {
                String stored = new String(Files.readAllBytes(file), StandardCharsets.UTF_8).trim();
                if (!stored.isEmpty()) {
                    currentToken = stored;
                    return currentToken;
                }
            }
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Não foi possível ler o token existente em " + file, ex);
        }

        String generated = generateToken();
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, generated.getBytes(StandardCharsets.UTF_8));
            restrictToOwner(file);
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "Não foi possível gravar o token de autenticação em " + file, ex);
        }
        currentToken = generated;
        return currentToken;
    }

    /**
     * Compara o valor recebido no header com o token vigente em tempo
     * constante, evitando vazamento por timing.
     */
    public static boolean isValid(String candidate) {
        if (candidate == null || candidate.isEmpty()) {
            return false;
        }
        String expected = getOrCreate();
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                candidate.getBytes(StandardCharsets.UTF_8));
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static void restrictToOwner(Path file) {
        try {
            Set<PosixFilePermission> perms = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(file, perms);
        } catch (UnsupportedOperationException ex) {
            // Sistema de arquivos sem POSIX (Windows): ACL padrão do perfil do usuário já restringe o acesso.
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Não foi possível restringir permissões do token em " + file, ex);
        }
    }
}
