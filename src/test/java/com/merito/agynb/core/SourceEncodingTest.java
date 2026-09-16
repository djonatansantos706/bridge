package com.merito.agynb.core;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Assert;
import org.junit.Test;

public class SourceEncodingTest {

    @Test
    public void resolveExplicitUtf8() {
        Assert.assertEquals("UTF-8", SourceEncoding.resolve("utf-8", null).name());
        Assert.assertEquals("UTF-8", SourceEncoding.resolve("UTF-8", null).name());
    }

    @Test
    public void resolveExplicitWindows1252() {
        Assert.assertEquals("windows-1252", SourceEncoding.resolve("windows-1252", null).name());
        Assert.assertEquals("windows-1252", SourceEncoding.resolve("cp1252", null).name());
    }

    @Test
    public void resolveExplicitIso88591() {
        Assert.assertEquals("ISO-8859-1", SourceEncoding.resolve("ISO-8859-1", null).name());
        Assert.assertEquals("ISO-8859-1", SourceEncoding.resolve("latin1", null).name());
    }

    @Test
    public void resolveFromProjectProperties() throws Exception {
        File root = Files.createTempDirectory("nb-enc").toFile();
        File nb = new File(root, "nbproject");
        Assert.assertTrue(nb.mkdirs());
        Files.write(new File(nb, "project.properties").toPath(),
                "source.encoding=UTF-8\n".getBytes(StandardCharsets.UTF_8));
        File src = new File(new File(root, "src"), "com");
        Assert.assertTrue(src.mkdirs());
        Assert.assertEquals("UTF-8", SourceEncoding.resolve(null, src).name());
        Assert.assertEquals("UTF-8", SourceEncoding.fromProjectProperties(src).name());
    }

    @Test
    public void resolveDefaultWithoutProject() {
        File nowhere = new File("/tmp/agy-nb-bridge-no-nbproject");
        Assert.assertEquals("windows-1252", SourceEncoding.resolve(null, nowhere).name());
        Assert.assertNull(SourceEncoding.fromProjectProperties(nowhere));
    }

    @Test
    public void explicitVenceProjeto() throws Exception {
        File root = Files.createTempDirectory("nb-enc-override").toFile();
        File nb = new File(root, "nbproject");
        Assert.assertTrue(nb.mkdirs());
        Files.write(new File(nb, "project.properties").toPath(),
                "source.encoding=UTF-8\n".getBytes(StandardCharsets.UTF_8));
        Assert.assertEquals("windows-1252",
                SourceEncoding.resolve("windows-1252", root).name());
    }

    @Test(expected = IllegalArgumentException.class)
    public void charsetOfUnknown() {
        SourceEncoding.charsetOf("EBCDIC");
    }
}
