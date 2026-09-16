package com.merito.agynb.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Charset dos .java gerados: o do projeto NetBeans, não um default da casa.
 * .form continua UTF-8 (XML Matisse).
 */
public final class SourceEncoding {

    public static final Charset DEFAULT = Charset.forName("windows-1252");

    private SourceEncoding() {
    }

    /**
     * Explicit encoding vence; senão source.encoding do nbproject mais próximo;
     * senão windows-1252. Encoding desconhecido → IllegalArgumentException.
     */
    public static Charset resolve(String explicit, File fromDir) {
        if (explicit != null && !explicit.trim().isEmpty()) {
            return charsetOf(explicit);
        }
        Charset fromProject = fromProjectProperties(fromDir);
        return fromProject != null ? fromProject : DEFAULT;
    }

    public static Charset fromProjectProperties(File start) {
        File dir = start;
        while (dir != null) {
            File props = new File(new File(dir, "nbproject"), "project.properties");
            if (props.isFile()) {
                String enc = readSourceEncoding(props);
                return enc != null ? charsetOf(enc) : null;
            }
            dir = dir.getParentFile();
        }
        return null;
    }

    public static Charset charsetOf(String nome) {
        try {
            return Charset.forName(canonical(nome));
        } catch (Exception e) {
            throw new IllegalArgumentException("Encoding não suportado: " + nome);
        }
    }

    static String canonical(String nome) {
        String k = nome.trim().toLowerCase().replace('_', '-');
        if ("utf8".equals(k) || "utf-8".equals(k)) {
            return "UTF-8";
        }
        if ("windows-1252".equals(k) || "cp1252".equals(k)) {
            return "windows-1252";
        }
        if ("iso-8859-1".equals(k) || "latin-1".equals(k) || "latin1".equals(k)) {
            return "ISO-8859-1";
        }
        return nome.trim();
    }

    private static String readSourceEncoding(File props) {
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(new FileInputStream(props), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                String s = line.trim();
                if (s.startsWith("#") || !s.contains("=")) {
                    continue;
                }
                int eq = s.indexOf('=');
                if (!"source.encoding".equals(s.substring(0, eq).trim())) {
                    continue;
                }
                String valor = s.substring(eq + 1).trim();
                if (valor.isEmpty() || valor.contains("${")) {
                    return null;
                }
                return valor;
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }
}
